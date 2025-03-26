
import java.util.Arrays;

import core.BeliefStore;

public class Main implements Observer {
    public static void main(String[] args) {
        try {
            BeliefStore beliefStore = new BeliefStore();
            String trFilePath = "RIPN_TR.txt"; 
            TRProgram program = TRParser.parse(trFilePath, beliefStore);

            // Agregar `Main` como observador
            Main observer = new Main();
            program.addObserver(observer);

            System.out.println("Initiating TR program...");
            new Thread(() -> program.run()).start();
            Thread.sleep(20000);

            System.out.println("\nFinal state of the BeliefStore:");
            beliefStore.dumpState();

            System.out.println("\nStopping TR program...");
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
