package pn;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import both.LoggerManager;
import bs.BeliefStore;

public class PetriNetLoader {
	@SuppressWarnings("resource")
	public static PetriNet loadFromFile(String filename, BeliefStore beliefStore, LoggerManager logger) throws IOException {
		boolean hasPlaces = false;
		boolean hasTransitions = false;
		boolean hasArcs = false;
		boolean hasInitMarking = false;

	    PetriNet net = new PetriNet(beliefStore);
	    Map<String, Integer> discreteActions = new HashMap<>();
	    net.setDiscreteActionArity(discreteActions);
	    @SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(filename));
	    String line;

	    Map<String, Place> places = new HashMap<>();
	    Map<String, Transition> transitions = new HashMap<>();
	    List<Arc> arcs = new ArrayList<>();
	    List<String> placeOrder = new ArrayList<>();  

	    List<String> arcLines = new ArrayList<>(); 
	    while ((line = reader.readLine()) != null) {
	        line = line.trim();
	        if (line.isEmpty()) continue;
	        if (line.startsWith("#")) continue;
	       
	        if (line.trim().startsWith("DISCRETE:")) {
	            String actionsLine = line.substring("DISCRETE:".length()).trim();
	            String[] actions = actionsLine.split(";");
	            for (String act : actions) {
	                act = act.trim();
	                if (act.isEmpty()) continue;

	                String name = act.substring(0, act.indexOf("(")).trim();
	                String params = act.substring(act.indexOf("(") + 1, act.lastIndexOf(")")).trim();
	                int arity = params.isEmpty() ? 0 : params.split(",").length;

	                discreteActions.put(name, arity);
	            }
	        }	     
	       
	        if (line.trim().startsWith("PLACES:")) {
	        	 hasPlaces = true;
	            String[] parts = line.substring(7).split(";");
	            for (String place : parts) {
	                String placeName = place.trim();
	                net.addPlace(placeName, false);
	                places.put(placeName, new Place(placeName, false));
	                placeOrder.add(placeName);
	            }
	        }
	        else if (line.trim().startsWith("TRANSITIONS:")) {
	        	 hasTransitions = true;
	            String[] parts = line.substring(12).split(";");
	            for (String transition : parts) {
	                String transitionName = transition.trim();
	                net.addTransition(transitionName);
	                transitions.put(transitionName, new Transition(transitionName)); 
	        
	            }
	        } else if (line.trim().startsWith("ARCS:")) {
	        	 hasArcs = true;
	            arcLines.add(line.substring(6)); 
	        }
	        else if (line.trim().startsWith("INITMARKING:")) { 
	        	 hasInitMarking = true;
	            String[] tokens = line.substring(12).replace("(", "").replace(")", "").split(",");
	            int i = 0;
	        
	            if (tokens.length != places.size()) {
	            	logger.log("❌ Error: The number of entries in INITMARKING (" + tokens.length + 
	                                                   ") does not match the number of PLACES (" + places.size() + ").", true, false);
	            	System.exit(1);
	            }
	            for (String placeName : placeOrder) {
	                boolean hasToken = tokens[i].trim().equals("1");
	                net.getPlaces().get(placeName).setToken(hasToken);
	                if (hasToken) {
	                    net.executePlaceActions(placeName);
	                }
	                i++;
	            }
	        }   
	    }
	    for (String arcLine : arcLines) {
	        String[] parts = arcLine.split(";");
	        for (String arc : parts) {
	            boolean isInhibitor = arc.contains("-o>");
	            String[] nodes = arc.trim().split(isInhibitor ? "-o>" : "->");

	            if (nodes.length != 2) {
	            	logger.log("❌ Error: Invalid arc syntax → '" + arc + "'", true, false);
	            	System.exit(1);
	            }

	            String from = nodes[0].trim();
	            String to = nodes[1].trim();

	            boolean fromIsPlace = places.containsKey(from);
	            boolean fromIsTransition = transitions.containsKey(from);
	            boolean toIsPlace = places.containsKey(to);
	            boolean toIsTransition = transitions.containsKey(to);
	            if ((fromIsPlace && toIsPlace) || (fromIsTransition && toIsTransition)) {
	            	logger.log("❌ Error: Invalid arc '" + arc + "'. Arcs must connect a PLACE and a TRANSITION, not two PLACES or two TRANSITIONS.", true, false);
	                System.exit(1);
	            }
	            if (!fromIsPlace && !fromIsTransition) {
	            	logger.log("❌ Error: '" + from + "' in arc '" + arc + "' is not declared as PLACE or TRANSITION.", true, false);
	                System.exit(1);
	            }
	            if (!toIsPlace && !toIsTransition) {
	            	logger.log("❌ Error: '" + to + "' in arc '" + arc + "' is not declared as PLACE or TRANSITION.", true, false);
	                System.exit(1);
	            }

	            Place place=null;
	            Transition transition=null;
	            boolean isInput=false;

	            if (fromIsPlace && toIsTransition) {
	                place = places.get(from);
	                transition = transitions.get(to);
	                isInput = true;
	            } else if (fromIsTransition && toIsPlace) {
	                place = places.get(to);
	                transition = transitions.get(from);
	                isInput = false;
	            } else {
	            	logger.log("❌ Error: Invalid arc '" + arc + "'. Must connect PLACE → TRANSITION or TRANSITION → PLACE.", true, false);
	            	System.exit(1);
	            }
	            arcs.add(new Arc(place, transition, isInput, isInhibitor));
	            net.addArc(from, to, isInhibitor);
	        }
	    }

	    for (String name : places.keySet()) {
	        if (transitions.containsKey(name)) {
	            logger.log("❌ Error: The identifier '" + name + "' is declared both as a PLACE and a TRANSITION.", true, false);
	            System.exit(1);
	        }
	    }
	    String namePattern = "^[a-zA-Z][a-zA-Z0-9_]*$";
	    for (String name : places.keySet()) {
	        if (!name.matches(namePattern)) {
	        	logger.log("❌ Error: Invalid PLACE name '" + name + "'. Only alphanumeric identifiers (starting with a letter) are allowed.", true, false);
	            System.exit(1);
	        }
	    }
	    for (String name : transitions.keySet()) {
	        if (!name.matches(namePattern)) {
	        	logger.log("❌ Error: Invalid TRANSITION name '" + name + "'. Only alphanumeric identifiers (starting with a letter) are allowed.", true, false);
	            System.exit(1);
	        }
	    }

	    if (!hasPlaces || !hasTransitions || !hasArcs || !hasInitMarking ||
	    	    net.getPlaces().isEmpty() || net.getTransitions().isEmpty() || arcLines.isEmpty()) {
	    	    
	    	    StringBuilder missing = new StringBuilder("❌ Error: Missing or empty required section(s): ");
	    	    if (!hasPlaces || net.getPlaces().isEmpty()) missing.append("PLACES, ");
	    	    if (!hasTransitions || net.getTransitions().isEmpty()) missing.append("TRANSITIONS, ");
	    	    if (!hasArcs || arcLines.isEmpty()) missing.append("ARCS, ");
	    	    if (!hasInitMarking) missing.append("INITMARKING, ");	    	  

	    	    String msg = missing.substring(0, missing.length() - 2); // remove trailing comma
	    	    logger.log(msg, true, false);
	    	    System.exit(1);
	    	}
	    // Detect and remove unused transitions (no arcs)
	    Set<String> connectedTransitions = new HashSet<>();
	    for (Arc arc : net.getArcs()) {
	        connectedTransitions.add(arc.getTransition().getName());
	    }
	    List<String> toRemove = new ArrayList<>();
	    for (String tName : net.getTransitions().keySet()) {
	        if (!connectedTransitions.contains(tName)) {
	        	logger.log("⚠️ Warning: Transition '" + tName + "' is declared but has no arcs. It will be ignored.", true, false);
	            toRemove.add(tName);
	        }
	    }
	    for (String tName : toRemove) {
	        net.getTransitions().remove(tName);
	    }
	    reader.close();
	    return net;
	}
}
