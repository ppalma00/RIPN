package guiEvents;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class GUIEvents extends JFrame {

    private static final long serialVersionUID = 1L;

	public GUIEvents() {
        setTitle("RIPN: GUI for sending events");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 400);
        setLocationRelativeTo(null);

        InputPanel panel = new InputPanel();
        add(panel);

        setVisible(true);
    }

    // Internal class for the input panel
    class InputPanel extends JPanel {
        private static final long serialVersionUID = 1L;
		private static final int MAX_ROWS = 5;

        public InputPanel() {
            setLayout(new GridLayout(MAX_ROWS+2, 5, 5, 5)); // 5 columns: word + 3 numbers + button

         // Add header labels
            add(new JLabel("Event Name", SwingConstants.CENTER));
            add(new JLabel("Param #1", SwingConstants.CENTER));
            add(new JLabel("Param #2", SwingConstants.CENTER));
            add(new JLabel("Param #3", SwingConstants.CENTER));
            add(new JLabel("Send", SwingConstants.CENTER));

            for (int i = 0; i < MAX_ROWS; i++) {
            	JTextField eventNameField = new JTextField(20);
                JTextField param1Field = new JTextField(8);
                JTextField param2Field = new JTextField(8);
                JTextField param3Field = new JTextField(8);
                JButton sendButton = new JButton("Send");

                add(eventNameField);
                add(param1Field);
                add(param2Field);
                add(param3Field);
                add(sendButton);

                sendButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                    	List<Double> params = new ArrayList<>();

                    	try {
                    	    if (!param1Field.getText().trim().isEmpty()) {
                    	        params.add(Double.parseDouble(param1Field.getText().trim()));
                    	    }
                    	    if (!param2Field.getText().trim().isEmpty()) {
                    	        params.add(Double.parseDouble(param2Field.getText().trim()));
                    	    }
                    	    if (!param3Field.getText().trim().isEmpty()) {
                    	        params.add(Double.parseDouble(param3Field.getText().trim()));
                    	    }

                    	    double[] paramArray = params.stream().mapToDouble(Double::doubleValue).toArray();
                    	    String eventName = eventNameField.getText().trim();
                    	    EventPool.getInstance().addEventFromGUI(eventName, paramArray);

                    	} catch (NumberFormatException ex) {
                    	    JOptionPane.showMessageDialog(null, "Invalid number format in one of the fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    	}

                    }
                });
            }

            // Add the Delete All Events button
            JButton deleteAllButton = new JButton("Delete All Events");
            deleteAllButton.addActionListener(e -> EventPool.getInstance().deleteAll());

            // Empty slots for alignment
            add(new JLabel(""));
            add(new JLabel(""));
            add(new JLabel(""));
            add(new JLabel(""));
            add(deleteAllButton);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GUIEvents());
    }
}

