/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

import com.sun.net.httpserver.HttpExchange;     // Represents one HTTP request-response
import com.sun.net.httpserver.HttpHandler;      // Interface for handling HTTP requests

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;       // UTF-8 constant
import java.nio.file.Files;                                         // Modern file operations
import java.nio.file.Path;                                          // Represents a file or directory path
import java.util.Map;


/**
 * This class serves the files inside the web/ folder.
 * 
 * - Browser requests /
 * - Server returns web/index.html
 * - Browser then requests /styles.css and /app.js
 */
class StaticFileHandler implements HttpHandler{
    
    // The absolute path to the web/ directory
    private final Path webRoot;
    
    // Constructor: store the web root directory as an absolute, clean path
    StaticFileHandler(Path webRoot){
        
        // toAbsolutePath() resolves relative paths to full paths
        // normalize() removes ".." and "." segments for clean path comparison
        this.webRoot = webRoot.toAbsolutePath().normalize();
    }
    
    // handle() is called every time the browser requests a file
    @Override
    public void handle(HttpExchange exchange) throws IOException{
        // Get the path from the URL
        String requestPath = exchange.getRequestURI().getPath();
        
        // If the path is "/" or empty, serve the default file index.html
        if(requestPath.equals("/") || requestPath.isBlank()){
            requestPath = "/index.html";
        }
        
        /**
         * Convert the request path to a file on disk
         * requestPath.substring(1) removes the leading "/" so "styles.css" becomes the filename
         * resolve() appends the filename to the webRoot directory path
         */
        Path file = webRoot.resolve(requestPath.substring(1)).normalize();
        
        
        // Security check: verify the resolved file is still inside webRoot
        if(!file.startsWith(webRoot)
                || !Files.exists(file)      // Does the file exist on disk?
                || Files.isDirectory(file)){        // Is it a folder ?
            // if any check fails, send 404 Not Found response
            send(exchange, 404, "text/plain; charset=utf-8", "File not found.");
            return;
        }
        
        // Read the entire file contents into a byte array
        byte[] bytes = Files.readAllBytes(file);
        
        // Set the Content-Type header so the browser knows how to render the file
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        
        // Send the HTTP response header with status 200 (OK) and file length
        exchange.sendResponseHeaders(200, bytes.length);
        
        // Write the file bytes to the output stream (this sends them to the browser)
        try(OutputStream output = exchange.getResponseBody()){
            output.write(bytes);
        }
    }
    
    // contentType returns the MIME type for a file based on its extension
    private String contentType(Path file){
        
        // Get the filename and convert to lowercase for case-insensitive comparison
        String name = file.getFileName().toString().toLowerCase();
        
        // A simple mapping of file extensions to MIME types
        Map<String, String> types = Map.of(
                ".html", "text/html; charset=utf-8",
                ".css", "text/css; charset=utf-8",
                ".js", "application/javascript; charset=utf-8",
                ".json", "application/json; charset=utf-8");
        
        // Loop through the mapping and return the matching content type
        for(Map.Entry<String, String> entry : types.entrySet()){
            if(name.endsWith(entry.getKey())){
                return entry.getValue();
            }
        }
        
        // Default: if the extension is not recognized, treat as binary data
        return "application/octet-stream";
    }
    
    // Send is a helper method that writes a plain text HTTP response
    private void send(HttpExchange exchange, int status, String type, String text) throws IOException{
        
        // Convert the text to UTF-8 bytes
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        
        // Write the bytes and close the stream
        try(OutputStream output = exchange.getResponseBody()){
            output.write(bytes);
        }
    }
}