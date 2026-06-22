/*
 * FRONTEND SCRIPT
 * ---------------
 * This file controls what the user sees in the browser.
 *
 * The backend is Java. The frontend is plain JavaScript so there is no React,
 * Angular, or Node.js setup. This keeps the NetBeans project easy to run.
 *
 * How it works:
 * 1. boot() is called on page load. It checks if you're logged in.
 * 2. If logged in, renderShell() builds the layout with top bar + tabs.
 * 3. If not logged in, renderLogin() shows the login/register form.
 * 4. The user clicks tabs, which call different render functions.
 * 5. Each render function calls api() to fetch data from the Java server,
 *    then builds HTML using template literals (the ` backtick strings).
 */

// Get a reference to the empty <div id="app"> in index.html
const app = document.querySelector("#app");

const state = {
  /*
   * state stores information that the UI needs between function calls.
   * Keeping it in one object makes it easier to understand what the current
   * user is doing.
   */
  user: null,             // The logged-in user object (from /api/me)
  view: "dashboard",      // Which tab/screen is currently showing
  activeAttemptId: null,  // ID of the exam attempt the student is working on
  examTimer: null,        // Interval ID for the countdown timer
  monitorTimer: null,     // Interval ID for auto-refreshing the admin monitor
  heartbeatTimer: null,   // Interval ID for sending heartbeats during exam
  antiCheatInstalled: false  // Have we added the anti-cheat event listeners yet?
};

async function api(path, options = {}) {
  /*
   * fetch() sends HTTP requests to the Java server.
   * credentials: "same-origin" tells the browser to include the login cookie.
   *
   * This is a wrapper that:
   * - Automatically adds JSON headers
   * - Sends cookies (so the server knows who you are)
   * - Throws an error if the response is not OK
   * - Parses the JSON response
   */
  const response = await fetch(path, {
    credentials: "same-origin",              // Send cookies with the request
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options                               // Merge in any custom options (method, body, etc.)
  });

  // Read the response body as text first, then parse as JSON
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  // If the HTTP status is not OK (e.g. 400, 401, 500) or the server sent ok: false
  if (!response.ok || data.ok === false) {
    throw new Error(data.message || "Request failed."); // Show the error message
  }
  return data;
}

function html(strings, ...values) {
  /*
   * This small helper joins template literal parts together. It lets us write
   * readable HTML blocks without adding a frontend framework.
   *
   * Example: html`<h1>${title}</h1>` returns "<h1>Hello</h1>"
   */
  return strings.map((part, index) => part + (values[index] ?? "")).join("");
}

function escapeHtml(value) {
  /*
   * Text from users should be escaped before it is inserted into the page.
   * This helps prevent script injection in the browser.
   *
   * For example: if a student's name contains <script>alert('hack')</script>,
   * this function converts the < and > to safe HTML entities.
   */
  // Build the replacement using character codes to avoid auto-formatting issues
  const amp = String.fromCharCode(38) + "amp;";
  const lt = String.fromCharCode(38) + "lt;";
  const gt = String.fromCharCode(38) + "gt;";
  const quot = String.fromCharCode(38) + "quot;";
  const apos = String.fromCharCode(38) + "#39;";
  const lookup = {
    "&": amp,
    "<": lt,
    ">": gt,
    '"': quot,
    "'": apos
  };
  return String(value ?? "").replace(/[&<>"']/g, function(ch) { return lookup[ch]; });
}

function showToast(message) {
  /*
   * A toast gives feedback without navigating away from the current screen.
   * It appears at the bottom-right and auto-disappears after 3.2 seconds.
   */
  const toast = document.createElement("div");
  toast.className = "toast";             // Apply CSS styling
  toast.textContent = message;           // Set the message text
  document.body.appendChild(toast);      // Add to the page
  setTimeout(() => toast.remove(), 3200); // Remove after 3.2 seconds
}

function formatDate(ms) {
  /*
   * Java stores dates as milliseconds since 1970. The browser converts that
   * number into a local readable date.
   *
   * Example: 1700000000000 -> "Jan 15, 2024, 10:13:20 AM"
   */
  if (!ms) return "Not available";        // Handle null/undefined
  return new Date(ms).toLocaleString();   // Convert to local date/time string
}

function formatTime(seconds) {
  /*
   * Convert seconds into MM:SS format for the exam countdown timer.
   *
   * Example: 125 -> "02:05"
   */
  const safeSeconds = Math.max(0, Number(seconds) || 0);  // Prevent negative numbers
  const minutes = Math.floor(safeSeconds / 60);
  const rest = safeSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`;
}

async function boot() {
  /*
   * On page load we ask the server "who am I?"
   * If the session cookie is valid, show the dashboard. Otherwise show login.
   *
   * This is the entry point of the entire frontend.
   */
  try {
    // Call GET /api/me to check if there is an active session
    const data = await api("/api/me");
    state.user = data.user;         // Store the user info
    renderShell();                  // Show the logged-in interface
  } catch {
    // If the request fails (no session), show the login screen
    renderLogin();
  }
}

function renderLogin(mode = "login") {
  /*
   * renderLogin() builds the login/register page.
   * mode can be "login" or "register" to toggle between forms.
   */
  clearTimers();                     // Stop any running timers
  state.user = null;                 // Clear any stale user data
  const isRegister = mode === "register";
  // Build the HTML using template literals (backtick strings)
  app.innerHTML = html`
    <main class="login-page">
      <section class="login-card">
        <div class="login-hero">
          <h2>Online Examination System</h2>
          <p>Conduct timed exams, grade instantly, and monitor active student attempts from one clean dashboard.</p>
        </div>
        <form class="login-form" id="authForm">
          <div>
            <h3>${isRegister ? "Create Student Account" : "Secure Login"}</h3>
            <p class="muted">
              ${isRegister
                ? "Register first if your username is not already in the database."
                : "Already registered students and admins can log in here."}
            </p>
          </div>
          <!-- Toggle buttons: Login / Sign Up -->
          <div class="auth-switch" aria-label="Authentication options">
            <button type="button" class="${isRegister ? "secondary" : ""}" data-auth-mode="login">Login</button>
            <button type="button" class="${isRegister ? "" : "secondary"}" data-auth-mode="register">Sign Up</button>
          </div>
          <!-- Full name field only shows when registering -->
          ${isRegister ? html`
            <label class="field">
              <span>Full name</span>
              <input name="fullName" autocomplete="name" required>
            </label>
          ` : ""}
          <label class="field">
            <span>Username</span>
            <input name="username" autocomplete="username" required>
          </label>
          <label class="field">
            <span>Password</span>
            <input name="password"
                   type="password"
                   autocomplete="${isRegister ? "new-password" : "current-password"}"
                   minlength="${isRegister ? "8" : "1"}"
                   required>
          </label>
          <button type="submit">${isRegister ? "Create Account" : "Login"}</button>
          <div class="demo-box">
            ${isRegister ? html`
              <strong>New accounts</strong><br>
              Public sign up creates student accounts only. Admin accounts can still be created from the admin dashboard.
            ` : html`
              <strong>Demo Accounts</strong><br>
              Admin: admin / admin@123<br>
              Student: student / student@123
            `}
          </div>
        </form>
      </section>
    </main>
  `;

  // Add click handlers to the Login/Sign Up toggle buttons
  document.querySelectorAll("[data-auth-mode]").forEach((button) => {
    button.addEventListener("click", () => renderLogin(button.dataset.authMode));
  });

  // Handle form submission (login or register)
  document.querySelector("#authForm").addEventListener("submit", async (event) => {
    event.preventDefault();  // Prevent the browser from reloading the page
    const form = new FormData(event.currentTarget);
    const payload = {
      username: form.get("username"),
      password: form.get("password")
    };
    if (isRegister) {
      payload.fullName = form.get("fullName");
    }

    try {
      // Send the request to either /api/register or /api/login
      const data = await api(isRegister ? "/api/register" : "/api/login", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      state.user = data.user;   // Save the logged-in user
      state.view = "dashboard";
      if (isRegister) {
        showToast("Account created. You are signed in.");
      }
      renderShell();            // Show the main application
    } catch (error) {
      showToast(error.message); // Show error (e.g. "Invalid username or password")
    }
  });
}

function renderShell() {
  /*
   * The shell is the common layout after login: top bar, tabs, and content.
   * Admins and students get different tabs based on their role.
   */
  clearTimers();                 // Stop any timers from the previous screen
  const isAdmin = state.user.role === "ADMIN";
  // Build the shell HTML
  app.innerHTML = html`
    <main class="app-shell">
      <header class="topbar">
        <div class="brand">
          <div class="brand-mark">OE</div>
          <div>
            <h1>Online Examination System</h1>
            <p>${escapeHtml(state.user.fullName)} - ${escapeHtml(state.user.role)}</p>
          </div>
        </div>
        <button class="secondary" id="logoutBtn">Logout</button>
      </header>
      <!-- Navigation tabs differ for admin vs student -->
      <nav class="tabs">
        ${isAdmin ? adminTabs() : studentTabs()}
      </nav>
      <section id="screen"></section>  <!-- This gets filled by the render functions -->
    </main>
  `;

  // Logout button handler
  document.querySelector("#logoutBtn").addEventListener("click", logout);
  // Tab switching: when any tab is clicked, update the view and re-render
  document.querySelectorAll("[data-view]").forEach((button) => {
    button.addEventListener("click", () => {
      state.view = button.dataset.view;  // Update which tab is active
      renderShell();                      // Re-render everything
    });
  });

  // Show the correct view based on the active tab
  if (isAdmin) {
    renderAdminView();
  } else {
    renderStudentView();
  }
}

// adminTabs() returns the HTML for admin navigation tabs
function adminTabs() {
  return ["dashboard", "users", "exams", "monitor"]
    .map((view) => `<button class="tab ${state.view === view ? "active" : ""}" data-view="${view}">${title(view)}</button>`)
    .join("");  // Join the array of buttons into a single string
}

// studentTabs() returns the HTML for student navigation tabs
function studentTabs() {
  return ["dashboard", "results"]
    .map((view) => `<button class="tab ${state.view === view ? "active" : ""}" data-view="${view}">${title(view)}</button>`)
    .join("");
}

// title() capitalizes the first letter of a word (e.g. "dashboard" -> "Dashboard")
function title(value) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

// logout() calls the server to destroy the session, then shows the login screen
async function logout() {
  await api("/api/logout", { method: "POST" });
  renderLogin();
}

// renderAdminView() decides which admin screen to show based on the active tab
async function renderAdminView() {
  if (state.view === "users") return renderUsers();
  if (state.view === "exams") return renderExamManager();
  if (state.view === "monitor") return renderMonitor();
  return renderAdminDashboard();  // Default: dashboard tab
}

// renderAdminDashboard() shows summary statistics (user count, exam count, live attempts)
async function renderAdminDashboard() {
  // Fetch all three data sources in parallel for speed
  const [users, exams, monitor] = await Promise.all([
    api("/api/admin/users"),
    api("/api/admin/exams"),
    api("/api/admin/monitor")
  ]);

  // Update the #screen section with stats and info
  document.querySelector("#screen").innerHTML = html`
    <section class="grid three">
      <div class="panel stat"><span class="muted">Users</span><strong>${users.users.length}</strong></div>
      <div class="panel stat"><span class="muted">Exams</span><strong>${exams.exams.length}</strong></div>
      <div class="panel stat"><span class="muted">Live Attempts</span><strong>${monitor.attempts.length}</strong></div>
    </section>
    <section class="panel" style="margin-top:16px">
      <div class="section-title">
        <div>
          <h2>Admin Workspace</h2>
          <p>Create student accounts, publish exams, and watch active attempts as they happen.</p>
        </div>
      </div>
      <div class="notice">Use the tabs above to manage users, question papers, and monitoring.</div>
    </section>
  `;
}

// renderUsers() shows the admin user management screen (create + list users)
async function renderUsers() {
  const data = await api("/api/admin/users");
  document.querySelector("#screen").innerHTML = html`
    <section class="grid two">
      <!-- Left panel: Create user form -->
      <form class="panel" id="userForm">
        <div class="section-title">
          <div>
            <h2>Create User</h2>
            <p>Add admins or students. Passwords are hashed by the Java server.</p>
          </div>
        </div>
        <label class="field"><span>Full name</span><input name="fullName" required></label>
        <label class="field"><span>Username</span><input name="username" required></label>
        <label class="field"><span>Password</span><input name="password" type="password" required minlength="8"></label>
        <label class="field">
          <span>Role</span>
          <select name="role">
            <option value="STUDENT">Student</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
        <div class="form-actions"><button type="submit">Create User</button></div>
      </form>
      <!-- Right panel: List of existing users -->
      <div class="panel">
        <div class="section-title">
          <div>
            <h2>Users</h2>
            <p>${data.users.length} accounts in the system.</p>
          </div>
        </div>
        <div class="list">${data.users.map(userRow).join("")}</div>
      </div>
    </section>
  `;

  // Handle the create user form submission
  document.querySelector("#userForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await api("/api/admin/users", {
        method: "POST",
        body: JSON.stringify(Object.fromEntries(form.entries()))
      });
      showToast("User created.");
      renderUsers();  // Refresh the user list
    } catch (error) {
      showToast(error.message);
    }
  });

  // Add click handlers to all delete buttons
  document.querySelectorAll("[data-delete-user]").forEach((button) => {
    button.addEventListener("click", async () => {
      const userIdToDelete = button.dataset.deleteUser;
      // Confirm dialog before deleting
      if (confirm("Are you sure you want to delete this user? This action cannot be undone.")) {
        try {
          await api(`/api/admin/users/${encodeURIComponent(userIdToDelete)}`, {
            method: "DELETE"
          });
          showToast("User deleted.");
          renderUsers(); // Re-render the list
        } catch (error) {
          showToast(error.message);
        }
      }
    });
  });
}

// userRow() generates HTML for one user in the user list
function userRow(user) {
  return html`
    <div class="row">
      <div>
        <h4>${escapeHtml(user.fullName)}</h4>
        <p>@${escapeHtml(user.username)} - Created ${formatDate(user.createdAt)}</p>
      </div>
      <div class="row-actions">
        <span class="badge ${user.role === "ADMIN" ? "warn" : "good"}">${escapeHtml(user.role)}</span>
        <!-- Don't show delete button for the currently logged-in user -->
        ${state.user.id !== user.id ? html`
          <button class="danger small" data-delete-user="${escapeHtml(user.id)}">Delete</button>
        ` : ''}
      </div>
    </div>
  `;
}

// renderExamManager() shows the exam creation form and list of existing exams
async function renderExamManager() {
  const data = await api("/api/admin/exams");
  document.querySelector("#screen").innerHTML = html`
    <section class="grid two">
      <!-- Left panel: Create exam form -->
      <form class="panel" id="examForm">
        <div class="section-title">
          <div>
            <h2>Create Exam</h2>
            <p>Build multiple-choice exams with automatic grading.</p>
          </div>
        </div>
        <label class="field"><span>Title</span><input name="title" required></label>
        <label class="field"><span>Description</span><textarea name="description"></textarea></label>
        <label class="field"><span>Duration in minutes</span><input name="durationMinutes" type="number" min="1" max="300" value="30"></label>
        <div id="questionEditors" class="grid"></div>  <!-- Dynamic question editors go here -->
        <div class="form-actions">
          <button type="button" class="secondary" id="addQuestionBtn">Add Question</button>
          <button type="submit">Publish Exam</button>
        </div>
      </form>
      <!-- Right panel: List of existing exams -->
      <div class="panel">
        <div class="section-title">
          <div>
            <h2>Exams</h2>
            <p>${data.exams.length} exam papers created.</p>
          </div>
        </div>
        <div class="list">
          ${data.exams.map(examRow).join("") || `<div class="notice">No exams yet.</div>`}
        </div>
      </div>
    </section>
  `;

  // Add event listeners
  document.querySelector("#addQuestionBtn").addEventListener("click", () => addQuestionEditor());
  document.querySelector("#examForm").addEventListener("submit", submitExamForm);
  // Toggle exam active/inactive buttons
  document.querySelectorAll("[data-toggle-exam]").forEach((button) => {
    button.addEventListener("click", async () => {
      await api(`/api/admin/exams/${encodeURIComponent(button.dataset.toggleExam)}/toggle`, {
        method: "POST",
        body: JSON.stringify({ active: button.dataset.active !== "true" })
      });
      renderExamManager();  // Refresh the list
    });
  });

  // Add the first question editor by default
  addQuestionEditor();
}

// examRow() generates HTML for one exam in the admin exam list
function examRow(exam) {
  return html`
    <div class="row">
      <div>
        <h4>${escapeHtml(exam.title)}</h4>
        <p>${exam.questionCount} questions - ${exam.durationMinutes} minutes - ${exam.totalPoints} points</p>
      </div>
      <!-- Toggle button: if active shows "Deactivate" (danger), if inactive shows "Activate" -->
      <button class="${exam.active ? "danger" : ""}" data-toggle-exam="${escapeHtml(exam.id)}" data-active="${exam.active}">
        ${exam.active ? "Deactivate" : "Activate"}
      </button>
    </div>
  `;
}

// addQuestionEditor() adds a new question editor block to the exam form
function addQuestionEditor() {
  const container = document.querySelector("#questionEditors");
  const index = container.children.length + 1;  // Question number (1, 2, 3...)
  const wrapper = document.createElement("div");
  wrapper.className = "question-editor";
  wrapper.innerHTML = html`
    <label class="field"><span>Question ${index}</span><textarea data-q-text required></textarea></label>
    <div class="option-grid">
      <label class="field"><span>Option A</span><input data-option required></label>
      <label class="field"><span>Option B</span><input data-option required></label>
      <label class="field"><span>Option C</span><input data-option></label>
      <label class="field"><span>Option D</span><input data-option></label>
    </div>
    <div class="option-grid">
      <label class="field">
        <span>Correct option</span>
        <select data-correct>
          <option value="0">A</option>
          <option value="1">B</option>
          <option value="2">C</option>
          <option value="3">D</option>
        </select>
      </label>
      <label class="field"><span>Points</span><input data-points type="number" min="1" value="1"></label>
    </div>
  `;
  container.appendChild(wrapper);
}

// submitExamForm() reads the form and question editors, then sends the data to the server
async function submitExamForm(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);

  /*
   * Convert the question editor fields into the JSON format expected by Java.
   */
  // Loop through all .question-editor elements and extract their data
  const questions = [...document.querySelectorAll(".question-editor")].map((editor) => ({
    text: editor.querySelector("[data-q-text]").value,          // Question text
    options: [...editor.querySelectorAll("[data-option]")]      // Option inputs
      .map((input) => input.value.trim())
      .filter(Boolean),                                          // Remove empty options
    correctIndex: Number(editor.querySelector("[data-correct]").value), // Which option is correct
    points: Number(editor.querySelector("[data-points]").value || 1)   // Point value
  }));

  try {
    // Send the exam data to the server
    await api("/api/admin/exams", {
      method: "POST",
      body: JSON.stringify({
        title: form.get("title"),
        description: form.get("description"),
        durationMinutes: Number(form.get("durationMinutes")),
        questions
      })
    });
    showToast("Exam published.");
    renderExamManager();  // Refresh the exam list
  } catch (error) {
    showToast(error.message);
  }
}

// renderMonitor() shows the real-time admin monitoring dashboard
async function renderMonitor() {
  // load() fetches the latest monitoring data and updates the screen
  async function load() {
    const data = await api("/api/admin/monitor");
    document.querySelector("#screen").innerHTML = html`
      <section class="panel">
        <div class="section-title">
          <div>
            <h2>Real-Time Monitoring</h2>
            <p>Updated every five seconds from student heartbeats and security events.</p>
          </div>
          <span class="badge">${data.attempts.length} live</span>
        </div>
        <div class="list">
          ${data.attempts.map(monitorRow).join("") || `<div class="notice">No active attempts right now.</div>`}
        </div>
      </section>
    `;
  }

  await load();  // Load immediately
  // Then refresh every 5 seconds so the admin sees live updates
  state.monitorTimer = setInterval(load, 5000);
}

// monitorRow() generates HTML for one active attempt in the monitoring view
function monitorRow(attempt) {
  // stale = no heartbeat for more than 20 seconds
  const stale = Date.now() - attempt.lastHeartbeatAt > 20000;
  return html`
    <div class="row">
      <div>
        <h4>${escapeHtml(attempt.student)} is taking ${escapeHtml(attempt.exam)}</h4>
        <p>@${escapeHtml(attempt.username)} - Remaining ${formatTime(attempt.remainingSeconds)} - Last heartbeat ${formatDate(attempt.lastHeartbeatAt)}</p>
      </div>
      <!-- Badge color: green = no alerts, amber = stale, red = alerts detected -->
      <span class="badge ${attempt.eventCount > 0 ? "bad" : stale ? "warn" : "good"}">
        ${attempt.eventCount} alerts
      </span>
    </div>
  `;
}

// renderStudentView() decides which student screen to show based on the active tab
async function renderStudentView() {
  if (state.view === "results") return renderResults();
  return renderStudentDashboard();  // Default: dashboard tab
}

// renderStudentDashboard() shows the list of available exams for the student
async function renderStudentDashboard() {
  const data = await api("/api/student/exams");
  document.querySelector("#screen").innerHTML = html`
    <section class="panel">
      <div class="section-title">
        <div>
          <h2>Available Exams</h2>
          <p>Start an active exam when you are ready. The timer begins immediately.</p>
        </div>
      </div>
      <div class="list">
        ${data.exams.map(studentExamRow).join("") || `<div class="notice">No exams are active right now.</div>`}
      </div>
    </section>
  `;

  // Add click handlers to all "Start" buttons
  document.querySelectorAll("[data-start-exam]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        const data = await api(`/api/student/exams/${encodeURIComponent(button.dataset.startExam)}/start`, {
          method: "POST"
        });
        state.activeAttemptId = data.attemptId;  // Store the attempt ID
        renderAttempt();                          // Start the exam screen
      } catch (error) {
        showToast(error.message);
      }
    });
  });
}

// studentExamRow() generates HTML for one exam in the student's available exams list
function studentExamRow(exam) {
  return html`
    <div class="row">
      <div>
        <h4>${escapeHtml(exam.title)}</h4>
        <p>${escapeHtml(exam.description)} - ${exam.questionCount} questions - ${exam.durationMinutes} minutes</p>
      </div>
      <button data-start-exam="${escapeHtml(exam.id)}">Start</button>
    </div>
  `;
}

// renderAttempt() displays the exam questions and starts the timer for the student
async function renderAttempt() {
  clearTimers();  // Stop any old timers
  installAntiCheatHandlers();  // Add anti-cheat event listeners
  // Fetch the attempt details from the server
  const data = await api(`/api/student/attempts/${encodeURIComponent(state.activeAttemptId)}`);

  // If already submitted, just show the result
  if (data.submitted) {
    renderResult(data.result);
    return;
  }

  // Build the exam layout: questions on the left, timer panel on the right
  app.innerHTML = html`
    <main class="app-shell">
      <header class="topbar">
        <div class="brand">
          <div class="brand-mark">EX</div>
          <div>
            <h1>${escapeHtml(data.exam.title)}</h1>
            <p>${escapeHtml(data.exam.description)}</p>
          </div>
        </div>
      </header>
      <section class="exam-layout">
        <div>
          ${data.exam.questions.map((question, index) => questionCard(question, index)).join("")}
        </div>
        <aside class="panel sticky-side">
          <span class="muted">Time remaining</span>
          <div class="timer" id="timer">${formatTime(data.remainingSeconds)}</div>
          <p class="muted">Answers save automatically when selected.</p>
          <button class="danger" id="submitAttemptBtn">Submit Exam</button>
        </aside>
      </section>
    </main>
  `;

  // Add change listeners to all radio buttons: save answer immediately on selection
  document.querySelectorAll("[data-question]").forEach((input) => {
    input.addEventListener("change", async () => {
      // Send the answer to the server as soon as the student selects an option
      await api(`/api/student/attempts/${encodeURIComponent(state.activeAttemptId)}/answer`, {
        method: "POST",
        body: JSON.stringify({
          questionId: input.dataset.question,
          optionIndex: Number(input.value)
        })
      });
    });
  });

  // Submit button handler
  document.querySelector("#submitAttemptBtn").addEventListener("click", submitAttempt);
  // Start the countdown timer and heartbeat
  startTimers(data.remainingSeconds);
}

// questionCard() generates HTML for one question with its radio button options
function questionCard(question, index) {
  return html`
    <article class="panel question-card">
      <div class="section-title">
        <div>
          <h3>Question ${index + 1}</h3>
          <p>${escapeHtml(question.text)}</p>
        </div>
        <span class="badge">${question.points} point${question.points === 1 ? "" : "s"}</span>
      </div>
      ${question.options.map((option, optionIndex) => html`
        <label class="option">
          <input type="radio"
                 name="${escapeHtml(question.id)}"          // Group radio buttons by question ID
                 data-question="${escapeHtml(question.id)}"  // Identify which question this is
                 value="${optionIndex}"                      // 0 = A, 1 = B, 2 = C, 3 = D
                 ${question.answer === optionIndex ? "checked" : ""}>  <!-- Pre-select if already answered -->
          <span>${escapeHtml(option)}</span>
        </label>
      `).join("")}
    </article>
  `;
}

function startTimers(remainingSeconds) {
  /*
   * The visible countdown runs in the browser for smooth display.
   * The server still checks time too, so changing browser JavaScript does not
   * give a student unlimited time.
   */
  let left = Number(remainingSeconds);  // How many seconds remain
  // Update the timer display every 1 second
  state.examTimer = setInterval(async () => {
    left -= 1;  // Decrease by 1 second
    const timer = document.querySelector("#timer");
    if (timer) timer.textContent = formatTime(left);  // Update the display
    if (left <= 0) {
      clearInterval(state.examTimer);  // Stop the timer
      showToast("Time is over. Submitting your exam.");
      await submitAttempt();  // Auto-submit when time runs out
    }
  }, 1000);

  /*
   * Heartbeats help the admin monitor know whether the student is still active.
   */
  // Send a heartbeat every 10 seconds
  state.heartbeatTimer = setInterval(() => {
    api(`/api/student/attempts/${encodeURIComponent(state.activeAttemptId)}/heartbeat`, {
      method: "POST"
    }).catch(() => {});  // Ignore errors (just a health check)
  }, 10000);
}

// submitAttempt() submits the current exam attempt to the server for grading
async function submitAttempt() {
  const data = await api(`/api/student/attempts/${encodeURIComponent(state.activeAttemptId)}/submit`, {
    method: "POST"
  });
  clearTimers();  // Stop all timers
  renderResult(data.result);  // Show the result screen
}

// renderResult() displays the score and summary after an exam is submitted
function renderResult(result) {
  app.innerHTML = html`
    <main class="app-shell">
      <section class="panel">
        <div class="section-title">
          <div>
            <h2>Instant Result</h2>
            <p>${escapeHtml(result.examTitle)}</p>
          </div>
          <!-- Badge color: green if 60%+, red if below 60% -->
          <span class="badge ${result.percent >= 60 ? "good" : "bad"}">${result.percent}%</span>
        </div>
        <div class="grid three">
          <div class="card stat"><span class="muted">Score</span><strong>${result.score}/${result.totalPoints}</strong></div>
          <div class="card stat"><span class="muted">Submitted</span><strong style="font-size:1rem">${formatDate(result.submittedAt)}</strong></div>
          <div class="card stat"><span class="muted">Security Alerts</span><strong>${result.eventCount}</strong></div>
        </div>
        <div class="form-actions" style="margin-top:16px">
          <button id="backToDashboard">Back to Dashboard</button>
        </div>
      </section>
    </main>
  `;

  // Back button returns to the student dashboard
  document.querySelector("#backToDashboard").addEventListener("click", () => {
    state.view = "dashboard";
    state.activeAttemptId = null;  // Clear the active attempt
    renderShell();
  });
}

// renderResults() shows the student's list of past exam results
async function renderResults() {
  const data = await api("/api/student/results");
  document.querySelector("#screen").innerHTML = html`
    <section class="panel">
      <div class="section-title">
        <div>
          <h2>My Results</h2>
          <p>Review submitted attempts and security alert counts.</p>
        </div>
      </div>
      <div class="list">
        ${data.results.map(resultRow).join("") || `<div class="notice">You have not submitted any exams yet.</div>`}
      </div>
    </section>
  `;
}

// resultRow() generates HTML for one result entry in the student's results list
function resultRow(result) {
  return html`
    <div class="row">
      <div>
        <h4>${escapeHtml(result.examTitle)}</h4>
        <p>Submitted ${formatDate(result.submittedAt)} - ${result.eventCount} security alerts</p>
      </div>
      <span class="badge ${result.percent >= 60 ? "good" : "bad"}">${result.score}/${result.totalPoints}</span>
    </div>
  `;
}

function installAntiCheatHandlers() {
  /*
   * Anti-cheating in a browser cannot be perfect, but these signals help the
   * admin identify suspicious activity during an exam.
   *
   * Each event is sent to the server and recorded in the attempt's event log.
   */
  // Only install the handlers once (prevent duplicates)
  if (state.antiCheatInstalled) return;
  state.antiCheatInstalled = true;

  // report() sends an anti-cheat event to the server
  const report = (type, detail) => {
    if (!state.activeAttemptId) return;  // Only report during an active attempt
    api(`/api/student/attempts/${encodeURIComponent(state.activeAttemptId)}/event`, {
      method: "POST",
      body: JSON.stringify({ type, detail })
    }).catch(() => {});  // Ignore errors (the attempt might already be submitted)
  };

  // blur: when the browser window loses focus (e.g. user clicked another window)
  window.addEventListener("blur", () => report("focus_lost", "Browser window lost focus."));
  // visibilitychange: when the browser tab is hidden (e.g. user switched tabs)
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) report("tab_hidden", "Student switched away from the exam tab.");
  });
  // copy: when Ctrl+C or right-click copy is used
  document.addEventListener("copy", () => report("copy", "Copy action was used during the exam."));
  // paste: when Ctrl+V or right-click paste is used
  document.addEventListener("paste", () => report("paste", "Paste action was used during the exam."));
  // contextmenu: right-click menu - we block it to prevent copying/pasting
  document.addEventListener("contextmenu", (event) => {
    if (state.activeAttemptId) {
      event.preventDefault();  // Block the right-click menu
      report("context_menu", "Right click menu was blocked during the exam.");
    }
  });
}

// clearTimers() stops all running intervals to prevent memory leaks
function clearTimers() {
  clearInterval(state.examTimer);       // Stop exam countdown
  clearInterval(state.monitorTimer);    // Stop monitor auto-refresh
  clearInterval(state.heartbeatTimer);  // Stop heartbeat pings
}

// Start the application by calling boot() when the script loads
boot();