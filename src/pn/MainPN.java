package pn;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;
import guiEvents.EventPool;
import guiEvents.GUIEvents;

public class MainPN implements Observer {
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static void main(String[] args) {
    	LoggerManager logger = new LoggerManager(false, "log_PN.txt");
        try {
            BeliefStore beliefStore = new BeliefStore();
            beliefStore.setLogger(logger);
            Map<String, List<String>> placeVariableUpdates = new HashMap<>();
            String filename = "RIPN_PN.txt";
            BeliefStoreLoader.loadFromFile(filename, beliefStore, logger);
            PetriNet net = PetriNetLoader.loadFromFile(filename, beliefStore, logger);
            net.setLogger(logger);
            net.setBeliefStore(beliefStore);
            MainPN observer = new MainPN();
            net.setObserver(observer);
            net.setPlaceVariableUpdates(placeVariableUpdates);
            Map<String, List<String>> transitionVariableUpdates = new HashMap<>();
            Map<String, String> placeConditions = new HashMap<>();
            Map<String, String> transitionConditions = new HashMap<>();
            Map<String, String> placeDiscreteActions = new HashMap<>();

            BeliefStoreLoader.loadPNVariableUpdates(filename, net, placeVariableUpdates, placeConditions, transitionVariableUpdates, transitionConditions, placeDiscreteActions, logger);
            net.setPlaceVariableUpdates(placeVariableUpdates);
            net.setPlaceConditions(placeConditions);
            net.setTransitionVariableUpdates(transitionVariableUpdates);
            net.setTransitionConditions(transitionConditions);
            net.setPlaceDiscreteActions(placeDiscreteActions);           
            Map<String, Boolean> emptyMarking = new HashMap<>(); 
            
            net.updateDurativeActions(emptyMarking); 
            PetriNetAnimator animator = new PetriNetAnimator(net, 2000);
            if (EventPool.getInstance().hasDeclaredEvents()) {
                SwingUtilities.invokeLater(() -> new GUIEvents());
            }
            new Thread(animator).start();
        } catch (IOException e) {
        	logger.log("❌ Error loading files: " + e.getMessage(), true, false);
        }
    }
    
    /*
     * These are the methods that would need to be modified to route outputs from the execution of actions (durative and discrete) to the environment. 
     * Alternatively, it's possible to build a separate class that implements the Observer pattern instead. 
     * You would only need to modify the line of code that notifies the network (net.setObserver(main);) of which object is their observer.
     */
    
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
