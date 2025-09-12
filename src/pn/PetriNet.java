package pn;

import java.util.*;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

import both.LoggerManager;
import bs.BeliefStore;
import bs.Observer;

public class PetriNet {
    private Map<String, Place> places = new HashMap<>();
    private Map<String, Transition> transitions = new HashMap<>();
    private List<Arc> arcs = new ArrayList<>();
    private Map<String, List<String>> placeVariableUpdates = new HashMap<>();
    private BeliefStore beliefStore;
    private Map<String, List<String>> transitionVariableUpdates = new HashMap<>();
    private Map<String, String> placeDiscreteActions = new HashMap<>();
    private Observer observer;
    private List<String> pendingDiscreteActions = new ArrayList<>();
    private Map<String, Integer> discreteActionArity = new HashMap<>();
    private LoggerManager logger;
    public LoggerManager getLogger() {
        return logger;
    }
    public void setBeliefStore(BeliefStore bs) { this.beliefStore = bs; }

    public void setDiscreteActionArity(Map<String, Integer> arityMap) {
        this.discreteActionArity = arityMap;
    }
    public void setLogger(LoggerManager logger) {
        this.logger = logger;
    }
    public PetriNet() {
        this.beliefStore = new BeliefStore();
        beliefStore.setLogger(logger);
    }
    public void setObserver(Observer observer) {
        this.observer = observer;
    }
    public BeliefStore getBeliefStore() {
        return beliefStore;
    }

    public void setPlaceDiscreteActions(Map<String, String> discreteActions) {
        this.placeDiscreteActions = discreteActions;
    }

    public void setTransitionVariableUpdates(Map<String, List<String>> updates) {
        this.transitionVariableUpdates = updates;
    }
    public PetriNet(BeliefStore beliefStore) {
        this.beliefStore = beliefStore;
    }
    public Map<String, Place> getPlaces() {
        return places;
    }

    public void addPlace(String name, boolean token) {
        places.put(name, new Place(name, token));
    }

    public void addTransition(String name) {
        transitions.put(name, new Transition(name));
    }

    public void addArc(String from, String to, boolean isInhibitor) {
        if (places.containsKey(from) && transitions.containsKey(to)) {
            arcs.add(new Arc(places.get(from), transitions.get(to), true, isInhibitor));
        } else if (transitions.containsKey(from) && places.containsKey(to)) {
            arcs.add(new Arc(places.get(to), transitions.get(from), false, false));
        }
    }

    public void setPlaceVariableUpdates(Map<String, List<String>> updates) {
        this.placeVariableUpdates = updates;
    }

    public boolean canFire(String transitionName, boolean showBlockedCondition) {
        Transition transition = transitions.get(transitionName);
        if (transition == null) return false;
        if (transitionConditions.containsKey(transitionName)) {
            String condition = transitionConditions.get(transitionName);
            Map<String, Object> context = transition.getTempContext();
            if (context == null) {
                context = new HashMap<>();
                transition.setTempContext(context);
            }
            boolean result = ExpressionEvaluatorPN.evaluateLogicalExpression(condition, beliefStore, logger, context);
            if (!result) {
                if (showBlockedCondition) {
                    logger.log("🚫 Transition '" + transitionName + "' blocked by condition: " + condition, true, false);
                }
                return false;
            }

            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String var = entry.getKey();
                Object value = entry.getValue();
                if (beliefStore.containsIntVar(var) && value instanceof Number) {
                    beliefStore.setIntVar(var, ((Number) value).intValue());
                } else if (beliefStore.containsRealVar(var) && value instanceof Number) {
                    beliefStore.setRealVar(var, ((Number) value).doubleValue());
                }
            }

            if (transition != null) {
                transition.setTempContext(context);
            }
        }

        List<Place> inputPlaces = new ArrayList<>();
        List<Place> outputPlaces = new ArrayList<>();
        List<Place> inhibitorPlaces = new ArrayList<>();

        for (Arc arc : arcs) {
            if (arc.getTransition() == transition) {
                if (arc.isInhibitor()) {
                    inhibitorPlaces.add(arc.getPlace());
                } else if (arc.isInput()) {
                    inputPlaces.add(arc.getPlace());
                } else {
                    outputPlaces.add(arc.getPlace());
                }
            }
        }

        for (Place p : inputPlaces) {
            if (!p.hasToken()) return false;
        }
        for (Place p : outputPlaces) {
            if (p.hasToken()) return false;
        }
        for (Place p : inhibitorPlaces) {
            if (p.hasToken()) return false;
        }

        return true;
    }

    public void notifyDiscreteActions(List<String> actions) {
        if (observer != null) {
            for (String actionName : actions) {
                observer.onDiscreteActionExecuted(actionName, new double[0]);
            }
        }
    }
    public List<String> fire(String transitionName) {
        List<String> pendingDiscreteNotifications = new ArrayList<>();

        if (!canFire(transitionName, false)) return pendingDiscreteNotifications;

        Transition transition = transitions.get(transitionName);
        List<Place> inputPlaces = new ArrayList<>();
        List<Place> outputPlaces = new ArrayList<>();

        for (Arc arc : arcs) {
            if (arc.getTransition() == transition) {
                if (arc.isInput()) {
                    inputPlaces.add(arc.getPlace());
                } else {
                    outputPlaces.add(arc.getPlace());
                }
            }
        }

        for (Place p : outputPlaces) {
            boolean wasEmpty = !p.hasToken();
            p.setToken(true);
        }

        for (Place p : inputPlaces) {
            p.setToken(false);
        }
        executeTransitionActions(transitionName);
        logger.log("🔥 Transition fired: " + transitionName, true, true);
        beliefStore.dumpState();
        return pendingDiscreteNotifications;
    }
    public List<Arc> getArcs() {
        return arcs;
    }

    public void notifyPendingDiscreteActions() {
        if (observer != null) {
            for (String actionName : pendingDiscreteActions) {
                observer.onDiscreteActionExecuted(actionName, new double[0]);
            }
        }
        pendingDiscreteActions.clear();
    }
    public Observer getObserver() {
        return observer;
    }
    public List<Place> getOutputPlaces(Transition t) {
        List<Place> outputPlaces = new ArrayList<>();
        for (Arc arc : arcs) {
            if (arc.getTransition() == t && !arc.isInput()) {
                outputPlaces.add(arc.getPlace());
            }
        }
        return outputPlaces;
    }

    private void executeTransitionActions(String transitionName) {
        Transition transition = this.transitions.get(transitionName);
        Map<String, Object> context = (transition != null) ? transition.getTempContext() : null;

        if (transitionConditions.containsKey(transitionName)) {
            String condition = transitionConditions.get(transitionName);

            boolean conditionMet = ExpressionEvaluatorPN.evaluateLogicalExpression(condition, beliefStore, logger, context);
            if (!conditionMet) {
                logger.log("🚫 Skipped actions in transition " + transitionName + " (Condition not met: " + condition + ")", true, true);
                return; // 🔹 Si la condición no se cumple, NO ejecutamos las acciones
            }
        }

        if (transitionVariableUpdates.containsKey(transitionName)) {
            for (String update : transitionVariableUpdates.get(transitionName)) {
                update = update.trim();

                if (update.startsWith("remember(") && update.endsWith(")")) {
                    String fact = update.substring(9, update.length() - 1).trim();
                    processRememberFactWithContext(fact, context);

                } else if (update.startsWith("forget(") && update.endsWith(")")) {
                    String fact = update.substring(7, update.length() - 1).trim();
                    processForgetFactWithContext(fact, context);

                } else {
                    String[] parts = update.split(":=");
                    if (parts.length == 2) {
                        String varName = parts[0].trim();
                        String expression = parts[1].trim();
                        try {
                            Object result = evaluateExpression(expression, context);
                            if (result instanceof Integer && beliefStore.isIntVar(varName)) {
                                beliefStore.setIntVar(varName, (Integer) result);
                            } else if (result instanceof Double && beliefStore.isRealVar(varName)) {
                                beliefStore.setRealVar(varName, (Double) result);
                            } else {
                                logger.log("❌ Invalid type for variable: " + varName, true, false);
                            }
                        } catch (Exception e) {
                            logger.log("❌ Error evaluating expression: " + expression, true, false);
                        }
                    }
                }
            }
        }
        if (transition != null) {
            transition.setTempContext(null);
        }
    }
    private void processForgetFactWithContext(String fact, Map<String, Object> context) {
        if (fact.contains("(") && fact.endsWith(")")) {
            String factName = fact.substring(0, fact.indexOf("(")).trim();
            String paramStr = fact.substring(fact.indexOf("(") + 1, fact.length() - 1).trim();

            // 🟡 Si contiene un wildcard explícito, usa directamente el eliminador con comodines
            if (paramStr.contains("_")) {
                beliefStore.removeFactWithWildcard(fact);
                return;
            }

            // 🟢 Evaluación estándar si no hay wildcard
            List<String> paramList = Arrays.stream(paramStr.split(","))
                    .map(String::trim)
                    .map(p -> {
                        Object val = ExpressionEvaluatorPN.evaluateExpression(p, beliefStore, logger, context);
                        return val.toString();
                    })
                    .collect(Collectors.toList());

            if (beliefStore.getActiveFacts().containsKey(factName)) {
                List<List<Object>> instances = beliefStore.getActiveFacts().get(factName);

                boolean removed = instances.removeIf(existingParams -> {
                    if (existingParams.size() != paramList.size()) return false;
                    for (int i = 0; i < existingParams.size(); i++) {
                        if (!paramList.get(i).equals(String.valueOf(existingParams.get(i)))) {
                            return false;
                        }
                    }
                    return true;
                });

                if (instances.isEmpty()) {
                    beliefStore.getActiveFacts().remove(factName);
                }
            }
        } else {
            beliefStore.removeFact(fact);
        }
    }

    private void processRememberFactWithContext(String fact, Map<String, Object> context) {
        if (fact.contains("(") && fact.endsWith(")")) {
            String factName = fact.substring(0, fact.indexOf("(")).trim();
            String paramStr = fact.substring(fact.indexOf("(") + 1, fact.length() - 1).trim();
            try {
                List<String> evaluatedParams = Arrays.stream(paramStr.split(","))
                        .map(String::trim)
                        .map(p -> {
                            Object val = ExpressionEvaluatorPN.evaluateExpression(p, beliefStore, logger, context);
                            return val.toString();
                        })
                        .collect(Collectors.toList());

                String paramString = String.join(", ", evaluatedParams);
                beliefStore.addFact(factName + "(" + paramString + ")");
            } catch (Exception e) {
                logger.log("❌ Error parsing parameters for fact: " + fact + " → " + e.getMessage(), true, false);
            }
        } else {
            beliefStore.addFact(fact);
        }
    }

    public Map<String, String> getPlaceDiscreteActions() {
        return placeDiscreteActions;
    }

    private void processForgetFact(String fact) {
        if (fact.contains("(") && fact.endsWith(")")) {
            String factName = fact.substring(0, fact.indexOf("(")).trim();
            String paramStr = fact.substring(fact.indexOf("(") + 1, fact.length() - 1).trim();

            List<String> paramList = Arrays.stream(paramStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (beliefStore.getActiveFacts().containsKey(factName)) {
                List<List<Object>> instances = beliefStore.getActiveFacts().get(factName);

                @SuppressWarnings("unused")
				boolean removed = instances.removeIf(existingParams -> {
                    if (existingParams.size() != paramList.size()) return false;
                    for (int i = 0; i < existingParams.size(); i++) {
                        if (!paramList.get(i).equals("_") && !paramList.get(i).equals(String.valueOf(existingParams.get(i)))) {
                            return false;
                        }
                    }
                    return true;
                });

                if (instances.isEmpty()) {
                    beliefStore.getActiveFacts().remove(factName);
                }
            }
        } else {
            beliefStore.removeFact(fact);
        }
    }
    private void processRememberFact(String fact) {
        if (fact.contains("(") && fact.endsWith(")")) {
            String factName = fact.substring(0, fact.indexOf("(")).trim();
            String paramStr = fact.substring(fact.indexOf("(") + 1, fact.length() - 1).trim();
            try {
                List<String> evaluatedParams = Arrays.stream(paramStr.split(","))
                        .map(String::trim)
                        .map(p -> {
                        	Object val = ExpressionEvaluatorPN.evaluateExpression(p, beliefStore, logger, null);
                            return val.toString();
                        })
                        .collect(Collectors.toList());

                String paramString = String.join(", ", evaluatedParams);
                beliefStore.addFact(factName + "(" + paramString + ")");
            } catch (Exception e) {
                logger.log("❌ Error parsing parameters for fact: " + fact + " → " + e.getMessage().charAt(0), true, false);
            }
        } else {
            beliefStore.addFact(fact); 
        }
    }

    public void executePlaceActions(String placeName) {
        if (placeConditions.containsKey(placeName)) {
            String condition = placeConditions.get(placeName);
            boolean conditionMet = ExpressionEvaluatorPN.evaluateLogicalExpression(condition, beliefStore, logger, null);
            if (!conditionMet) {
            	logger.log("🚫 Skipped actions in place " + placeName + " (Condition not met: " + condition + ")", true, true);
                return;
            }
        }
        if (placeVariableUpdates.containsKey(placeName)) {
            for (String update : placeVariableUpdates.get(placeName)) {
                update = update.trim();

                if (update.startsWith("remember(") && update.endsWith(")")) {
                    String fact = update.substring(9, update.length() - 1).trim();
                    processRememberFactWithContext(fact, null);

                } else if (update.startsWith("forget(") && update.endsWith(")")) {
                    String fact = update.substring(7, update.length() - 1).trim();
                    processForgetFactWithContext(fact, null);

                } else if (update.contains(":=")) {
                    String[] parts = update.split(":=");
                    if (parts.length == 2) {
                        String varName = parts[0].trim();
                        String expression = parts[1].trim();
                        try {
                            Object result = evaluateExpression(expression);
                            if (result instanceof Integer && beliefStore.isIntVar(varName)) {
                                beliefStore.setIntVar(varName, (Integer) result);
                            } else if (result instanceof Double && beliefStore.isRealVar(varName)) {
                                beliefStore.setRealVar(varName, (Double) result);
                            } else {
                            	logger.log("❌ Invalid type for variable: " + varName, true, false);
                            }
                        } catch (Exception e) {
                        	logger.log("❌ Error evaluating expression: " + expression, true, false);
                        }
                    }

                } else if (update.matches("\\w+(\\.\\w+)?\\(.*\\)")) {
                    String name = update.substring(0, update.indexOf("("));
                    String argsRaw = update.substring(update.indexOf("(") + 1, update.lastIndexOf(")")).trim();

                    double[] args;
                    if (argsRaw.isEmpty()) {
                        args = new double[0];
                    } else {
                        String[] tokens = argsRaw.split(",");
                        args = new double[tokens.length];
                        for (int i = 0; i < tokens.length; i++) {
                            String token = tokens[i].trim();
                            try {
                                Object result = ExpressionEvaluatorPN.evaluateExpression(token, beliefStore, logger);
                                if (result instanceof Number) {
                                    args[i] = ((Number) result).doubleValue();
                                } else {
                                    logger.log("❌ Non-numeric argument in action: " + token, true, false);
                                    args[i] = 0;
                                }
                            } catch (Exception e) {
                                logger.log("❌ Error evaluating argument '" + token + "': " + e.getMessage(), true, false);
                                args[i] = 0;
                            }
                        }
                    }
                    if (name.endsWith(".start")) {
                        String timerName = name.substring(0, name.indexOf(".start"));
                        if (beliefStore.getDeclaredTimers().contains(timerName)) {
                            if (args.length == 1) {
                                int duration = (int) args[0];
                                beliefStore.startTimer(timerName, duration);
                            } else {
                            	logger.log("❌ Timer start requires 1 argument: " + update, true, false);
                            }
                            continue; 
                        }
                    }

                    if (name.contains(".stop")) {
                        String timerName = name.substring(0, name.indexOf(".stop"));
                        if (beliefStore.getDeclaredTimers().contains(timerName)) {
                            beliefStore.stopTimer(timerName);
                            logger.log("⏹️ Timer " + timerName + " stopped manually → t.end() activated", true, true);
                            continue; 
                        }
                    }
                    if (name.endsWith(".pause")) {
                        String timerName = name.substring(0, name.indexOf(".pause"));
                        if (beliefStore.getDeclaredTimers().contains(timerName)) {
                            beliefStore.pauseTimer(timerName);
                            continue;
                        }
                    }

                    if (name.endsWith(".continue")) {
                        String timerName = name.substring(0, name.indexOf(".continue"));
                        if (beliefStore.getDeclaredTimers().contains(timerName)) {
                            beliefStore.continueTimer(timerName);
                            continue;
                        }
                    }

                    if (discreteActionArity.containsKey(name)) {
                        if (observer != null) {
                            observer.onDiscreteActionExecuted(name, args);
                        }
                    }
                }
            }
        }
    }

    public void checkExpiredTimers() {
        for (String timerId : beliefStore.getDeclaredTimers()) {
            beliefStore.isTimerExpired(timerId); // este método añade el hecho t1.end() si expira
        }
    }


    private Object evaluateExpression(String expression, Map<String, Object> context) {
        Map<String, Object> combinedContext = new HashMap<>();

        combinedContext.putAll(beliefStore.getAllIntVars());
        combinedContext.putAll(beliefStore.getAllRealVars());

        if (context != null) {
            combinedContext.putAll(context); // sobrescribe si hay conflicto
        }

        return MVEL.eval(expression, combinedContext);
    }

    private Object evaluateExpression(String expression) {
        return evaluateExpression(expression, null);
    }


    public void printState() {
    	logger.log("Current state of the Petri Net:", true, true);
        for (Place p : places.values()) {
        	logger.log(p.getName() + ": " + (p.hasToken() ? "●" : "○"), true, false);
        }
        beliefStore.dumpState(); // Mostrar estado actualizado del BeliefStore
    }
    public Map<String, Transition> getTransitions() {
        return transitions;
    }
    private Map<String, String> placeConditions = new HashMap<>();
    private Map<String, String> transitionConditions = new HashMap<>();

    public void setPlaceConditions(Map<String, String> conditions) {
        this.placeConditions = conditions;
    }

    public void setTransitionConditions(Map<String, String> conditions) {
        this.transitionConditions = conditions;
    }

    public boolean hasPNDefinition(String transitionName) {
        return transitionVariableUpdates.containsKey(transitionName) || transitionConditions.containsKey(transitionName);
    }

    public Map<String, Boolean> captureCurrentMarking() {
        Map<String, Boolean> marking = new HashMap<>();
        for (Map.Entry<String, Place> entry : places.entrySet()) {
            marking.put(entry.getKey(), entry.getValue().hasToken());
        }
        return marking;
    }

    public void updateDurativeActions(Map<String, Boolean> previousMarking) {
        for (String placeName : places.keySet()) {
            Place place = places.get(placeName);
            boolean currentlyMarked = place.hasToken();
            boolean previouslyMarked = previousMarking.getOrDefault(placeName, false);      
            if (!currentlyMarked && previouslyMarked) {
                if (placeVariableUpdates.containsKey(placeName)) {
                    for (String update : placeVariableUpdates.get(placeName)) {
                        if (isDurativeAction(update)) {
                            String actionName = extractActionName(update);                    
                            if (observer != null) {
                                observer.onDurativeActionStopped(actionName);
                            }
                        }
                    }
                }
            }
        }

        for (String placeName : places.keySet()) {
            Place place = places.get(placeName);
            boolean currentlyMarked = place.hasToken();
            boolean previouslyMarked = previousMarking.getOrDefault(placeName, false);

            if (currentlyMarked && !previouslyMarked) {
                if (placeVariableUpdates.containsKey(placeName)) {
                    for (String update : placeVariableUpdates.get(placeName)) {
                        if (isDurativeAction(update)) {
                            String actionName = extractActionName(update);
                            double[] params = extractActionParameters(update);                 
                            if (observer != null) {
                                observer.onDurativeActionStarted(actionName, params);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isDurativeAction(String update) {
        String base = extractActionName(update);
        return beliefStore.getDeclaredDurativeActions().stream()
                .anyMatch(declared -> declared.startsWith(base));
    }
    private String extractActionName(String update) {
        int parenIndex = update.indexOf('(');
        return parenIndex > 0 ? update.substring(0, parenIndex).trim() : update.trim();
    }
    private double[] extractActionParameters(String update) {
        int start = update.indexOf('(');
        int end = update.lastIndexOf(')');
        if (start >= 0 && end > start) {
            String paramStr = update.substring(start + 1, end).trim();
            if (!paramStr.isEmpty()) {
                String[] parts = paramStr.split(",");
                double[] result = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try {
                        result[i] = Double.parseDouble(parts[i].trim());
                    } catch (NumberFormatException e) {
                        result[i] = 0; 
                    }
                }
                return result;
            }
        }
        return new double[0];
    }
}
