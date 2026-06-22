# Online Examination System Using Java and Apache NetBeans

This project is a complete beginner-friendly online examination system built with Java.
It opens directly in Apache NetBeans as a Java SE project and runs without external libraries,
Maven, Gradle, MySQL, or Tomcat.

The Java application starts a small local web server. Students and admins then use the browser UI.

## Main Features

- Admin and Student login
- Password hashing with PBKDF2
- Session-based secure login using HTTP-only cookies
- Admin user creation
- Admin exam and question management
- Student timed exams
- Automatic grading
- Instant result display
- Admin real-time monitoring using student heartbeats
- Anti-cheating event tracking for tab switching, copy, paste, right-click, and focus loss
- Encrypted local data file using AES-GCM
- Clean responsive browser UI
- Beginner-friendly comments in the Java, JavaScript, HTML, CSS, and project files

## Demo Accounts

These accounts are created automatically on first run:

| Role | Username | Password |
| --- | --- | --- |
| Admin | `admin` | `admin@123` |
| Student | `student` | `student@123` |

## How To Open In Apache NetBeans

1. Open Apache NetBeans.
2. Choose **File > Open Project**.
3. Select this project folder:
4. Click **Open Project**.
5. Select the 'Main' class and run the file.
6. The app will try to open the browser automatically.
7. If the browser does not open, manually open this address:

   `http://localhost:8080`



## How To Run From Terminal

Compile:

```powershell
javac -d build/classes src/onlineexam/*.java
```

Run:

```powershell
java -cp build/classes onlineexam.Main
```

## Optional Stronger Encryption Secret

The app encrypts the local data file at:

`data/exam-data.bin`

For a real deployment, set a private secret before starting the server:

```powershell
$env:EXAM_APP_SECRET="replace-this-with-a-long-random-secret"
java -cp build/classes onlineexam.Main
```

If you change the secret after data has already been saved, the old encrypted file cannot be opened.

## Project Files

```text
build.xml                   Ant build file used by NetBeans
nbproject/                  NetBeans project settings
src/onlineexam/Main.java    Starts the server
src/onlineexam/ApiHandler.java
                             Handles login, exams, grading, and monitoring APIs
src/onlineexam/DataStore.java
                             Saves encrypted data
src/onlineexam/Json.java    Small JSON parser/writer to avoid external libraries
src/onlineexam/Models.java  User, Exam, Question, Attempt model classes
src/onlineexam/SecurityUtil.java
                             Password hashing, session tokens, and helper methods
web/index.html              Browser entry point
web/styles.css              UI design
web/app.js                  Frontend logic
```
