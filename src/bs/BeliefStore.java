package bs;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import both.LoggerManager;

public class BeliefStore {
    private final Map<String, Integer> intVars = new HashMap<>();
    private final Map<String, Double> realVars = new HashMap<>();
    private final Map<String, List<List<Integer>>> activeFacts = new HashMap<>();
    private final Set<String> activeFactsNoParams = new HashSet<>();
    private final Map<String, Integer> declaredFacts = new HashMap<>();

    private final Set<String> declaredTimers = new HashSet<>();
    private final Set<String> declaredDurativeActions = new HashSet<>();
    private final Map<String, Long> timers = new HashMap<>();
    private final Map<String, Long> pausedTimers = new HashMap<>();
    private final Set<String> declaredDiscreteActions = new HashSet<>();
    private LoggerManager logger;

    public void setLogger(LoggerManager logger) {
        this.logger = logger;
    }

    public int getActionParameterCount(String actionName) {
        int paramCount = countParameters(actionName, declaredDiscreteActions);
        if (paramCount != -1) return paramCount;
        
        paramCount = countParameters(actionName, declaredDurativeActions);
        if (paramCount != -1) return paramCount;

        return -1; // Si la acción no está en `DISCRETE:` ni `DURATIVE:`, devolver -1 (acción no declarada)
    }

    /**
     * ✅ Método auxiliar para contar los parámetros de una acción en su declaración.
     */
    private int countParameters(String actionName, Set<String> declaredActions) {
        for (String declaredAction : declaredActions) {
            if (declaredAction.startsWith(actionName + "(") && declaredAction.endsWith(")")) { // Buscar la acción con `()`
                String paramContent = declaredAction.substring(actionName.length() + 1, declaredAction.length() - 1);
                if (paramContent.trim().isEmpty()) {
                    return 0; // ✅ Acciones como `alarma()` deben devolver 0 parámetros
                }
                return paramContent.split(",").length;
            }
        }
        return -1; // ✅ Si la acción no tiene `()`, no está correctamente declarada
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

    public void removeFact(String factPattern) {
    	factPattern = factPattern.replace(".end", "_end");
        if (factPattern.equals("t1_end")) {
            // ✅ Si el hecho es `t1_end`, eliminarlo directamente sin pasar por wildcard
            if (activeFactsNoParams.remove(factPattern)) {
              //  logger.logPN("🗑️ Fact removed: " + factPattern);
            }
            return;
        }

        // ✅ Para hechos con `_`, usar la lógica de wildcard
        if (factPattern.contains("_")) {
            removeFactWithWildcard(factPattern);
            return;
        }

        // ✅ Manejo estándar para otros hechos
        if (factPattern.contains("(")) {
            String baseFactName = factPattern.substring(0, factPattern.indexOf("("));
            String paramPattern = factPattern.substring(factPattern.indexOf("(") + 1, factPattern.indexOf(")"));

            if (activeFacts.containsKey(baseFactName)) {
                List<List<Integer>> instances = activeFacts.get(baseFactName);                      
                if (instances.isEmpty()) {
                    activeFacts.remove(baseFactName);
                }
            }
        } else {
            if (activeFactsNoParams.remove(factPattern)) {
               // logger.logPN("🗑️ Fact removed: " + factPattern);
            }
        }
    }


    public void removeFactWithWildcard(String factPattern) {
        logger.logPN("🔍 Calling removeFactWithWildcard with: " + factPattern);

        if (!factPattern.contains("_")) {
            removeFact(factPattern);
            return;
        }

        if (!factPattern.contains("(") || !factPattern.contains(")")) {
        	//logger.logPN("ℹ️ Skipped removal: " + factPattern + " is a fact without parameters.");
            return;
        }

        String baseFactName = factPattern.substring(0, factPattern.indexOf("("));

        // ❌ Excluir `t1_end` y hechos sin parámetros
        if (baseFactName.endsWith("_end") || !activeFacts.containsKey(baseFactName)) {
        	logger.logPN("⚠️ Ignoring wildcard removal for: " + factPattern);
            return;
        }

        String paramPattern = factPattern.substring(factPattern.indexOf("(") + 1, factPattern.indexOf(")"));
        String[] paramParts = paramPattern.split(",");

        List<List<Integer>> instances = activeFacts.get(baseFactName);
        boolean removed = instances.removeIf(params -> {
            if (params.size() != paramParts.length) return false;

            for (int i = 0; i < params.size(); i++) {
                if (!paramParts[i].equals("_") && !paramParts[i].equals(String.valueOf(params.get(i)))) {
                    return false;
                }
            }
            return true;
        });

        if (removed) {
        	logger.logPN("🗑️ Removed facts matching wildcard pattern: " + factPattern);
        }

        if (instances.isEmpty()) {
            activeFacts.remove(baseFactName);
        }
    }


    // ------------------------ Manejo de Variables ------------------------
    public void addIntVar(String varName, int initialValue) {
        intVars.put(varName, initialValue);
    }

    public void setIntVar(String varName, int value) {
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

    public void addRealVar(String varName, double initialValue) {
        realVars.put(varName, initialValue);
    }

    public void setRealVar(String varName, double value) {
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

    // ------------------------ Manejo de Hechos ------------------------
    public void declareFact(String fact) {
        fact = fact.trim();
        
        // Extraer la parte base del hecho, sin los parámetros
        String baseFact = fact.contains("(") ? fact.substring(0, fact.indexOf("(")) : fact;

        // Determinar cuántos parámetros tiene el hecho
        int paramCount = 0;
        if (fact.contains("(") && fact.contains(")")) {
            String paramPart = fact.substring(fact.indexOf("(") + 1, fact.indexOf(")")).trim();
            if (!paramPart.isEmpty()) {
                paramCount = paramPart.split(",").length; // Contar parámetros
            }
        }

        // Agregar solo la base del hecho con la cantidad de parámetros esperada
        if (!declaredFacts.containsKey(baseFact)) {
            declaredFacts.put(baseFact, paramCount);
           // logger.logPN("📌 Declared fact: " + baseFact + " (Expected Parameters: " + paramCount + ")");
        }
    }

    public int getFactParameterCount(String factName) {
        if (declaredFacts.containsKey(factName)) {
            return declaredFacts.get(factName);
        }
        return -1; // Retorna -1 si el hecho no está declarado
    }

    public void addFact(String factWithParams) {
        factWithParams = factWithParams.trim();

        // ✅ **Extraer el nombre base del hecho (sin parámetros)**
        String baseFactName = factWithParams.contains("(") ? factWithParams.substring(0, factWithParams.indexOf("(")) : factWithParams;

        // ✅ **Extraer parámetros (si existen)**
        Integer[] parameters = new Integer[0];
        if (factWithParams.contains("(") && factWithParams.contains(")")) {
            String paramStr = factWithParams.substring(factWithParams.indexOf("(") + 1, factWithParams.indexOf(")"));
            String[] paramArray = paramStr.split(",");

            if (!paramStr.isEmpty()) {
                parameters = Arrays.stream(paramArray)
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .toArray(Integer[]::new);
            }
        }

        // ✅ **Verificar que el hecho base está en `declaredFacts`**
        if (!declaredFacts.containsKey(baseFactName)) {
        	logger.logPN("⚠️ Attempt to activate an undeclared fact: " + factWithParams);
            return;
        }

        // ✅ **Registrar hechos SIN parámetros**
        if (parameters.length == 0) {
            if (!activeFactsNoParams.contains(baseFactName)) {
                activeFactsNoParams.add(baseFactName);
              //  System.out.println("✅ Activated fact without parameters: " + baseFactName);
            }
        } 
        // ✅ **Registrar hechos CON parámetros**
        else {
            List<Integer> paramList = Arrays.asList(parameters);
            activeFacts.computeIfAbsent(baseFactName, k -> new ArrayList<>());

            // Evitar duplicados en los parámetros
            if (!activeFacts.get(baseFactName).contains(paramList)) {
                activeFacts.get(baseFactName).add(paramList);
            //    logger.logPN("✅ Activated fact with parameters: " + baseFactName + paramList);
            }
        }
    }

    public boolean isFactActive(String factPattern) {
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

    public Map<String, List<List<Integer>>> getActiveFacts() {
        return activeFacts;
    }

    public void declareDurativeAction(String action) {
        declaredDurativeActions.add(action);
    }
/*
    public boolean isDurativeAction(String action) {
        return declaredDurativeActions.contains(action);
    }
*/
    public Set<String> getDeclaredDurativeActions() {
        return new HashSet<>(declaredDurativeActions);
    }

    // ------------------------ Manejo de Temporizadores ------------------------
    public void declareTimer(String timer) {
        declaredTimers.add(timer);
        declaredFacts.put(timer + "_end", 0); // 🔹 Agregarlo con 0 parámetros
// Registrar `t1_end` como hecho en lugar de `t1.end`
    }

    public void startTimer(String timerId, int durationSeconds) {
        if (!declaredTimers.contains(timerId)) {
        	logger.logPN("⚠️ Attempt to start an undeclared timer: " + timerId);
            return;
        }
        timers.put(timerId, System.currentTimeMillis() + (durationSeconds * 1000));
        removeFact(timerId + "_end");
        logger.logPN("⏳ Timer started: " + timerId + " for " + durationSeconds + " seconds");
    }

    public void stopTimer(String timerId) {
        if (!timers.containsKey(timerId) && !pausedTimers.containsKey(timerId)) {
        	logger.logPN("⚠️ Attempt to stop an undeclared or already removed timer: " + timerId);
            return;
        }
        timers.remove(timerId);
        pausedTimers.remove(timerId);
        addFact(timerId + "_end");
        logger.logPN("🛑 Timer stopped: " + timerId);
    }

    public void pauseTimer(String timerId) {
        if (!timers.containsKey(timerId)) {
        	logger.logPN("⚠️ Attempt to pause an undeclared timer: " + timerId);
            return;
        }

        long remainingTime = timers.get(timerId) - System.currentTimeMillis();
        if (remainingTime > 0) {
            pausedTimers.put(timerId, remainingTime);
            timers.remove(timerId);
            logger.logPN("⏸️ Timer paused: " + timerId + ", remaining time: " + remainingTime + " ms");
        }
    }

    public void continueTimer(String timerId) {
        if (pausedTimers.containsKey(timerId)) {
            long remainingTime = pausedTimers.remove(timerId);
            long resumeTime = System.currentTimeMillis() + remainingTime;

            timers.put(timerId, resumeTime);
            logger.logPN("▶️ Timer resumed: " + timerId + ", new expiration in " + remainingTime + " ms.");
        } else {
        	logger.logPN("⚠️ Attempted to resume a non-paused timer: " + timerId);
        }
    }

    public boolean isTimerExpired(String timerId) {
        if (!timers.containsKey(timerId)) {
            return false;
        }

        boolean expired = System.currentTimeMillis() >= timers.get(timerId);
        String timerEndFact = timerId + "_end";

        if (expired) {
            if (!isFactActive(timerEndFact)) {
                addFact(timerEndFact);
                logger.logPN("✅ Timer expired: " + timerEndFact + " activated");
            }
            timers.remove(timerId);
            logger.logPN("🛑 Timer fully removed: " + timerId);
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

    public void dumpState() {
    	logger.logPN("\n🔹 Current BeliefStore state:");
    	logger.logPN("   Active facts without parameters: " + activeFactsNoParams);
        
    	logger.logPN2("   Active facts with parameters: {");
        for (Map.Entry<String, List<List<Integer>>> entry : activeFacts.entrySet()) {
        	logger.logPN2(entry.getKey() + "=" + entry.getValue() + ", ");
        }
        logger.logPN("}");

        logger.logPN("   Integer variables: " + intVars);
        logger.logPN2("   Real variables: {");
        for (Map.Entry<String, Double> entry : realVars.entrySet()) {
        	logger.logPN2(entry.getKey() + "=" + entry.getValue() + ", ");
        }
        logger.logPN("}\n");
    }


}
