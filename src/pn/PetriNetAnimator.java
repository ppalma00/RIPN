package pn;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;
import guiEvents.EventPool;

public class PetriNetAnimator implements Runnable {
    private PetriNet net;
    private int refreshRate;
    private LoggerManager logger;
    @SuppressWarnings("unused")
	private final BeliefStore beliefStore; 
    public PetriNetAnimator(PetriNet net, int refreshRate) {
    	this.net = net;
        this.refreshRate = refreshRate;
        this.logger = net.getLogger();
        this.beliefStore = net.getBeliefStore(); 
    }
    @Override
    public void run() {
    	@SuppressWarnings("unused")
		BeliefStore beliefStore = net.getBeliefStore();
        @SuppressWarnings("unused")
		Map<String, Boolean> previousMarking = net.captureCurrentMarking();

        while (true) {
            List<String> enabledTransitions = net.getTransitions().keySet().stream()
                    .filter(net::canFire)
                    .collect(Collectors.toList());

            List<String> immediateTransitions = enabledTransitions.stream()
            		.filter(t -> {
            		    Transition tr = net.getTransitions().get(t);
            		    return !net.hasPNDefinition(t) || (tr != null && tr.getTriggerEvents().isEmpty());
            		})
                    .collect(Collectors.toList());

            while (!immediateTransitions.isEmpty()) {
                String t = immediateTransitions.get(0);
                Map<String, Boolean> beforeFire = net.captureCurrentMarking();
                List<String> discreteActions = net.fire(t);
                net.updateDurativeActions(beforeFire);
                net.checkExpiredTimers(); 
                Observer observer = net.getObserver();
                for (String action : discreteActions) {             
                    if (observer != null) {
                        observer.onDiscreteActionExecuted(action, new double[0]);
                    }
                }
                net.printState();
                try {
                    Thread.sleep(refreshRate); 
                } catch (InterruptedException e) {
                    return;
                }

                enabledTransitions = net.getTransitions().keySet().stream()
                        .filter(net::canFire)
                        .collect(Collectors.toList());
                immediateTransitions = enabledTransitions.stream()
                        .filter(tr -> !net.hasPNDefinition(tr))
                        .collect(Collectors.toList());
            }
            List<String> nonImmediate = enabledTransitions.stream()
                    .filter(net::hasPNDefinition)
                    .collect(Collectors.toList());
            if (nonImmediate.isEmpty()) {
            	logger.log("No more transitions can fire. Stopping simulation.", true, true);
                break;
            }
            String t = null;
            for (String candidate : nonImmediate) {
                Transition tr = net.getTransitions().get(candidate);
                if (tr != null && (tr.getTriggerEvents().isEmpty() || consumeFirstAvailableTriggerEvent(tr))) {
                    t = candidate;
                    break;
                }
            }
            if (t == null) {
                logger.log("⏸️ No non-immediate transitions enabled at this time. Waiting...", true, false);
                try {
                    Thread.sleep(refreshRate); 
                } catch (InterruptedException e) {
                    logger.log("Simulation interrupted.", true, true);
                    break;
                }
                continue; 
            }
            Map<String, Boolean> beforeFire = net.captureCurrentMarking();
            List<String> discreteActions = net.fire(t);
            net.updateDurativeActions(beforeFire);
            Observer observer = net.getObserver();
            for (String action : discreteActions) {       
                if (observer != null) {
                    observer.onDiscreteActionExecuted(action, new double[0]);
                }
            }
            net.checkExpiredTimers();
            net.printState();
            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
            	logger.log("Simulation interrupted.", true, true);
                break;
            }
            previousMarking = net.captureCurrentMarking();
        }
    }
    private boolean consumeFirstAvailableTriggerEvent(Transition tr) {
        BeliefStore beliefStore = net.getBeliefStore();
        LoggerManager logger = net.getLogger();
        for (String rawEvent : tr.getTriggerEvents()) {
            rawEvent = rawEvent.trim();
            String[] eventOptions = rawEvent.split("\\|\\|");
            for (String ev : eventOptions) {
                ev = ev.trim();
                if (ev.isEmpty()) continue;

                if (!ev.contains("(")) {
                    EventPool.EventInstance inst = EventPool.getInstance().consumeEvent(ev);
                    if (inst != null) return true;
                } else {
                    try {
                        String name = ev.substring(0, ev.indexOf("(")).trim();
                        String paramString = ev.substring(ev.indexOf("(") + 1, ev.lastIndexOf(")"));
                        String[] paramVars = paramString.split(",");
                        double[] resolved = new double[paramVars.length];

                        for (int i = 0; i < paramVars.length; i++) {
                            String var = paramVars[i].trim();
                            if (beliefStore.isIntVar(var)) {
                                resolved[i] = beliefStore.getIntVar(var);
                            } else if (beliefStore.isRealVar(var)) {
                                resolved[i] = beliefStore.getRealVar(var);
                            } else {
                                logger.log("❌ Unknown variable in event match: " + var, true, false);
                                continue;
                            }
                        }
                        EventPool.EventInstance inst = EventPool.getInstance().consumeEvent(name);
                        if (inst != null) {
                            double[] values = inst.getParameters();
                            if (values.length == paramVars.length) {
                                for (int i = 0; i < values.length; i++) {
                                    String var = paramVars[i].trim();
                                    if (beliefStore.isIntVar(var)) {
                                        beliefStore.setIntVar(var, (int) values[i]);
                                    } else if (beliefStore.isRealVar(var)) {
                                        beliefStore.setRealVar(var, values[i]);
                                    }
                                }
                            }
                            return true;
                        }
                    } catch (Exception ex) {
                        logger.log("❌ Error parsing multi-event trigger: " + ev + " → " + ex.getMessage(), true, false);
                    }
                }
            }
        }
        return false;
    }
    public void simulate() {
        while (true) {
            net.printState();

            List<String> enabledTransitions = net.getTransitions().keySet().stream()
                    .filter(net::canFire)
                    .collect(Collectors.toList());

            if (enabledTransitions.isEmpty()) {
            	logger.log("No more transitions can fire. Stopping simulation.", true, true);
                break;
            }
            String transitionToFire = enabledTransitions.get((int) (Math.random() * enabledTransitions.size()));
            net.fire(transitionToFire);
            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
            	logger.log("Simulation interrupted.", true, true);
                break;
            }
        }
    }
}
