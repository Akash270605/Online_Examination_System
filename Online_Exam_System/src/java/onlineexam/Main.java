/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

//HttpServer is Java's built-in way to create a web server (no extra libraries needed)
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;


// When this main method runs, it starts a local web server on port 8080
public class Main {
    public static void main(String[] args) throws Exception{
        
        // Step 1: Decide which port to use (read PORT from the environment, otherwise uses 8080)
        int port = readPort();
        
        /**
         * These system properties are optional.
         * They are useful for testing because we can place test data somewhere separate from the
         * real application data file.
         */
        
        // "exam.data.file" specify a custom path to the encrypted data file
        Path dataFile = Path.of(System.getProperty("exam.data.file", "data/exam-data.bin"));
        
        // "exam.web.root" specify a custom folder for HTML/CSS/JS files
        Path webRoot = Path.of(System.getProperty("exam.web.root", "web"));
        
        //DataStore is the brain that manages all users, exams, and attempts
        DataStore store = new DataStore(dataFile);
        
        //Create an HTTP server that listens on the chosen port
        //The second parameter (0) means we let the system choose the backlog size
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);;
        
        /**
         * Requests beginning with /api are handled by Java code.
         * Other requests are static browser files from the web folder.
         */
        
        // Any URL starting with /api goes to ApiHandler (login, exams, grading, etc.)
        server.createContext("/api", new ApiHandler(store));
        
        // Everything else (like /index.html, /styles.css, /app.js) goes to StaticFileHandler
        server.createContext("/", new StaticFileHandler(webRoot));
        
        
        //A thread pool with 12 threads means up to 12 people can be served simultaneously
        server.setExecutor(Executors.newFixedThreadPool(12));
        
        // Start() makes the server begin accepting HTTP connections
        server.start();
        
     
        //Print login instructions so the user knows what to do next
        System.out.println("Online Examination System is running.");
        System.out.println("Open http://localhost:" + port + " in your browser.");
        System.out.println("Admin login: admin / admin@123");
        System.out.println("Student login: student / student@123");
        
        // Try to open the default web browser to the application URL
        openBrowser("http://localhost:" + port);
        
        /**
         * Without this wait, the main method would finish and the application could stop
         * CTRL + C will end the process.
         *
         * CountDownLatch(1) creates a one-time barrier that waits forever until counted down
         * Since nobody ever calls countDown(), this keeps the server until we kill it
         */
        new CountDownLatch(1).await();
    }
    
    // readPort checks the PORT environment variable. If not set, return 8080 as default.
    private static int readPort(){
        
        //System.getenv reads an operating system environment variable
        String configured = System.getenv("PORT");
        
        // If PORT is empty or missing, fall back to the default port 8080
        if(configured == null || configured.isBlank()){
            return 8080;
        }
        
        // convert the string (e.g. "9090") into an integer number
        return Integer.parseInt(configured);
    }
    
    
    // openBrowser attempts to launch the system's default web browser to the given URL
    private static void openBrowser(String url){
        
        /**
         * exam.open.browser is optional and defaults to true.
         *  It lets automated tests disable browser opening by running Java with:
         *                  -Dexam.open.browser = false
         */
        boolean shouldOpenBrowser = Boolean.parseBoolean(System.getProperty("exam.open.browser", "true"));
        
        // If someone set exam.open.browser = false, skip opening the browser
        if(!shouldOpenBrowser){
            return;
        }
        
        try{
            /**
             * Desktop is Java's standard way to ask the operating system to open
             * a file, email client, or web browser.
             */
            // Check: is the Desktop API available on this OS? and does it support opening a browser?
            if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){
                
                // browse() tells the OS to open the given URL in the default browser
                Desktop.getDesktop().browse(URI.create(url));
            }else{
                System.out.println("Automatic browser opening is not supported on this system.");
            }
        }catch(Exception ex){
            /**
             * Browser opening is convenient, but it should not crash the server.
             * If it fails, the user can still copy the printed URL manually.
             */
            System.out.println("Could not open the browser automatically. Open this URL manually: " + url);
        }
    }
}
