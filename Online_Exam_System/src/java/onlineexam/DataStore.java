/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

// IO classes for reading/writing the encrypted data file
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;         // Modern way to read/write files
import java.nio.file.Path;         
import java.security.SecureRandom;      // Cryptographically strong random number generator
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;

// Java Cryptography Extension (JCE) classes for AES encryption
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

        
 /**
  * This class is the persistence layer of the system.
  * It stores all data in one encrypted local file : data/exam-data.bin
  */     
class DataStore {
    
    // MAGIC is a 4-character identifier we write at the start of the file to verify it is our format
    private static final String MAGIC = "OES1";
    
    // GCM (Galois/Counter Mode) uses a 128-bit authentication tag to verify data integrity
    private static final int GCM_TAG_BITS = 128;
    
    // PBKDF2 iterations: higher = slower = harder for attackers to brute-force passwords
    private static final int KEY_ITERATIONS = 180_000;
    
    // Salt used to derive the encryption key from the secret passphrase
    private static final byte[] KEY_SALT = SecurityUtil.utf8("OnlineExamSystemSaltV1");
    
    
    //Path to the encrypted data file (e.g. "data/exam-data.bin")
    private final Path dataFile;
    
    // The AES key used to encrypt/decrypt the data file
    private final SecretKey encryptionKey;
    
    // SecureRandom produces random bytes suitable for cryptography (Stronger than Math.random())
    private final SecureRandom random = new SecureRandom();
    
    //AppState holds all users, exams, and attempts in memory while the server runs
    private AppState state;
    
    
    // Constructor: loads existing data from file or creates default data if file does not exist
    DataStore(Path dataFile){
        this.dataFile = dataFile;
        
        //Derive an AES encryption key from the EXAM_APP_SECRET environment variable
        this.encryptionKey = createEncryptionKey();
        
        // Try loading from file; if file does not exist create sample data (seedInitialData)
        this.state = loadOrCreate();
    }
    
    // Returns a copy of all users (copy protects the internal list from accidental modification)
    synchronized List<User> users(){
        return new ArrayList<>(state.usersById.values());
    }
    
    // Returns a copy of all exams
    synchronized List<Exam> exams(){
        return new ArrayList<>(state.examsById.values());
    }
    
    // Returns a copy of all attempts
    synchronized List<Attempt> attempts(){
        return new ArrayList<>(state.attemptsById.values());
    }
    
    // Find a user by their unique ID (e.g. "usr_abc123...")
    synchronized User findUserById(String id){
        return state.usersById.get(id);
    }
    
    // Find a user by their login username (case-insensitive)
    synchronized User findUserByUsername(String username){
        
        //Loop through all users and check if the username matches (ignoring case)
        for(User user : state.usersById.values()){
            if(user.username.equalsIgnoreCase(username)){
                return user;
            }
        }
        
        // Return null if no user has that username
        return null;
    }
    
    // Find an exam by its unique ID
    synchronized Exam findExam(String id){
        return state.examsById.get(id);
    }
    
    // Find an attempt by its unique ID
    synchronized Attempt findAttempt(String id){
        return state.attemptsById.get(id);
    }
    
    // Creates a new user after checking the username is not already taken
    synchronized User createUser(String username, String fullName, Role role, String password){
        
        // Reject if the username already exists (duplicate check)
        if(findUserByUsername(username) != null){
            throw new IllegalArgumentException("Username already exists.");
        }
        
        // Hash the password before storing (never store plain passwords!)
        PasswordHash passwordHash = SecurityUtil.hashPassword(password);
        
        // Generate a unique ID with prefix 'usr' and store the user
        User user = new User(SecurityUtil.newId("usr"), username, fullName, role,
                passwordHash.salt, passwordHash.hash);
        state.usersById.put(user.id, user);
        
        // Save to disk so the new user persists after server restart
        save();
        return user;
    }
    
    // Create a new exam with the given title, description, duration and questions
    synchronized Exam createExam(String title, String description, int durationMinutes, List<Question> questions){
        Exam exam = new Exam(SecurityUtil.newId("exam"), title, description, durationMinutes, questions);
        state.examsById.put(exam.id, exam);
        save();     // Persist to disk immediately
        return exam;
    }
    
    //Activate or deactivate an exam (only active exams can be started by students)
    synchronized void setExamActive(String examId, boolean active){
        Exam exam = requireExam(examId);    // throws error if exam does not exist
        exam.active = active;
        save();
    }
    
    // Start a new attempt for a student on a specific exam
    synchronized Attempt startAttempt(String examId, String studentId){
        Exam exam = requireExam(examId);
        
        // Prevent student from starting an exam that is not active
        if(!exam.active){
            throw new IllegalArgumentException("This exam is not active.");
        }
        
        /**
         * If the student refreshes the browser, we return the open attempt instead of
         * creating duplicate attempts for the same exam.
         */
        
        // Check if there is already an active (not submitted) attempt from this student on this exam
        for(Attempt attempt : state.attemptsById.values()){
            if(attempt.examId.equals(examId) && attempt.studentId.equals(studentId) && !attempt.submitted){
                return attempt;     // Return existing attempt instead of creating a new one
            }
        }
        
        // Create a brand new attempt
        Attempt attempt = new Attempt(SecurityUtil.newId("att"), examId, studentId);
        state.attemptsById.put(attempt.id, attempt);
        save();
        return attempt;
    }
    
    // Permanently remove a user from the system
    synchronized void deleteUser(String userId){
        if(!state.usersById.containsKey(userId)){
            throw new IllegalArgumentException("User not found.");
        }
        state.usersById.remove(userId);
        save();
    }
    
    // Save the student's answer to a specific question within an attempt
    synchronized void saveAnswer(String attemptId, String questionId, int optionIndex){
        Attempt attempt = requireAttempt(attemptId);
        ensureAttemptOpen(attempt);  //Prevent saving answers to submitted/ expired attempts.
        
        // Store the selected option index (e.g. 0 = Option A, 1 = Option B)
        attempt.answers.put(questionId, optionIndex);
        
        // Update the heartbeat timestamp so admin monitoring knows student is active
        attempt.lastHeartbeatAt = System.currentTimeMillis();
        save();
    }
    
    // Record an anti-cheating event (e.g. tab switch, copy/paste attempt)
    synchronized void recordEvent(String attemptId, String type, String detail){
        Attempt attempt = requireAttempt(attemptId);
        attempt.events.add(new AntiCheatEvent(type, detail));
        attempt.lastHeartbeatAt = System.currentTimeMillis();
        save();
    }
    
    // Heartbeat: the student's browser calls this periodically to show they are still active
    synchronized void heartbeat(String attemptId){
        Attempt attempt = requireAttempt(attemptId);
        attempt.lastHeartbeatAt = System.currentTimeMillis();
        save();
    }
    
    // Submit an attempt: calculate the score and mark it as finished
    synchronized Attempt submitAttempt(String attemptId){
        Attempt attempt = requireAttempt(attemptId);
        
        // if already submitted, just return it without recalculating
        if(attempt.submitted){
            return attempt;
        }
        
        /**
         * Automated grading happens here. The server compares each selected
         * option with the hidden answer stored in the exam questions.
         */
        Exam exam = requireExam(attempt.examId);
        int score = 0;
        int total = 0;
        
        // Loop through every question in the exam
        for(Question question : exam.questions){
            total += question.points;       // Add up the total possible points
            Integer selectedIndex = attempt.answers.get(question.id);     // what student pick
            
            // If the student selected the correct option index, award the points
            if(selectedIndex != null && selectedIndex == question.correctIndex){
                score += question.points;
            }
        }
        
        // Store the score and mark as submitted
        attempt.score = score;
        attempt.totalPoints = total;
        attempt.submitted = true;
        attempt.submittedAt = System.currentTimeMillis();
        attempt.lastHeartbeatAt = attempt.submittedAt;
        save();
        return attempt;
    }
    
    // Check if the attempt's time has run out (duration from the start passed)
    synchronized boolean isAttemptExpired(Attempt attempt){
        Exam exam = requireExam(attempt.examId);
        
        // Convert duration from minutes to milliseconds
        long durationMillis = exam.durationMinutes * 60_000L;
        
        // if current time is past start + duration, the attempt has expired
        return System.currentTimeMillis() > attempt.startedAt + durationMillis;
    }
    
    // Calculate how many seconds remain before the attempt expires
    synchronized long remainingSeconds(Attempt attempt){
        Exam exam = requireExam(attempt.examId);
        
        // Calculate the exact end time (start + duration in milliseconds)
        long endsAt = attempt.startedAt + exam.durationMinutes * 60_000L;
        
        // Math.max ensures we never return a negative number
        long remainingMillis = Math.max(0L, endsAt - System.currentTimeMillis());
        
        // convert milliseconds to seconds
        return remainingMillis / 1000L;
    }
    
    // Helper : fetch an exam or throw a clear error if not found
    private Exam requireExam(String examId){
        Exam exam = state.examsById.get(examId);
        if(exam == null){
            throw new IllegalArgumentException("Exam was not found.");
        }
        
        return exam;
    }
    
    // Helper: fetch an attempt or throw a clear error if not found
    private Attempt requireAttempt(String attemptId){
        Attempt attempt = state.attemptsById.get(attemptId);
        if(attempt == null){
            throw new IllegalArgumentException("Attempt was not found.");
        }
        
        return attempt;
    }
    
    // Helper: verify the attempt is still open (not submitted and not expired)
    private void ensureAttemptOpen(Attempt attempt){
        if(attempt.submitted){
            throw new IllegalArgumentException("This attempt has already been submitted.");
        }
        
        // If time is up, auto-submit and then throw an error
        if(isAttemptExpired(attempt)){
            submitAttempt(attempt.id);      // Auto-submit to preserve whatever answers were saved
            throw new IllegalArgumentException("Time is over. The attempt was submitted automatically.");
        }
    }
    
    // Load data from file, or create default data if the file does not exist
    private AppState loadOrCreate(){
        try{
            //Check if the data file exists on disk
            if(!Files.exists(dataFile)){
                
                // No file yet, so create the initial data with sample users and exam
                AppState freshState = seedInitialData();
                this.state = freshState;
                save();     // Write the initial data to disk
                return freshState;  
            }
            
            // File exists, so read and decrypt it
            return readEncryptedState();
        }catch(Exception ex){
            
            // If decryption fails (e.g. wrong EXAM_APP_SECRET).
            throw new IllegalStateException("Could not load encrypted exam data. Check EXAM_APP_SECRET.", ex);
        }
    }
    
    // Create the initial data: one admin user, one student user, and one sample exam
    private AppState seedInitialData(){
        AppState seeded = new AppState();
        
        // Create admin user with password "admin@123"
        PasswordHash adminPassword = SecurityUtil.hashPassword("admin@123");
        User admin = new User(SecurityUtil.newId("usr"), "admin", "System Administrator",
                Role.ADMIN, adminPassword.salt, adminPassword.hash);
        seeded.usersById.put(admin.id, admin);
        
        // Create student user with password "student@123"
        PasswordHash studentPassword = SecurityUtil.hashPassword("student@123");
        User student = new User(SecurityUtil.newId("usr"), "student", "Demo Student", 
                Role.STUDENT, studentPassword.salt, studentPassword.hash);
        seeded.usersById.put(student.id, student);
        
        // Builds a sample exam about Java security basics (3 questions, 10 minutes duration)
    List<Question> questions = new ArrayList<>();
    questions.add(new Question(SecurityUtil.newId("q"), "Which Java feature hides internal data from direct access?",
                List.of("Encapsulation", "Compilation", "Iteration", "Importing"), 0, 1));
    questions.add(new Question(SecurityUtil.newId("q"), "Which HTTP status usually means login is required?",
                List.of("200", "401", "404", "500"), 1, 1));
    questions.add(new Question(SecurityUtil.newId("q"), "Why should passwords be hashed?",
                List.of("To make CSS faster", "To protect users if data leaks", "To remove Java files", "To increase monitor brightness"), 1,1));
    
    // Wrap the questions in an Exam object (10 minutes duration)
    Exam exam = new Exam(SecurityUtil.newId("exam"), "Java Security Basics", 
                "A short sample exam that demonstrates automated grading.", 10, questions);
    seeded.examsById.put(exam.id, exam);
    
    return seeded;
    }
    
    // Read the encrypted file from disk and decrypt it back into an AppState object
    private AppState readEncryptedState() throws Exception{
        
        // DataInputStream lets us read primitive types (int , UTF strings) from a file
        try(DataInputStream input = new DataInputStream(Files.newInputStream(dataFile))){
            
            // Read the 4-character magic number to verify this is our file format
            String magic = input.readUTF();
            if(!MAGIC.equals(magic)){
                throw new IOException("Unknown data file format.");
            }
            
            // Read the IV (Initialization Vector) length and bytes
            // IV is a random value needed to decrypt AES-GCM data
            int ivLength = input.readInt();
            byte[] iv = input.readNBytes(ivLength);
            
            // Read the actual encrypted data length and bytes
            int encryptedLength = input.readInt();
            byte[] encryptedBytes = input.readNBytes(encryptedLength);
            
            // Set up AES-GCM decryption with the stored IV
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            
            // Decrypt the bytes
            byte[] plainBytes = cipher.doFinal(encryptedBytes);
            
            // Convert the decrypted bytes back into an AppState object using Java serialization
            try(ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(plainBytes))){
                return (AppState) objectInput.readObject();
            }
        }
    }
    
    // Encrypt and write the current AppState to the data file
    private void save(){
        try{
            // Ensure the parent directory (e.g., "data/") exists
            Files.createDirectories(dataFile.getParent());
            
            // Convert AppState object into a byte array using Java serialization
            byte[] plainBytes = serializeState();
            
            /**
             * AES-GCM needs a fresh IV for every encryption operation.
             * Reusing an IV with the same key would weaken encryption.
             */
            
            // Generate 12 random bytes for the IV (12 bytes is standard for AES-GCM)
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            
            // Set up AES-GCM encryption with the fresh IV
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedBytes = cipher.doFinal(plainBytes);
            
            // Write to file : magic number, IV length, IV bytes, encrypted data length, encrypted data
            try(DataOutputStream output = new DataOutputStream(Files.newOutputStream(dataFile))){
                output.writeUTF(MAGIC);         // 4-char magic identifier
                output.writeInt(iv.length);         // How many bytes the IV is
                output.write(iv);                           // The IV bytes themselves
                output.writeInt(encryptedBytes.length); // How many encrypted bytes follow
                output.write(encryptedBytes);       // The encrypted data
            }
        }catch(Exception ex){
            throw new IllegalStateException("Could not save encrypted exam data.", ex);
        }
    }
    
    // Convert the AppState object into a byte array using Java's built-in-serialization
    private byte[] serializeState() throws IOException{
        
        // ByteArrayOutputStream is a buffer that grows as we write data into it
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        
        // ObjectOutputStream converts Java objects into bytes (serialization)
        try(ObjectOutputStream objectOutput = new ObjectOutputStream(bytes)){
            objectOutput.writeObject(state);    // Write the entire AppState tree
        }
        return bytes.toByteArray();
    }
    
    // Derive an AES encryption key from the EXAM_APP_SECRET environment variable
    private SecretKey createEncryptionKey(){
        try{
            
           // First check EXAM_APP_SECRET environment variable
           String secret = System.getenv("EXAM_APP_SECRET");
           
           // If not set, check the exam.secret system property; if neither, use default
           if(secret == null || secret.isBlank()){
               secret = System.getProperty("exam.secret", "development-secret-change-before-production");
           }
           
           // PBKDF2 is a slow key derivation function that makes brute-force attacks harder
           KeySpec spec = new PBEKeySpec(secret.toCharArray(), KEY_SALT, KEY_ITERATIONS, 256);
           SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
           
           // Generate the raw key bytes (256 bits = 32 bytes for AES-256)
           byte[] keyBytes = factory.generateSecret(spec).getEncoded();
           
           // Wrap the raw bytes into a SecretKey object suitable for AES
           return new SecretKeySpec(keyBytes, "AES");
        }catch(Exception ex){
            throw new IllegalStateException("Could not create encryption key.", ex);
        }
    }
}