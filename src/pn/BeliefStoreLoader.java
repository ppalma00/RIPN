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
    public static void loadFromFile(String filename, BeliefStore beliefStore, LoggerManager logger) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
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
    
    private static void loadTimers(String line, BeliefStore beliefStore) {
        String[] timers = line.split(";");
        for (String timer : timers) {
            String timerName = timer.trim();
            if (!timerName.isEmpty()) {
                beliefStore.declareTimer(timerName);
            }
        }
    }
    private static void loadEvents(String eventsLine) {
        String[] events = eventsLine.split(";");
        for (String event : events) {
            event = event.trim();
            if (!event.isEmpty()) {             
                String name = event.substring(0, event.indexOf("(")).trim();
                String content = event.substring(event.indexOf("(") + 1, event.indexOf(")")).trim();
                String[] parts = content.split(",");
                double time = 0;
                List<String> types = new ArrayList<>();
                if (parts.length > 0) {
                    time = Double.parseDouble(parts[0].trim());
                    for (int i = 1; i < parts.length; i++) {
                        types.add(parts[i].trim());
                    }
                }
                EventPool.getInstance().registerEvent(name, time, types.toArray(new String[0]));
            }
        }
    }

    private static void loadFacts(String factsLine, BeliefStore beliefStore) {
        String[] facts = factsLine.split(";");
        for (String fact : facts) {
            fact = fact.trim();
            if (!fact.isEmpty() && !beliefStore.isFactDeclared(fact)) {
                beliefStore.declareFact(fact);
            }
        }
    }

    private static void loadIntVars(String varsLine, BeliefStore beliefStore) {
        String[] vars = varsLine.split(";");
        for (String var : vars) {
            var = var.trim();
            if (!var.isEmpty() && !beliefStore.isIntVar(var) && !beliefStore.isRealVar(var)) {
                beliefStore.addIntVar(var, 0); // Default initialization
            }
        }
    }

    private static void loadRealVars(String varsLine, BeliefStore beliefStore) {
        String[] vars = varsLine.split(";");
        for (String var : vars) {
            var = var.trim();
            if (!var.isEmpty() && !beliefStore.isIntVar(var) && !beliefStore.isRealVar(var)) {
                beliefStore.addRealVar(var, 0.0); // Default initialization
            }
        }
    }

    private static void initializeVars(String initLine, BeliefStore beliefStore, LoggerManager logger) {
        String[] assignments = initLine.split(";");
        for (String assignment : assignments) {
            assignment = assignment.trim();
            if (!assignment.isEmpty() && assignment.contains(":=")) {
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
                    }
                } catch (NumberFormatException e) {
                	logger.log("⚠️ Error parsing initial value for variable: " + assignment, true, false);
                }
            }
        }
    }

    private static void loadActions(String actionsLine, BeliefStore beliefStore, boolean isDurative) {
        String[] actions = actionsLine.split(";");
        for (String action : actions) {
            action = action.trim();
            if (!action.isEmpty()) {
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
    @SuppressWarnings("unused")
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
		
    			Pattern ifPattern = Pattern.compile("if\\s*\\(\\s*([^)]*?)\\s*\\)");
    			Matcher ifMatcher = ifPattern.matcher(actionLine);
    			if (ifMatcher.find()) {
    			    condition = ifMatcher.group(1).trim();
    			    actionLine = actionLine.replace(ifMatcher.group(0), "").trim();
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
