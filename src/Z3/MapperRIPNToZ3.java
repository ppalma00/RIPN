// Z3/MapperRIPNToZ3.java
package Z3;
import java.util.*;
import java.util.regex.*;

public class MapperRIPNToZ3 {

    public static NetworkZ3 map(RIPNModel model) {
        NetworkZ3 net = new NetworkZ3();

        // Places
        for (int i = 0; i < model.places.size(); i++) {
            String pname = model.places.get(i);
            boolean marked = model.initialMarking.size() > i && model.initialMarking.get(i) == 1;
            net.places.add(new PlaceZ3(pname, marked));
        }

        // Transitions
        for (String tname : model.transitions) {
            net.transitions.add(new TransitionZ3(tname));
        }

        // Arcs (lista bruta)
        for (String arcStr : model.arcs) {
            String[] parts = arcStr.split("->");
            String from = parts[0].trim();
            String to = parts[1].trim();
            net.arcs.add(new ArcZ3(from, to));
        }

        // ⬇️ Cablea arcos a input/output de cada transición
        for (ArcZ3 a : net.arcs) {
            boolean fromIsPlace = model.places.contains(a.from);
            boolean toIsTrans   = model.transitions.contains(a.to);
            boolean fromIsTrans = model.transitions.contains(a.from);
            boolean toIsPlace   = model.places.contains(a.to);

            if (fromIsPlace && toIsTrans) {
                net.findTransition(a.to).inputPlaces.add(a.from);
            } else if (fromIsTrans && toIsPlace) {
                net.findTransition(a.from).outputPlaces.add(a.to);
            }
        }

        // ⬇️ Parseo de <PN>: [ops] y if(cond) (sin '->', esto es PN, no TR)
        //   - Lugar:     name: [ ... ] [if(...)]?
        //   - Transición: name: [ ... ] [if(...)]?  (ignoramos when(...))
        Pattern pnPat = Pattern.compile(
            "^(\\w+)\\s*:\\s*(?:when\\s*\\([^)]*\\)\\s*)?\\[(.*?)]\\s*(?:if\\s*\\((.*)\\)\\s*)?$"
        );

        for (String line : model.pnSection) {
            if (line.isEmpty()) continue;
            Matcher m = pnPat.matcher(line);
            if (!m.find()) {
                System.err.println("⚠️ Línea PN no reconocida: " + line);
                continue;
            }
            String name = m.group(1).trim();
            String body = m.group(2) == null ? "" : m.group(2).trim();
            String cond = m.group(3) == null ? "" : m.group(3).trim();

            String[] ops = body.isEmpty() ? new String[0] : body.split(";");
            if (model.places.contains(name)) {
                PlaceZ3 pz3 = net.findPlace(name);
                pz3.condition = cond;
                for (String a : ops) {
                    a = a.trim();
                    if (a.isEmpty()) continue;
                    if (a.startsWith("remember(")) pz3.rememberFacts.add(a.substring(9, a.length()-1));
                    else if (a.startsWith("forget(")) pz3.forgetFacts.add(a.substring(7, a.length()-1));
                    else if (a.contains(":="))      pz3.assignments.add(a);
                    else if (a.endsWith(")"))       pz3.actions.add(a);
                }
            } else if (model.transitions.contains(name)) {
                TransitionZ3 tz3 = net.findTransition(name);
                tz3.condition = cond;
                for (String a : ops) {
                    a = a.trim();
                    if (a.isEmpty()) continue;
                    if (a.startsWith("remember(")) tz3.rememberFacts.add(a.substring(9, a.length()-1));
                    else if (a.startsWith("forget(")) tz3.forgetFacts.add(a.substring(7, a.length()-1));
                    else if (a.contains(":="))        tz3.assignments.add(a);
                    // En transiciones PN no ejecutamos acciones (solo BeliefStore), dejamos
                    // cualquier 'action()' en ignorado deliberadamente.
                }
            } else {
                System.err.println("⚠️ Elemento en <PN> no declarado en PLACES/TRANSITIONS: " + name);
            }
        }

        net.varsInt = new ArrayList<>(model.intVars);
        net.initAssignments = new ArrayList<>(model.initOps);
        return net;
    }
}
