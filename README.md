# 🎓 Online Examination System

A beginner-friendly, secure online examination platform built with pure Java and no external dependencies. Designed for educational institutions to conduct and monitor student exams with built-in security features and real-time monitoring.

## ✨ Features

### Authentication & Security
- **Secure Login System** - Admin and Student authentication
- **PBKDF2 Password Hashing** - Industry-standard encryption for stored passwords
- **HTTP-Only Session Cookies** - Session-based authentication protecting against XSS attacks
- **AES-GCM Encryption** - All exam data encrypted locally

### Admin Capabilities
- User account management and creation
- Exam and question management
- Real-time student monitoring using heartbeat tracking
- Live visibility of student activity and progress
- Anti-cheating event logs and reports

### Student Features
- Timed exams with countdown timer
- Intuitive exam interface with responsive design
- Instant automatic grading
- Immediate result display
- Anti-cheating monitoring and notifications

### Anti-Cheating Protection
- Tab switching detection and tracking
- Copy/Paste prevention and logging
- Right-click context menu blocking
- Window focus loss detection
- Comprehensive event logging for administrators

### Technical Highlights
- **Zero External Dependencies** - Runs with Java built-in libraries only
- **No Database Required** - Encrypted local file storage
- **No Additional Servers** - Built-in HTTP server on port 8080
- **No Build Tools Required** - Open and run directly in Apache NetBeans
- **Beginner-Friendly** - Extensively commented code across Java, JavaScript, HTML, and CSS
- **Responsive UI** - Works seamlessly on desktop, tablet, and mobile browsers

## 🛠️ Tech Stack

- **Backend:** Java SE (JDK 11+)
- **Web Server:** Java HttpServer (built-in)
- **Frontend:** HTML5, CSS3, Vanilla JavaScript
- **Data Storage:** AES-GCM encrypted binary files
- **IDE:** Apache NetBeans (recommended)
- **Security:** PBKDF2, AES-GCM, HTTP-only cookies

## 📋 Prerequisites

- **Java Development Kit (JDK)** - Version 11 or higher
- **Apache NetBeans** - Latest version (optional, but recommended for development)
- **Web Browser** - Any modern browser (Chrome, Firefox, Safari, Edge)

## 📝 Demo Accounts
The system creates these accounts automatically on first launch:

```
Role	  Username	Password
Admin	  admin	    admin@123
Student	  student	student@123
```

## 🎯 Usage

### For Administrators

-  Login with admin credentials
-  Create Users - Add new student accounts
-  Create Exams - Set exam name, duration, and passing score
-  Add Questions - Create multiple-choice or text questions
-  Monitor - View real-time student activity and exam progress
-  Review Results - Check grades, anti-cheat events, and student performance

### For Students

-  Login with student credentials
-  View Available Exams - See exams you're enrolled in
-  Take Exam - Answer questions within the time limit
-  Submit - Review and submit your answers
-  View Results - See your grade and performance immediately

## 🚀 Getting Started

### Option 1: Run in Apache NetBeans (Recommended)

1. **Open Apache NetBeans**
2. Choose **File > Open Project**
3. Select this project folder
4. Click **Open Project**
5. Select the `Main` class and run the file
6. The app will try to open the browser automatically
7. If the browser does not open, manually navigate to:

   http://localhost:8080
http://localhost:8080

### Option 2: Run From Terminal

**Compile:**
```powershell
javac -d build/classes src/java/onlineexam/*.java
```

**Run:**
```
java -cp build/classes onlineexam.Main
```

