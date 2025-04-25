package tr;

import java.util.Arrays;

import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;

public class MainTR implements Observer {
    public static void main(String[] args) {
        try {
            BeliefStore beliefStore = new BeliefStore();
            String trFilePath = "RIPN_TR.txt"; 
            int cycleDelayMs = 100;
            LoggerManager logger = new LoggerManager(true, "log_TR.txt");
            beliefStore.setLogger(logger);
            TRProgram program = TRParser.parse(trFilePath, beliefStore, cycleDelayMs, logger); // ✅


            // Agregar `MainPN` como observador
            MainTR observer = new MainTR();
            program.addObserver(observer);

            System.out.println("Initiating tr program...");
            new Thread(() -> program.run()).start();
            Thread.sleep(20000);

            System.out.println("\nFinal state of the BeliefStore:");
            beliefStore.dumpState();

            System.out.println("\nStopping tr program...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDiscreteActionExecuted(String actionName, double[] parameters) {
        System.out.println("Observer: discrete action executed: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStarted(String actionName, double[] parameters) {
        System.out.println("Observer: Starting durative action: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStopped(String actionName) {
        System.out.println("Observer: Stopping durative action: " + actionName);
    }
}
