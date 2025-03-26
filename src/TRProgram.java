import java.util.*;
import core.BeliefStore;
public class TRProgram {
    private final BeliefStore beliefStore;
    private final List<TRRule> rules = new ArrayList<>();
    private final Map<String, Boolean> activeDurativeActions = new HashMap<>();
    private final List<Observer> observers = new ArrayList<>();
    private boolean running = true;
    private TRRule lastExecutedRule = null;

    public TRProgram(BeliefStore beliefStore) {
        this.beliefStore = beliefStore;
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
        beliefStore.dumpState();
        for (TRRule rule : rules) {
            if (rule.evaluateCondition(beliefStore)) {
                return rule;
            }
        }
        return null;
    }
    public void run() {
        while (running) {
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
                Thread.sleep(100); // Control del ciclo de ejecución
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private void executeRule(TRRule rule) {
        System.out.println("🔄 Executing rule with condition: " + rule.getConditionText());

        boolean isFirstActivation = (lastExecutedRule == null || lastExecutedRule != rule);
        boolean hasActions = !rule.getDiscreteActions().isEmpty() || !rule.getDurativeActions().isEmpty();

        if (isFirstActivation && hasActions) {
            for (String action : rule.getDiscreteActions()) {
                action = action.trim();

                // ** Verificar que la acción tenga paréntesis bien formateados **
                if (!action.matches(".*\\(.*\\)$")) {  // Acción debe terminar en `)`
                    System.err.println("⚠️ Malformed action detected: " + action);
                    continue;
                }

                String actionName = action.substring(0, action.indexOf("(")).trim(); // Extraer nombre de la acción
                Double[] parameters = extractParameters(action);

                System.out.println("⏩ Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters));

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
            System.err.println("⚠️ Malformed timer command: " + action);
            return;
        }

        String timerId = parts[0];  // Extracts `t1`
        String commandWithParams = parts[1];  // Extracts `start(1)`, `pause()`, etc.
        String command = commandWithParams.split("\\(")[0];  // Extracts command without parameters

        // Debugging: Show correct extracted command
        System.out.println("🛠 Extracted timer command: " + command + " for timer: " + timerId);

        // Validate if timer is declared
        if (!beliefStore.getDeclaredTimers().contains(timerId)) {
            System.err.println("⚠️ Attempted to use an undeclared timer: " + timerId);
            return;
        }

        switch (command) {
            case "start":
                if (parameters.length > 0) {
                    beliefStore.startTimer(timerId, parameters[0].intValue());
                } else {
                    System.err.println("⚠️ `start` requires a duration (seconds).");
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
                System.err.println("⚠️ Unknown timer action: " + command);
        }
    }

    private boolean isTimerCommand(String action) {
        return action.matches(".*\\.start\\(\\d+(\\.\\d+)?\\)") ||  // Matches `t1.start(1)`, `t1.start(1.5)`
               action.matches(".*\\.stop\\(\\)") || 
               action.matches(".*\\.pause\\(\\)") || 
               action.matches(".*\\.continue\\(\\)");
    }
    private void executeDiscreteAction(String actionName, Double[] parameters) {
        System.out.println("⏩ Executing discrete action: " + actionName + " with parameters: " + Arrays.toString(parameters));
    }

    private void startDurativeAction(String action) {
        System.out.println("⏳ Acción durativa INICIADA: " + action);
    }

    private void stopDurativeActionsOfRule(TRRule rule) {
        if (rule == null) return;

        for (String action : rule.getDurativeActions()) {
            if (activeDurativeActions.containsKey(action)) {
                activeDurativeActions.remove(action);
                System.out.println("✅ Acción durativa FINALIZADA: " + action);
                notifyDurativeActionStopped(action);
            }
        }
    }

    private Double[] extractParameters(String action) {
        int startIndex = action.indexOf("(");
        int endIndex = action.lastIndexOf(")");

        if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
            System.err.println("⚠️ Error extracting parameters from: " + action);
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
                System.err.println("⚠️ Invalid parameter: " + param);
            }
        }

        return paramList.toArray(new Double[0]);
    }

    public void shutdown() {
        running = false;
        stopAllDurativeActions();
        System.out.println("🚨 TRProgram detenido.");
    }

    private void stopAllDurativeActions() {
        for (String action : activeDurativeActions.keySet()) {
            System.out.println("✅ Acción durativa FINALIZADA: " + action);
            notifyDurativeActionStopped(action);
        }
        activeDurativeActions.clear();
    }
}
