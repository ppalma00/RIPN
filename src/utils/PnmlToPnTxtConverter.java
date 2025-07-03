package utils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class PnmlToPnTxtConverter {

    public static void main(String[] args) {
        // Update these filenames as needed
        String inputPnmlFile = "Parking.pnml";
        String outputTxtFile = "myNet_converted.txt";

        try {
            new PnmlToPnTxtConverter().convertPnml(inputPnmlFile, outputTxtFile);
            System.out.println("Conversion completed. Output file: " + outputTxtFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts a PNML file into the custom textual PN format.
     *
     * @param inputPnml Path to the PNML file.
     * @param outputTxt Path for the generated textual PN file.
     * @throws Exception if any parsing error occurs.
     */
    public void convertPnml(String inputPnml, String outputTxt) throws Exception {
        File xmlFile = new File(inputPnml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        List<String> places = new ArrayList<>();
        List<String> transitions = new ArrayList<>();
        List<String> arcs = new ArrayList<>();
        Map<String, Integer> initialMarkings = new HashMap<>();

        // Parse places
        NodeList placeList = doc.getElementsByTagName("place");
        for (int i = 0; i < placeList.getLength(); i++) {
            Element place = (Element) placeList.item(i);
            String id = place.getAttribute("id");
            places.add(id);

            NodeList markingNodes = place.getElementsByTagName("initialMarking");
            if (markingNodes.getLength() > 0) {
                Element markingElem = (Element) markingNodes.item(0);
                String text = markingElem.getTextContent().trim();
                int tokens = parseIntOrZero(text);
                initialMarkings.put(id, tokens);
            } else {
                initialMarkings.put(id, 0);
            }
        }

        // Parse transitions
        NodeList transitionList = doc.getElementsByTagName("transition");
        for (int i = 0; i < transitionList.getLength(); i++) {
            Element transition = (Element) transitionList.item(i);
            String id = transition.getAttribute("id");
            transitions.add(id);
        }

        // Parse arcs
        NodeList arcList = doc.getElementsByTagName("arc");
        for (int i = 0; i < arcList.getLength(); i++) {
            Element arc = (Element) arcList.item(i);
            String source = arc.getAttribute("source");
            String target = arc.getAttribute("target");

            String arrow = "->";
            NodeList typeNodes = arc.getElementsByTagName("type");
            if (typeNodes.getLength() > 0) {
                String typeValue = typeNodes.item(0).getTextContent().trim();
                if (typeValue.equalsIgnoreCase("inhibitor")) {
                    arrow = "-o>";
                }
            }
            arcs.add(source + arrow + target);
        }

        // Sort places and transitions for consistency
        Collections.sort(places);
        Collections.sort(transitions);

        // Build INITMARKING string
        List<Integer> markingValues = new ArrayList<>();
        for (String placeId : places) {
            markingValues.add(initialMarkings.getOrDefault(placeId, 0));
        }
        String markingString = markingValues.toString()
                .replace("[", "(")
                .replace("]", ")")
                .replace(",", "");

        // Write the output file
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputTxt))) {
            writer.println("FACTS: ");
            writer.println("VARSINT: ");
            writer.println("VARSREAL: ");
            writer.println("INIT:");
            writer.println("DISCRETE: ");
            writer.println("DURATIVE: ");
            writer.println("TIMERS: ");
            writer.println("PLACES: " + String.join("; ", places));
            writer.println("TRANSITIONS: " + String.join("; ", transitions));

            // Write arcs in blocks of max 8 arcs per line
            int wrapLimit = 8;
            if (!arcs.isEmpty()) {
                for (int i = 0; i < arcs.size(); i += wrapLimit) {
                    int end = Math.min(i + wrapLimit, arcs.size());
                    List<String> chunk = arcs.subList(i, end);
                    writer.print("ARCS: ");
                    writer.println(String.join("; ", chunk) + ";");
                }
            } else {
                writer.println("ARCS: ");
            }

            writer.println("INITMARKING: " + markingString);
            writer.println("EVENTS: ");
            writer.println();
            writer.println("<PN>");
        }
    }

    /**
     * Safely parses an integer string, returning 0 if parsing fails.
     */
    private int parseIntOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }
}
