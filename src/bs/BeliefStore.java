package bs;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import both.LoggerManager;

public class BeliefStore {
	private final Map<String, Integer> intVars = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, Double> realVars = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, List<List<Object>>> activeFacts = Collections.synchronizedMap(new HashMap<>());
	private final Set<String> activeFactsNoParams = Collections.synchronizedSet(new HashSet<>());
	private final Set<String> declaredTimers = Collections.synchronizedSet(new HashSet<>());
	private final Map<String, Long> timers = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, Long> pausedTimers = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> declaredDurativeActions = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> declaredDiscreteActions = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Integer> declaredFacts = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> declaredPercepts = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, List<String>> perceptsParameterTypes = new HashMap<>();
    private Map<String, List<String>> factParameterTypes = new HashMap<>();


    private LoggerManager logger;
    private String lastDump = "";
    public Set<String> getDeclaredPercepts() {
        return new HashSet<>(declaredPercepts);
    }
    public boolean containsIntVar(String var) {
        return intVars.containsKey(var);
    }

    public boolean containsRealVar(String var) {
        return realVars.containsKey(var);
    }
    public List<String> getPerceptParameterTypes(String name) {
        List<String> original = perceptsParameterTypes.get(name);
        return (original == null) ? new ArrayList<>() : new ArrayList<>(original);
    }
    public List<List<String>> getFactsMatchingNameAndArity(String name, int arity) {
        List<List<String>> results = new ArrayList<>();
        for (String fact : getAllFacts()) {
            if (!fact.startsWith(name + "(")) continue;
            if (!fact.endsWith(")")) continue;

            String inside = fact.substring(fact.indexOf("(") + 1, fact.lastIndexOf(")"));
            String[] parts = inside.split(",");
            if (parts.length != arity) continue;

            List<String> params = Arrays.stream(parts).map(String::trim).collect(Collectors.toList());
            results.add(params);
        }
        return results;
    }
    public List<String> getAllFacts() {
        List<String> facts = new ArrayList<>();

        // Sin parámetros
        for (String fact : activeFactsNoParams) {
            facts.add(fact);
        }

        // Con parámetros
        for (Map.Entry<String, List<List<Object>>> entry : activeFacts.entrySet()) {
            String baseName = entry.getKey();
            for (List<Object> params : entry.getValue()) {
                String joined = params.stream()
                                      .map(Object::toString)
                                      .collect(Collectors.joining(","));
                facts.add(baseName + "(" + joined + ")");
            }
        }

        return facts;
    }

    public String getVariableValueAsString(String var) {
        if (isIntVar(var)) return String.valueOf(getIntVar(var));
        if (isRealVar(var)) return String.valueOf(getRealVar(var));
        return null;
    }
    public boolean hasFactWithArity(String name, int arity) {
        for (String fact : getAllFacts()) {
            if (!fact.startsWith(name + "(")) continue;
            if (!fact.endsWith(")")) continue;

            String inside = fact.substring(fact.indexOf("(") + 1, fact.lastIndexOf(")"));
            String[] parts = inside.split(",");
            if (parts.length == arity) return true;
        }
        return false;
    }

    public void declarePercept(String percept) {
        percept = percept.trim();
        if (percept.isEmpty()) return;
        String baseName = percept;
        List<String> types = new ArrayList<>();

        if (percept.contains("(") && percept.endsWith(")")) {
            baseName = percept.substring(0, percept.indexOf("(")).trim();
            String paramStr = percept.substring(percept.indexOf("(") + 1, percept.lastIndexOf(")")).trim();

            if (!paramStr.isEmpty()) {
                String[] parts = paramStr.split(",");
                for (String type : parts) {
                    type = type.trim();
                    if (!type.equals("INT") && !type.equals("REAL")) {
                        logger.log("❌ Error: Invalid parameter type in percept declaration: " + type, true, false);
                        System.exit(1);
                    }
                    types.add(type);
                }
            }
        }
        declaredPercepts.add(baseName);
        perceptsParameterTypes.put(baseName, types); 

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            sb.append(types.get(i));             // ✔️ Usa el tipo real: INT o REAL
            if (i < types.size() - 1) sb.append(",");
        }
      
        if (!types.isEmpty()) {
            declareFact(baseName + "(" + sb + ")");
        } else {
            declareFact(baseName);
        }
    }

    public void setLogger(LoggerManager logger) {
        this.logger = logger;
    }
    
    public int getActionParameterCount(String actionName) {
        int paramCount = countParameters(actionName, declaredDiscreteActions);
        if (paramCount != -1) return paramCount;
        
        paramCount = countParameters(actionName, declaredDurativeActions);
        if (paramCount != -1) return paramCount;

        return -1; 
    }
    
    private int countParameters(String actionName, Set<String> declaredActions) {
        for (String declaredAction : declaredActions) {
            if (declaredAction.startsWith(actionName + "(") && declaredAction.endsWith(")")) { 
                String paramContent = declaredAction.substring(actionName.length() + 1, declaredAction.length() - 1);
                if (paramContent.trim().isEmpty()) {
                    return 0; 
                }
                return paramContent.split(",").length;
            }
        }
        return -1; 
    }
    
    public Set<String> getDeclaredActions() {
        Set<String> allActions = new HashSet<>();
        allActions.addAll(declaredDiscreteActions);
        allActions.addAll(declaredDurativeActions);
        return allActions;
    }
    public boolean isDurativeAction(String action) {
        String baseAction = action.contains("(") ? action.substring(0, action.indexOf("(")) : action;
        
        // Comprobamos si la acción base coincide con alguna de las acciones declaradas (sin paréntesis ni parámetros)
        return declaredDurativeActions.stream().map(a -> a.contains("(") ? a.substring(0, a.indexOf("(")) : a).anyMatch(a -> a.equals(baseAction));
    }
    public boolean isDiscreteAction(String action) {
        String baseAction = action.contains("(") ? action.substring(0, action.indexOf("(")) : action;
        
        // Comprobamos si la acción base coincide con alguna de las acciones declaradas (sin paréntesis ni parámetros)
        return declaredDiscreteActions.stream().map(a -> a.contains("(") ? a.substring(0, a.indexOf("(")) : a).anyMatch(a -> a.equals(baseAction));
    }
    public void declareDiscreteAction(String action) {
        declaredDiscreteActions.add(action);
    }
    public Set<String> getDeclaredDiscreteActions() {
        return new HashSet<>(declaredDiscreteActions);
    }
    public synchronized void removeFact(String factPattern) {
        factPattern = factPattern.replace(".end", "_end");

        if (factPattern.equals("t1_end")) {
            if (activeFactsNoParams.remove(factPattern)) {
                logger.log("🗑️ Fact removed: " + factPattern, true, false);
            }
            return;
        }

        // 1. Wildcard case
        if (factPattern.contains("_") && factPattern.contains("(")) {
            String insideParens = factPattern.substring(factPattern.indexOf("(") + 1, factPattern.indexOf(")"));
            if (insideParens.contains("_")) {
                removeFactWithWildcard(factPattern);
                return;
            }
        }

        // 2. No-parameter case
        if (!factPattern.contains("(")) {
            if (activeFactsNoParams.remove(factPattern)) {
                logger.log("🗑️ Fact removed: " + factPattern, true, false);
            }
            return;
        }

        // 3. Fact with parameters – this part was missing!
        String baseFactName = factPattern.substring(0, factPattern.indexOf("(")).trim();
        String paramStr = factPattern.substring(factPattern.indexOf("(") + 1, factPattern.lastIndexOf(")")).trim();
        String[] paramArray = paramStr.split(",");

        List<String> expectedTypes = factParameterTypes.getOrDefault(baseFactName, new ArrayList<>());
        if (paramArray.length != expectedTypes.size()) {
            logger.log("⚠️ Mismatch in parameter count for fact: " + factPattern, true, false);
            return;
        }

        List<Object> paramList = new ArrayList<>();
        for (int i = 0; i < paramArray.length; i++) {
            String value = paramArray[i].trim();
            String expectedType = expectedTypes.get(i);

            try {
                if (expectedType.equals("INT")) {
                    paramList.add(Integer.parseInt(value));
                } else if (expectedType.equals("REAL")) {
                    paramList.add(Double.parseDouble(value));
                } else {
                    logger.log("⚠️ Unknown parameter type for fact: " + factPattern, true, false);
                    return;
                }
            } catch (NumberFormatException e) {
                logger.log("⚠️ Invalid parameter format for fact: " + factPattern, true, false);
                return;
            }
        }

        List<List<Object>> instances = activeFacts.get(baseFactName);
        if (instances != null) {
            boolean removed = instances.remove(paramList);
            if (removed) {
                logger.log("🗑️ Fact removed: " + factPattern, true, false);
            }
            if (instances.isEmpty()) {
                activeFacts.remove(baseFactName);
            }
        }
    }

    public synchronized void removeFactWithWildcard(String factPattern) {
        logger.log("🔍 Calling removeFactWithWildcard with: " + factPattern, true, true);

        if (!factPattern.contains("_")) {
            removeFact(factPattern);
            return;
        }

        if (!factPattern.contains("(") || !factPattern.contains(")")) {        	
            return;
        }

        String baseFactName = factPattern.substring(0, factPattern.indexOf("("));       
        if (baseFactName.endsWith("_end") || !activeFacts.containsKey(baseFactName)) {
        	logger.log("⚠️ Ignoring wildcard removal for: " + factPattern, true, false);
            return;
        }

        String paramPattern = factPattern.substring(factPattern.indexOf("(") + 1, factPattern.indexOf(")"));
        String[] paramParts = paramPattern.split(",");

        List<List<Object>> instances = activeFacts.get(baseFactName);
        boolean removed = instances.removeIf(params -> {
            if (params.size() != paramParts.length) return false;

            for (int i = 0; i < params.size(); i++) {
            	if (!paramParts[i].equals("_") && !isParameterMatch(paramParts[i], params.get(i))){
            	    return false;
                }
            }
            return true;
        });
        if (removed) {
        	logger.log("🗑️ Removed facts matching wildcard pattern: " + factPattern, true, true);
        }
        if (instances.isEmpty()) {
            activeFacts.remove(baseFactName);
        }
    }
    private boolean isParameterMatch(String pattern, Object value) {
        if (value instanceof Integer) {
            try {
                return Integer.parseInt(pattern) == (Integer) value;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (value instanceof Double) {
            try {
                return Double.parseDouble(pattern) == ((Double) value);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public synchronized void addIntVar(String varName, int initialValue) {
        if (!intVars.containsKey(varName)) {
            intVars.put(varName, initialValue);
            if (logger != null) {
                logger.log("ℹ️ Initialized integer variable '" + varName + "' to " + initialValue, true, false);
            }
        } else {
            if (logger != null) {
                logger.log("🔁 Skipped reinitialization of int var '" + varName + "' (already has value: " + intVars.get(varName) + ")", true, false);
            }
        }
    }


    public synchronized void setIntVar(String varName, int value) {
        if (intVars.containsKey(varName)) {
            intVars.put(varName, value);
        }
    }

    public boolean isIntVar(String varName) {
        return intVars.containsKey(varName);
    }

    public Map<String, Integer> getAllIntVars() {
        return new HashMap<>(intVars);
    }

    public synchronized void addRealVar(String varName, double initialValue) {
        if (!realVars.containsKey(varName)) {
            realVars.put(varName, initialValue);
            if (logger != null) {
                logger.log("ℹ️ Initialized real variable '" + varName + "' to " + initialValue, false, false);
            }
        } else {
            if (logger != null) {
                logger.log("🔁 Skipped reinitialization of real var '" + varName + "' (already has value: " + realVars.get(varName) + ")", false, false);
            }
        }
    }


    public synchronized void setRealVar(String varName, double value) {
        if (realVars.containsKey(varName)) {
            realVars.put(varName, value);
        }
    }

    public boolean isRealVar(String varName) {
        return realVars.containsKey(varName);
    }

    public Map<String, Double> getAllRealVars() {
        return new HashMap<>(realVars);
    }

    public void declareFact(String fact) {
        fact = fact.trim();
        String baseFact = fact.contains("(") ? fact.substring(0, fact.indexOf("(")) : fact;

        List<String> types = new ArrayList<>();

        if (fact.contains("(") && fact.contains(")")) {
            String paramPart = fact.substring(fact.indexOf("(") + 1, fact.indexOf(")")).trim();
            if (!paramPart.isEmpty()) {
                String[] tokens = paramPart.split(",");
                for (String tok : tokens) {
                    types.add(tok.trim());  
                }
            }
        }
        perceptsParameterTypes.put(baseFact, types); 
        if (!declaredFacts.containsKey(baseFact)) {
            declaredFacts.put(baseFact, types.size());
            factParameterTypes.put(baseFact, types);  
        }
    }

    public List<String> getFactParameterTypes(String factName) {
        return factParameterTypes.getOrDefault(factName, new ArrayList<>());
    }


    public int getFactParameterCount(String factName) {
        if (declaredFacts.containsKey(factName)) {
            return declaredFacts.get(factName);
        }
        return -1; 
    }

    public synchronized void addFact(String factWithParams) {
        factWithParams = factWithParams.trim();

        String baseFactName = factWithParams.contains("(")
            ? factWithParams.substring(0, factWithParams.indexOf("("))
            : factWithParams;

        if (!declaredFacts.containsKey(baseFactName)) {
            logger.log("⚠️ Attempt to activate an undeclared fact: " + factWithParams, true, false);
            return;
        }

        List<Object> parameters = new ArrayList<>();
        if (factWithParams.contains("(") && factWithParams.contains(")")) {
            String paramStr = factWithParams.substring(factWithParams.indexOf("(") + 1, factWithParams.lastIndexOf(")"));
            String[] paramArray = paramStr.split(",");
            List<String> expectedTypes = perceptsParameterTypes.getOrDefault(baseFactName, new ArrayList<>());

            if (expectedTypes.size() != paramArray.length) {
                logger.log("⚠️ Mismatch in parameter count for fact: " + factWithParams, true, false);
                return;
            }

            for (int i = 0; i < paramArray.length; i++) {
                String text = paramArray[i].trim();
                String expectedType = expectedTypes.get(i);

                try {
                    if (expectedType.equals("INT")) {
                        parameters.add(Integer.parseInt(text));
                    } else if (expectedType.equals("REAL")) {
                        parameters.add(Double.parseDouble(text));
                    } else {
                        logger.log("⚠️ Unknown expected parameter type for " + baseFactName + ": " + expectedType, true, false);
                        return;
                    }
                } catch (NumberFormatException e) {
                    logger.log("⚠️ Invalid parameter value: '" + text + "' for expected type: " + expectedType, true, false);
                    return;
                }
            }
        }

        if (parameters.isEmpty()) {
            if (!activeFactsNoParams.contains(baseFactName)) {
                activeFactsNoParams.add(baseFactName);
            }
        } else {
            List<List<Object>> factParamsList = activeFacts.computeIfAbsent(baseFactName, k -> new ArrayList<>());
            if (!factParamsList.contains(parameters)) {
                factParamsList.add(parameters);
            }
        }
    }


    public synchronized boolean isFactActive(String factPattern) {
        if (factPattern.contains("(")) {
            String factBase = factPattern.split("\\(")[0];
            Pattern pattern = Pattern.compile(factPattern.replace("_", "\\\\d+"));
            
            return activeFacts.containsKey(factBase) &&
                   activeFacts.get(factBase).stream().anyMatch(params -> pattern.matcher(params.toString()).matches());
        } else {
            return activeFactsNoParams.contains(factPattern);
        }
    }

    public Set<String> getDeclaredFacts() {
        return new HashSet<>(declaredFacts.keySet()); // 🔹 Retorna solo los nombres de los hechos
    }


    public Set<String> getActiveFactsNoParams() {
        return new HashSet<>(activeFactsNoParams);
    }

    public Map<String, List<List<Object>>> getActiveFacts() {
        return activeFacts;
    }

    public void declareDurativeAction(String action) {
        declaredDurativeActions.add(action);
    }
    public Set<String> getDeclaredDurativeActions() {
        return new HashSet<>(declaredDurativeActions);
    }
    public synchronized void declareTimer(String timer) {
        declaredTimers.add(timer);
        declaredFacts.put(timer + "_end", 0); 
    }

    public void startTimer(String timerId, int durationSeconds) {
        if (!declaredTimers.contains(timerId)) {
        	logger.log("⚠️ Attempt to start an undeclared timer: " + timerId, true, false);
            return;
        }
        timers.put(timerId, System.currentTimeMillis() + (durationSeconds * 1000));
        removeFact(timerId + "_end");
        logger.log("⏳ Timer started: " + timerId + " for " + durationSeconds + " seconds", true, true);
    }

    public synchronized void stopTimer(String timerId) {
        if (!timers.containsKey(timerId) && !pausedTimers.containsKey(timerId)) {
        	logger.log("⚠️ Attempt to stop an undeclared or already removed timer: " + timerId, true, true);
            return;
        }
        timers.remove(timerId);
        pausedTimers.remove(timerId);
        addFact(timerId + "_end");
        logger.log("🛑 Timer stopped: " + timerId, true, true);
    }

    public synchronized void pauseTimer(String timerId) {
        if (!timers.containsKey(timerId)) {
        	logger.log("⚠️ Attempt to pause an undeclared timer: " + timerId, true, true);
            return;
        }

        long remainingTime = timers.get(timerId) - System.currentTimeMillis();
        if (remainingTime > 0) {
            pausedTimers.put(timerId, remainingTime);
            timers.remove(timerId);
            logger.log("⏸️ Timer paused: " + timerId + ", remaining time: " + remainingTime + " ms", true, true);
        }
    }
    public synchronized void continueTimer(String timerId) {
        if (pausedTimers.containsKey(timerId)) {
            long remainingTime = pausedTimers.remove(timerId);
            long resumeTime = System.currentTimeMillis() + remainingTime;

            timers.put(timerId, resumeTime);
            logger.log("▶️ Timer resumed: " + timerId + ", new expiration in " + remainingTime + " ms.", true, true);
        } else {
        	logger.log("⚠️ Attempted to resume a non-paused timer: " + timerId, true, true);
        }
    }
    public synchronized boolean isTimerExpired(String timerId) {
        if (!timers.containsKey(timerId)) {
            return false;
        }
        boolean expired = System.currentTimeMillis() >= timers.get(timerId);
        String timerEndFact = timerId + "_end";

        if (expired) {
            if (!isFactActive(timerEndFact)) {
                addFact(timerEndFact);
                logger.log("✅ Timer expired: " + timerEndFact + " activated", true, true);
            }
            timers.remove(timerId);
            logger.log("🛑 Timer fully removed: " + timerId, true, true);
        }
        return expired;
    }
    public Set<String> getDeclaredTimers() {
        return new HashSet<>(declaredTimers);
    }
    public Map<String, Long> getAllTimers() {
        return new HashMap<>(timers);
    }
    public Set<String> getDeclaredIntVars() {
        return new HashSet<>(intVars.keySet());
    }
    public Set<String> getDeclaredRealVars() {
        return new HashSet<>(realVars.keySet());
    }
    public boolean isFactDeclared(String factName) {
        return declaredFacts.containsKey(factName);
    }
    
    public synchronized void dumpState() {
    	StringBuilder sb = new StringBuilder();      
        sb.append(activeFacts).append("\n");
        sb.append(activeFactsNoParams).append("\n");
        sb.append(intVars).append("\n");
        sb.append(realVars).append("\n");
        String currentDump = sb.toString();
        if (currentDump.equals(lastDump)) {
            return;
        }
        lastDump = currentDump;
    	logger.log("\n🔹 Current BeliefStore state:", true, true);
    	logger.log("   Active facts without parameters: " + activeFactsNoParams, true, false);
        
    	logger.log("   Active facts with parameters: {", false, false);
        for (Map.Entry<String, List<List<Object>>> entry : activeFacts.entrySet()) {
        	logger.log(entry.getKey() + "=" + entry.getValue() + ", ", false, false);
        }
        logger.log("}", true, false);

        logger.log("   Integer variables: " + intVars, true, false);
        logger.log("   Real variables: {", false, false);
        for (Map.Entry<String, Double> entry : realVars.entrySet()) {
        	logger.log(entry.getKey() + "=" + entry.getValue() + ", ", false, false);
        }
        logger.log("}\n", true, false);
    }
    public synchronized int getIntVar(String varName) {
        if (intVars.containsKey(varName)) {
            return intVars.get(varName);
        } else {
            throw new IllegalArgumentException("Undefined integer variable: " + varName);
        }
    }

    public synchronized double getRealVar(String varName) {
        if (realVars.containsKey(varName)) {
            return realVars.get(varName);
        } else {
            throw new IllegalArgumentException("Undefined real variable: " + varName);
        }
    }

}
