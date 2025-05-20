package tr;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;
public class MainTR implements Observer {
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static void main(String[] args) {
        try {
            BeliefStore beliefStore = new BeliefStore();
            String trFilePath = "RIPN_TR.txt"; 
            int cycleDelayMs = 100;
            LoggerManager logger = new LoggerManager(false, "log_TR.txt");
            beliefStore.setLogger(logger);
            TRProgram program = TRParser.parse(trFilePath, beliefStore, cycleDelayMs, logger); // ✅
            MainTR observer = new MainTR();
            program.addObserver(observer);

            System.out.println("Initiating tr program...");
            new Thread(() -> program.run()).start();             
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDiscreteActionExecuted(String actionName, double[] parameters) {
    	String timestamp = LocalDateTime.now().format(formatter);
        System.out.println("[" + timestamp + "] " + "Observer: Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStarted(String actionName, double[] parameters) {
    	String timestamp = LocalDateTime.now().format(formatter);
        System.out.println("[" + timestamp + "] " + "Observer: Starting durative action: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStopped(String actionName) {
    	String timestamp = LocalDateTime.now().format(formatter);
        System.out.println("[" + timestamp + "] " + "Observer: Stopping durative action: " + actionName);
    }
}
