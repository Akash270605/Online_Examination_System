/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

// Serializable allows the objects to be converted to bytes for saving the disk
import java.io.Serializable;
import java.time.Instant;       // Provides the current timestamp in milliseconds
import java.util.ArrayList;
import java.util.LinkedHashMap;     // Like HashMap but remembers the order items were added
import java.util.List;
import java.util.Map;

// Role is an enum (enumeration) that defines two possible values : ADMIN or STUDENT
// Enums are great for fixed sets of options that should not change at runtime
enum Role{
    ADMIN,
    STUDENT
}

// User represents a person who can log in to the system (either admin or student)
class User implements Serializable{
    private static final long serialVersionUID = 1L;    // Version ID for Java serialization
    
    String id;                       // Unique identifier
    String username;        // Login name
    String fullName;         // Display name
    Role role;                     // Either ADMIN or STUDENT
    String passwordSalt; //Random bytes used during hashing (defends against rainbow tables)
    String passwordHash; // The result of hashing the password + salt together
    boolean active;           // Can this user log in? Set to false to disable an account
    long createdAt;         // Timestamp (milliseconds since 1970) when this user was created.
    
    // Constructor: called when creating a new User object
    User(String id, String username, String fullName, Role role, String passwordSalt, String passwordHash){
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.passwordSalt = passwordSalt;
        this.passwordHash = passwordHash;
        this.active = true;         // New users start as active by default
        
        //Instant.now().toEpochMilli() gives the current time as long number
        this.createdAt = Instant.now().toEpochMilli();
    }
}


// Question is one multiple-choice item within an Exam
class Question implements Serializable{
    private static final long serialVersionUID = 1L;
    
    String id;                      // Unique identifier 
    String text;                   // The question text displayed to the student
    List<String> options; // The answer choices.
    int correctIndex;         // Index (0-based) of the correct option in the options list
    int points;                      // How many points this questions is worth
    
    Question(String id, String text, List<String> options, int correctIndex, int points){
        this.id = id;
        this.text = text;
        
        // Copy the options list so external changes don't affect this question
        this.options = new ArrayList<>(options);
        this.correctIndex = correctIndex;
        this.points = points;
    }    
}

// Exam is a collection of questions with a title, description, and time limit
class Exam implements Serializable{
    private static final long serialVersionUID = 1L;
    
    String id;                  // Unique identifier
    String title;               // Short name like "Java Security Basics"
    String description; // Longer explanation of what the exam covers
    int durationMinutes;    // How long students have to complete the exam
    boolean active;         // Can student start this exam? (Controlled by admin)
    long createdAt;         // Timestamp when the exam was created
    List<Question> questions;   // The list of questions in this exam
    
    Exam(String id, String title, String description, int durationMinutes, List<Question> questions){
        this.id = id;
        this.title = title;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.questions = new ArrayList<>(questions);
        this.active = true;     // New exams are active by default
        this.createdAt = Instant.now().toEpochMilli();
    }
}


// AntiCheatEvent records suspicious actions detected during an exam attempt
class AntiCheatEvent implements Serializable{
    private static final long serialVersionUID = 1L;
    
    long happenedAt;         // When the event occurred (milliseconds)
    String type;                   // Category of event (e.g, "focus_lost", "tab_hidden", "copy")
    String detail;                // Human-readable description (e.g. "Browser window lost focus.")
    
    AntiCheatEvent(String type, String detail){
        this.happenedAt = Instant.now().toEpochMilli();
        this.type = type;
        this.detail = detail;
    }
}

// Attempt represents one student sitting one exam from the start to submission
class Attempt implements Serializable{
    private static final long serialVersionUID = 1L;
    
    String id;                      // Unique identifier
    String examId;           // Which exam the student is taking
    String studentId;       // Which student is taking it
    long startedAt;           // When the attempt began (milliseconds)
    long submittedAt;     // When the attempt was submitted (0 if not yet submitted)    
    long lastHeartbeatAt; // Last time the student's browser sent a heartbeat signal
    boolean submitted;   // Has the student submitted this attempt?
    int score;                       // Points earned by the student (calculated at submission time)
    int totalPoints;               // Maximum possible points for this exam
    
    
    /**
     * answers map question id -> student's selected options index.
     * 
     * LinkedHashMap is used instead of HashMap because LinkedHashMap keeps
     * insertion order, which makes debugging and result display easier.
     */
    Map<String, Integer> answers;
    
    //List of anti-cheat events recorded during this attempt
    List<AntiCheatEvent> events;
    
    Attempt(String id, String examId, String studentId){
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.startedAt = Instant.now().toEpochMilli();
        this.lastHeartbeatAt = this.startedAt;
        this.answers = new LinkedHashMap<>();
        this.events = new ArrayList<>();
    }
}

// AppStore is the root container that holds all data in memory.
// It serializes everything into one object for easy file saving
class AppState implements Serializable{
    private static final long serialVersionUID = 1L;
    
    // userById maps users ID string to the User object (e.g. "usr_1" -> User{...})
    Map<String, User> usersById = new LinkedHashMap<>();
    
    // examsById maps exam ID string to the Exam object
    Map<String, Exam> examsById = new LinkedHashMap<>();
    
    // attemptsById maps attempt ID string to the Attempt object
    Map<String, Attempt> attemptsById = new LinkedHashMap<>();
}
