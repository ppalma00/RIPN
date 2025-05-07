package tr;
import org.mvel2.MVEL;
import both.LoggerManager;
import bs.BeliefStore;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
public class ExpressionEvaluator {
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
            for (Map.Entry<String, List<List<Integer>>> entry : beliefStore.getActiveFacts().entrySet()) {
                String factBase = entry.getKey();
                for (List<Integer> params : entry.getValue()) {
                    String factWithParams = factBase + "(" + params.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")) + ")";
                    context.put(factWithParams, true);
                }
            }        
            Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)"); 
            Matcher matcher = factPattern.matcher(condition);
            StringBuffer processedCondition = new StringBuffer();
            while (matcher.find()) {
                String factBase = matcher.group(1);
                String params = matcher.group(2).trim();

                if (params.contains("_")) {
                    boolean matchFound = beliefStore.getActiveFacts().entrySet().stream()
                            .anyMatch(entry -> entry.getKey().equals(factBase) && entry.getValue().stream()
                                    .anyMatch(list -> list.size() == params.split(",").length));

                    matcher.appendReplacement(processedCondition, String.valueOf(matchFound));
                } else {
                    String fullFact = factBase + "(" + params + ")";
                    boolean isActive = context.containsKey(fullFact) && context.get(fullFact) != null && (Boolean) context.get(fullFact);
                    matcher.appendReplacement(processedCondition, String.valueOf(isActive));
                }
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

