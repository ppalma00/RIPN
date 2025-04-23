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
import java.util.*;

public class MainUnified implements Observer {

    // Configuration to indicate which elements of RIPN will run: Petri net and/or TR program
    private static final boolean ENABLE_PN = true;
    private static final boolean ENABLE_TR = true;

    private static final int PN_REFRESH_RATE_MS = 2000;   // Delay between Petri Net transitions
    private static final int TR_CYCLE_DELAY_MS = 1000;    // Delay between TR evaluation cycles
    private final LoggerManager logger = new LoggerManager(true); // true => logging into files; false => screen
    public static void main(String[] args) {
        MainUnified main = new MainUnified();
        main.startAll();
    }
    
    public void startAll() {
        BeliefStore sharedBeliefStore = new BeliefStore();
        sharedBeliefStore.setLogger(logger);
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

                BeliefStoreLoader.loadFromFile(filename, bs, logger);
                PetriNet net = PetriNetLoader.loadFromFile(filename, bs);
                net.setLogger(main.logger); 
                net.setObserver(main);

                BeliefStoreLoader.loadPNVariableUpdates(
                        filename, net,
                        placeVarUpdates, placeConds,
                        transitionVarUpdates, transitionConds,
                        placeDiscreteActions, logger
                );

                net.setPlaceVariableUpdates(placeVarUpdates);
                net.setPlaceConditions(placeConds);
                net.setTransitionVariableUpdates(transitionVarUpdates);
                net.setTransitionConditions(transitionConds);
                net.setPlaceDiscreteActions(placeDiscreteActions);

                Map<String, Boolean> emptyMarking = new HashMap<>();
                for (String placeName : net.getPlaces().keySet()) {
                    if (net.getPlaces().get(placeName).hasToken()) {
                        net.executePlaceActions(placeName);
                    }
                }

                net.updateDurativeActions(emptyMarking);
                net.printState();

                PetriNetAnimator animator = new PetriNetAnimator(net, PN_REFRESH_RATE_MS, logger);
                new Thread(animator).start();

            } catch (Exception e) {
            	logger.logTR("❌ Petri Net execution error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
        System.out.println("...started Petri net.");
    }

    private static void runTRProgram(BeliefStore bs, MainUnified main) {
        new Thread(() -> {
            try {
                String trFile = "RIPN_TR.txt";
                TRProgram program = TRParser.parse(trFile, bs, TR_CYCLE_DELAY_MS, main.logger);
                program.addObserver(main);
                main.logger.logTR("Initiating TR program...");
                program.run(); // internally loops
            } catch (Exception e) {
            	main.logger.logTR("❌ TR Program execution error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
        System.out.println("...started TR program.\n");
    }


    @Override
    public void onDiscreteActionExecuted(String actionName, double[] parameters) {
        System.out.println("Observer: Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters));
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
