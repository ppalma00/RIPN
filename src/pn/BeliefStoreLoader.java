// v.7
package pn;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import both.LoggerManager;
import bs.BeliefStore;
import guiEvents.EventPool;
public class BeliefStoreLoader {
private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\(.*\\))?$");
static LoggerManager logger;
    public static void loadFromFile(String filename, BeliefStore beliefStore, LoggerManager mlogger) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        logger=mlogger;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;
            if (line.startsWith("FACTS:")) {
                loadFacts(line.substring(6), beliefStore);
            } else if (line.startsWith("VARSINT:")) {
                loadIntVars(line.substring(8), beliefStore);
            } else if (line.startsWith("VARSREAL:")) {
                loadRealVars(line.substring(9), beliefStore);
            } else if (line.startsWith("INIT:")) {
                initializeVars(line.substring(5), beliefStore, logger);
            } else if (line.startsWith("DISCRETE:")) {
                loadActions(line.substring(9), beliefStore, false);
            } else if (line.startsWith("DURATIVE:")) {
                loadActions(line.substring(9), beliefStore, true);
            } else if (line.startsWith("EVENTS:")) {
                loadEvents(line.substring(7));
            }else if (line.startsWith("TIMERS:")) {
                loadTimers(line.substring(7), beliefStore);
            }
        }
        reader.close();
    }
    private static boolean hasBalancedParenthesesAndBrackets(String line) {
        int parenCount = 0;
        int bracketCount = 0;
        for (char c : line.toCharArray()) {
            if (c == '(') parenCount++;
            else if (c == ')') parenCount--;
            else if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;

            if (parenCount < 0 || bracketCount < 0) return false; // Cierre sin apertura
        }
        return parenCount == 0 && bracketCount == 0;
    }

    private static boolean isValidName(String name) {
        return VALID_NAME_PATTERN.matcher(name).matches();
    }
    private static void loadTimers(String line, BeliefStore beliefStore) {
    	if (line.trim().isEmpty()) return;
        String[] timers = line.split(";");
        for (String timer : timers) {
            String timerName = timer.trim();
            if (!isValidName(timerName)) {
                logger.log("❌ Error: Invalid variable or timer name '" + timerName + "'", true, false);
                System.exit(1);
            }
            if (!timerName.isEmpty()) {
                beliefStore.declareTimer(timerName);
            }
        }
    }
    private static void loadEvents(String eventsLine) {
    	if (eventsLine.trim().isEmpty()) return;
        String[] events = eventsLine.split(";");
        for (String event : events) {
            event = event.trim();
            if (event.isEmpty()) continue;

            if (!event.contains("(") || !event.contains(")") || !event.endsWith(")")) {
                logger.log("❌ Error #40: Invalid event declaration. Missing or malformed parentheses in: '" + event + "'", true, false);
                System.exit(1);
            }

            String name = event.substring(0, event.indexOf("(")).trim();
            String content = event.substring(event.indexOf("(") + 1, event.lastIndexOf(")")).trim();

            if (name.isEmpty() || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                logger.log("❌ Error #41: Invalid event name: '" + name + "'", true, false);
                System.exit(1);
            }

            if (content.isEmpty()) {
                logger.log("❌ Error #42: Event '" + name + "' must contain at least the duration parameter.", true, false);
                System.exit(1);
            }

            String[] parts = content.split(",");
            if (parts.length == 0) {
                logger.log("❌ Error #43: Event '" + name + "' must specify duration as the first parameter.", true, false);
                System.exit(1);
            }

            int duration=0;
            try {
                duration = Integer.parseInt(parts[0].trim());
                if (duration < 0) {
                    logger.log("❌ Error #44: Duration must be ≥ 0 in event '" + name + "'. Found: " + duration, true, false);
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                logger.log("❌ Error #45: First parameter must be a valid integer duration in event '" + name + "'", true, false);
                System.exit(1);
            }

            List<String> types = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String type = parts[i].trim();
                if (!type.equals("INT") && !type.equals("REAL")) {
                    logger.log("❌ Error #46: Invalid type '" + type + "' in event '" + name + "'. Only INT or REAL are allowed.", true, false);
                    System.exit(1);
                }
                types.add(type);
            }

            EventPool.getInstance().registerEvent(name, duration, types.toArray(new String[0]));
        }
    }

    private static void loadFacts(String factsLine, BeliefStore beliefStore) {
    	if (factsLine.trim().isEmpty()) return;
        String[] facts = factsLine.split(";");
        for (String fact : facts) {
            fact = fact.trim();       
            if (!fact.isEmpty() && !beliefStore.isFactDeclared(fact)) {
            	if (!isValidName(fact)) {
                    logger.log("❌ Error: Invalid FACT name '" + fact + "'", true, false);
                    System.exit(1);
                }
                beliefStore.declareFact(fact);
            }
        }
    }

    private static void loadIntVars(String varsLine, BeliefStore beliefStore) {
    	if (varsLine.trim().isEmpty()) return;
        String[] vars = varsLine.split(";");
        for (String var : vars) {
            var = var.trim();          
            if (!var.isEmpty() && !beliefStore.isIntVar(var) && !beliefStore.isRealVar(var)) {
            	if (!isValidName(var)) {
                    logger.log("❌ Error: Invalid INTVAR name '" + var + "'", true, false);
                    System.exit(1);
                }
            	if(!beliefStore.isIntVar(var)) {
                beliefStore.addIntVar(var, 0); 
            	}
            }
        }
    }

    private static void loadRealVars(String varsLine, BeliefStore beliefStore) {
    	if (varsLine.trim().isEmpty()) return;
        String[] vars = varsLine.split(";");
        for (String var : vars) {
            var = var.trim();           
            if (!var.isEmpty() && !beliefStore.isIntVar(var) && !beliefStore.isRealVar(var)) {
            	if (!isValidName(var)) {
                    logger.log("❌ Error: Invalid REALVAR name '" + var + "'", true, false);
                    System.exit(1);
                }
            	if(!beliefStore.isRealVar(var)) {
                beliefStore.addRealVar(var, 0.0); 
            	}
            }
        }
    }

    private static void initializeVars(String initLine, BeliefStore beliefStore, LoggerManager logger) {
        if (initLine.trim().isEmpty()) return;
        String[] assignments = initLine.split(";");
        
        for (String assignment : assignments) {
            assignment = assignment.trim();
            if (assignment.isEmpty()) continue;

            // Caso: asignación a variable (x := 3)
            if (assignment.contains(":=")) {
                String[] parts = assignment.split(":=");
                String varName = parts[0].trim();
                String valueStr = parts[1].trim();

                try {
                    if (beliefStore.isIntVar(varName)) {
                        int value = Integer.parseInt(valueStr);
                        beliefStore.setIntVar(varName, value);
                    } else if (beliefStore.isRealVar(varName)) {
                        double value = Double.parseDouble(valueStr);
                        beliefStore.setRealVar(varName, value);
                    } else {
                        logger.log("❌ Error: Variable '" + varName + "' no declarada en VARSINT o VARSREAL.\n   ❌ Línea: " + assignment, true, false);
                        System.exit(1);
                    }
                } catch (NumberFormatException e) {
                    logger.log("❌ Error: Valor no numérico en la inicialización: " + assignment, true, false);
                    System.exit(1);
                }

            // Caso: hecho (con o sin rango)
            } else {
                if (assignment.contains("(") && assignment.endsWith(")")) {
                    String base = assignment.substring(0, assignment.indexOf("(")).trim();
                    String inside = assignment.substring(assignment.indexOf("(") + 1, assignment.lastIndexOf(")")).trim();

                    if (inside.contains("..")) {
                        // Intervalo detectado
                        String[] bounds = inside.split("\\.\\.");
                        if (bounds.length != 2) {
                            logger.log("❌ Error: Formato inválido de rango en INIT: " + assignment, true, false);
                            System.exit(1);
                        }
                        try {
                            int from = Integer.parseInt(bounds[0].trim());
                            int to = Integer.parseInt(bounds[1].trim());
                            if (to < from || (to - from + 1) > 1000) {
                                logger.log("❌ Error: Rango inválido o demasiado grande (máx 1000). Línea: " + assignment, true, false);
                                System.exit(1);
                            }
                            for (int i = from; i <= to; i++) {
                                beliefStore.addFact(base + "(" + i + ")");
                            }
                        } catch (NumberFormatException e) {
                            logger.log("❌ Error: Valores del rango deben ser enteros en INIT: " + assignment, true, false);
                            System.exit(1);
                        }
                    } else {
                        // Hecho normal con parámetros
                        beliefStore.addFact(assignment);
                    }
                } else {
                    // Hecho sin parámetros
                    beliefStore.addFact(assignment);
                }
            }
        }
    }


    private static void loadActions(String actionsLine, BeliefStore beliefStore, boolean isDurative) {
    	if (actionsLine.trim().isEmpty()) return;
        String[] actions = actionsLine.split(";");
        for (String action : actions) {
            action = action.trim();
            if (!action.isEmpty()) {
            	if (!isValidName(action)) {
                    logger.log("❌ Error: Invalid action name '" + action + "'", true, false);
                    System.exit(1);
                }
                if (isDurative) {
                    if (!beliefStore.getDeclaredDurativeActions().contains(action)) {
                        beliefStore.declareDurativeAction(action);
                    }
                } else {
                    if (!beliefStore.getDeclaredDiscreteActions().contains(action)) {
                        beliefStore.declareDiscreteAction(action);
                    }
                }
            }
        }
    }
    
	public static void loadPNVariableUpdates(String filename, 
    		PetriNet net, 
    		Map<String, List<String>> placeVariableUpdates, 
    		Map<String, String> placeConditions, 
    		Map<String, List<String>> transitionVariableUpdates, 
    		Map<String, String> transitionConditions, 
    		Map<String, String> placeDiscreteActions, LoggerManager logger) throws IOException {
    	BufferedReader reader = new BufferedReader(new FileReader(filename));
    	String line;
    	boolean inPNSection = false;
    	Set<String> discreteActionsDeclared = new HashSet<>(); // 🔹 Guardar acciones discretas declaradas

    	while ((line = reader.readLine()) != null) {
    		line = line.trim();
    		if (line.isEmpty()) continue;
    		if (!inPNSection) {
    		    if (line.equalsIgnoreCase("<PN>")) {
    		        inPNSection = true;
    		    }
    		    continue; 
    		}

    		if (line.startsWith("DISCRETE:")) {
    			String[] actions = line.substring(9).split(";");
    			for (String action : actions) {
    				discreteActionsDeclared.add(action.trim().replace("()", "")); // Guardar sin `()`
    			}
    			continue;
    		}
    		if (line.contains(":")) {
    			if (!hasBalancedParenthesesAndBrackets(line)) {
    			    logger.log("❌ Error: Unmatched parenthesis or bracket in line: " + line, true, false);
    			    System.exit(1); 
    			}
    			String[] parts = line.split(":", 2);
    			String elementName = parts[0].trim();
    			String actionLine = parts[1].trim();

    			String condition = null;
    			List<String> triggerEvents = new ArrayList<>();

    			Pattern whenPattern = Pattern.compile("when\\s*\\(([^()]*(\\([^()]*\\))?[^()]*)\\)");
    			Matcher whenMatcher = whenPattern.matcher(actionLine);
    			if (whenMatcher.find()) {
    			    String evRaw = whenMatcher.group(1).trim();
    			    triggerEvents.add(evRaw);
   
    			    Pattern evWithArgsPattern = Pattern.compile("(\\w+)\\s*\\(([^)]*)\\)");
    			    Matcher evArgMatcher = evWithArgsPattern.matcher(evRaw);
    			    if (evArgMatcher.matches()) {
    			        String eventName = evArgMatcher.group(1).trim(); // ev1
    			        String[] args = evArgMatcher.group(2).split(",");
    			        List<String> variables = new ArrayList<>();
    			        for (String arg : args) {
    			            String trimmed = arg.trim();
    			            if (!trimmed.isEmpty()) {
    			                variables.add(trimmed);
    			            }
    			        }
    			        triggerEvents.add(eventName); 
    			        net.getTransitions().get(elementName).setTriggerVariables(variables); 
    			    } else {
    			        triggerEvents.add(evRaw.trim()); 
    			        net.getTransitions().get(elementName).setTriggerVariables(new ArrayList<>()); 
    			    }
    			    actionLine = actionLine.replace(whenMatcher.group(0), "").trim(); 
    			}
		
    			// Buscar 'if(...)' con paréntesis equilibrados
    			int ifIndex = actionLine.indexOf("if(");
    			if (ifIndex != -1) {
    			    int open = ifIndex + 2; // posición del primer paréntesis
    			    int depth = 1;
    			    int close = -1;
    			    for (int i = open + 1; i < actionLine.length(); i++) {
    			        char c = actionLine.charAt(i);
    			        if (c == '(') depth++;
    			        else if (c == ')') depth--;
    			        if (depth == 0) {
    			            close = i;
    			            break;
    			        }
    			    }
    			    if (close != -1) {
    			        condition = actionLine.substring(open + 1, close).trim();
    			        actionLine = (actionLine.substring(0, ifIndex) + actionLine.substring(close + 1)).trim();
    			    } else {
    			        logger.log("❌ Error: unmatched parentheses in if(...) clause → " + line, true, false);
    			        continue;
    			    }
    			}


    			actionLine = actionLine.trim();

    			if (actionLine.startsWith("[") && actionLine.endsWith("]")) {
    			    String rawActions = actionLine.substring(1, actionLine.length() - 1).trim();
    			  
    			    List<String> actionList = new ArrayList<>();
    			    for (String rawAction : rawActions.split("\\s*;\\s*")) {
    			        rawAction = rawAction.trim();
    			        if (rawAction.isEmpty()) continue;

    			        if (rawAction.contains("=") && !rawAction.contains(":=")) {
    			            logger.log("⚠️ Malformed assignment (did you mean ':='?) → " + rawAction, true, false);
    			            continue;
    			        }

    			        actionList.add(rawAction);
    			    }
	    
    			    if (net.getPlaces().containsKey(elementName)) {
    			    	if (condition != null || !triggerEvents.isEmpty()) {
    			            logger.log("⚠️ 'if' or 'when' clauses are not allowed in place definitions: " + line, true, false);   			          
    			            continue;
    			    	}
    			        placeVariableUpdates.put(elementName, actionList);
    			        if (condition != null) placeConditions.put(elementName, condition);

    			        for (String action : actionList) {
    			            action = action.trim().replace("()", "");
    			            if (discreteActionsDeclared.contains(action)) {
    			                placeDiscreteActions.put(elementName, action);
    			            }
    			        }

    			    } else if (net.getTransitions().containsKey(elementName)) {
    			        transitionVariableUpdates.put(elementName, actionList);
    			        if (condition != null) transitionConditions.put(elementName, condition);
    			        net.getTransitions().get(elementName).setTriggerEvents(triggerEvents);
    			    } else {
    			        logger.log("⚠️ Warning: Element '" + elementName + "' not found in places or transitions.", true, false);
    			    }
    			} else {
    			    logger.log("⚠️ Malformed line in <pn>: " + line, true, false);    			   
    			}
    	}
    	}
    	reader.close();
    }
}
