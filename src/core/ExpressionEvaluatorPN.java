
package core;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

public class ExpressionEvaluatorPN {
/*
	public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore) {
	    try {
	        // ✅ Crear un contexto compatible con MVEL
	        Map<String, Object> context = new HashMap<>();

	        // ✅ Convertir variables en `BeliefStore` al contexto de MVEL
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());

	        // ✅ Agregar hechos sin parámetros como booleanos
	        for (String fact : beliefStore.getDeclaredFacts()) {
	            context.put(fact, beliefStore.getActiveFactsNoParams().contains(fact));
	        }

	        // ✅ Agregar hechos con parámetros en el mismo formato que TR usa
	        for (String factName : beliefStore.getDeclaredFacts()) {
	            if (beliefStore.getFactParameterCount(factName) > 0) { // Solo hechos con parámetros
	                List<List<Integer>> instances = beliefStore.getActiveFacts().getOrDefault(factName, new ArrayList<>());

	                for (List<Integer> params : instances) {
	                    // 🔹 Convertir tres(5,6) en tres(5,6) para que coincida con TR
	                    String factWithParams = factName + "(" + params.stream()
	                            .map(String::valueOf)
	                            .collect(Collectors.joining(",")) + ")";
	                    
	                    context.put(factWithParams, true);
	                }
	            }
	        }

	        // ✅ Procesar expresiones con comodines `_` antes de evaluar con MVEL
	        Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)"); // Captura `tres(5,_)`
	        Matcher matcher = factPattern.matcher(condition);
	        StringBuffer processedCondition = new StringBuffer();

	        while (matcher.find()) {
	            String factBase = matcher.group(1);
	            String paramStr = matcher.group(2).trim();

	            // 🔹 Si la condición usa `_`, verificar coincidencias en `BeliefStore`
	            if (paramStr.contains("_")) {
	                boolean matchFound = beliefStore.getActiveFacts().entrySet().stream()
	                        .filter(entry -> entry.getKey().equals(factBase))
	                        .anyMatch(entry -> entry.getValue().stream()
	                                .anyMatch(params -> matchWildcard(paramStr, params)));

	                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	            } else {
	                // 🔹 Si no hay comodín, evaluar normalmente
	                String fullFact = factBase + "(" + paramStr + ")";
	                boolean isActive = context.containsKey(fullFact) && context.get(fullFact) != null && (Boolean) context.get(fullFact);
	                matcher.appendReplacement(processedCondition, String.valueOf(isActive));
	            }
	        }
	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();

	        // ✅ Evaluar la condición con todos los valores cargados en el contexto
	        Object result = MVEL.eval(condition, context);
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	        System.err.println("❌ Error evaluating logical expression: " + condition);
	        e.printStackTrace();
	        return false;
	    }
	    
	}
	private static boolean matchWildcard(String wildcardPattern, List<Integer> factParams) {
	    String[] patternParts = wildcardPattern.split(",");
	    
	    if (patternParts.length != factParams.size()) {
	        return false; // Si el número de parámetros no coincide, no hay coincidencia
	    }

	    for (int i = 0; i < patternParts.length; i++) {
	        if (!patternParts[i].equals("_") && !patternParts[i].equals(String.valueOf(factParams.get(i)))) {
	            return false; // Si no es `_` y no coincide, es falso
	        }
	    }

	    return true; // Si pasa todas las comparaciones, hay coincidencia
	}

*/
	
	public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore) {
	    try {
	        // ✅ Crear un contexto compatible con MVEL
	        Map<String, Object> context = new HashMap<>();

	        // ✅ Convertir variables en `BeliefStore` al contexto de MVEL
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());

	        // ✅ Agregar hechos sin parámetros como booleanos
	        for (String fact : beliefStore.getDeclaredFacts()) {
	            context.put(fact, beliefStore.getActiveFactsNoParams().contains(fact));
	        }

	        // ✅ Agregar hechos con parámetros en el mismo formato que TR usa
	        for (String factName : beliefStore.getDeclaredFacts()) {
	            if (beliefStore.getFactParameterCount(factName) > 0) { // Solo hechos con parámetros
	                List<List<Integer>> instances = beliefStore.getActiveFacts().getOrDefault(factName, new ArrayList<>());

	                for (List<Integer> params : instances) {
	                    // 🔹 Convertir `tres(5,6)` en `tres(5,6)` para que coincida con TR
	                    String factWithParams = factName + "(" + params.stream()
	                            .map(String::valueOf)
	                            .collect(Collectors.joining(",")) + ")";
	                    
	                    context.put(factWithParams, true);
	                }
	            }
	        }

	        // ✅ Procesar expresiones con comodines `_` antes de evaluar con MVEL
	       // Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)"); // Captura `tres(5,_)`
	        Pattern factPattern = Pattern.compile("\\b(\\w+)\\((.*?)\\)"); // 🔹 Captura contenido con `)`

	        Matcher matcher = factPattern.matcher(condition);
	        StringBuffer processedCondition = new StringBuffer();

	        while (matcher.find()) {
	            String factBase = matcher.group(1);
	            String paramStr = matcher.group(2).trim();

	            // 🔹 Si la condición usa `_`, verificar coincidencias en `BeliefStore`
	            if (paramStr.contains("_")) {
	                boolean matchFound = beliefStore.getActiveFacts().entrySet().stream()
	                        .filter(entry -> entry.getKey().equals(factBase))
	                        .anyMatch(entry -> entry.getValue().stream()
	                                .anyMatch(params -> matchWildcard(paramStr, params)));

	                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	            } else {
	                // 🔹 Si no hay comodín, evaluar normalmente
	                String fullFact = factBase + "(" + paramStr + ")";
	                boolean isActive = context.containsKey(fullFact) && context.get(fullFact) != null && (Boolean) context.get(fullFact);
	                matcher.appendReplacement(processedCondition, String.valueOf(isActive));
	            }
	        }
	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();

	        // ✅ Evaluar la condición con todos los valores cargados en el contexto
	        Object result = MVEL.eval(condition, context);
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	        System.err.println("❌ Error evaluating logical expression: " + condition);
	        e.printStackTrace();
	        return false;
	    }
	}
	private static boolean matchWildcard(String wildcardPattern, List<Integer> factParams) {
	    String[] patternParts = wildcardPattern.split(",");
	    
	    if (patternParts.length != factParams.size()) {
	        return false; // Si el número de parámetros no coincide, no hay coincidencia
	    }

	    for (int i = 0; i < patternParts.length; i++) {
	        if (!patternParts[i].equals("_") && !patternParts[i].equals(String.valueOf(factParams.get(i)))) {
	            return false; // Si no es `_` y no coincide, es falso
	        }
	    }

	    return true; // Si pasa todas las comparaciones, hay coincidencia
	}


}
