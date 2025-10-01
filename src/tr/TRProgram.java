package tr;
import java.util.*;


import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;
import guiEvents.EventPool;
public class TRProgram {
	private int cycleDelayMs = 100;
    private final BeliefStore beliefStore;
    private final List<TRRule> rules = new ArrayList<>();
    private final Map<String, Boolean> activeDurativeActions = new HashMap<>();
    private final List<Observer> observers = new ArrayList<>();
    private boolean running = true;
    private TRRule lastExecutedRule = null;

    private final LoggerManager logger;

    public TRProgram(BeliefStore beliefStore, int cycleDelayMs, LoggerManager logger) {
        this.beliefStore = beliefStore;
        this.cycleDelayMs = cycleDelayMs;
        this.logger = logger;
    }

    public void addRule(TRRule rule) {
        rules.add(rule);
    }

    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    private void notifyObservers(String actionName, Double[] parameters) {
        double[] primitiveParams = Arrays.stream(parameters).mapToDouble(Double::doubleValue).toArray();
        
        for (Observer observer : observers) {
            observer.onDiscreteActionExecuted(actionName, primitiveParams);
        }
    }

    private void notifyDurativeActionStarted(String actionName, double[] parameters) {
        for (Observer observer : observers) {
            observer.onDurativeActionStarted(actionName, parameters);
        }
    }

    private void notifyDurativeActionStopped(String actionName) {
        for (Observer observer : observers) {
            observer.onDurativeActionStopped(actionName);
        }
    }
    public TRRule findHighestPriorityRule() {
        for (TRRule rule : rules) {
            if (rule.evaluateCondition(beliefStore)) {
                return rule;
            }
        }
        return null;
    }
    public void run() {
        while (running) {      	
        	beliefStore.dumpState();
            TRRule activeRule = findHighestPriorityRule();

            if (lastExecutedRule != null && lastExecutedRule != activeRule) {
                stopDurativeActionsOfRule(lastExecutedRule);
            }

            if ((lastExecutedRule == null || lastExecutedRule != activeRule) && activeRule != null) {
                executeRule(activeRule);
                lastExecutedRule = activeRule;
            }

            for (String timerId : beliefStore.getDeclaredTimers()) {
                beliefStore.isTimerExpired(timerId);
            }

            try {
                Thread.sleep(cycleDelayMs); // Control del ciclo de ejecución
            } catch (InterruptedException e) {
                logger.log("Warning:️ TR execution interrupted.", true, true);
                return;
            } catch (Exception e) {
                logger.log("Error: Exception in TR execution: " + e.getMessage(), true, true);
                e.printStackTrace();
            }
        }
    }
    private void executeRule(TRRule rule) {
    	logger.log("🔄 Executing rule with condition: " + rule.getConditionText(), true, true);

        boolean isFirstActivation = (lastExecutedRule == null || lastExecutedRule != rule);
        boolean hasActions = !rule.getDiscreteActions().isEmpty() || !rule.getDurativeActions().isEmpty();

        if (isFirstActivation && hasActions) {
            for (String action : rule.getDiscreteActions()) {
                action = action.trim();

                if (!action.matches(".*\\(.*\\)$")) { 
                	logger.log("Warning:️ Malformed action detected: " + action, true, false);
                    continue;
                }

                String actionName = action.substring(0, action.indexOf("(")).trim(); 
                Double[] parameters=null;
                if (!actionName.equals("_send")) {
                	 parameters = extractParameters(action);         
                }
                if (isTimerCommand(action)) {
                    executeTimerCommand(action, parameters);
                } else if (actionName.equals("_send")) {
                    Pair<String, double[]> envData = extractEnvEventNameAndParams(action);
                    String eventName = envData.getKey();
                    double[] eventParams = envData.getValue();

                    if (eventName == null || eventName.isEmpty()) {
                        logger.log("Warning:️ Cannot send event: missing or invalid name.", true, false);
                        continue;
                    }

                    EventPool.getInstance().addEvent(eventName, eventParams);
                    logger.log("Msg: Event sent to environment: " + eventName + Arrays.toString(eventParams), true, true);
                    continue;
                }
 else {
                    executeDiscreteAction(actionName, parameters);
                    notifyObservers(actionName, parameters);
                }

            }
        }

        if (isFirstActivation && rule.hasUpdates()) {
            rule.applyUpdates();
        }

        for (String action : rule.getDurativeActions()) {
            if (!activeDurativeActions.containsKey(action)) {
                Double[] parameters = extractParameters(action);
                double[] primitiveParams = Arrays.stream(parameters).mapToDouble(Double::doubleValue).toArray();
                startDurativeAction(action);
                activeDurativeActions.put(action, true);
                notifyDurativeActionStarted(action, primitiveParams);
            }
        }

        lastExecutedRule = rule;
    }


    private void executeTimerCommand(String action, Double[] parameters) {
        String[] parts = action.split("\\.");
        if (parts.length < 2) {
        	logger.log("Warning:️ Malformed timer command: " + action, true, false);
            return;
        }

        String timerId = parts[0];  
        String commandWithParams = parts[1];  
        String command = commandWithParams.split("\\(")[0];  

        logger.log("Msg: Extracted timer command: " + command + " for timer: " + timerId, true, true);

        if (!beliefStore.getDeclaredTimers().contains(timerId)) {
        	logger.log("Warning: Attempted to use an undeclared timer: " + timerId, true, false);
            return;
        }

        switch (command) {
            case "start":
                if (parameters.length > 0) {
                    beliefStore.startTimer(timerId, parameters[0].intValue());
                } else {
                	logger.log("Warning:️ `start` requires a duration (seconds).", true, false);
                }
                break;
            case "stop":
                beliefStore.stopTimer(timerId);
                break;
            case "pause":
                beliefStore.pauseTimer(timerId);
                break;
            case "continue":
                beliefStore.continueTimer(timerId);
                break;
            default:
            	logger.log("Warning:️ Unknown timer action: " + command, true, false);
        }
    }

    private boolean isTimerCommand(String action) {
        return action.matches(".*\\.start\\(\\d+(\\.\\d+)?\\)") ||  
               action.matches(".*\\.stop\\(\\)") || 
               action.matches(".*\\.pause\\(\\)") || 
               action.matches(".*\\.continue\\(\\)");
    }
    private void executeDiscreteAction(String actionName, Double[] parameters) {
    	logger.log("⏩ Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters), true, true);
    }

    private void startDurativeAction(String action) {
    	logger.log("⏳ Starting durative action: " + action, true, true);
    }

    private void stopDurativeActionsOfRule(TRRule rule) {
        if (rule == null) return;

        for (String action : rule.getDurativeActions()) {
            if (activeDurativeActions.containsKey(action)) {
                activeDurativeActions.remove(action);
                logger.log("Msg: Stopping durative action: " + action, true, true);
                notifyDurativeActionStopped(action);
            }
        }
    }
    private Pair<String, double[]> extractEnvEventNameAndParams(String action) {
        int startIndex = action.indexOf("(");
        int endIndex = action.lastIndexOf(")");

        if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
            logger.log("Warning:️ Error extracting parameters from: " + action, true, false);
            return new Pair<>(null, new double[0]);
        }

        String paramString = action.substring(startIndex + 1, endIndex).trim();
        if (paramString.isEmpty()) {
            logger.log("Warning:️ _send requires at least one parameter (event name).", true, false);
            return new Pair<>(null, new double[0]);
        }

        String[] parts = paramString.split(",");
        String eventName = parts[0].trim().replaceAll("^\"|\"$", ""); // quita comillas si hay
        List<Double> paramList = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            try {
                if (beliefStore.isIntVar(p)) {
                    paramList.add((double) beliefStore.getIntVar(p));
                } else if (beliefStore.isRealVar(p)) {
                    paramList.add(beliefStore.getRealVar(p));
                } else {
                    // Intenta convertirlo directamente
                    paramList.add(Double.parseDouble(p));
                }
            } catch (Exception e) {
                logger.log("Warning:️ Invalid numeric parameter in _send: " + p, true, false);
            }
        }


        return new Pair<>(eventName, paramList.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private Double[] extractParameters(String action) {
        int startIndex = action.indexOf("(");
        int endIndex = action.lastIndexOf(")");

        if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
            logger.log("Warning:️ Error extracting parameters from: " + action, true, false);
            return new Double[0];
        }

        String paramString = action.substring(startIndex + 1, endIndex).trim();
        if (paramString.isEmpty()) {
            return new Double[0];
        }

        String[] paramArray = paramString.split(",");
        List<Double> paramList = new ArrayList<>();

        for (String param : paramArray) {
            param = param.trim();

            try {
                if (beliefStore.isIntVar(param)) {
                    paramList.add((double) beliefStore.getIntVar(param));
                } else if (beliefStore.isRealVar(param)) {
                    paramList.add(beliefStore.getRealVar(param));
                } else {
                    // Intenta evaluar como número directamente
                    paramList.add(Double.parseDouble(param));
                }
            } catch (Exception e) {
                logger.log("Warning:️ Invalid parameter: " + param, true, true);
            }
        }

        return paramList.toArray(new Double[0]);
    }


    public void shutdown() {
        running = false;
        stopAllDurativeActions();
        logger.log("Msg: TRProgram stopped.", true, true);
    }

    private void stopAllDurativeActions() {
        for (String action : activeDurativeActions.keySet()) {
        	logger.log("Msg: Stopping durative action: " + action, true, true);
            notifyDurativeActionStopped(action);
        }
        activeDurativeActions.clear();
    }
    private static class Pair<K, V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }

}
