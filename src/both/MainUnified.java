/*
 * Developed by Pedro Sánchez Palma. Universidad Politécnica de Cartagena, Spain (pedro.sanchez@upct.es). April 2025.
 * Specify the Petri net and the Teleo-Reactive (TR) program in separate files. 
 * The system considers a BeliefStore shared by the Petri net and the TR programme. 
 * It is possible that one of them is not implemented and the system is only the specification given by the Petri 
 * net or by the TR program. You can either activate the output of the execution trace in both files or have 
 * it output in the execution console (but both traces overlapping).
 * The only outputs displayed by default are those relating to the observer by which the start or stop of a durative action 
 * and the execution of a discrete action are notified. In the implementation provided, main is the observer. 
 * If you need it to be another class you would have to delegate this behaviour to that other class.
 * 
 */
package both;

import bs.BeliefStore;
import bs.Observer;
import pn.*;
import tr.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.SwingUtilities;
import guiEvents.GUIEvents;

public class MainUnified implements Observer {

    // Configuration to indicate which elements of RIPN will run: Petri net and/or TR program
    private static final boolean ENABLE_PN = true;
    private static final boolean ENABLE_TR = true;

    private static final int PN_REFRESH_RATE_MS = 2000;   // Delay between Petri Net transitions
    private static final int TR_CYCLE_DELAY_MS = 1000;    // Delay between TR evaluation cycles
    private final LoggerManager loggerPN = new LoggerManager(true, "log_PN.txt"); // true => logging into files; false => screen
    private final LoggerManager loggerTR = new LoggerManager(true, "log_TR.txt"); // true => logging into files; false => screen
    private final LoggerManager loggerBS = new LoggerManager(true, "log_BS.txt"); // true => logging into files; false => screen
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static void main(String[] args) {
        MainUnified main = new MainUnified();
        main.startAll();
    }
    
    public void startAll() {
        BeliefStore sharedBeliefStore = new BeliefStore();
        sharedBeliefStore.setLogger(loggerBS);
        if (ENABLE_PN) {
            runPetriNet(sharedBeliefStore, this);
        }

        if (ENABLE_TR) {
            runTRProgram(sharedBeliefStore, this);
        }
    }

    private void runPetriNet(BeliefStore bs, MainUnified main){
        new Thread(() -> {
            try {
                String filename = "RIPN_PN.txt";
                Map<String, List<String>> placeVarUpdates = new HashMap<>();
                Map<String, List<String>> transitionVarUpdates = new HashMap<>();
                Map<String, String> placeConds = new HashMap<>();
                Map<String, String> transitionConds = new HashMap<>();
                Map<String, String> placeDiscreteActions = new HashMap<>();

                BeliefStoreLoader.loadFromFile(filename, bs, loggerPN);
                PetriNet net = PetriNetLoader.loadFromFile(filename, bs, loggerPN);
                net.setBeliefStore(bs);
                net.setLogger(main.loggerPN); 
                net.setObserver(main);

                BeliefStoreLoader.loadPNVariableUpdates(
                        filename, net,
                        placeVarUpdates, placeConds,
                        transitionVarUpdates, transitionConds,
                        placeDiscreteActions, loggerPN
                );

                net.setPlaceVariableUpdates(placeVarUpdates);
                net.setPlaceConditions(placeConds);
                net.setTransitionVariableUpdates(transitionVarUpdates);
                net.setTransitionConditions(transitionConds);
                net.setPlaceDiscreteActions(placeDiscreteActions);

                Map<String, Boolean> emptyMarking = new HashMap<>();
                
                net.updateDurativeActions(emptyMarking);
                net.printState();
                SwingUtilities.invokeLater(() -> new GUIEvents());
                PetriNetAnimator animator = new PetriNetAnimator(net, PN_REFRESH_RATE_MS);             
                new Thread(animator).start();

            } catch (Exception e) {
            	loggerTR.log("❌ Petri Net execution error: " + e.getMessage(),true, false);
                e.printStackTrace();
            }
        }).start();
        System.out.println("...started Petri net.");
    }

    private static void runTRProgram(BeliefStore bs, MainUnified main) {
        new Thread(() -> {
            try {
                String trFile = "RIPN_TR.txt";
                TRProgram program = TRParser.parse(trFile, bs, TR_CYCLE_DELAY_MS, main.loggerTR);
                program.addObserver(main);
                main.loggerTR.log("Initiating TR program...", true, true);
                program.run(); // internally loops
            } catch (Exception e) {
            	main.loggerTR.log("❌ TR Program execution error: " + e.getMessage(), true, false);
                e.printStackTrace();
            }
        }).start();
        System.out.println("...started TR program.\n");
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
