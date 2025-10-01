package both;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggerManager {
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private PrintWriter Writer;
    boolean file_or_screen; // true => file, false => screen
    public LoggerManager(boolean file_or_screen, String fileName) {
    	this.file_or_screen=file_or_screen;
    	if(file_or_screen) {
        try {
            Writer = new PrintWriter(new FileWriter(fileName, false));
        } catch (IOException e) {
            System.err.println("Error: Failed to initialize log files: " + e.getMessage());
        }
    	}
    }

    public synchronized void log(String message, boolean ln, boolean ts) {
            if(ln) {
            	if(ts) {
            	String timestamp = LocalDateTime.now().format(formatter);
            	if(file_or_screen) {
                Writer.println("[" + timestamp + "] " + message);
            	}
            	else {
            	System.out.println("[" + timestamp + "] " + message);
            	}
            	}
            	else {
            		if(file_or_screen) {
                        Writer.println(message);
                    	}
                    	else {
                    	System.out.println(message);
                    	}
            	}
            }
            else {           
            	if(ts) {
                	String timestamp = LocalDateTime.now().format(formatter);
                	if(file_or_screen) {
                    Writer.print("[" + timestamp + "] " + message);
                	}
                	else {
                    	System.out.println("[" + timestamp + "] " + message);
                    	}
                	}
                	else {
                		if(file_or_screen) {
                            Writer.print(message);
                        	}
                        	else {
                        	System.out.print(message);
                        	}
                	}
            	}
        	if(file_or_screen) {
            Writer.flush();
        	}
    }
    
    public void close() {
        if (Writer != null) Writer.close();
    }
}
