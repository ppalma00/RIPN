package pn;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import bs.BeliefStore;

public class PetriNetLoader {
	public static PetriNet loadFromFile(String filename, BeliefStore beliefStore) throws IOException {
	    PetriNet net = new PetriNet(beliefStore);
	    Map<String, Integer> discreteActions = new HashMap<>();
	    net.setDiscreteActionArity(discreteActions);
	    @SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(filename));
	    String line;

	    Map<String, Place> places = new HashMap<>();
	    Map<String, Transition> transitions = new HashMap<>();
	    List<Arc> arcs = new ArrayList<>();

	    List<String> arcLines = new ArrayList<>(); 
	    while ((line = reader.readLine()) != null) {
	        line = line.trim();
	        if (line.isEmpty()) continue;
	        if (line.startsWith("#")) continue;
	       
	        if (line.startsWith("DISCRETE:")) {
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
	        if (line.startsWith("PLACES:")) {
	            String[] parts = line.substring(7).split(";");
	            for (String place : parts) {
	                String placeName = place.trim();
	                net.addPlace(placeName, false);
	                places.put(placeName, new Place(placeName, false)); 
	            }
	        } else if (line.startsWith("TRANSITIONS:")) {
	            String[] parts = line.substring(12).split(";");
	            for (String transition : parts) {
	                String transitionName = transition.trim();
	                net.addTransition(transitionName);
	                transitions.put(transitionName, new Transition(transitionName)); 
	        
	            }
	        } else if (line.startsWith("ARCS:")) {
	            arcLines.add(line.substring(6)); 
	        }
	        else if (line.startsWith("INITMARKING:")) {
	            String[] tokens = line.substring(12).replace("(", "").replace(")", "").split(",");
	            int i = 0;
	        
	            if (tokens.length != places.size()) {
	                throw new IllegalArgumentException("❌ Error: The number of entries in INITMARKING (" + tokens.length + 
	                                                   ") does not match the number of PLACES (" + places.size() + ").");
	            }
	            for (String placeName : net.getPlaces().keySet()) {
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

	            String from = nodes[0].trim();
	            String to = nodes[1].trim();

	            Place place;
	            Transition transition;
	            boolean isInput;

	            if (places.containsKey(from) && transitions.containsKey(to)) {
	                place = places.get(from);
	                transition = transitions.get(to);
	                isInput = true;
	            }

	            else if (transitions.containsKey(from) && places.containsKey(to)) {
	                place = places.get(to);
	                transition = transitions.get(from);
	                isInput = false; 
	            }

	            else {
	                throw new IllegalArgumentException("❌ Error: '" + from + "' or '" + to + "' in ARCS is not declared in PLACES or TRANSITIONS.");
	            }

	             isInput = places.containsKey(from); 

	            if (place == null && transition == null) {
	                throw new IllegalArgumentException("❌ Error: '" + from + "' in ARCS is not declared in PLACES or TRANSITIONS.");
	            }
	            if (!places.containsKey(from) && !transitions.containsKey(from)) {
	                throw new IllegalArgumentException("❌ Error: '" + from + "' in ARCS is not declared in PLACES or TRANSITIONS.");
	            }
	            if (!places.containsKey(to) && !transitions.containsKey(to)) {
	                throw new IllegalArgumentException("❌ Error: '" + to + "' in ARCS is not declared in PLACES or TRANSITIONS.");
	            }
	            if (!places.containsKey(to) && !transitions.containsKey(to)) {	               
	                throw new IllegalArgumentException("❌ Error: '" + to + "' in ARCS is not declared in PLACES or TRANSITIONS.");
	            }

	            arcs.add(new Arc(place != null ? place : places.get(to), transition != null ? transition : transitions.get(from), isInput, isInhibitor));
	            net.addArc(from, to, isInhibitor);
	        }
	    }
	    reader.close();
	    return net;
	}

}
