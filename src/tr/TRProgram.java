package tr;
import java.util.*;

import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;
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

            // Ejecutar acciones ANTES de evaluar la expiración del temporizador
            if (lastExecutedRule == null || lastExecutedRule != activeRule) {
                executeRule(activeRule);
                lastExecutedRule = activeRule;
            }

            // Verificar expiración de temporizadores después de ejecutar acciones
            for (String timerId : beliefStore.getDeclaredTimers()) {
                beliefStore.isTimerExpired(timerId);
            }

            try {
                Thread.sleep(cycleDelayMs); // Control del ciclo de ejecución
            } catch (InterruptedException e) {
                logger.log("⚠️ TR execution interrupted.", true, true);
                return;
            } catch (Exception e) {
                logger.log("❌ Exception in TR execution: " + e.getMessage(), true, true);
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

                // ** Verificar que la acción tenga paréntesis bien formateados **
                if (!action.matches(".*\\(.*\\)$")) {  // Acción debe terminar en `)`
                	logger.log("⚠️ Malformed action detected: " + action, true, false);
                    continue;
                }

                String actionName = action.substring(0, action.indexOf("(")).trim(); // Extraer nombre de la acción
                Double[] parameters = extractParameters(action);

                logger.log("⏩ Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters), true, true);

                if (isTimerCommand(action)) {
                    executeTimerCommand(action, parameters);
                } else {
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
        	logger.log("⚠️ Malformed timer command: " + action, true, false);
            return;
        }

        String timerId = parts[0];  // Extracts `t1`
        String commandWithParams = parts[1];  // Extracts `start(1)`, `pause()`, etc.
        String command = commandWithParams.split("\\(")[0];  // Extracts command without parameters

        // Debugging: Show correct extracted command
        logger.log("🛠 Extracted timer command: " + command + " for timer: " + timerId, true, true);

        // Validate if timer is declared
        if (!beliefStore.getDeclaredTimers().contains(timerId)) {
        	logger.log("⚠️ Attempted to use an undeclared timer: " + timerId, true, false);
            return;
        }

        switch (command) {
            case "start":
                if (parameters.length > 0) {
                    beliefStore.startTimer(timerId, parameters[0].intValue());
                } else {
                	logger.log("⚠️ `start` requires a duration (seconds).", true, false);
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
            	logger.log("⚠️ Unknown timer action: " + command, true, false);
        }
    }

    private boolean isTimerCommand(String action) {
        return action.matches(".*\\.start\\(\\d+(\\.\\d+)?\\)") ||  // Matches `t1.start(1)`, `t1.start(1.5)`
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
                logger.log("✅ Stopping durative action: " + action, true, true);
                notifyDurativeActionStopped(action);
            }
        }
    }

    private Double[] extractParameters(String action) {
        int startIndex = action.indexOf("(");
        int endIndex = action.lastIndexOf(")");

        if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
        	logger.log("⚠️ Error extracting parameters from: " + action, true, false);
            return new Double[0];
        }

        String paramString = action.substring(startIndex + 1, endIndex).trim();
        if (paramString.isEmpty()) {
            return new Double[0];
        }

        String[] paramArray = paramString.split(",");
        List<Double> paramList = new ArrayList<>();
        
        for (String param : paramArray) {
            try {
                paramList.add(Double.parseDouble(param.trim()));
            } catch (NumberFormatException e) {
            	logger.log("⚠️ Invalid parameter: " + param, true, true);
            }
        }

        return paramList.toArray(new Double[0]);
    }

    public void shutdown() {
        running = false;
        stopAllDurativeActions();
        logger.log("🚨 TRProgram stopped.", true, true);
    }

    private void stopAllDurativeActions() {
        for (String action : activeDurativeActions.keySet()) {
        	logger.log("✅ Stopping durative action: " + action, true, true);
            notifyDurativeActionStopped(action);
        }
        activeDurativeActions.clear();
    }
}
