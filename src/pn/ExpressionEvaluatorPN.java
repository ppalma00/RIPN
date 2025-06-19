
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
	public static Object evaluateExpression(String expr, BeliefStore beliefStore, LoggerManager logger, Map<String, Object> context) {
	    try {
	        Map<String, Object> fullContext = new HashMap<>();
	        fullContext.putAll(beliefStore.getAllIntVars());
	        fullContext.putAll(beliefStore.getAllRealVars());

	        if (context != null) {
	            fullContext.putAll(context); // valores del evento sobrescriben
	        }

	        return MVEL.eval(expr, fullContext);
	    } catch (Exception e) {
	        logger.log("❌ Error evaluating expression with context: " + expr + " → " + e.getMessage(), true, false);
	        return null;
	    }
	}

/*
	public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore, LoggerManager logger, Map<String, Object> contextOverride)
	{	    try {
	    	condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");
	    	Map<String, Object> context = new HashMap<>();
	    	if (contextOverride != null) {
	    	    context.putAll(contextOverride); 
	    	}
	    	context.putAll(beliefStore.getAllIntVars());
	    	context.putAll(beliefStore.getAllRealVars());

	        for (String fact : beliefStore.getDeclaredFacts()) {
	            context.put(fact, beliefStore.getActiveFactsNoParams().contains(fact));
	        }
	        for (String fact : beliefStore.getActiveFactsNoParams()) {
	            if (!context.containsKey(fact)) {
	                context.put(fact, true);
	            }
	        }
	        for (String factName : beliefStore.getDeclaredFacts()) {
	            if (beliefStore.getFactParameterCount(factName) > 0) {
	                List<List<Object>> instances = beliefStore.getActiveFacts().getOrDefault(factName, new ArrayList<>());
	                for (List<Object> params : instances) {
	                    String factWithParams = factName + "(" + params.stream()
	                            .map(String::valueOf)
	                            .collect(Collectors.joining(",")) + ")";
	                    context.put(factWithParams, true);
	                }
	            }
	        }
	    
	        Map<String, Object> outVarValues = new HashMap<>();
	        Pattern outVarPattern = Pattern.compile("\\b(\\w+)\\((.*?)\\)");
	        Matcher outMatcher = outVarPattern.matcher(condition);
	        while (outMatcher.find()) {
	            String factName = outMatcher.group(1);
	            String paramStr = outMatcher.group(2).trim();
	            String[] tokens = paramStr.split(",");
	            boolean containsOut = false;
	            for (String tok : tokens) {
	                if (tok.trim().startsWith("out ")) {
	                    containsOut = true;
	                    break;
	                }
	            }
	            if (!containsOut) continue;

	            // Extraer valores para variables 'out' si el hecho está presente
	            List<List<Object>> instances = beliefStore.getActiveFacts().getOrDefault(factName, new ArrayList<>());
	            for (List<Object> params : instances) {
	                if (params.size() != tokens.length) continue;
	                boolean match = true;
	                Map<String, Object> candidateValues = new HashMap<>();
	                for (int i = 0; i < tokens.length; i++) {
	                    String tok = tokens[i].trim();
	                    if (tok.startsWith("out ")) {
	                        String varName = tok.substring(4).trim();
	                        candidateValues.put(varName, params.get(i));
	                    } else {
	                        Object val = context.get(tok);
	                        if (val == null || !val.toString().equals(String.valueOf(params.get(i)))) {
	                            match = false;
	                            break;
	                        }
	                    }
	                }
	                if (match) {
	                    outVarValues.putAll(candidateValues);
	                    break;
	                }
	            }
	        }

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
	            	try {
	            	    String[] paramTokens = paramStr.split(",");
	            	    List<Integer> paramValues = new ArrayList<>();
	            	    for (String token : paramTokens) {
	            	        token = token.trim();
	            	        if (context.containsKey(token) && context.get(token) instanceof Number) {
	            	            paramValues.add(((Number) context.get(token)).intValue());
	            	        } else {
	            	            throw new IllegalArgumentException("Unknown parameter or not numeric: " + token);
	            	        }
	            	    }
	            	    String normalizedFact = factBase + "(" + paramValues.stream()
	            	            .map(String::valueOf)
	            	            .collect(Collectors.joining(",")) + ")";
	            	    boolean isActive = context.containsKey(normalizedFact) &&
	            	                       context.get(normalizedFact) != null &&
	            	                       (Boolean) context.get(normalizedFact);
	            	    matcher.appendReplacement(processedCondition, String.valueOf(isActive));
	            	} catch (Exception ex) {	            	
	            	    logger.log("❌ Error parsing parameters for fact: " + factBase + "(" + paramStr + ")", true, false);
	            	    matcher.appendReplacement(processedCondition, "false");
	            	}
	            }
	        }
	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();
	        Object result = MVEL.eval(condition, context);
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	    	logger.log("❌ Error evaluating logical expression: " + condition, true, false);
	        e.printStackTrace();
	        return false;
	    }
	}
	public static Object evaluateExpression(String expr, BeliefStore beliefStore, LoggerManager logger) {
	    try {
	        Map<String, Object> context = new HashMap<>();
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());
	        return MVEL.eval(expr, context);
	    } catch (Exception e) {
	        logger.log("❌ Error evaluating expression: " + expr, true, false);
	        return null;
	    }
	}
*/
	public static Object evaluateExpression(String expr, BeliefStore beliefStore, LoggerManager logger) {
	    return evaluateExpression(expr, beliefStore, logger, null);
	}

	public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore, LoggerManager logger, Map<String, Object> contextOverride) {
	    try {
	        condition = condition.replaceAll("\\bTrue\\b", "true").replaceAll("\\bFalse\\b", "false");
	        condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");

	        Map<String, Object> context = new HashMap<>();
	        if (contextOverride != null) {
	            context.putAll(contextOverride);
	        }
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());

	        Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)");
	        Matcher matcher = factPattern.matcher(condition);
	        StringBuffer processedCondition = new StringBuffer();

	        while (matcher.find()) {
	            String factBase = matcher.group(1);
	            String[] params = matcher.group(2).trim().split(",");
	            List<List<Object>> instances = beliefStore.getActiveFacts().getOrDefault(factBase, Collections.emptyList());

	            boolean matchFound = false;
	            outer:
	            for (List<Object> instance : instances) {
	                if (params.length != instance.size()) continue;

	                for (int i = 0; i < params.length; i++) {
	                    String token = params[i].trim();
	                    Object value = instance.get(i);

	                    if (token.equals("_")) continue;

	                    // Coincidencia por variable conocida
	                    if (context.containsKey(token)) {
	                        if (!context.get(token).toString().equals(value.toString())) continue outer;
	                    } else {
	                        // Coincidencia literal
	                        if (!token.equals(value.toString())) continue outer;
	                    }
	                }

	                matchFound = true;
	                break;
	            }

	            matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	        }

	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();

	        Object result = MVEL.eval(condition, context);
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	        logger.log("❌ Error evaluating logical expression: " + condition, true, false);
	        e.printStackTrace();
	        return false;
	    }
	}

	private static boolean matchWildcard(String wildcardPattern, List<Object> factParams) {
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
