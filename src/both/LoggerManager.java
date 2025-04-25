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
            System.err.println("❌ Failed to initialize log files: " + e.getMessage());
        }
    	}
    }

    public synchronized void log(String message, boolean ln, boolean ts) {
    	if(file_or_screen) {
        if (Writer != null) {
            if(ln) {
            	if(ts) {
            	String timestamp = LocalDateTime.now().format(formatter);
                Writer.println("[" + timestamp + "] " + message);
            	}
            	else {
            	Writer.println(message);	
            	}
            }
            else {           
            	if(ts) {
                	String timestamp = LocalDateTime.now().format(formatter);
                    Writer.print("[" + timestamp + "] " + message);
                	}
                	else {
                	Writer.print(message);	
                	}
            	}
            Writer.flush();
        }
    	}
    	else {
    		System.out.println(message);
    	}
    }
    
    public void close() {
        if (Writer != null) Writer.close();
    }
}
