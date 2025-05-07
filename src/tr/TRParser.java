package tr;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

import bs.BeliefStore;
import both.LoggerManager;
public class TRParser {
	private static LoggerManager logger;
	public static TRProgram parse(String filePath, BeliefStore beliefStore, int cycleDelayMs, LoggerManager inputlogger) throws IOException {
	    logger=inputlogger;
		TRProgram program = new TRProgram(beliefStore, cycleDelayMs, logger);
	    boolean insideTRSection = false;
	    List<String> ruleConditions = new ArrayList<>(); 

	    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
	        String line;
	        while ((line = reader.readLine()) != null) {
	            line = line.trim();
	            if (line.isEmpty()) continue;
	            if (line.startsWith("#")) continue;
	            if (line.startsWith("FACTS:")) {
	                parseFacts(line, beliefStore);
	            } else if (line.startsWith("VARSINT:")) {
	                parseIntVars(line, beliefStore);
	            } else if (line.startsWith("VARSREAL:")) {
	                parseRealVars(line, beliefStore);
	            } else if (line.startsWith("DISCRETE:")) {
	                parseDiscreteActions(line, beliefStore);
	            } else if (line.startsWith("DURATIVE:")) {
	                parseDurativeActions(line, beliefStore);
	            } else if (line.startsWith("TIMERS:")) {
	                parseTimers(line, beliefStore);
	            } else if (line.startsWith("INIT:")) {
	                parseInit(line, beliefStore);
	            } else if (line.startsWith("<TR>")) {
	                insideTRSection = true;
	            } else if (insideTRSection) {
	                ruleConditions.add(line.split("->")[0].trim()); 
	                validateAndParseRule(line, program, beliefStore);
	            }
	        }
	    }
	    validateVariablesInRules(ruleConditions, beliefStore);
	    return program;
	}

	private static void parseInit(String line, BeliefStore beliefStore) {
	    String[] initializations = line.substring(5).trim().split(";");	    
	    for (String init : initializations) {
	        init = init.trim();        
	        if (!init.contains(":=")) {
	            beliefStore.addFact(init); 
	        } else {
	            String[] parts = init.split(":=");
	            String varName = parts[0].trim();
	            String value = parts[1].trim();

	            if (!beliefStore.isIntVar(varName) && !beliefStore.isRealVar(varName)) {
	            	logger.log("❌ Error #26: Variable '" + varName + "' is not declared in VARSINT or VARSREAL before initialization.\n   ❌ Line: " + init, true, false);
	                System.exit(1);
	            }
	            try {
	                if (beliefStore.isIntVar(varName)) {
	                    beliefStore.setIntVar(varName, Integer.parseInt(value));
	                } else if (beliefStore.isRealVar(varName)) {
	                    beliefStore.setRealVar(varName, Double.parseDouble(value));
	                }
	            } catch (NumberFormatException e) {
	            	logger.log("❌ Error #27: Invalid format in initialization: " + init, true, false);
	                System.exit(1);
	            }
	        }
	    }
	}

	private static void validateAndParseRule(String line, TRProgram program, BeliefStore beliefStore) {
	    if (line.isEmpty()) return;
	    String[] parts = line.split("->");
	    if (parts.length != 2) {
	    	logger.log("❌ Error #6: Invalid rule syntax - missing '->'. Rule: " + line, true, false);
	        System.exit(1);
	    }
	    String conditionStr = parts[0].trim();
	    String actionsAndUpdates = parts[1].trim();
	    String actionsStr = "";
	    String updatesStr = "";   
	    if (actionsAndUpdates.contains("[") && actionsAndUpdates.contains("]")) {
	        int start = actionsAndUpdates.indexOf("[");
	        int end = actionsAndUpdates.lastIndexOf("]");
	        actionsStr = actionsAndUpdates.substring(0, start).trim();
	        updatesStr = actionsAndUpdates.substring(start + 1, end).trim(); // sin corchetes
	    } else {
	        actionsStr = actionsAndUpdates.trim();
	    }
	    if (actionsStr.isEmpty() && updatesStr.isEmpty()) {
	    	logger.log("❌ Error #9: A rule must have at least one action or BeliefStore update. Rule: " + line, true, false);
	        System.exit(1);
	    }
	    validateLogicalCondition(conditionStr, beliefStore, line);
	    validateActionsInRule(actionsStr, beliefStore, line); 
	    if (!updatesStr.isEmpty()) {
	        validateArithmeticExpressions(updatesStr, line);
	    }
	    List<String> discreteActions = new ArrayList<>();
	    List<String> durativeActions = new ArrayList<>();
	    if (!actionsStr.isEmpty()) {
	        for (String action : actionsStr.split(";")) {
	            action = action.trim();
	            if (!action.isEmpty()) {
	                if (beliefStore.isDurativeAction(action)) {
	                    durativeActions.add(action);
	                } else if (beliefStore.isDiscreteAction(action)) {
	                    discreteActions.add(action);
	                } 
	                else if (action.matches(".*\\.(start|stop|pause|continue)\\(.*\\)")) {
	                    discreteActions.add(action);
	                } else {
	                	logger.log("❌ Error #10: Action '" + action + "' is used in a rule but not declared.\n   ❌ Rule: " + line, true, false);
	                    System.exit(1);
	                }
	            }
	        }
	    }

	    final String finalUpdatesStr = updatesStr;
	    Runnable beliefStoreUpdates = finalUpdatesStr.isEmpty() ? null : () -> applyUpdates(finalUpdatesStr, beliefStore);
	    Predicate<BeliefStore> condition = beliefStoreState -> {
	        ExpressionEvaluator evaluator = new ExpressionEvaluator();
	        return evaluator.evaluateLogicalExpression(conditionStr, beliefStoreState, logger);
	    };
	    TRRule rule = new TRRule(condition, conditionStr, discreteActions, durativeActions, beliefStoreUpdates);
	    program.addRule(rule);
	}

	private static void validateLogicalCondition(String conditionStr, BeliefStore beliefStore, String fullRule) {
	    if (conditionStr.contains("&") && !conditionStr.contains("&&")) {
	    	logger.log("❌ Error #31: Invalid logical operator '&' found in: " + conditionStr + "\n   ❌ Rule: " + fullRule, false, false);
	    	logger.log("   ↳ Use '&&' instead of '&'.", true, false);
	        System.exit(1);
	    }
	    if (conditionStr.contains("|") && !conditionStr.contains("||")) {
	    	logger.log("❌ Error #31: Invalid logical operator '|' found in: " + conditionStr + "\n   ❌ Rule: " + fullRule, false, false);
	    	logger.log("   ↳ Use '||' instead of '|'.", true, false);
	        System.exit(1);
	    }

	    String cleanedCondition = conditionStr.replaceAll("[a-zA-Z0-9_().<>=!&|, ]", ""); 
	    if (!cleanedCondition.isEmpty()) {
	    	logger.log("❌ Error #32: Invalid characters found in condition: " + conditionStr + "\n   ❌ Rule: " + fullRule, false, false);
	    	logger.log("   ↳ Found unexpected symbols: " + cleanedCondition, true, false);
	        System.exit(1);
	    }

	    try {
	        MVEL.compileExpression(conditionStr);
	    } catch (Exception e) {
	    	logger.log("❌ Error #33: Expression evaluation error: " + conditionStr + "\n   ❌ Rule: " + fullRule, false, false);
	    	logger.log("   ↳ " + e.getMessage(), true, false);
	        System.exit(1);
	    }
	}

	private static void validateArithmeticExpressions(String updates, String fullRule) {
	    Pattern pattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*:=\\s*([^;]+)"); 
	    Matcher matcher = pattern.matcher(updates);

	    while (matcher.find()) {
	        @SuppressWarnings("unused")
			String varName = matcher.group(1);  
	        String expression = matcher.group(2).trim(); 

	        if (expression.contains("++") || expression.contains("--") || expression.contains("**") || expression.contains("//")) {
	        	logger.log("❌ Error #34: Invalid arithmetic expression in update: " + expression + "\n   ❌ Rule: " + fullRule, false, false);
	        	logger.log("   ↳ Use only valid arithmetic operators ('+', '-', '*', '/', '()').", true, false);
	            System.exit(1);
	        }

	        try {
	            MVEL.compileExpression(expression);
	        } catch (Exception e) {
	        	logger.log("❌ Error #35: Invalid arithmetic syntax: " + expression + "\n   ❌ Rule: " + fullRule, false, false);
	        	logger.log("   ↳ " + e.getMessage(), true, false);
	            System.exit(1);
	        }
	    }
	}

    private static void validateActionsInRule(String actions, BeliefStore beliefStore, String fullRule) {
        Pattern pattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_\\.]*)\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(actions);

        while (matcher.find()) {
            String actionName = matcher.group(1); 
            String paramString = matcher.group(2).trim(); 

            if (actionName.matches(".*\\.(start|stop|pause|continue)")) {
                String timerName = actionName.split("\\.")[0]; 

                if (!beliefStore.getDeclaredTimers().contains(timerName)) {
                	logger.log("❌ Error #24: Timer '" + timerName + "' is used but not declared.\n   ❌ Rule: " + fullRule, true, false);
                    System.exit(1);
                }

                int givenParams = paramString.isEmpty() ? 0 : paramString.split(",").length;
                int expectedParams = actionName.endsWith(".start") ? 1 : 0; 

                if (givenParams != expectedParams) {
                	logger.log("❌ Error #25: Command '" + actionName + "' expects " + expectedParams + " parameters but got " + givenParams + ".\n   ❌ Rule: " + fullRule, true, false);
                    System.exit(1);
                }
                continue;
            }

            if (!beliefStore.isDiscreteAction(actionName) && !beliefStore.isDurativeAction(actionName)) {
            	logger.log("❌ Error #22: The action '" + actionName + "' is used but not declared.\n   ❌ Rule: " + fullRule, true, false);
                System.exit(1);
            }

            int expectedParams = beliefStore.getActionParameterCount(actionName);
            int givenParams = paramString.isEmpty() ? 0 : paramString.split(",").length;

            if (givenParams != expectedParams) {
            	logger.log("❌ Error #23: Action '" + actionName + "' expects " + expectedParams + " parameters but got " + givenParams + ".\n   ❌ Rule: " + fullRule, true, false);
                System.exit(1);
            }
        }
    }
    @SuppressWarnings("unused")
	private static boolean isProperlySeparatedIgnoringParentheses(String expression) {
        int parenCount = 0;
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '(') {
                parenCount++;
            } else if (ch == ')') {
                parenCount--;
            } else if (ch == ',' && parenCount == 0) {
            	logger.log("❌ Error: Found ',' instead of ';' in: " + expression, true, false);
                return false;
            }
        }
        return true;
    }
    @SuppressWarnings("unused")
	private static void validateFactUsageInUpdates(String updates, BeliefStore beliefStore, String fullRule) {
        Pattern pattern = Pattern.compile("\\b(remember|forget)\\(([^)]+)\\)"); 
        Matcher matcher = pattern.matcher(updates);

        while (matcher.find()) {
            String operation = matcher.group(1);
            String factExpression = matcher.group(2).trim();

            boolean hasParentheses = factExpression.contains("(");
            if (hasParentheses && !factExpression.endsWith(")")) {
                factExpression += ")";
            }
            String factName = hasParentheses ? 
                factExpression.substring(0, factExpression.indexOf("(")) : factExpression;
            int expectedParams = beliefStore.getFactParameterCount(factName);
            int givenParams = 0;
            if (factName.endsWith(".end")) {
                String timerName = factName.replace(".end", "");
                if (beliefStore.getDeclaredTimers().contains(timerName)) {
                    expectedParams = 0;  
                }
            }
            if (hasParentheses) {
                String paramContent = factExpression.substring(factExpression.indexOf("(") + 1, factExpression.lastIndexOf(")")).trim();
                if (!paramContent.isEmpty()) {

                    givenParams = (int) Arrays.stream(paramContent.split(","))
                            .map(String::trim)
                            .filter(p -> !p.isEmpty()) 
                            .count();
                }
            }
            if (!beliefStore.isFactDeclared(factName) && !factName.endsWith(".end")) {
            	logger.log("❌ Error #17: The fact '" + factName + "' used in " + operation + "() is not declared.\n   ❌ Rule: " + fullRule, true, false);
                System.exit(1);
            }

            if (givenParams != expectedParams) {
            	logger.log("❌ Error #18: Fact '" + factName + "' expects " + expectedParams + " parameters but got " + givenParams + " in " + operation + "().\n   ❌ Rule: " + fullRule, true, false);
                System.exit(1);
            }
        }
    }

    @SuppressWarnings("unused")
	private static void validateFactsInRules(List<String> ruleConditions, List<String> ruleUpdates, BeliefStore beliefStore) {

        Map<String, Integer> declaredFacts = beliefStore.getDeclaredFacts()
            .stream().collect(Collectors.toMap(
                f -> f.contains("(") ? f.substring(0, f.indexOf("(")) : f, 
                f -> f.contains("(") ? (f.substring(f.indexOf("(") + 1, f.indexOf(")")).isEmpty() ? 0 : f.split(",").length) : 0 
            ));

        for (String condition : ruleConditions) {
            validateFactUsage(condition, declaredFacts);
        }

        for (String update : ruleUpdates) {
            validateFactUsage(update, declaredFacts);
        }
    }

    private static void validateFactUsage(String text, Map<String, Integer> declaredFacts) {
        Pattern factPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\(([^)]*)\\)\\b"); 
        Matcher matcher = factPattern.matcher(text);

        while (matcher.find()) {
            String baseFact = matcher.group(1);
            String paramString = matcher.group(2).trim();

            int paramCount = paramString.isEmpty() ? 0 : paramString.split(",").length;

            if (!declaredFacts.containsKey(baseFact)) {
            	logger.log("❌ Error #15: Fact '" + baseFact + "' is used in a rule but not declared.", true, false);
                System.exit(1);
            }

            int expectedParams = declaredFacts.get(baseFact);
            if (paramCount != expectedParams) {
            	logger.log("❌ Error #16: Fact '" + baseFact + "' expects " + expectedParams + " parameters but got " + paramCount + ".", true, false);
                System.exit(1);
            }
        }
    }
    @SuppressWarnings("unused")
	private static void validateActionsInRules(List<String> ruleActions, BeliefStore beliefStore) {

        Map<String, Integer> declaredDiscrete = beliefStore.getDeclaredDiscreteActions()
            .stream().collect(Collectors.toMap(
                a -> a.contains("(") ? a.substring(0, a.indexOf("(")) : a,  
                a -> a.contains("(") ? (a.substring(a.indexOf("(") + 1, a.indexOf(")")).isEmpty() ? 0 : a.split(",").length) : 0 // Parameter count
            ));

        Map<String, Integer> declaredDurative = beliefStore.getDeclaredDurativeActions()
            .stream().collect(Collectors.toMap(
                a -> a.contains("(") ? a.substring(0, a.indexOf("(")) : a,  
                a -> a.contains("(") ? (a.substring(a.indexOf("(") + 1, a.indexOf(")")).isEmpty() ? 0 : a.split(",").length) : 0 // Parameter count
            ));

        for (String action : ruleActions) {
            String baseAction = action.contains("(") ? action.substring(0, action.indexOf("(")) : action;

            int paramCount = 0;
            if (action.contains("(") && action.contains(")")) {
                String paramContent = action.substring(action.indexOf("(") + 1, action.indexOf(")")).trim();
                paramCount = paramContent.isEmpty() ? 0 : paramContent.split(",").length;
            }
            if (baseAction.matches(".*\\.(start|stop|pause|continue)")) {
                continue;
            }
            boolean isDiscrete = declaredDiscrete.containsKey(baseAction);
            boolean isDurative = declaredDurative.containsKey(baseAction);
            if (!isDiscrete && !isDurative) {
            	logger.log("❌ Error #10: Action '" + action + "' is used in a rule but not declared.", true, false);
                System.exit(1);
            }
            int expectedParams = isDiscrete ? declaredDiscrete.get(baseAction) : declaredDurative.get(baseAction);
            if (!declaredDiscrete.containsKey(baseAction) && !declaredDurative.containsKey(baseAction)) {
                expectedParams = 0;
            }
            if (paramCount != expectedParams) {
            	logger.log("❌ Error #12: Action '" + action + "' expects " + expectedParams + " parameters but got " + paramCount + ".", true, false);
                System.exit(1);
            }
        }
    }

    private static void validateVariablesInRules(List<String> ruleConditions, BeliefStore beliefStore) {
        Set<String> declaredVars = new HashSet<>();
        declaredVars.addAll(beliefStore.getDeclaredIntVars());
        declaredVars.addAll(beliefStore.getDeclaredRealVars());
        declaredVars.addAll(beliefStore.getDeclaredTimers());  

        for (String condition : ruleConditions) {
            Pattern pattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*(\\.end)?)\\b");
            Matcher matcher = pattern.matcher(condition);

            while (matcher.find()) {
                String varName = matcher.group(1);

                if (varName.equalsIgnoreCase("true") || varName.equalsIgnoreCase("false")) {
                    continue;  
                }

                if (varName.endsWith(".end")) {
                    String timerName = varName.replace(".end", "");
                    if (!beliefStore.getDeclaredTimers().contains(timerName)) {
                    	logger.log("❌ Error #11: Timer '" + timerName + "' is used in a rule but not declared.", true, false);
                        System.exit(1);
                    }
                    continue;
                }

                if (beliefStore.isFactDeclared(varName)) {
                    Pattern factPattern = Pattern.compile("\\b" + varName + "\\(([^)]*)\\)");
                    Matcher factMatcher = factPattern.matcher(condition);

                    while (factMatcher.find()) {
                        String[] params = factMatcher.group(1).split(",");
                        int declaredParams = beliefStore.getFactParameterCount(varName);

                        if (params.length != declaredParams) {
                        	logger.log("❌ Error #18: Fact '" + varName + "' is used with " + params.length +
                                               " parameters, but was declared with " + declaredParams + ".", true, false);
                            System.exit(1);
                        }
                    }
                }

                if (!varName.equals("_") && !declaredVars.contains(varName) && !beliefStore.isFactDeclared(varName)) {
                	logger.log("❌ Error #11: Variable or fact '" + varName + "' is used in a rule but not declared.", true, false);
                    System.exit(1);
                }

            }
        }
    }
    private static void applyUpdates(String updates, BeliefStore beliefStore) {
        for (String update : updates.split(";")) {
            update = update.trim();
            if (!update.isEmpty()) {
                if (update.startsWith("forget(")) {
                    beliefStore.removeFact(update.substring(7, update.length() - 1).trim());
                } else if (update.startsWith("remember(")) {
                    String factWithParams = update.substring(9, update.length() - 1).trim();
                    String baseFactName = factWithParams.contains("(") ? 
                        factWithParams.substring(0, factWithParams.indexOf("(")) : factWithParams;

                    if (!beliefStore.isFactDeclared(baseFactName)) {
                    	logger.log("❌ Error #17: Cannot remember an undeclared fact: " + baseFactName, true, false);
                        System.exit(1);
                    }

                    if (factWithParams.contains("_")) {
                    	logger.log("❌ Error #8: The wildcard `_` cannot be used in remember(). Update: " + update, true, false);
                        System.exit(1);
                    }

                    beliefStore.addFact(factWithParams);
                } else if (update.contains(":=")) {
                    String[] parts = update.split(":=");
                    if (parts.length == 2) {
                        String varName = parts[0].trim();
                        String expression = parts[1].trim();

                        try {
                            Map<String, Object> context = new HashMap<>();
                            context.putAll(beliefStore.getAllIntVars());
                            context.putAll(beliefStore.getAllRealVars());

                            for (String var : beliefStore.getAllIntVars().keySet()) {
                                context.putIfAbsent(var, 0);
                            }
                            for (String var : beliefStore.getAllRealVars().keySet()) {
                                context.putIfAbsent(var, 0.0);
                            }

                            Object result = MVEL.eval(expression, context);

                            if (beliefStore.isIntVar(varName)) {
                                if (result instanceof Integer) {
                                    beliefStore.setIntVar(varName, (Integer) result);
                                } else if (result instanceof Double) {
                                    beliefStore.setIntVar(varName, ((Double) result).intValue()); // Truncate double to integer
                                }
                            } else if (beliefStore.isRealVar(varName)) {
                                if (result instanceof Number) {
                                    beliefStore.setRealVar(varName, ((Number) result).doubleValue());
                                }
                            } else {
                            	logger.log("❌ Error #11: Undeclared variable used: " + varName, true, false);
                                System.exit(1);
                            }

                        } catch (Exception e) {
                        	logger.log("❌ Error #7: Invalid arithmetic expression: " + expression, true, false);
                            System.exit(1);
                        }
                    }
                }
            }
        }
    }

    private static void parseIntVars(String line, BeliefStore beliefStore) {
    	String rest = line.substring(8).trim();
        if (rest.isEmpty()) return;
        String[] vars = rest.split(";");
        for (String var : vars) {
            var = var.trim();
            if (var.isEmpty()) continue;
            if (beliefStore.isRealVar(var)) {
            	logger.log("❌ Error #28: Variable '" + var + "' is already declared as REAL and cannot be redeclared as INT.", true, false);
                System.exit(1);
            }
            if (beliefStore.isFactDeclared(var)) {
            	logger.log("❌ Error #29: Variable '" + var + "' cannot be declared as it conflicts with a FACTS declaration.", true, false);
                System.exit(1);
            }
            beliefStore.addIntVar(var, 0);
        }
    }

    private static void parseRealVars(String line, BeliefStore beliefStore) {
    	String rest = line.substring(9).trim();
        if (rest.isEmpty()) return;

        String[] vars = rest.split(";");
        for (String var : vars) {
            var = var.trim();
            if (var.isEmpty()) continue;
            if (beliefStore.isIntVar(var)) {
            	logger.log("❌ Error #28: Variable '" + var + "' is already declared as INT and cannot be redeclared as REAL.", true, false);
                System.exit(1);
            }
            if (beliefStore.isFactDeclared(var)) {
            	logger.log("❌ Error #29: Variable '" + var + "' cannot be declared as it conflicts with a FACTS declaration.", true, false);
                System.exit(1);
            }
            beliefStore.addRealVar(var, 0.0);
        }
    }
    private static void parseFacts(String line, BeliefStore beliefStore) {
        String rest = line.substring(6).trim();
        if (rest.isEmpty()) return;

        String[] facts = rest.split(";");
        for (String fact : facts) {
            fact = fact.trim();
            if (fact.isEmpty()) continue;

            if (beliefStore.isIntVar(fact) || beliefStore.isRealVar(fact)) {
                logger.log("❌ Error #30: Fact '" + fact + "' cannot be declared as it conflicts with a variable declaration.", true, false);
                System.exit(1);
            }

            beliefStore.declareFact(fact);
        }
    }

    private static void parseDiscreteActions(String line, BeliefStore beliefStore) {
    	 String rest = line.substring(9).trim();
    	    if (rest.isEmpty()) return;
    	    String[] parts = rest.split(";");
    	    for (String part : parts) {
    	        part = part.trim();  	      
            if (!part.isEmpty()) {
                beliefStore.declareDiscreteAction(part); // 🔹 Registrar la acción en BeliefStore
            }
        }
    }

    private static void parseDurativeActions(String line, BeliefStore beliefStore) {
    	String rest = line.substring(9).trim();
        if (rest.isEmpty()) return;
        String[] parts = rest.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            beliefStore.declareDurativeAction(part.trim());
        }
    }
    private static void parseTimers(String line, BeliefStore beliefStore) {
    	String rest = line.substring(7).trim();
        if (rest.isEmpty()) return;

        String[] timers = rest.split(";");
        for (String timer : timers) {
        	timer = timer.trim();
            if (timer.isEmpty()) continue;
            beliefStore.declareTimer(timer);
        }
    }
}
