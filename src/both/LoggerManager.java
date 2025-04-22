package both;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class LoggerManager {

    private static final String PN_LOG_FILE = "log_PN.txt";
    private static final String TR_LOG_FILE = "log_TR.txt";

    private PrintWriter pnWriter;
    private PrintWriter trWriter;
    boolean file_or_screen; // true => file, false => screen
    public LoggerManager(boolean file_or_screen) {
    	this.file_or_screen=file_or_screen;
    	if(file_or_screen) {
        try {
            pnWriter = new PrintWriter(new FileWriter(PN_LOG_FILE, false));
            trWriter = new PrintWriter(new FileWriter(TR_LOG_FILE, false));
        } catch (IOException e) {
            System.err.println("❌ Failed to initialize log files: " + e.getMessage());
        }
    	}
    }

    public synchronized void logPN(String message) {
    	if(file_or_screen) {
        if (pnWriter != null) {
            pnWriter.println(message);
            pnWriter.flush();
        }
    	}
    	else {
    		System.out.println(message);
    	}
    }
    public synchronized void logPN2(String message) {
    	if(file_or_screen) {
        if (pnWriter != null) {
            pnWriter.print(message);
            pnWriter.flush();
        }
    	}
    	else {
    		System.out.println(message);
    	}
    }
    public synchronized void logTR(String message) {
    	if(file_or_screen) {
        if (trWriter != null) {
            trWriter.println(message);
            trWriter.flush();
        }
        else {
    		System.out.println(message);
    	}
    	}
    }

    public void close() {
        if (pnWriter != null) pnWriter.close();
        if (trWriter != null) trWriter.close();
    }
}
