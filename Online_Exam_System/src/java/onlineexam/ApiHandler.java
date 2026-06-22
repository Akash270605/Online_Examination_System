/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;     // Represents one HTTP request + response
import com.sun.net.httpserver.HttpHandler;          // Interface for handling HTTP requests.

import java.io.IOException;
import java.io.InputStream;             //Read data from the request body
import java.io.OutputStream;        // Write data to the response body
import java.net.URLDecoder;         // Decode URL-Encoded characters. (like %20 -> space)
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;         // Like HashMap but keeps insertion order
import java.util.List;
import java.util.Map;


/**
 * This class is the controller layer of the application.
 * 
 * The browser sends HTTP requests such as:
 *      - POST /api/login
 *      - GET /api/admin/exams
 *      - POST /api/student/attempts/{id}/submit
 * 
 * ApiHandler checks authentication, call DataStore, and returns JSON.
 */
public class ApiHandler implements HttpHandler{
    
    //Session lasts 8 hours (8 * 60 * 60 * 1000 milliseconds)
    private static final long SESSION_DURATION_MILLIS = 8L * 60L * 60L * 1000L;
    
    // After 3 failed login attempts, the account is temporary locked
    private static final int MAX_FAILED_LOGINS = 3;
    
    // Lock duration: 5 minutes ( 5 * 60 * 1000 milliseconds)
    private static final long LOGIN_LOCK_MILLIS = 5L * 60L * 1000L;
    
    //Reference to the DataStore that manages all users, exams, and attempts
    private final DataStore store;
    
    
    
     //Sessions maps sessions token -> login information.    
    // Map from session token string to Session object (userId + expiration time)
    private final Map<String, Session> sessions = new HashMap<>();
    
    // Map from username to LoginFailure object (tracks failed attempts and lock time)
    private final Map<String, LoginFailure> loginFailures = new HashMap<>();
    
    ApiHandler(DataStore store){
        this.store = store;
    }
    
    // handle() is the main entry point. It wraps route() in try/catch for error handling.
    @Override
    public void handle(HttpExchange exchange) throws IOException{
        try{
            
            route(exchange);    // Decide which function to call based on the URL path
        }catch(IllegalArgumentException ex){
            
            // 400 = Bad request (e.g. missing fields, invalid data)
            sendJson(exchange, 400, Map.of("ok", false, "message", ex.getMessage()));
        }catch(SecurityException ex){
            
            //403 = Forbidden (e.g. not logged in, wrong role)
            sendJson(exchange, 403, Map.of("ok", false, "message", ex.getMessage()));
        }catch(Exception ex){
            
            // 500 = Internal Server Error (unexpected bugs)
            ex.printStackTrace();
            sendJson(exchange, 500, Map.of("ok", false, "message", "Unexpected server error."));
        }
    }
    
    // route() reads the HTTP method and path, then calls the correct handler method
    private void route(HttpExchange exchange) throws IOException{
        String method = exchange.getRequestMethod();    // e.g. "GET", "POST", "DELETE"
        String path = exchange.getRequestURI().getPath();   // e.g. "/api/login"
        
        
        
        //----------- PUBLIC endpoints------------
        
        // POST /api/login - authenticate a user and create a session
        if(path.equals("/api/login") && method.equals("POST")){
            login(exchange);
            return;
        }
        
        // POST /api/register - create a new student account
        if(path.equals("/api/register") && method.equals("POST")){
            register(exchange);
            return;
        }
        
        // POST /api/logout - destroy the current session
        if(path.equals("/api/logout") && method.equals("POST")){
            logout(exchange);
            return;
        }
        
        // GET /api/me - returns the currently logged-in user's info (used on page load)
        if (path.equals("/api/me") && method.equals("GET")) {
            User user = requireUser(exchange); // Will throw if not logged in
            sendJson(exchange, 200, Map.of("ok", true, "user", publicUser(user)));
            return;
        }
        
        
        // ---------- ADMIN only endpoints -------------
        
        // /api/admin/users - list all users (GET) or create a new user (POST)
        if(path.equals("/api/admin/users")){
            requireRole(exchange, Role.ADMIN);      // Only admins can manage users
            if(method.equals("GET")){
                listUsers(exchange);
                return;
            }
            
            if(method.equals("POST")){
                createUser(exchange);
                return;
            }
        }
        
        // /api/admin/users/{id} (DELETE) - remove a user from the system.
        if(path.startsWith("/api/admin/users/") && method.equals("DELETE")){
            requireRole(exchange, Role.ADMIN);
            deleteUser(exchange, path);
            return;
        }
        
        // /api/admin/exams - list exams (GET) or create a new exam (POST)
        if(path.equals("/api/admin/exams")){
            requireRole(exchange, Role.ADMIN);
            if(method.equals("GET")){
                listAdminExams(exchange);
                return;
            }
            
            if(method.equals("POST")){
                createExam(exchange);
                return;
            }
        }
        
        // /api/admin/exams/{id}/toggle (POST) - activate or deactivate an exam
        if(path.startsWith("/api/admin/exams") && path.endsWith("/toggle") && method.equals("POST")){
            requireRole(exchange, Role.ADMIN);
            toggleExam(exchange, path);
            return;
        } 
        
        // /api/admin/monitor (GET) - view all active student attempts
        if(path.equals("/api/admin/monitor") && method.equals("GET")){
            requireRole(exchange, Role.ADMIN);
            monitor(exchange);
            return;
        }
        
        
        // -------- STUDENT-only endpoints------------
        
        // /api/student/exams (GET) - list available exams that the student can start
        if(path.equals("/api/student/exams") && method.equals("GET")){
            requireRole(exchange, Role.STUDENT);
            listStudentExams(exchange);
            return;
        }
        
        // /api/student/exams/{id}/start (POST) - begin an exam attempt
        if(path.startsWith("/api/student/exams/") && path.endsWith("/start") && method.equals("POST")){
            User student = requireRole(exchange, Role.STUDENT);
            startExam(exchange, path, student);
            return;
        }
        
        // /api/student/attempts/{id}/.... - various operations on a live attempt
        if(path.startsWith("/api/student/attempts/")){
            User student = requireRole(exchange, Role.STUDENT);
            handleAttemptRoute(exchange, path, method, student);
            return;
        }
        
        // /api/student/results (GET) - list the student's past results
        if(path.equals("/api/student/results") && method.equals("GET")){
            User student= requireRole(exchange, Role.STUDENT);
            studentResults(exchange, student);
            return;
        }
        
        // if no route matched, return 404 NOT Found
        sendJson(exchange, 404, Map.of("ok", false, "message", "API route not found."));
    }
    
    
    // login() handles user authentication: check username/password, create session
    private void login(HttpExchange exchange) throws IOException{
        
        // Read the JSON body from the request (contains "username" and "password"
        Map<String, Object> body = readJsonObject(exchange);
        
        // safeTrim handles null values and removes leading/trailing spaces
        String username = SecurityUtil.safeTrim(body.get("username")).toLowerCase();
        String password = SecurityUtil.safeTrim(body.get("password"));
        
        
        /**
         * Basic brute-force protection: after several failed login attempts, this username 
         * is locked briefly.
         */
        
        // check if this username is currently locked due to too many failed logins
        LoginFailure failure = loginFailures.get(username);
        long now = System.currentTimeMillis();
        if(failure != null && failure.lockedUntil > now){
            
            // 429 = Too many requests
            sendJson(exchange, 429, Map.of("ok", false, "message", "Too many failed logins. Try again later..."));
            return;
        }
        
        // Look up the user by username.
        User user = store.findUserByUsername(username);
        
        //Checks: user exists And account is active And password matches the stored hash
        boolean valid = user != null
                && user.active
                && SecurityUtil.verifyPassword(password, user.passwordSalt, user.passwordHash);
        
        if(!valid){
            // failed login : increment the failure counter
            registerLoginFailure(username);
            sendJson(exchange, 401, Map.of("ok", false, "message", "Invalid username or password."));
            return;
        }
        
        // Successful login: clear any previous failure records
        loginFailures.remove(username);
        
        // Create a session (sets a cookie in the browser)
        startSession(exchange, user);
        sendJson(exchange, 200, Map.of("ok", true, "user", publicUser(user)));
    }
    
    
    // register() creates a new Student account
    private void register(HttpExchange exchange) throws IOException{
        Map<String, Object> body = readJsonObject(exchange);
        String username = SecurityUtil.safeTrim(body.get("username")).toLowerCase();
        String fullName = SecurityUtil.safeTrim(body.get("fullName"));
        String password = SecurityUtil.safeTrim(body.get("password"));
        
        //Validation: minimum length checks
        if(username.length() < 3){
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }
        
        if(fullName.length() < 2){
            throw new IllegalArgumentException("Full name is required.");
        }
        
        if(password.length() < 8){
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        
        // Check if username is already taken
        if(store.findUserByUsername(username) != null){
            throw new IllegalArgumentException("This username is already registered. Please log in instead.");
        }
        
        
        // Create the user in the DataStore (password will be hashed automatically)
        User user = store.createUser(username, fullName, Role.STUDENT, password);
        loginFailures.remove(username);
        startSession(exchange, user);       // Auto-login after registration.
        sendJson(exchange, 201, Map.of("ok", true, "user", publicUser(user)));
    }
    
    
    // startSession() generates a random token and sets it as a browser cookie
    private void startSession(HttpExchange exchange, User user){
        long now = System.currentTimeMillis();
        
        //Generates a secure random session token
        String token = SecurityUtil.newSessionToken();
        
        //Store the session: userId + expiration time (8 hours from now)
        sessions.put(token, new Session(user.id, now + SESSION_DURATION_MILLIS));
        
        
        /**
         * HttpOnly means JavaScript cannot read the cookie.
         * SameSite = Strict reduces cross-site request risk.
         */
        
        // Set the cookie header so the browser stores it
        exchange.getResponseHeaders().add("Set-Cookie", 
                "EXAM_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + (SESSION_DURATION_MILLIS / 1000));
    }
    
    
    // logout() destroys the session by removing the token from the memory and clearing the cookie
    private void logout(HttpExchange exchange) throws IOException{
        String token = cookie(exchange, "EXAM_SESSION");
        
        if(token != null){
            sessions.remove(token);     // Remove the session from the server's memory
        }
        
        //Tell the browser to delete the cookie (Max-Age = 0 means immediate expiration)
        exchange.getResponseHeaders().add("Set-Cookie", 
                "EXAM_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        sendJson(exchange, 200, Map.of("ok", true));
    }
    
    
    // listUsers() returns all users (admin only)
    private void listUsers(HttpExchange exchange) throws IOException{
        List<Map<String, Object>> users = new ArrayList<>();
        for(User user : store.users()){
            users.add(publicUser(user));    // Convert to safe representation (no password data)
        }
        
        sendJson(exchange, 200, Map.of("ok", true, "users", users));
    }
    
    
    // createUser() lets an admin create a user with any role
    private void createUser(HttpExchange exchange) throws IOException{
        Map<String, Object> body = readJsonObject(exchange);
        String username = SecurityUtil.safeTrim(body.get("username")).toLowerCase();
        String fullName = SecurityUtil.safeTrim(body.get("fullName"));
        String password = SecurityUtil.safeTrim(body.get("password"));
        String roleText = SecurityUtil.safeTrim(body.get("role")).toUpperCase();
        
        //Validation checks
        if(username.length() < 3){
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }
        
        if(fullName.length() < 2){
            throw new IllegalArgumentException("Full name is required.");
        }
        
        if(password.length() < 8){
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        
        
        // Convert the role string ("ADMIN" or "STUDENT") to Role enum
        Role role = Role.valueOf(roleText);
        User user = store.createUser(username, fullName, role, password);
        sendJson(exchange, 201, Map.of("ok", true, "user", publicUser(user)));
    }
    
    
    // deleteUser() removes a user (admin cannot delete themselves)
    private void deleteUser(HttpExchange exchange, String path) throws IOException{
        
        // Extract the user Id from the URL path, e.g. "/api/admin/users/usr_abc" -> "usr_abc"
        String userId = decodePathPart(path.substring("/api/admin/users/".length()));
        
        //Prevent admin from deleting themselves
        User currentUser = requireUser(exchange);
        if(currentUser.id.equals(userId)){
            throw new SecurityException("You cannot delete your own account.");
        }
        
        store.deleteUser(userId);
        sendJson(exchange, 200, Map.of("ok", true, "message", "User deleted successfully."));
    }
    
    
    // listAdminExams() returns all exams with full details (including correct answers)
    private void listAdminExams(HttpExchange exchange) throws IOException{
        List<Map<String, Object>> exams = new ArrayList<>();
        for(Exam exam : store.exams()){
            exams.add(adminExam(exam));     // includes correctIndex for admin
        }
        
        sendJson(exchange, 200, Map.of("ok", true, "exams", exams));
    }
    
    
    // createExam() parses the JSON body and builds a new Exam with questions
    @SuppressWarnings("unchecked")
    private void createExam(HttpExchange exchange) throws IOException{
        Map<String, Object> body = readJsonObject(exchange);
        String title = SecurityUtil.safeTrim(body.get("title"));
        String description = SecurityUtil.safeTrim(body.get("description"));
       int durationMinutes = toInt(body.get("durationMinutes"), 30);    // Default 30 minutes
       List<Object> rawQuestions = (List<Object>) body.get("questions");
       
       // validation
       if(title.length() < 3){
           throw new IllegalArgumentException("Exam title must be at least 3 characters.");
       }
       
       if(durationMinutes < 1 || durationMinutes > 300){
           throw new IllegalArgumentException("Duration must be between 1 and 300 minutes.");
       }
       
       if(rawQuestions == null || rawQuestions.isEmpty()){
           throw new IllegalArgumentException("Add at least one question.");
       }
       
       // Convert each raw JSON question into a Question object
       List<Question> questions = new ArrayList<>();
       for(Object item : rawQuestions){
           Map<String, Object> questionMap = (Map<String, Object>) item;
           String text = SecurityUtil.safeTrim(questionMap.get("text"));
           List<Object> rawOptions = (List<Object>) questionMap.get("options");
           int correctIndex = toInt(questionMap.get("correctIndex"), -1);
           int points = toInt(questionMap.get("points"), 1);
           
           if(text.length() < 5){
               throw new IllegalArgumentException("Each question needs meaningful text.");
           }
           
           if(rawOptions == null || rawOptions.size() < 2){
               throw new IllegalArgumentException("Each question needs at least two options.");
           }
           
           if(correctIndex < 0 || correctIndex >= rawOptions.size()){
               throw new IllegalArgumentException("Correct option is invalid for one question.");
           }
           
           
           // Convert the option strings, filtering out blanks
           List<String> options = new ArrayList<>();
           for(Object option : rawOptions){
               String optionText = SecurityUtil.safeTrim(option);
               if(optionText.isBlank()){
                   throw new IllegalArgumentException("Options cannot be blank.");
               }
               options.add(optionText);
           }
           
           questions.add(new Question(SecurityUtil.newId("q"), text, options, correctIndex, Math.max(1, points)));
       }
       
       Exam exam = store.createExam(title, description, durationMinutes, questions);
       sendJson(exchange, 201, Map.of("ok", true, "exam", adminExam(exam)));
    }
    
    
    // toggleExam() activates or deactivates an exam (controls student access)
    private void toggleExam(HttpExchange exchange, String path) throws IOException{
        
        // Extract the exam Id from the URL path
        String examId = decodePathPart(path.substring("/api/admin/exams/".length(), path.length() - "/toggle".length()));
        Map<String, Object> body = readJsonObject(exchange);
        boolean active = Boolean.TRUE.equals(body.get("active"));
        store.setExamActive(examId, active);
        sendJson(exchange, 200, Map.of("ok", true));
    }
    
    
    // monitor() returns all currently active exam attempts for the admin dashboard
    private void monitor(HttpExchange exchange) throws IOException{
        List<Map<String, Object>> activeAttempts = new ArrayList<>();
        for(Attempt attempt : store.attempts()){
            if(attempt.submitted){
                continue;       //Skip attempts that have already been submitted
            }
            
            Exam exam = store.findExam(attempt.examId);
            User student = store.findUserById(attempt.studentId);
            if(exam == null || student == null){
                continue;       // Skip if data is inconsistent
            }
            
            // Build a map of useful monitoring information
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attemptId", attempt.id);
            row.put("student", student.fullName);                   // Student's display name
            row.put("username", student.username);              // Student's login name
            row.put("exam", exam.title);                                    // Exam title
            row.put("startedAt", attempt.startedAt);                // When they started
            row.put("remainingSeconds", store.remainingSeconds(attempt));    // Time left
            row.put("lastHeartbeatAt", attempt.lastHeartbeatAt);    // Last sign of life
            row.put("eventCount", attempt.events.size());    // No. of anti-cheat alerts
            activeAttempts.add(row);
        }
        
        // sort by student name for easier reading
        activeAttempts.sort(Comparator.comparing(item -> item.get("student").toString()));
        sendJson(exchange, 200, Map.of("ok", true, "attempts", activeAttempts));
    }
    
    // listStudentExams() returns only active exams (without exposing correct answers)
    private void listStudentExams(HttpExchange exchange) throws IOException{
        List<Map<String, Object>> exams = new ArrayList<>();
        for(Exam exam : store.exams()){
            if(exam.active){        // Student can only see active exams
                exams.add(studentExamSummary(exam));    // No correctIndex in the response
            }
        }
        
        sendJson(exchange, 200, Map.of("ok", true, "exams", exams));
    }
    
    
    // startExam() begins a new attempt for the student on the given exam
    private void startExam(HttpExchange exchange, String path, User student) throws IOException{
        String examId = decodePathPart(path.substring("/api/student/exams/".length(), path.length() - "/start".length()));
        Attempt attempt = store.startAttempt(examId, student.id);
        sendJson(exchange, 200, Map.of("ok", true, "attemptId", attempt.id));
    }
    
    
    // handleAttemptRoute() dispatches sub-routes under /api/student/attempts/{id}/...
    private void handleAttemptRoute(HttpExchange exchange, String path, String method, User student) throws IOException{
        
        // split path into parts: ["api", "student", "attempts", "{attemptId}", "...", "..."]
        String afterPrefix = path.substring("/api/student/attempts/".length());
        String[] parts = afterPrefix.split("/");
        String attemptId = decodePathPart(parts[0]);
        Attempt attempt = requireStudentAttempt(attemptId, student);    //Verify ownership
        
        // Determine which sub-route based on the URL pattern
        if(parts.length == 1 && method.equals("GET")){
            attemptDetails(exchange, attempt);  // GET /api/student/attempts/{id}
            return;
        }
        
        if(parts.length == 2 && parts[1].equals("answer") && method.equals("POST")){
            saveAnswer(exchange, attempt);      // POST /api/student/attempts/{id}/answer
            return;
        }
        
        if(parts.length == 2 && parts[1].equals("event") && method.equals("POST")){
            recordEvent(exchange, attempt);     // POST /api/student/attempts/{id}/event
            return;
        }
        
        if(parts.length == 2 && parts[1].equals("heartbeat") && method.equals("POST")){
            store.heartbeat(attempt.id);        // POST /api/student/attempt/{id}/heartbeat
            sendJson(exchange, 200, Map.of("ok", true));
            return;
        }
        
        if(parts.length == 2 && parts[1].equals("submit") && method.equals("POST")){
            Attempt submitted = store.submitAttempt(attempt.id);        // POST ..../submit
            sendJson(exchange, 200, Map.of("ok", true, "result", resultMap(submitted)));
            return;
        } 
        
        sendJson(exchange, 404, Map.of("ok", false, "message", "Attempt route not found"));
    }
    
    
    // attemptDetails() returns the exam questions and student's answers for the current attempt
    private void attemptDetails(HttpExchange exchange, Attempt attempt) throws IOException{
        
        // Auto-submit if time has expired
        if(!attempt.submitted && store.isAttemptExpired(attempt)){
            attempt = store.submitAttempt(attempt.id);
        }
        
        Exam exam = store.findExam(attempt.examId);
        List<Map<String, Object>> questions = new ArrayList<>();
        for(Question question : exam.questions){
            
            /**
             * Students do not receive correctIndex. The answer key stays on the 
             * server so the browser cannot reveal correct answers.
             */
            Map<String, Object> questionMap = new LinkedHashMap<>();
            questionMap.put("id", question.id);
            questionMap.put("text", question.text);
            questionMap.put("options", question.options);   // The possible answers
            questionMap.put("points", question.points);     // How many points
            questionMap.put("answer", attempt.answers.get(question.id));    // Student's selection
            questions.add(questionMap);
        }
        
        Map<String, Object> examMap = new LinkedHashMap<>();
        examMap.put("id", exam.id);
        examMap.put("title", exam.title);
        examMap.put("description", exam.description);
        examMap.put("durationMinutes", exam.durationMinutes);
        examMap.put("questions", questions);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("submitted", attempt.submitted);
        response.put("remainingSeconds", store.remainingSeconds(attempt));
        response.put("exam", examMap);
        response.put("result", attempt.submitted ? resultMap(attempt) : null);
        sendJson(exchange, 200, response);
    }
    
    
    // saveAnswer() stores the student's selected option for a specific question
    private void saveAnswer(HttpExchange exchange, Attempt attempt) throws IOException{
        Map<String, Object> body = readJsonObject(exchange);
        String questionId = SecurityUtil.safeTrim(body.get("questionId"));
        int optionIndex = toInt(body.get("optionIndex"), -1);
        store.saveAnswer(attempt.id, questionId, optionIndex);
        sendJson(exchange, 200, Map.of("ok", true));
    }
    
    
    // recordEvent() stores an anti-cheat event (e.g. tab switch, copy attempt)
    private void recordEvent(HttpExchange exchange, Attempt attempt) throws IOException{
        Map<String, Object> body = readJsonObject(exchange);
        String type = SecurityUtil.safeTrim(body.get("type"));
        String detail = SecurityUtil.safeTrim(body.get("detail"));
        
        if(type.isBlank()){
            type = "unknown";       // Fallback type if none provided
        }
        
        store.recordEvent(attempt.id, type, detail);
        sendJson(exchange, 200, Map.of("ok", true));
    }
    
    
    // studentResults() returns all submitted attempts for the logged-in student
    private void studentResults(HttpExchange exchange, User student) throws IOException{
        List<Map<String, Object>> results = new ArrayList<>();
        for(Attempt attempt : store.attempts()){
            
            // Only include this student's submitted attempts
            if(attempt.studentId.equals(student.id) && attempt.submitted){
                results.add(resultMap(attempt));
            }
        }
        
        // Sort by submission time, most recent first
        results.sort((left, right) -> Long.compare((Long) right.get("submittedAt"), (Long) left.get("submittedAt")));
        sendJson(exchange, 200, Map.of("ok", true, "results", results));
    }
    
    
    // requireStudentAttempt() verifies that an attempt belongs to the given student
    private Attempt requireStudentAttempt(String attemptId, User student){
        Attempt attempt = store.findAttempt(attemptId);
        
        //Check : attempt exists and the student owns it
        if(attempt == null || !attempt.studentId.equals(student.id)){
            throw new SecurityException("You cannot access this attempt.");
        }
        return attempt;
    }
    
    
    // requireUser() checks that the request has a valid session and returns the user
    private User requireUser(HttpExchange exchange){
        
        // Read the EXAM_SESSION cookie from the request headers
        String token = cookie(exchange, "EXAM_SESSION");
        if(token == null){
            throw new SecurityException("Login is required.");
        }
        
        // Look up the session by token
        Session session = sessions.get(token);
        if(session == null || session.expiresAt < System.currentTimeMillis()){
            sessions.remove(token);     // Clean up expired sessions
            throw new SecurityException("Session expired. Please log in again.");
        }
        
        // Find the user associated with this session
        User user = store.findUserById(session.userId);
        if(user == null || !user.active){
            throw new SecurityException("User account is not active.");
        }
        
        
        /**
         * Sliding expiration: active users remain logged in while they are working,
         * but old inactive sessions expire.
         */
        
        // Extend the session expiration so active users stay logged in
        session.expiresAt = System.currentTimeMillis() + SESSION_DURATION_MILLIS;
        return user;
    }
    
    
    // requireRole() checks that the user is logged in and has the required role
    private User requireRole(HttpExchange exchange, Role role){
        User user = requireUser(exchange);  // First make sure they are logged in
        if(user.role != role){
            throw new SecurityException("This action requires the " + role + " role.");
        }
        return user;
    }
    
    
    // registerLoginFailure() increments the failure counter and locks if threshold reached
    private void registerLoginFailure(String username){
        LoginFailure failure = loginFailures.getOrDefault(username, new LoginFailure());
        failure.count++;
        
        if(failure.count >= MAX_FAILED_LOGINS){
            // Lock the account for 5 minutes
            failure.lockedUntil = System.currentTimeMillis() + LOGIN_LOCK_MILLIS;
            failure.count = 0;      // Reset counter after locking
        }
        
        loginFailures.put(username, failure);
    }
    
    
    // publicUser() creates a safe map of user data (no password hash/salt included)
    private Map<String, Object> publicUser(User user){
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.id);
        map.put("username", user.username);
        map.put("fullName", user.fullName);
        map.put("role", user.role.name());
        map.put("active", user.active);
        map.put("createdAt", user.createdAt);
        return map;
    }
    
    
   // adminExam() returns full exam details including correct answers (admin only)
    private Map<String, Object> adminExam(Exam exam){
        Map<String, Object> map = studentExamSummary(exam);
        List<Map<String, Object>> questions = new ArrayList<>();
        for(Question question : exam.questions){
            
            // Admin can see the correctIndex (student cannot)
            questions.add(Map.of(
                    "id", question.id,
                    "text", question.text,
                    "options", question.options,
                    "correctIndex", question.correctIndex,
                    "points", question.points));
        }
        
        map.put("questions", questions);
        map.put("createdAt", exam.createdAt);
        return map;
    }
    
    
    // studentExamSummary() returns a safe summary of an exam (no correct answers)
    private Map<String, Object> studentExamSummary(Exam exam){
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exam.id);
        map.put("title", exam.title);
        map.put("description", exam.description);
        map.put("durationMinutes", exam.durationMinutes);
        map.put("active", exam.active);
        map.put("questionCount", exam.questions.size());
        map.put("totalPoints", exam.questions.stream().mapToInt(question -> question.points).sum());
        return map;
    }
    
    
    // resultMap() builds a summary of a completed attempt for the results screen
    private Map<String, Object> resultMap(Attempt attempt){
        Exam exam = store.findExam(attempt.examId);
        
        // Calculate percentage (avoid division by zero)
        int percent = attempt.totalPoints == 0 ? 0 : Math.round((attempt.score * 100f) / attempt.totalPoints);
        
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attemptId", attempt.id);
        map.put("examTitle", exam == null ? "Unknown exam" : exam.title);
        map.put("score", attempt.score);                    // Points earned
        map.put("totalPoints", attempt.totalPoints);   // Maximum possible
        map.put("percent", percent);                            // Percentage score
        map.put("startedAt", attempt.startedAt);        // When they began
        map.put("submittedAt", attempt.submittedAt);  // When they submitted
        map.put("eventCount", attempt.events.size());   // No. of security alerts
        return map;
    }
    
    
    // readJsonObject() reads the HTTP request body and parses it as a JSON object
    private Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException{
        String body = readBody(exchange);
        if(body.isBlank()){
            return new LinkedHashMap<>();   // Empty body = empty map
        }
        
        return Json.parseObject(body);      // Use our custom JSON parser
    }
    
    
    // readBody() reads all bytes from the request body and converts to a UTF-8 string
    private String readBody(HttpExchange exchange) throws IOException{
        try (InputStream input = exchange.getRequestBody()){
            
            // readAllBytes() reads the entire request body into memory
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    
    // sendJson() writes a JSON response back to the browser
    private void sendJson(HttpExchange exchange, int status, Object data) throws IOException{
        
        // convert the data map to a JSON string using our custom Json class
        byte[] response = Json.stringify(data).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        
        // no-store tells the browser not to cache API responses
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, response.length);
        try(OutputStream output = exchange.getResponseBody()){
            output.write(response);         // Send the JSON bytes to the browser
        }
    }
    
    
    // cookie() extracts the value of a specific cookie from the request header
    private String cookie(HttpExchange exchange, String name){
        
        // Get all Cookie headers from the request
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if(cookieHeaders == null){
            return null;        // No cookies at all
        }
        
        // Loop through all cookies to find the one we want
        for(String cookieHeader : cookieHeaders){
            String[] parts = cookieHeader.split(";");   // Multiple cookies are separated by ;
            for(String part : parts){
                String[] pair = part.trim().split("=", 2);      //Split "name=value"
                if(pair.length == 2 && pair[0].equals(name)){
                    return pair[1];     // return the cookie value
                }
            }
        }
        
        return null;    // Cookie not found
    }
    
    
    // decodePathPart() decodes URL-encoded characters (e.g. %20 becomes space)
    private String decodePathPart(String value){
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    
    
    //toInt() safely converts an object to an integer, using a fallback if conversion fails
    private int toInt(Object value, int fallback){
        if(value == null){
            return fallback;
        }
        
        if(value instanceof Number number){
            return number.intValue();   // Direct conversion for Number types
        }
        
        try{
            return Integer.parseInt(value.toString());  // Try parsing the string
        }catch(NumberFormatException ex){
            return fallback;        // parsing failed, use default
        }
    }
    
    
    // Session is a simple class that ties a userId to an expiration time
    private static class Session{
        String userId;      // the ID of the logged-in user
        long expiresAt;     // when this session expires  (milliseconds since 1970)
        
        Session(String userId, long expiresAt){
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
    
    //LoginFailure tracks how many times a login has failed and when the lock ends
    private static class LoginFailure{
        int count;                  // How many consecutive failures so far
        long lockedUntil;   // Timestamp when the lock expires (0 = not locked)
    }
}