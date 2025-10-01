package guiEvents;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class GUIEvents extends JFrame {
    private static final long serialVersionUID = 1L;
    public GUIEvents() {
        setTitle("RIPN: GUI for sending events");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        InputPanel panel = new InputPanel();
        add(new JScrollPane(panel));
        setVisible(true);
    }

    class InputPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        public InputPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            add(new JLabel("Events", SwingConstants.CENTER));

            Map<String, EventPool.EventSpec> declaredEvents = EventPool.getInstance().getDeclaredEvents();

            for (Map.Entry<String, EventPool.EventSpec> entry : declaredEvents.entrySet()) {
                String eventName = entry.getKey();
                List<String> types = entry.getValue().parameterTypes;

                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
                row.add(new JLabel(eventName + ":"));

                List<JTextField> fields = new ArrayList<>();

                for (String type : types) {
                    JPanel fieldGroup = new JPanel(new BorderLayout(2, 2));

                    JLabel typeLabel = new JLabel(type);
                    typeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    typeLabel.setHorizontalAlignment(SwingConstants.CENTER);

                    JTextField field = new JTextField(6);
                    fields.add(field);

                    fieldGroup.add(typeLabel, BorderLayout.NORTH);
                    fieldGroup.add(field, BorderLayout.CENTER);

                    row.add(fieldGroup);
                }

                JButton sendButton = new JButton("Send");
                row.add(sendButton);

                sendButton.addActionListener(e -> {
                    double[] params = new double[fields.size()];
                    for (int i = 0; i < fields.size(); i++) {
                        String input = fields.get(i).getText().trim();
                        String expectedType = types.get(i);

                        if (input.isEmpty()) {
                            JOptionPane.showMessageDialog(null, "All parameters are required.", "Missing Input", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        try {
                            double value = Double.parseDouble(input);
                            if (expectedType.equals("INT") && value != (int) value) {
                                JOptionPane.showMessageDialog(null, "Parameter " + (i + 1) + " must be an integer.", "Type Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            params[i] = value;
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(null, "Parameter " + (i + 1) + " must be a valid number.", "Format Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    }

                    EventPool.getInstance().addEvent(eventName, params);
                    JOptionPane.showMessageDialog(null, "Event " + eventName+" added!");

                });

                this.add(row);
            }

            JButton deleteAllButton = new JButton("Delete All Events");
            deleteAllButton.addActionListener(e -> EventPool.getInstance().deleteAll());

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            footer.add(deleteAllButton);
            this.add(footer);
        }
    }


    public static void main(String[] args) {
    	EventPool pool = EventPool.getInstance();
        if (!pool.hasDeclaredEvents()) {
            System.out.println("Warning: No declared events found. GUIEvents will not launch.");
            return;
        }
        SwingUtilities.invokeLater(GUIEvents::new);    }
}
