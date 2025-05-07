
package pn;
import java.util.ArrayList;
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
	    	condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");
	        Map<String, Object> context = new HashMap<>();
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
	                List<List<Integer>> instances = beliefStore.getActiveFacts().getOrDefault(factName, new ArrayList<>());
	                for (List<Integer> params : instances) {
	                    String factWithParams = factName + "(" + params.stream()
	                            .map(String::valueOf)
	                            .collect(Collectors.joining(",")) + ")";
	                    context.put(factWithParams, true);
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
	public static Object evaluateExpression(String expr, BeliefStore beliefStore) {
	    try {
	        Map<String, Object> context = new HashMap<>();
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());
	        return MVEL.eval(expr, context);
	    } catch (Exception e) {
	        System.err.println("❌ Error evaluating expression: " + expr);
	        return null;
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
