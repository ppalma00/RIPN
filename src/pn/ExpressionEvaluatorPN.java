
package pn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

import both.LoggerManager;
import bs.BeliefStore;

public class ExpressionEvaluatorPN {
	
	public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore, LoggerManager logger) {
	    try {
	        // ✅ Reemplazar `nombre.end()` por `nombre_end`
	    	condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");
	        // ✅ Preparar contexto para MVEL
	        Map<String, Object> context = new HashMap<>();
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());

	        // ✅ Hechos sin parámetros como booleanos
	        for (String fact : beliefStore.getDeclaredFacts()) {
	            context.put(fact, beliefStore.getActiveFactsNoParams().contains(fact));
	        }
	     // ✅ También incluir hechos sin parámetros actualmente activos (como temp_end)
	        for (String fact : beliefStore.getActiveFactsNoParams()) {
	            if (!context.containsKey(fact)) {
	                context.put(fact, true);
	            }
	        }

	        // ✅ Hechos con parámetros como `f(5,2)` → true
	        for (String factName : beliefStore.getDeclaredFacts()) {
	            if (beliefStore.getFactParameterCount(factName) > 0) {
	                List<List<Integer>> instances = beliefStore.getActiveFacts().getOrDefault(factName, new ArrayList<>());
	                for (List<Integer> params : instances) {
	                    String factWithParams = factName + "(" + params.stream()
	                            .map(String::valueOf)
	                            .collect(Collectors.joining(",")) + ")";
	                    context.put(factWithParams, true);
	                }
	            }
	        }

	        // ✅ Comodines `_` → true/false si hay alguna coincidencia
	        Pattern factPattern = Pattern.compile("\\b(\\w+)\\((.*?)\\)");
	        Matcher matcher = factPattern.matcher(condition);
	        StringBuffer processedCondition = new StringBuffer();

	        while (matcher.find()) {
	            String factBase = matcher.group(1);
	            String paramStr = matcher.group(2).trim();

	            if (paramStr.contains("_")) {
	                boolean matchFound = beliefStore.getActiveFacts().entrySet().stream()
	                        .filter(entry -> entry.getKey().equals(factBase))
	                        .anyMatch(entry -> entry.getValue().stream()
	                                .anyMatch(params -> matchWildcard(paramStr, params)));

	                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	            } else {
	                String fullFact = factBase + "(" + paramStr + ")";
	                boolean isActive = context.containsKey(fullFact) &&
	                        context.get(fullFact) != null &&
	                        (Boolean) context.get(fullFact);
	                matcher.appendReplacement(processedCondition, String.valueOf(isActive));
	            }
	        }

	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();

	        // ✅ Evaluar con MVEL
	        Object result = MVEL.eval(condition, context);
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	    	logger.logPN("❌ Error evaluating logical expression: " + condition);
	        e.printStackTrace();
	        return false;
	    }
	}

	private static boolean matchWildcard(String wildcardPattern, List<Integer> factParams) {
	    String[] patternParts = wildcardPattern.split(",");
	    if (patternParts.length != factParams.size()) return false;

	    for (int i = 0; i < patternParts.length; i++) {
	        if (!patternParts[i].equals("_") && !patternParts[i].equals(String.valueOf(factParams.get(i)))) {
	            return false;
	        }
	    }
	    return true;
	}



}
