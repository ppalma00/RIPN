package tr;
import org.mvel2.MVEL;
import both.LoggerManager;
import bs.BeliefStore;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
public class ExpressionEvaluator {
	
	public boolean evaluateLogicalExpression(String expr, BeliefStore store, LoggerManager logger, Map<String, Boolean> outVarsMap) {
	    List<String> predicates = new ArrayList<>();
	    List<String> expressions = new ArrayList<>();

	    for (String rawPart : expr.split("&&")) {
	        String part = rawPart.trim();

	        if (part.matches("!?[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(.*\\)")) {
	            predicates.add(part);
	        } else {
	            expressions.add(part);
	        }
	    }


	    // Primero: evaluar predicados (con o sin out)
	    for (String part : predicates) {
	        boolean negated = false;
	        if (part.startsWith("!")) {
	            negated = true;
	            part = part.substring(1).trim();
	        }

	        int parenStart = part.indexOf("(");
	        int parenEnd = part.lastIndexOf(")");
	        if (parenStart == -1 || parenEnd == -1 || parenEnd < parenStart) {
	            logger.log("❌ Error: condición mal formada: " + part, true, false);
	            return false;
	        }

	        String predName = part.substring(0, parenStart).trim();
	        String[] args = part.substring(parenStart + 1, parenEnd).split(",");
	        List<String> params = new ArrayList<>();
	        for (String a : args) params.add(a.trim());

	        // Caso especial: comodín
	        if (params.size() == 1 && params.get(0).equals("_")) {
	            boolean exists = store.hasFactWithArity(predName, 1);
	            if (negated) exists = !exists;
	            if (!exists) return false;
	            continue;
	        }

	        List<List<String>> candidateFacts = store.getFactsMatchingNameAndArity(predName, params.size());

	        boolean matchFound = false;

	        for (List<String> factParams : candidateFacts) {
	            boolean compatible = true;

	            for (int i = 0; i < params.size(); i++) {
	                String param = params.get(i);
	                String factVal = factParams.get(i);
	                boolean isOut = outVarsMap.getOrDefault(param, false);

	                if (param.equals("_")) continue;

	                if (store.isIntVar(param)) {
	                    if (isOut) {
	                        try {
	                            int val = Integer.parseInt(factVal);
	                            store.setIntVar(param, val);
	                        } catch (NumberFormatException e) {
	                            logger.log("❌ Error: no se pudo asignar valor INT a " + param, true, false);
	                            return false;
	                        }
	                    } else {
	                        String val = String.valueOf(store.getIntVar(param));
	                        if (!val.equals(factVal)) {
	                            compatible = false;
	                            break;
	                        }
	                    }
	                } else if (store.isRealVar(param)) {
	                    if (isOut) {
	                        try {
	                            double val = Double.parseDouble(factVal);
	                            store.setRealVar(param, val);
	                        } catch (NumberFormatException e) {
	                            logger.log("❌ Error: no se pudo asignar valor REAL a " + param, true, false);
	                            return false;
	                        }
	                    } else {
	                        String val = String.valueOf(store.getRealVar(param));
	                        if (!val.equals(factVal)) {
	                            compatible = false;
	                            break;
	                        }
	                    }
	                } else {
	                    // Valor literal
	                    if (!param.equals(factVal)) {
	                        compatible = false;
	                        break;
	                    }
	                }
	            }

	            if (compatible) {
	                boolean hasOutInPredicate = params.stream().anyMatch(p -> outVarsMap.getOrDefault(p, false));
	                if (hasOutInPredicate && !expressions.isEmpty()) {
	                    // Requiere evaluar expresión con el binding actual
	                    Map<String, Object> candidateContext = new HashMap<>();
	                    candidateContext.putAll(store.getAllIntVars());
	                    candidateContext.putAll(store.getAllRealVars());
	                 // Añadir todos los hechos declarados (sin parámetros) como false si no existen
	                    for (String fact : store.getDeclaredFacts()) {
	                        if (!fact.contains("(")) {
	                            candidateContext.putIfAbsent(fact, false);
	                        }
	                    }
	                    for (String timer : store.getDeclaredTimers()) {
	                        String timerEndFact = timer + "_end";
	                        candidateContext.putIfAbsent(timerEndFact, false);
	                    }

	                    for (String var : outVarsMap.keySet()) {
	                        if (store.containsIntVar(var)) candidateContext.put(var, store.getIntVar(var));
	                        if (store.containsRealVar(var)) candidateContext.put(var, store.getRealVar(var));
	                    }
	                    for (String timer : store.getDeclaredTimers()) {
	                        String timerEndFact = timer + "_end";
	                        boolean isActive = store.isFactActive(timerEndFact);
	                        candidateContext.put(timerEndFact, isActive);
	                    }
	                    String exprOnly = String.join(" && ", expressions);
	                    try {
	                        Object result = MVEL.eval(exprOnly, candidateContext);
	                        if (result instanceof Boolean && (Boolean) result) {
	                            matchFound = true;
	                            break;
	                        }
	                    } catch (Exception e) {
	                        logger.log("❌ Error evaluando expresión lógica/arimética con binding: " + exprOnly + " → " + e.getMessage(), true, false);
	                        return false;
	                    }
	                } else {
	                    // No hay out o no hay expresión, basta con el matching
	                    matchFound = true;
	                    break;
	                }
	            }
	        }

	        if ((negated && matchFound) || (!negated && !matchFound)) {
	            return false;
	        }
	    }
	    if (!expressions.isEmpty()) {
	        String logicalExpr = String.join(" && ", expressions);
	        try {
	        	Map<String, Object> context = new HashMap<>();
	        	context.putAll(store.getAllIntVars());
	        	context.putAll(store.getAllRealVars());
	        	for (String fact : store.getDeclaredFacts()) {
	        	    if (!fact.contains("(")) {
	        	        context.putIfAbsent(fact, false);
	        	    }
	        	}
	        	for (String timer : store.getDeclaredTimers()) {
	        	    String timerEndFact = timer + "_end";
	        	    context.putIfAbsent(timerEndFact, false);
	        	}

	        	for (String timer : store.getDeclaredTimers()) {
	        	    String timerEndFact = timer + "_end";
	        	    boolean isActive = store.isFactActive(timerEndFact);
	        	    context.put(timerEndFact, isActive);
	        	}

	            for (String timer : store.getDeclaredTimers()) {
	                String timerEndFact = timer + "_end";
	                boolean isActive = store.isFactActive(timerEndFact);
	                context.put(timerEndFact, isActive);
	            }
	            Object result = MVEL.eval(logicalExpr, context);
	            if (result instanceof Boolean) return (Boolean) result;
	            logger.log("❌ Error: expresión no booleana: " + logicalExpr, true, false);
	            return false;
	        } catch (Exception e) {
	            logger.log("❌ Error en MVEL: " + e.getMessage() + "\n   ❌ Expresión: " + expr, true, false);
	            return false;
	        }
	    }

	    return true;
	}

	public boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore, LoggerManager logger) {
        try {
            condition = condition.replaceAll("\\bTrue\\b", "true").replaceAll("\\bFalse\\b", "false");
            condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");     
            Map<String, Object> context = new HashMap<>();
            context.putAll(beliefStore.getAllIntVars());
            context.putAll(beliefStore.getAllRealVars());
            for (String fact : beliefStore.getActiveFactsNoParams()) {
                context.put(fact, true);
            }
            for (Map.Entry<String, List<List<Object>>> entry : beliefStore.getActiveFacts().entrySet()) {
                String factBase = entry.getKey();
                for (List<Object> params : entry.getValue()) {
                    String factWithParams = factBase + "(" + params.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")) + ")";
                    context.put(factWithParams, true);
                }
            }        
            Set<String> intVars = beliefStore.getDeclaredIntVars();
            Set<String> realVars = beliefStore.getDeclaredRealVars();

            Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)"); 
            Matcher matcher = factPattern.matcher(condition);
            StringBuffer processedCondition = new StringBuffer();
            while (matcher.find()) {
                String factBase = matcher.group(1);
                String params = matcher.group(2).trim();
                String[] paramParts = params.isEmpty() ? new String[0] : params.split(",");
                boolean matchFound = false;

                List<List<Object>> factInstances = beliefStore.getActiveFacts().getOrDefault(factBase, Collections.emptyList());
                outerLoop:
                for (List<Object> instance : factInstances) {
                    if (instance.size() != paramParts.length) continue;
                    for (int i = 0; i < paramParts.length; i++) {
                        String token = paramParts[i].trim();
                        Object value = instance.get(i);

                        if (token.equals("_")) continue;

                        if (intVars.contains(token)) {
                            int val = ((Number) value).intValue();
                            context.put(token, val);
                            beliefStore.setIntVar(token, val);  // ✅ sincroniza con BeliefStore
                        } else if (realVars.contains(token)) {
                            double val = ((Number) value).doubleValue();
                            context.put(token, val);
                            beliefStore.setRealVar(token, val); // ✅ sincroniza con BeliefStore
                        } else {
                            // Constant match
                            if (!String.valueOf(value).equals(token)) {
                                continue outerLoop;
                            }
                        }

                    }
                    matchFound = true;
                    break;
                }
                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
            }
           
            matcher.appendTail(processedCondition);
            condition = processedCondition.toString();

            for (String fact : beliefStore.getDeclaredFacts()) {
                if (!fact.contains("(")) { 
                    boolean isActive = context.containsKey(fact) && context.get(fact) != null && (Boolean) context.get(fact);
                    condition = condition.replaceAll("\\b" + fact + "\\b", String.valueOf(isActive));
                }
            }
            for (String timer : beliefStore.getDeclaredTimers()) {
                String timerEndFact = timer + "_end";
                boolean isActive = beliefStore.isFactActive(timerEndFact);
                context.put(timerEndFact, isActive); // True si expiró, False si aún no
            }
            Object result = MVEL.eval(condition, context);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
        	logger.log("❌ Error evaluating logical expression: " + condition, true, false);
            e.printStackTrace();
            return false;
        }
    }
}

