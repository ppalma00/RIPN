// v.7
package pn;
import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import both.LoggerManager;
import bs.BeliefStore;
public class BeliefStoreLoader {
	private LoggerManager logger;

    public static void loadFromFile(String filename, BeliefStore beliefStore, LoggerManager logger) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

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
            }
        }
        reader.close();
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
                	logger.logPN("⚠️ Error parsing initial value for variable: " + assignment);
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

    		// 🔹 Capturar acciones discretas declaradas en `DISCRETE:`
    		if (line.startsWith("DISCRETE:")) {
    			String[] actions = line.substring(9).split(";");
    			for (String action : actions) {
    				discreteActionsDeclared.add(action.trim().replace("()", "")); // Guardar sin `()`
    			}
    			continue;
    		}

    		// Detectar la sección <pn>
    		if (line.equalsIgnoreCase("<PN>")) {
    			inPNSection = true;
    			continue;
    		}

    		// Procesar líneas dentro de <pn>
    		if (inPNSection && line.contains(":")) {
    			String[] parts = line.split(":", 2);
    			String elementName = parts[0].trim(); // Nombre del lugar o transición
    			String actions = parts[1].trim();

    			// 🔹 Flexibilizar solo el espacio después de `if`
    			String condition = null;

    			Pattern ifPattern = Pattern.compile("\\bif\\s*\\(([^)]+)\\)"); // 🔹 Captura cualquier contenido dentro de `()`, incluyendo nested ()
    			
    			Matcher matcher = ifPattern.matcher(actions);

    			if (matcher.find()) {   				
    				// 🔹 Buscar manualmente `if(...)` en la línea
    				// 🔹 Buscar `if(...)` con o sin espacio después de `if`
    				int ifIndex = actions.indexOf("if(");
    				if (ifIndex == -1) { // 🔹 Si no encontró `if(`, intenta con `if (`
    				    ifIndex = actions.indexOf("if (");
    				}

    				if (ifIndex != -1) {
    				    int openParen = ifIndex + 2; // Posición del '(' después de "if"
    				    while (openParen < actions.length() && actions.charAt(openParen) == ' ') openParen++; // 🔹 Saltar espacios adicionales

    				    int closeParen = actions.lastIndexOf(")"); // Buscar el último `)`

    				    if (closeParen > openParen) { // Asegurar que hay un `)` después de `(`
    				        condition = actions.substring(openParen + 1, closeParen).trim(); // Extraer contenido dentro de `if(...)`
    				        actions = actions.substring(0, ifIndex).trim(); // Eliminar `if(...)` de la línea de acciones
    				    } else {
    				    	logger.logPN("⚠️ Syntax error: Unmatched parentheses in if-condition -> " + actions);
    				    }
    				}


    			}
    			// 🔹 Verificar que las acciones estén en `[...]`
    			if (actions.startsWith("[") && actions.endsWith("]")) {
    				actions = actions.substring(1, actions.length() - 1).trim(); // Quitar corchetes
    				actions = actions.replaceAll("\\s*;\\s*", "; "); // 🔹 Normalizar espacios en las acciones
    				List<String> actionList = Arrays.asList(actions.split(";"));

    				if (net.getPlaces().containsKey(elementName)) {
    					placeVariableUpdates.put(elementName, actionList);
    					if (condition != null) {
    						placeConditions.put(elementName, condition);
    					}

    					// 🔹 Buscar si hay una acción discreta y si está en `DISCRETE:`
    					for (String action : actionList) {
    						action = action.trim().replace("()", ""); // Eliminar paréntesis `()`
    						if (discreteActionsDeclared.contains(action)) { // Validar si es una acción discreta
    							placeDiscreteActions.put(elementName, action);
    						}
    					}
    				} else if (net.getTransitions().containsKey(elementName)) {
    					transitionVariableUpdates.put(elementName, actionList);
    					if (condition != null) {
    						transitionConditions.put(elementName, condition);
    					}
    				} else {
    					logger.logPN("⚠️ Warning: Element '" + elementName + "' not found in places or transitions.");
    				}
    			} else {
    				logger.logPN("⚠️ Malformed line in <pn>: " + line);
    			}
    		}
    	}
    	reader.close();
    }



}
