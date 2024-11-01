import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashSet;
import java.util.Set;

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
                Set<String> uniqueReferences = extractUniqueReferences(inputText);

                // Display unique references in the output area
                StringBuilder outputText = new StringBuilder();
                for (String ref : uniqueReferences) {
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

    private static Set<String> extractUniqueReferences(String inputText) {
        Set<String> uniqueReferences = new LinkedHashSet<>();
        StringBuilder currentReference = new StringBuilder();
        boolean inReference = false;

        for (String line : inputText.split("\\n")) {
            // Trim only trailing whitespace to maintain original formatting within the references
            line = line.replaceFirst("\\s+$", "");

            if (line.startsWith("@")) {
                // Start of a new reference block
                if (inReference) {
                    // Store the previous reference block exactly as it appears
                    uniqueReferences.add(currentReference.toString().trim());
                    currentReference.setLength(0); // Clear the current reference
                }
                inReference = true;
            }

            // Accumulate lines in the current reference block
            if (inReference) {
                currentReference.append(line).append("\n");
            }
        }

        // Add the last reference block if any
        if (inReference && currentReference.length() > 0) {
            uniqueReferences.add(currentReference.toString().trim());
        }

        return uniqueReferences;
    }
}
