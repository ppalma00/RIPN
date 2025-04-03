package PN;

import java.util.*;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

import core.BeliefStore;
import core.ExpressionEvaluatorPN;

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

    public void setDiscreteActionArity(Map<String, Integer> arityMap) {
        this.discreteActionArity = arityMap;
    }


    public PetriNet() {
        this.beliefStore = new BeliefStore();
    }
    public void setObserver(Observer observer) {
        this.observer = observer;
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

    public boolean canFire(String transitionName) {
        Transition transition = transitions.get(transitionName);
        if (transition == null) return false;

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

        if (transitionConditions.containsKey(transitionName)) {
            String condition = transitionConditions.get(transitionName);
            if (!ExpressionEvaluatorPN.evaluateLogicalExpression(condition, beliefStore)) {
                System.out.println("🚫 Skipped firing transition " + transitionName + " (Condition not met: " + condition + ")");
                return pendingDiscreteNotifications; // devuelvo vacío si no se cumple
            }
        }

        if (!canFire(transitionName)) return pendingDiscreteNotifications;

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

        // 🔁 Marcar lugares de salida y acumular acciones discretas
        for (Place p : outputPlaces) {
            boolean wasEmpty = !p.hasToken();
            p.setToken(true);

            if (wasEmpty) {
                executePlaceActions(p.getName());

         //       if (placeDiscreteActions.containsKey(p.getName())) {
           //         String actionName = placeDiscreteActions.get(p.getName());
           //         System.out.println("⚙️ Acción discreta acumulada: " + actionName + " en lugar " + p.getName());
             //       pendingDiscreteNotifications.add(actionName); // ← esta es la que se devuelve
              //  }
            }
        }

        // 🔁 Vaciar lugares de entrada
        for (Place p : inputPlaces) {
        //    System.out.println("💥 Vaciando " + p.getName());
            p.setToken(false);
        }

        executeTransitionActions(transitionName);

        System.out.println("🔥 Transition fired: " + transitionName);
   //     System.out.println("📤 Se devuelven acciones desde fire: " + pendingDiscreteNotifications);
        return pendingDiscreteNotifications;
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


    private void executeTransitionActions(String transitionName) {
    	// 🔹 Verificar si hay una condición para esta transición
        if (transitionConditions.containsKey(transitionName)) {
            String condition = transitionConditions.get(transitionName);
            boolean conditionMet = ExpressionEvaluatorPN.evaluateLogicalExpression(condition, beliefStore);

            if (!conditionMet) {
                System.out.println("🚫 Skipped actions in transition " + transitionName + " (Condition not met: " + condition + ")");
                return; // 🔹 Si la condición no se cumple, NO ejecutamos las acciones
            }
        }
        if (transitionVariableUpdates.containsKey(transitionName)) {
            for (String update : transitionVariableUpdates.get(transitionName)) {
                update = update.trim();
                
                if (update.startsWith("remember(") && update.endsWith(")")) {
                    String fact = update.substring(9, update.length() - 1).trim();
                    processRememberFact(fact);
                } else if (update.startsWith("forget(") && update.endsWith(")")) {
                    String fact = update.substring(7, update.length() - 1).trim();
                    processForgetFact(fact);
                } else {
                    // Procesar asignación de variables como antes
                    String[] parts = update.split(":=");
                    if (parts.length == 2) {
                        String varName = parts[0].trim();
                        String expression = parts[1].trim();
                        try {
                            Object result = evaluateExpression(expression);
                            if (result instanceof Integer && beliefStore.isIntVar(varName)) {
                                beliefStore.setIntVar(varName, (Integer) result);
                              //  System.out.println("🔄 Updated INT variable: " + varName + " = " + result + " (from transition " + transitionName + ")");
                            } else if (result instanceof Double && beliefStore.isRealVar(varName)) {
                                beliefStore.setRealVar(varName, (Double) result);
                               // System.out.println("🔄 Updated REAL variable: " + varName + " = " + result + " (from transition " + transitionName + ")");
                            } else {
                                System.err.println("❌ Invalid type for variable: " + varName);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error evaluating expression: " + expression);
                        }
                    }
                }
            }
        }
    }
    public Map<String, String> getPlaceDiscreteActions() {
        return placeDiscreteActions;
    }

    private void processForgetFact(String fact) {
        if (fact.contains("(") && fact.endsWith(")")) {
            String factName = fact.substring(0, fact.indexOf("(")).trim();
            String paramStr = fact.substring(fact.indexOf("(") + 1, fact.length() - 1).trim();

            // Convertir parámetros a lista de Strings (manteniendo el comodín '_')
            List<String> paramList = Arrays.stream(paramStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (beliefStore.getActiveFacts().containsKey(factName)) {
                List<List<Integer>> instances = beliefStore.getActiveFacts().get(factName);

                // 🔹 Filtrar y eliminar hechos que coincidan con el patrón del comodín '_'
                boolean removed = instances.removeIf(existingParams -> {
                    if (existingParams.size() != paramList.size()) return false;
                    for (int i = 0; i < existingParams.size(); i++) {
                        if (!paramList.get(i).equals("_") && !paramList.get(i).equals(String.valueOf(existingParams.get(i)))) {
                            return false;
                        }
                    }
                    return true;
                });

                if (removed) {
                 //   System.out.println("🗑️ Forgot facts matching pattern: " + fact);
                }

                // Si no quedan más hechos con ese nombre, eliminar la clave
                if (instances.isEmpty()) {
                    beliefStore.getActiveFacts().remove(factName);
                }
            }
        } else {
            // Eliminación estándar sin parámetros
            beliefStore.removeFact(fact);
     //       System.out.println("🗑️ Forgot fact: " + fact);
        }
    }



    private void processRememberFact(String fact) {
        if (fact.contains("(") && fact.endsWith(")")) {
            String factName = fact.substring(0, fact.indexOf("(")).trim();
            String paramStr = fact.substring(fact.indexOf("(") + 1, fact.length() - 1).trim();
            
            // Convertir parámetros a enteros
            try {
                List<Integer> params = Arrays.stream(paramStr.split(","))
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .collect(Collectors.toList()); // 🔹 Corrección aquí
                beliefStore.addFact(factName + "(" + paramStr + ")"); // Guardar hecho con parámetros
       //         System.out.println("🧠 Remembered fact with parameters: " + factName + params);
            } catch (NumberFormatException e) {
                System.err.println("❌ Error parsing parameters for fact: " + fact);
            }
        } else {
            beliefStore.addFact(fact); // Hecho sin parámetros
        //    System.out.println("🧠 Remembered fact: " + fact);
        }
    }

    public void executePlaceActions(String placeName) {
        // 🔹 Verificar si hay una condición para este lugar
        if (placeConditions.containsKey(placeName)) {
            String condition = placeConditions.get(placeName);
            boolean conditionMet = ExpressionEvaluatorPN.evaluateLogicalExpression(condition, beliefStore);

            if (!conditionMet) {
                System.out.println("🚫 Skipped actions in place " + placeName + " (Condition not met: " + condition + ")");
                return; // 🔹 Si la condición no se cumple, NO ejecutamos las acciones
            }
        }

        if (placeVariableUpdates.containsKey(placeName)) {
            for (String update : placeVariableUpdates.get(placeName)) {
                update = update.trim();

                // 🔹 Procesar hechos
                if (update.startsWith("remember(") && update.endsWith(")")) {
                    String fact = update.substring(9, update.length() - 1).trim();
                    processRememberFact(fact);

                } else if (update.startsWith("forget(") && update.endsWith(")")) {
                    String fact = update.substring(7, update.length() - 1).trim();
                    processForgetFact(fact);

                } else if (update.contains(":=")) {
                    // 🔹 Procesar asignación de variables
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
                                System.err.println("❌ Invalid type for variable: " + varName);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error evaluating expression: " + expression);
                        }
                    }

                } else if (update.matches("\\w+\\(.*\\)")) {
                    // 🔹 Acción con parámetros: act1(), act3(5,6.7)
                    String name = update.substring(0, update.indexOf("("));
                    String argsRaw = update.substring(update.indexOf("(") + 1, update.lastIndexOf(")")).trim();

                    double[] args;
                    if (argsRaw.isEmpty()) {
                        args = new double[0];
                    } else {
                        args = Arrays.stream(argsRaw.split(","))
                                     .map(String::trim)
                                     .mapToDouble(Double::parseDouble)
                                     .toArray();
                    }

                    // Ejecutar solo si es una acción discreta declarada
                    if (discreteActionArity.containsKey(name)) {
                        if (observer != null) {
                            observer.onDiscreteActionExecuted(name, args);
                        }
                    }
                    // (Acciones durativas se manejan en updateDurativeActions)
                }
            }
        }
    }


    private Object evaluateExpression(String expression) {
        Map<String, Object> context = new HashMap<>();
        
        // Cargar variables del BeliefStore en el contexto de MVEL
        beliefStore.getAllIntVars().forEach(context::put);
        beliefStore.getAllRealVars().forEach(context::put);

        return MVEL.eval(expression, context);
    }

    public void printState() {
        System.out.println("Current state of the Petri Net:");
        for (Place p : places.values()) {
            System.out.println(p.getName() + ": " + (p.hasToken() ? "●" : "○"));
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
 // 📌 Método auxiliar para saber si una transición está en la sección <PN>
    public boolean hasPNDefinition(String transitionName) {
        return transitionVariableUpdates.containsKey(transitionName) || transitionConditions.containsKey(transitionName);
    }

    // 📌 Captura el marcado actual como un Map simple
    public Map<String, Boolean> captureCurrentMarking() {
        Map<String, Boolean> marking = new HashMap<>();
        for (Map.Entry<String, Place> entry : places.entrySet()) {
            marking.put(entry.getKey(), entry.getValue().hasToken());
        }
        return marking;
    }

    // 📌 Lógica de parada e inicio de acciones durativas
    public void updateDurativeActions(Map<String, Boolean> previousMarking) {
        // 🛑 PRIMERO: Parar acciones durativas
        for (String placeName : places.keySet()) {
            Place place = places.get(placeName);
            boolean currentlyMarked = place.hasToken();
            boolean previouslyMarked = previousMarking.getOrDefault(placeName, false);

         //   System.out.println("🔍 Durativa [" + placeName + "]: prev=" + previouslyMarked + ", now=" + currentlyMarked);

            if (!currentlyMarked && previouslyMarked) {
                if (placeVariableUpdates.containsKey(placeName)) {
                    for (String update : placeVariableUpdates.get(placeName)) {
                        if (isDurativeAction(update)) {
                            String actionName = extractActionName(update);
                      //      System.out.println("🛑 Parando acción durativa: " + actionName);
                            if (observer != null) {
                                observer.onDurativeActionStopped(actionName);
                            }
                        }
                    }
                }
            }
        }

        // 🟢 DESPUÉS: Iniciar acciones durativas
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
                    //        System.out.println("🟢 Iniciando acción durativa: " + actionName + Arrays.toString(params));
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
                        result[i] = 0; // por defecto si hay error
                    }
                }
                return result;
            }
        }
        return new double[0];
    }

    private int extractParameter(String actionCall) {
        int start = actionCall.indexOf("(") + 1;
        int end = actionCall.indexOf(")");
        if (start >= 0 && end > start) {
            try {
                return Integer.parseInt(actionCall.substring(start, end).trim());
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Error extracting parameter from: " + actionCall);
            }
        }
        return 0;
    }

}
