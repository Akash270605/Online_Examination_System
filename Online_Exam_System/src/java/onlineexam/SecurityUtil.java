/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

import java.nio.charset.StandardCharsets;       //UTF-8 encoding constant
import java.security.MessageDigest;                 // For constant-time comparison of hashes
import java.security.SecureRandom;                  // Cryptographically strong random generator
import java.security.spec.KeySpec;                    
import java.util.Base64;                                          // converts binary bytes to readable text and back

// JCE classes for PBKDF2 password hashing
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


/**
 * Security-related code is kept here so the rest of the application can call clear
 * methods like hashPassword(), verifyPassword(), and newSessionToken()
 */
class SecurityUtil {
    
    // SecureRandom produces random bytes that are suitable for cryptographic operations
    private static final SecureRandom RANDOM = new SecureRandom();
    
    // PBKDF2 iterations: 120000 rounds to slow down brute-force attacks
    private static final int PASSWORD_ITERATIONS = 120_000;
    
    // Output key length: 256 bits (32 bytes) for the hash
    private static final int PASSWORD_KEY_BITS = 256;
    
    
    // Private constructor ensures no one creates a SecurityUtil object
    // All methods are static, so no instance is needed
    private SecurityUtil(){
        
        // Utility classes don't need objects, so the constructor is private
    }
    
    // hashPassword takes a plain text password and returns a salt + hash pair
    static PasswordHash hashPassword(String password){
        
        /**
         * Passwords should never be stored directly.
         * 
         * We generate a random salt for each password and use PBKDF2 to create
         * a slow hash. If a data file leaks, attackers still do not get the real passwords.
         */
        
        // Generate 16 random bytes for the salt
        byte[] salt = randomBytes(16);
        
        // Run PBKDF2 with the password, salt, iterations, and desired key length
        byte[] hash = pbkdf2(password.toCharArray(), salt, PASSWORD_ITERATIONS, PASSWORD_KEY_BITS);
        
        // return both salt and hash as Base64 strings (so they can be stored as text)
        return new PasswordHash(base64(salt), base64(hash));
    }
    
    
    // verifyPasswords checks if a plain text password matches the stored hash
    static boolean verifyPassword(String password, String saltBase64, String expectedHashBase64){
        
        // Decode the Base64 strings back into raw bytes
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] expectedHash = Base64.getDecoder().decode(expectedHashBase64);
        
        // Hash the provided password with the same salt and iterations
        byte[] actualHash = pbkdf2(password.toCharArray(), salt, PASSWORD_ITERATIONS, PASSWORD_KEY_BITS);
        
        /**
         * MessageDigest.isEqual compares safely. A normal equals check can
         * sometimes reveal timing information about where bytes differ.
         */
        
        // Constant-time comparison prevents timing attacks that could leak password info.
        return MessageDigest.isEqual(expectedHash, actualHash);
        
    }
    
    
    // newSessionToken generates a random token used as a browser cookie after login
    static String newSessionToken(){
        /**
         * The browser stores this random token in a cookie after login.
         * The token does not contain the username or password
         */
        
        // Generate 32 random bytes and encode them as a URL-safe Base64 string
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    }
    
    // newId creates a unique identifier with a readable prefix
    static String newId(String prefix){
        // Concatenate the prefix with 12 random bytes encoded in URL-safe Base64
        return prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(12));
    }
    
    
    // randomBytes returns a byte array filled with cryptographically strong random values
    static byte[] randomBytes(int length){
        byte[] bytes = new byte[length];
        
        // SecureRandom.nextBytes fills the array with unpredictable values
        RANDOM.nextBytes(bytes);
        return bytes;
    }
    
    // base64 encode raw bytes into a standard Base64 string (used for storing salt/hash)
    static String base64(byte[] bytes){
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    // safeTrim safely converts an object to a trimmed string (returns empty string for null)
    static String safeTrim(Object value){
        return value == null ? "" : value.toString().trim();
    }
    
    // utf8 converts a string to its UTF-8 byte representation
    static byte[] utf8(String value){
        return value.getBytes(StandardCharsets.UTF_8);
    }
    
    // pbkdf2 is the core password hashing function using PBKDF2 with HMAC-SHA256
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits){
        try{
            //PBEKeySpec holds the password, salt, iterations count, and output key length
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            
            // SecretKeyFactory is Java's way to create keys from the password-based specifications
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            
            // Generate the key and return the raw bytes
            return factory.generateSecret(spec).getEncoded();
        }catch(Exception ex){
            throw new IllegalStateException("Could not hash password.", ex);
        }
    }
}


// PasswordHash is the simple container that pairs the salt with the hash
// Both are stored as Base64-encoded strings in the User object
class PasswordHash{
    final String salt;          // Random data used during hashing (different for every user)
    final String hash;         // The resulting hash value that replaces the plain password
    
    PasswordHash(String salt, String hash){
        this.salt = salt;
        this.hash = hash;
    }
}