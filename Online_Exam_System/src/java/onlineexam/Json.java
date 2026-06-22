/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package onlineexam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Java SE does not include a built-in JSON parser.
 * 
 * This class supports the JSON objects, arrays, strings, numbers, booleans, 
 * and null values used by the browser API.
 */
class Json {
    
    // Private constructor = no one can create a Json object (all methods are static)
    private Json(){
        
    }
    
    // parse takes a JSON string and converts it into Java objects (Map, List, String, Number, Boolean, null)
    static Object parse(String text){
        return new Parser(text).parseValue();
    }
    
    // parseObject parses a JSON string and guarantees it returns a Map (object)    
    @SuppressWarnings("unchecked")   // Suppress compiler warning about unchecked cast 
    static Map<String, Object> parseObject(String text){
        Object parsed = parse(text);
        if(!(parsed instanceof Map)){
            throw new IllegalArgumentException("Expected a JSON object.");
        }
        
        //cast to Map<String, Object> since we know it's a JSON object
        return (Map<String, Object>) parsed;
    }
    
    // stringify converts a Java object back into a JSON string
    static String stringify(Object value){
        StringBuilder builder = new StringBuilder();
        writeJson(builder, value);
        return builder.toString();
    }
    
    // writeJson is the recursive engine that builds the JSON string
    private static void writeJson(StringBuilder builder, Object value){
        
        if(value == null){           
            // null in Java becomes the literal "null" in JSON
            builder.append("null");
            
        }else if(value instanceof String text){           
            // String need special escaping for quotes, backslashes, and control characters
            writeString(builder, text);
            
        }else if(value instanceof Number || value instanceof Boolean){
            //Numbers and booleans can be appended directly (e.g. 42, true)
            builder.append(value);
            
        }else if(value instanceof Map<?, ?> map){
            //Map becomes JSON objects: {"key": value, "key2": value2, ...}
            builder.append('{');
            boolean first = true;       // track whether this is the first entry (no comma before it)
            for(Map.Entry<?, ?> entry : map.entrySet()){
                if(!first){
                    builder.append(',');    // Add comma between entries
                }               
                first = false;
                
                // Key must be a string in JSON, so convert with String.valueOf()
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(":");        // Colon separates key from values 
                writeJson(builder, entry.getValue());  // Recursively write the value
            }
            builder.append('}');
        }else if(value instanceof Iterable<?> iterable){
            // Iterables (like List, ArrayList) become JSON arrays: [item1, item2,...]
            builder.append('[');
            boolean first = true;
            for(Object item : iterable){
                if(!first){
                    builder.append(',');
                }
                first = false;
                writeJson(builder, item);   // Recursively write each array element
            }
            builder.append(']');
        }else{
            // Fallback: convert any other object to string and quote it
            writeString(builder, value.toString());
        }
    }
    
    // writeString escapes special characters and wraps the result in double quotes
    private static void writeString(StringBuilder builder, String text){
        builder.append('"');    // Opening quote
        for (int i=0; i<text.length(); i++){
            char character = text.charAt(i);
            
            // Handle special JSON escape sequences
            switch(character){
                case '"' -> builder.append("\\\"");       // Quote inside string -> \"
                case '\\' -> builder.append("\\\\");    // Backslash -> \\
                case '\b' -> builder.append("\\b");    // Backspace
                case '\f' -> builder.append("\\f");       // Form feed
                case '\n' -> builder.append("\\n");    // Newline
                case '\r' -> builder.append("\\r");      // Carriage return 
                case '\t' -> builder.append("\\t");      // Tab
                default -> {
                    if(character < 32){
                        
                        // control characters below 32 use unicode escape \\uXXXX
                        builder.append(String.format("\\u%04x", (int) character));
                    }else{
                        
                        // Normal printable characters go through as-is
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');    // Closing quote
    }
    
    // Parser is an inner class that reads a JSON string character by character
    private static class Parser{
        private final String text;      // The full JSON string we are parsing
        private int index;                  // Our current position within the string
        
        Parser(String text){
            // text == null ? "" : text.trim() ensures we always have a non-null string without leading/trailing spaces
            this.text = text == null ? "" : text.trim();
        }
        
        // parseValue figures out what kind of JSON value we are looking at and dispatches accordingly
        Object parseValue(){
            skipWhitespace();   // Ignore any spaces, tabs, newlines before the value
            if(index >= text.length()){
                return null;        // End of string, nothing to parse
            }
            
            char character = text.charAt(index);
            
            // Decide which parser to use based on the first character
            if(character == '{'){
                return parseObject();       // JSON object;
            }
            
            if(character == '['){
                return parseArray();    // JSON array
            }
            
            if(character == '"'){
                return parseString();       // JSON string
            }
            
            if(character == 't' || character == 'f'){
                return parseBoolean();          // true or false
            }
            
            if(character == 'n'){
                expect("null");         // JSON null
                return null;    
            }
            
            // If none of the above, it must be a number
            return parseNumber();
        }
        
        // parseObject reads a JSON object : {"key": value, "key2" : value2, ...}
        private Map<String, Object> parseObject(){
            Map<String, Object> object = new LinkedHashMap<>();
            expect('{');        // Consume the opening brace
            skipWhitespace();
            if(peek('}')){          // Empty object?
                expect('}');
                return object;
            }
            
            // Loop through key-value pairs
            while(true){
                String key = parseString(); // Keys must be strings
                skipWhitespace();
                expect(':');                                        // Colon between key and value
                Object value = parseValue();    // Parse the value (can be any JSON type)
                object.put(key, value);             // Store in the map
                skipWhitespace();
                if(peek('}')){                                      // Closing brace means we're done
                    expect('}');
                    return object;
                }
                expect(',');                                        // Comma separates pairs
            }
        }
        
        // parseArray reads a JSON array: [item1, item2, ...]
        
        private List<Object> parseArray(){
            List<Object> array = new ArrayList<>();
            expect('[');                // Consume the opening bracket
            skipWhitespace();   
            if(peek(']')){              // Empty array?
                expect(']');
                return array;
            }
            
            // Loop through array elements
            while(true){
                array.add(parseValue());        // Parse each element (any JSON type)
                skipWhitespace();
                if(peek(']')){
                    expect(']');                            // Closing bracket means we're done
                    return array;
                }
                expect(',');                                // Comma separates elements
            }
        }
        
        // parseString reads a JSON string between double quotes, handling escape sequences       
        private String parseString(){
            expect('"');        // consume the opening quote
            StringBuilder builder = new StringBuilder();
            
            while(index < text.length()){
                char character = text.charAt(index++);
                if(character == '"'){
                    return builder.toString(); // Closing quote ends string 
                }
                if(character == '\\'){
                    //Escape sequence: read the next character to see what to produce
                    if(index >= text.length()){
                        throw new IllegalArgumentException("Invalid JSON escape.");
                    }
                    
                    char escaped = text.charAt(index ++);
                    switch(escaped){
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            // Unicode escape : \\uXXXX ( 4 hex digits)
                            if(index + 4 > text.length()){
                                throw new IllegalArgumentException("Invalid unicode escape.");
                            }
                            String hex = text.substring(index, index + 4);
                            builder.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                        }
                        default -> throw new IllegalArgumentException("Unknown JSON escape.");
                    }
                }else{
                    //Normal character, just append it
                    builder.append(character);
                }
            }
            
            // If we reach here, the string never got a closing quote
            throw new IllegalArgumentException("Unclosed JSON string.");
        }
        
        // parseBoolean looks for the literal "true" or "false"
        private Boolean parseBoolean(){
            if(text.startsWith("true", index)){
                index += 4;
                return Boolean.TRUE;
            }
            
            if(text.startsWith("false", index)){
                index += 5;
                return Boolean.FALSE;
            }
            
            throw new IllegalArgumentException("Invalid JSON boolean.");
        }
        
        // parseNumber reads digits and optional decimal points / exponent
        private Number parseNumber(){
            int start = index;
            
            // Keep consuming characters that look like part of a number
            while(index < text.length()){
                char character = text.charAt(index);
                if((character >= '0' && character <= '9') || character == '-' || character == '+'
                        || character == '.' || character == 'e' || character == 'E'){
                    index++;
                }else{
                    break;      // Stop at the first non-number character
                }
            }
            
            String number = text.substring(start, index);
            
            // If it contains a decimal point or exponent, parse as double; otherwise as long
            if(number.contains(".") || number.contains("e") || number.contains("E")){
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        }
        
        // skipWhitespace advances the index past any whitespace
        private void skipWhitespace(){
            while(index < text.length() && Character.isWhitespace(text.charAt(index))){
                index++;
            }
        }
        
        // peek checks whether the next non-whitespace character matches the expected one
        private boolean peek(char expected){
            skipWhitespace();
            return index < text.length() && text.charAt(index) == expected;
        }
        
        // expect verifies the next non-whitespace character and advances past it
        private void expect(char expected){
            skipWhitespace();
            if(index >= text.length() || text.charAt(index) != expected){
                throw new IllegalArgumentException("Expected '" + expected + "'.");
            }
            
            index++;
        }
        
        // expect (String version) verifies the next text matches a literal string
        private void expect(String expected){
            skipWhitespace();
            if(!text.startsWith(expected, index)){
                throw new IllegalArgumentException("Expected " + expected + ".");
            }
            
            index += expected.length();
        }
    }
}