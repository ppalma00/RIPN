
package pn;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	        // Normalización de booleanos y temporizadores
	        condition = condition.replaceAll("\\bTrue\\b", "true").replaceAll("\\bFalse\\b", "false");
	        condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");

	        // Verificación de duplicados en variables 'out'
	        Pattern outVarPattern = Pattern.compile("\\bout\\s+(\\w+)\\b");
	        Matcher outVarMatcher = outVarPattern.matcher(condition);
	        Set<String> outVars = new HashSet<>();
	        while (outVarMatcher.find()) {
	            String varName = outVarMatcher.group(1);
	            if (!outVars.add(varName)) {
	                logger.log("❌ Error: variable '" + varName + "' no puede usarse con 'out' más de una vez en una condición: " + condition, true, false);
	                return false;
	            }
	        }

	        Map<String, Object> context = new HashMap<>();
	        if (contextOverride != null) context.putAll(contextOverride);
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());

	        Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)");
	        Matcher matcher = factPattern.matcher(condition);
	        StringBuffer processedCondition = new StringBuffer();

	        while (matcher.find()) {
	            String factBase = matcher.group(1);
	            String[] params = matcher.group(2).trim().isEmpty() ? new String[0] : matcher.group(2).trim().split("\\s*,\\s*");
	            List<List<Object>> instances = beliefStore.getActiveFacts().getOrDefault(factBase, Collections.emptyList());

	            boolean hasOut = Arrays.stream(params).anyMatch(tok -> tok.startsWith("out "));
	            boolean matchFound = false;

	            if (hasOut) {
	                for (List<Object> instance : instances) {
	                    if (params.length != instance.size()) continue;

	                    Map<String, Object> candidateContext = new HashMap<>(context);
	                    boolean valid = true;

	                    for (int i = 0; i < params.length; i++) {
	                        String tok = params[i];
	                        Object value = instance.get(i);

	                        if (tok.startsWith("out ")) {
	                            String varName = tok.substring(4).trim();
	                            if (varName.matches(".*[+\\-*/()].*")) {
	                                logger.log("❌ Error: no se permite expresión aritmética con 'out': " + tok, true, false);
	                                return false;
	                            }
	                            candidateContext.put(varName, value);
	                        } else if (tok.equals("_")) {
	                            continue;
	                        } else {
	                            try {
	                                Object expected = MVEL.eval(tok, candidateContext);
	                                if (!expected.toString().equals(value.toString())) {
	                                    valid = false;
	                                    break;
	                                }
	                            } catch (Exception e) {
	                                logger.log("❌ Error evaluando expresión: " + tok, true, false);
	                                valid = false;
	                                break;
	                            }
	                        }
	                    }

	                    if (!valid) continue;
	                    String fullCond = condition.replaceAll("\\bout\\s+(\\w+)\\b", "$1");
	                    Matcher innerMatcher = factPattern.matcher(fullCond);
	                    StringBuffer innerProcessed = new StringBuffer();

	                    while (innerMatcher.find()) {
	                        String base = innerMatcher.group(1);
	                        String[] args = innerMatcher.group(2).trim().isEmpty() ? new String[0] : innerMatcher.group(2).trim().split("\\s*,\\s*");
	                        List<List<Object>> factInsts = beliefStore.getActiveFacts().getOrDefault(base, Collections.emptyList());
	                        boolean found = false;

	                        for (List<Object> inst : factInsts) {
	                            if (args.length != inst.size()) continue;

	                            boolean fits = true;
	                            for (int i = 0; i < args.length; i++) {
	                                String tok = args[i];
	                                Object val = inst.get(i);

	                                if (tok.equals("_")) continue;

	                                Object evaluated;
	                                try {
	                                    evaluated = MVEL.eval(tok, candidateContext);
	                                } catch (Exception e) {
	                                    fits = false;
	                                    break;
	                                }

	                                if (!evaluated.toString().equals(val.toString())) {
	                                    fits = false;
	                                    break;
	                                }
	                            }

	                            if (fits) {
	                                found = true;
	                                break;
	                            }
	                        }

	                        innerMatcher.appendReplacement(innerProcessed, String.valueOf(found));
	                    }

	                    innerMatcher.appendTail(innerProcessed);

	                    try {
	                        Object result = MVEL.eval(innerProcessed.toString(), candidateContext);
	                        if (result instanceof Boolean && (Boolean) result) {
	                            context.putAll(candidateContext);
	                            for (Map.Entry<String, Object> entry : candidateContext.entrySet()) {
	                                String var = entry.getKey();
	                                Object val = entry.getValue();
	                                if (beliefStore.containsIntVar(var) && val instanceof Number)
	                                    beliefStore.setIntVar(var, ((Number) val).intValue());
	                                else if (beliefStore.containsRealVar(var) && val instanceof Number)
	                                    beliefStore.setRealVar(var, ((Number) val).doubleValue());
	                            }
	                            matchFound = true;
	                            break;
	                        }
	                    } catch (Exception e) {
	                        logger.log("❌ Error evaluating full condition with binding: " + e.getMessage(), true, false);
	                    }
	                }

	                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	            } else {
	                outer:
	                for (List<Object> instance : instances) {
	                    if (params.length != instance.size()) continue;
	                    for (int i = 0; i < params.length; i++) {
	                        String token = params[i].trim();
	                        Object value = instance.get(i);

	                        if (token.equals("_")) continue;
	                        try {
	                            Object evaluated = MVEL.eval(token, context);
	                            if (!evaluated.toString().equals(value.toString())) continue outer;
	                        } catch (Exception e) {
	                            logger.log("❌ Error evaluando expresión: " + token, true, false);
	                            continue outer;
	                        }
	                    }
	                    matchFound = true;
	                    break;
	                }
	                matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
	            }
	        }

	        matcher.appendTail(processedCondition);
	        Object result = MVEL.eval(processedCondition.toString(), context);
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
