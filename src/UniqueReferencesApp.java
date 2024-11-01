import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

public class UniqueReferencesApp {

    public static void main(String[] args) {
        // Create the frame
        JFrame frame = new JFrame("Unique LaTeX References");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);

        // Create a panel for layout
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));

        // Create text areas
        JTextArea inputArea = new JTextArea(10, 50);
        JTextArea outputArea = new JTextArea(10, 50);
        outputArea.setEditable(false);

        // Create scroll panes for the text areas
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);

        // Create an "Enter" button
        JButton enterButton = new JButton("Enter");

        // Action listener to process the input and get unique references
        ActionListener processReferencesAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Get input text
                String inputText = inputArea.getText();

                // Process the input text to extract and store unique references
                Map<String, String> uniqueReferences = extractUniqueReferences(inputText);

                // Display unique references in the output area
                StringBuilder outputText = new StringBuilder();
                for (String ref : uniqueReferences.values()) {
                    outputText.append(ref).append("\n\n");
                }

                outputArea.setText(outputText.toString());
            }
        };

        // Add action listener to the "Enter" button
        enterButton.addActionListener(processReferencesAction);

        // Add components to the panel
        panel.add(new JLabel("Enter LaTeX References (BibTeX format):"), BorderLayout.NORTH);
        panel.add(inputScrollPane, BorderLayout.CENTER);
        panel.add(enterButton, BorderLayout.WEST);  // Added the "Enter" button here
        panel.add(new JLabel("Unique References:"), BorderLayout.SOUTH);
        panel.add(outputScrollPane, BorderLayout.SOUTH);

        // Add panel to the frame
        frame.add(panel);

        // Display the frame
        frame.setVisible(true);
    }

    private static Map<String, String> extractUniqueReferences(String inputText) {
        Map<String, String> uniqueReferences = new LinkedHashMap<>();
        StringBuilder currentReference = new StringBuilder();
        boolean inReference = false;
        String currentKey = null;

        for (String line : inputText.split("\\n")) {
            line = line.trim();

            if (line.startsWith("@")) {
                // Start of a new reference block
                if (inReference && currentKey != null) {
                    // Store the previous reference block if key is unique
                    uniqueReferences.putIfAbsent(currentKey, currentReference.toString().trim());
                    currentReference.setLength(0); // Clear the current reference
                }
                inReference = true;

                // Extract the key from the line (e.g., @article{key, ...)
                int startIndex = line.indexOf("{") + 1;
                int endIndex = line.indexOf(",");
                if (startIndex > 0 && endIndex > startIndex) {
                    currentKey = line.substring(startIndex, endIndex).trim();
                } else {
                    currentKey = null;
                }
            }

            // Accumulate lines in the current reference block
            if (inReference) {
                currentReference.append(line).append("\n");
            }
        }

        // Add the last reference block if any
        if (inReference && currentKey != null) {
            uniqueReferences.putIfAbsent(currentKey, currentReference.toString().trim());
        }

        return uniqueReferences;
    }
}
