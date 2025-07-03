package guiPercepts;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import bs.BeliefStore;

public class GUIPercepts extends JFrame {

    private static final long serialVersionUID = 1L;

    public GUIPercepts(BeliefStore beliefStore) {
        setTitle("RIPN: GUI for Percepts");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        InputPanel panel = new InputPanel(beliefStore);
        add(new JScrollPane(panel));

        setVisible(true);
    }

    class InputPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        public InputPanel(BeliefStore beliefStore) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            
            add(new JLabel("Percepts", SwingConstants.CENTER));
            add(new JLabel("Use '_' as a wildcard for a parameter", SwingConstants.CENTER));

            for (String percept : beliefStore.getDeclaredPercepts()) {
                int arity = beliefStore.getFactParameterCount(percept);

                final List<String> paramTypes;
                if (arity > 0) {
                    paramTypes = beliefStore.getPerceptParameterTypes(percept);
                } else {
                    paramTypes = new ArrayList<>();
                }

                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
                row.add(new JLabel(percept + ":"));

                List<JTextField> fields = new ArrayList<>();

                for (int i = 0; i < arity; i++) {
                    String expectedType = (i < paramTypes.size()) ? paramTypes.get(i) : "INT";
                    JPanel fieldGroup = new JPanel(new BorderLayout(2, 2));

                    JLabel typeLabel = new JLabel(expectedType);
                    typeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    typeLabel.setHorizontalAlignment(SwingConstants.CENTER);

                    JTextField field = new JTextField(6);
                    fields.add(field);

                    fieldGroup.add(typeLabel, BorderLayout.NORTH);
                    fieldGroup.add(field, BorderLayout.CENTER);
                    row.add(fieldGroup);
                }

                JButton addButton = new JButton("Add percept");
                JButton removeButton = new JButton("Remove percept");

                row.add(addButton);
                row.add(removeButton);

                addButton.addActionListener(e -> {
                    StringBuilder fact = new StringBuilder(percept);

                    if (!fields.isEmpty()) {
                        if (fields.stream().anyMatch(f -> f.getText().trim().equals("_"))) {
                            JOptionPane.showMessageDialog(null, "Wildcard '_' not allowed when adding percepts.", "Wildcard Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        fact.append("(");
                        for (int i = 0; i < fields.size(); i++) {
                            String text = fields.get(i).getText().trim();
                            if (text.isEmpty()) {
                                JOptionPane.showMessageDialog(null, "All parameters are required.", "Missing Input", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            String expectedType = paramTypes.get(i);
                            try {
                                if (expectedType.equals("INT")) {
                                    Integer.parseInt(text);
                                } else if (expectedType.equals("REAL")) {
                                    Double.parseDouble(text);
                                }
                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(null, "Parameter " + (i+1) + " must be of type " + expectedType + ".", "Format Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            fact.append(text);
                            if (i < fields.size()-1) fact.append(",");
                        }
                        fact.append(")");
                    }
                    beliefStore.addFact(fact.toString());
                    JOptionPane.showMessageDialog(null, "Percept " + fact.toString() + " added!");
                });

                removeButton.addActionListener(e -> {
                    StringBuilder fact = new StringBuilder(percept);

                    if (!fields.isEmpty()) {
                        fact.append("(");
                        for (int i = 0; i < fields.size(); i++) {
                            String text = fields.get(i).getText().trim();
                            if (text.isEmpty()) {
                                JOptionPane.showMessageDialog(null, "All parameters are required.", "Missing Input", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            if (!text.equals("_")) {
                                String expectedType = paramTypes.get(i);
                                try {
                                    if (expectedType.equals("INT")) {
                                        Integer.parseInt(text);
                                    } else if (expectedType.equals("REAL")) {
                                        Double.parseDouble(text);
                                    }
                                } catch (NumberFormatException ex) {
                                    JOptionPane.showMessageDialog(null, "Parameter " + (i+1) + " must be of type " + expectedType + " or '_'.", "Format Error", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                            }
                            fact.append(text);
                            if (i < fields.size()-1) fact.append(",");
                        }
                        fact.append(")");
                    }

                    String factStr = fact.toString();
                    boolean useWildcard = false;

                    if (factStr.contains("(")) {
                        String paramStr = factStr.substring(factStr.indexOf("(") + 1, factStr.lastIndexOf(")"));
                        if (!paramStr.isEmpty()) {
                            String[] params = paramStr.split(",");
                            for (String p : params) {
                                if (p.trim().equals("_")) {
                                    useWildcard = true;
                                    break;
                                }
                            }
                        }
                    }

                    boolean deleted;
                    if (useWildcard) {
                        beliefStore.removeFactWithWildcard(factStr);
                    } else {
                        beliefStore.removeFact(factStr);
                    }

                        JOptionPane.showMessageDialog(null, "Percept " + factStr + " removed!");
                     
                });

                this.add(row);
            }
        }
    }

    public static void launchIfPerceptsExist(BeliefStore beliefStore) {
        if (!beliefStore.getDeclaredPercepts().isEmpty()) {
            SwingUtilities.invokeLater(() -> new GUIPercepts(beliefStore));
        }
    }
}
