
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

	            boolean hasOut = false;
	            for (String tok : params) {
	                if (tok.trim().startsWith("out ")) {
	                    hasOut = true;
	                    break;
	                }
	            }

	            if (hasOut) {
	                boolean bound = false;

	                outer:
	                for (List<Object> instance : instances) {
	                    if (params.length != instance.size()) continue;

	                    Map<String, Object> outValues = new HashMap<>();

	                    for (int i = 0; i < params.length; i++) {
	                        String tok = params[i].trim();
	                        Object value = instance.get(i);

	                        if (tok.startsWith("out ")) {
	                            String varName = tok.substring(4).trim();
	                            outValues.put(varName, value);
	                        } else if (context.containsKey(tok)) {
	                            if (!context.get(tok).toString().equals(value.toString())) continue outer;
	                        } else if (!tok.equals("_")) {
	                            if (!tok.equals(value.toString())) continue outer;
	                        }
	                    }

	                    context.putAll(outValues);
	                    for (Map.Entry<String, Object> entry : outValues.entrySet()) {
	                        String var = entry.getKey();
	                        Object value = entry.getValue();
	                        if (beliefStore.containsIntVar(var) && value instanceof Number) {
	                            beliefStore.setIntVar(var, ((Number) value).intValue());
	                        } else if (beliefStore.containsRealVar(var) && value instanceof Number) {
	                            beliefStore.setRealVar(var, ((Number) value).doubleValue());
	                        }
	                    }

	                    bound = true;
	                    break;
	                }

	                matcher.appendReplacement(processedCondition, String.valueOf(bound));
	            } else {
	                boolean matchFound = false;

	                outer:
	                for (List<Object> instance : instances) {
	                    if (params.length != instance.size()) continue;

	                    for (int i = 0; i < params.length; i++) {
	                        String token = params[i].trim();
	                        Object value = instance.get(i);

	                        if (token.equals("_")) continue;

	                        if (context.containsKey(token)) {
	                            if (!context.get(token).toString().equals(value.toString())) continue outer;
	                        } else {
	                            if (!token.equals(value.toString())) continue outer;
	                        }
	                    }

	                    matchFound = true;
	                    break;
	                }

	                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	            }
	        }

	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();

	        Object result = MVEL.eval(condition, context);
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	        logger.log("❌ Error evaluating logical expression: " + condition + " → " + e.getMessage(), true, false);
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
