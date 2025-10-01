package Z3;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class RIPNParser {

    public static RIPNModel parse(String filename) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(filename));
        RIPNModel model = new RIPNModel();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("FACTS:")) {
                String factsStr = line.substring(6).trim();
                if (!factsStr.isEmpty()) {
                    String[] facts = factsStr.split(";");
                    for (String f : facts) model.facts.add(f.trim());
                }
            } else if (line.startsWith("VARSINT:")) {
                String varsStr = line.substring(8).trim();
                if (!varsStr.isEmpty()) {
                    String[] vars = varsStr.split(";");
                    for (String v : vars) model.intVars.add(v.trim());
                }
            } else if (line.startsWith("INIT:")) {
                String initStr = line.substring(5).trim();
                if (!initStr.isEmpty()) {
                    String[] assigns = initStr.split(";");
                    for (String a : assigns) model.initOps.add(a.trim());
                }
            } else if (line.startsWith("DISCRETE:")) {
                String actsStr = line.substring(9).trim();
                if (!actsStr.isEmpty()) {
                    String[] acts = actsStr.split(";");
                    for (String a : acts) model.discreteActions.add(a.trim());
                }
            } else if (line.startsWith("PLACES:")) {
                String placesStr = line.substring(7).trim();
                if (!placesStr.isEmpty()) {
                    String[] places = placesStr.split(";");
                    for (String p : places) model.places.add(p.trim());
                }
            } else if (line.startsWith("TRANSITIONS:")) {
                String trStr = line.substring(12).trim();
                if (!trStr.isEmpty()) {
                    String[] tr = trStr.split(";");
                    for (String t : tr) model.transitions.add(t.trim());
                }
            } else if (line.startsWith("ARCS:")) {
                String arcStr = line.substring(5).trim();
                if (!arcStr.isEmpty()) {
                    String[] arcs = arcStr.split(";");
                    for (String a : arcs) model.arcs.add(a.trim());
                }
            } else if (line.startsWith("INITMARKING:")) {
                String markStr = line.substring(12).trim();
                markStr = markStr.replaceAll("[()]", "");
                String[] tokens = markStr.split(",");
                for (String tok : tokens) {
                    model.initialMarking.add(Integer.parseInt(tok.trim()));
                }
            } else if (line.startsWith("<PN>")) {
                // read subsequent lines until empty
                int idx = lines.indexOf(line) + 1;
                while (idx < lines.size()) {
                    String l = lines.get(idx).trim();
                    if (l.isEmpty()) break;
                    model.pnSection.add(l);
                    idx++;
                }
            }
        }

        return model;
    }
}
