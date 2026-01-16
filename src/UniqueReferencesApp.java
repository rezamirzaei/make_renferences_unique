import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.DefaultEditorKit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * A Swing application for extracting and deduplicating LaTeX/BibTeX references.
 * <p>
 * Features:
 * <ul>
 *   <li>Upload or drag-and-drop .bib files</li>
 *   <li>Deduplicate entries by key (first occurrence wins)</li>
 *   <li>Optional alphabetical sorting by key</li>
 *   <li>Save output to file</li>
 *   <li>Recent files menu</li>
 *   <li>Keyboard shortcuts for all actions</li>
 *   <li>Undo/redo support</li>
 * </ul>
 */
public class UniqueReferencesApp {

    private static final String APP_TITLE = "Unique LaTeX References";
    private static final int WINDOW_WIDTH = 950;
    private static final int WINDOW_HEIGHT = 800;
    private static final int TEXT_AREA_ROWS = 18;
    private static final int TEXT_AREA_COLS = 70;

    // Preferences keys
    private static final String PREF_SORT_BY_KEY = "sortByKey";
    private static final String PREF_RECENT_FILES = "recentFiles";
    private static final String PREF_LAST_DIR = "lastDirectory";
    private static final int MAX_RECENT_FILES = 5;

    private final Preferences prefs = Preferences.userNodeForPackage(UniqueReferencesApp.class);

    private JFrame frame;
    private JTextArea inputArea;
    private JTextArea outputArea;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JCheckBox sortCheckBox;
    private JTextField searchField;

    private JButton uploadButton;
    private JButton processButton;
    private JButton copyButton;
    private JButton clearButton;
    private JButton saveButton;

    private JMenu recentFilesMenu;
    private final List<Path> recentFiles = new ArrayList<>();

    private final UndoManager inputUndo = new UndoManager();
    private final UndoManager outputUndo = new UndoManager();

    // Store last result for filtering
    private String lastFullOutput = "";

    public static void main(String[] args) {
        // Set System Look and Feel for native experience
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Enable anti-aliasing for text
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Use the Event Dispatch Thread for Swing components
        SwingUtilities.invokeLater(() -> new UniqueReferencesApp().createAndShowGUI());
    }

    /**
     * Creates and displays the main application window.
     */
    private void createAndShowGUI() {
        loadPreferences();

        frame = createMainFrame();
        frame.setJMenuBar(createMenuBar());

        JPanel mainPanel = createMainPanel();
        frame.add(mainPanel);
        frame.setLocationRelativeTo(null); // Center on screen

        // Save preferences on close
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                savePreferences();
            }
        });

        frame.setVisible(true);
    }

    /**
     * Creates the main application frame.
     */
    private JFrame createMainFrame() {
        JFrame frame = new JFrame(APP_TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setMinimumSize(new Dimension(600, 500));
        return frame;
    }

    /**
     * Creates the main panel with all UI components.
     */
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = createInputPanel();
        JPanel outputPanel = createOutputPanel();

        // Status bar with progress indicator
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        statusLabel.setForeground(Color.GRAY);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 16));

        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(progressBar, BorderLayout.EAST);

        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(createButtonPanel(), BorderLayout.CENTER);
        controlPanel.add(statusPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputPanel, outputPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setOneTouchExpandable(true);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(controlPanel, BorderLayout.SOUTH);

        // Keyboard shortcuts (work even without menu focus)
        installGlobalKeyBindings(panel);

        return panel;
    }

    /**
     * Creates the input panel with label and text area.
     */
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel label = new JLabel("Enter LaTeX References (BibTeX format) or Drag & Drop .bib file:");
        label.setFont(label.getFont().deriveFont(Font.BOLD));

        inputArea = new JTextArea(TEXT_AREA_ROWS, TEXT_AREA_COLS);
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.getDocument().addUndoableEditListener((UndoableEditEvent e) -> inputUndo.addEdit(e.getEdit()));

        // Add Drag and Drop support
        inputArea.setDropTarget(new DropTarget() {
            @SuppressWarnings("unchecked")
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<File> droppedFiles = (java.util.List<File>)
                        evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                    if (droppedFiles.isEmpty()) {
                        setStatus("No file dropped.", true);
                        return;
                    }

                    Path path = droppedFiles.get(0).toPath();
                    if (!isLikelyBibFile(path)) {
                        setStatus("Please drop a .bib file.", true);
                        return;
                    }

                    loadFromFileAsync(path);
                } catch (Exception ex) {
                    setStatus("Error dropping file: " + ex.getMessage(), true);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(inputArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(label, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the output panel with label and text area.
     */
    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Header with label and search field
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        JLabel label = new JLabel("Unique References:");
        label.setFont(label.getFont().deriveFont(Font.BOLD));

        searchField = new JTextField(15);
        searchField.setToolTipText("Filter output by keyword (live search)");
        searchField.putClientProperty("JTextField.placeholderText", "Filter…");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterOutput(); }
            @Override public void removeUpdate(DocumentEvent e) { filterOutput(); }
            @Override public void changedUpdate(DocumentEvent e) { filterOutput(); }
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(searchField);

        headerPanel.add(label, BorderLayout.WEST);
        headerPanel.add(searchPanel, BorderLayout.EAST);

        outputArea = new JTextArea(TEXT_AREA_ROWS, TEXT_AREA_COLS);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(245, 245, 245));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.getDocument().addUndoableEditListener((UndoableEditEvent e) -> outputUndo.addEdit(e.getEdit()));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the button panel with action buttons.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        uploadButton = new JButton("Upload File");
        processButton = new JButton("Process");
        copyButton = new JButton("Copy Output");
        saveButton = new JButton("Save Output");
        clearButton = new JButton("Clear");

        sortCheckBox = new JCheckBox("Sort by Key");
        sortCheckBox.setSelected(prefs.getBoolean(PREF_SORT_BY_KEY, false));

        // Set reasonable sizes
        Dimension btnSize = new Dimension(110, 30);
        uploadButton.setPreferredSize(btnSize);
        processButton.setPreferredSize(btnSize);
        copyButton.setPreferredSize(btnSize);
        saveButton.setPreferredSize(btnSize);
        clearButton.setPreferredSize(new Dimension(80, 30));

        // Add action listeners
        processButton.addActionListener(e -> processReferencesAsync());
        uploadButton.addActionListener(e -> uploadFile());
        copyButton.addActionListener(e -> copyToClipboard());
        saveButton.addActionListener(e -> saveOutputToFile());
        clearButton.addActionListener(e -> clearAll());

        panel.add(uploadButton);
        panel.add(sortCheckBox);
        panel.add(processButton);
        panel.add(copyButton);
        panel.add(saveButton);
        panel.add(clearButton);

        return panel;
    }

    /**
     * Creates the menu bar with file, edit, actions, and help menus.
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // File menu
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);

        JMenuItem openItem = new JMenuItem("Open…");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
        openItem.addActionListener(e -> uploadFile());
        file.add(openItem);

        // Recent files submenu
        recentFilesMenu = new JMenu("Open Recent");
        updateRecentFilesMenu();
        file.add(recentFilesMenu);

        file.addSeparator();

        JMenuItem saveItem = new JMenuItem("Save Output…");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
        saveItem.addActionListener(e -> saveOutputToFile());
        file.add(saveItem);

        file.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuMask));
        exitItem.addActionListener(e -> {
            savePreferences();
            frame.dispose();
        });
        file.add(exitItem);

        // Edit menu
        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);

        JMenuItem cutItem = new JMenuItem(new DefaultEditorKit.CutAction());
        cutItem.setText("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask));
        edit.add(cutItem);

        JMenuItem copyItem = new JMenuItem(new DefaultEditorKit.CopyAction());
        copyItem.setText("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
        edit.add(copyItem);

        JMenuItem pasteItem = new JMenuItem(new DefaultEditorKit.PasteAction());
        pasteItem.setText("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
        edit.add(pasteItem);

        edit.addSeparator();

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
        undoItem.addActionListener(e -> undo(inputUndo));
        edit.add(undoItem);

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        redoItem.addActionListener(e -> redo(inputUndo));
        edit.add(redoItem);

        edit.addSeparator();

        JMenuItem findItem = new JMenuItem("Find in Output");
        findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask));
        findItem.addActionListener(e -> searchField.requestFocusInWindow());
        edit.add(findItem);

        // Actions menu
        JMenu actions = new JMenu("Actions");
        actions.setMnemonic(KeyEvent.VK_A);

        JMenuItem processItem = new JMenuItem("Process");
        processItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuMask));
        processItem.addActionListener(e -> processReferencesAsync());
        actions.add(processItem);

        JMenuItem copyOutputItem = new JMenuItem("Copy Output");
        copyOutputItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        copyOutputItem.addActionListener(e -> copyToClipboard());
        actions.add(copyOutputItem);

        JMenuItem clearItem = new JMenuItem("Clear All");
        clearItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, menuMask));
        clearItem.addActionListener(e -> clearAll());
        actions.add(clearItem);

        // Help menu
        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        help.add(aboutItem);

        JMenuItem shortcutsItem = new JMenuItem("Keyboard Shortcuts");
        shortcutsItem.addActionListener(e -> showShortcutsDialog());
        help.add(shortcutsItem);

        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(actions);
        menuBar.add(help);

        return menuBar;
    }

    /**
     * Installs global key bindings for common actions.
     */
    private void installGlobalKeyBindings(JComponent root) {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, menuMask), "open");
        am.put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                uploadFile();
            }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, menuMask), "process");
        am.put("process", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                processReferencesAsync();
            }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, menuMask), "clear");
        am.put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearAll();
            }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, menuMask), "copyOutput");
        am.put("copyOutput", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                copyToClipboard();
            }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, menuMask), "undo");
        am.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                undo(inputUndo);
            }
        });

        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, menuMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "redo");
        am.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                redo(inputUndo);
            }
        });
    }

    /**
     * Undoes the last action in the specified UndoManager.
     */
    private void undo(UndoManager undo) {
        try {
            if (undo.canUndo()) undo.undo();
        } catch (CannotUndoException ignored) {
        }
    }

    /**
     * Redoes the last undone action in the specified UndoManager.
     */
    private void redo(UndoManager undo) {
        try {
            if (undo.canRedo()) undo.redo();
        } catch (CannotRedoException ignored) {
        }
    }

    /**
     * Sets the application to busy or idle state, with an optional status message.
     */
    private void setBusy(boolean busy, String message) {
        uploadButton.setEnabled(!busy);
        processButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        copyButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        sortCheckBox.setEnabled(!busy);
        inputArea.setEditable(!busy);

        progressBar.setVisible(busy);

        frame.setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        if (message != null) setStatus(message, false);
    }

    /**
     * Processes the input text and displays unique references asynchronously.
     */
    private void processReferencesAsync() {
        String inputText = inputArea.getText();
        if (inputText == null || inputText.isBlank()) {
            setStatus("Please enter or upload BibTeX references first.", true);
            return;
        }

        setBusy(true, "Processing…");

        SwingWorker<BibTeXDeduplicator.Result, Void> worker = new SwingWorker<>() {
            @Override
            protected BibTeXDeduplicator.Result doInBackground() {
                return BibTeXDeduplicator.deduplicate(inputText, sortCheckBox.isSelected());
            }

            @Override
            protected void done() {
                try {
                    BibTeXDeduplicator.Result result = get();

                    if (result.totalEntries() == 0) {
                        outputArea.setText("");
                        lastFullOutput = "";
                        setStatus("No reference entries found.", true);
                        return;
                    }

                    StringBuilder out = new StringBuilder();
                    out.append("% Total entries parsed: ").append(result.totalEntries()).append('\n');
                    out.append("% Unique kept: ").append(result.uniqueCount()).append('\n');
                    out.append("% Duplicates removed: ").append(result.duplicateCount()).append('\n');
                    out.append("% Parse issues: ").append(result.parseErrorCount()).append('\n');
                    out.append("% ").append("=".repeat(50)).append("\n\n");

                    for (String raw : result.uniqueEntries().values()) {
                        out.append(raw).append("\n\n");
                    }

                    lastFullOutput = out.toString();
                    outputArea.setText(lastFullOutput);
                    outputArea.setCaretPosition(0);
                    searchField.setText(""); // Clear search filter
                    setStatus("Done. Unique: " + result.uniqueCount() + ", duplicates removed: " + result.duplicateCount() + ".", false);
                } catch (Exception ex) {
                    setStatus("Processing failed: " + ex.getMessage(), true);
                } finally {
                    setBusy(false, null);
                }
            }
        };

        worker.execute();
    }

    /**
     * Opens a file chooser to upload a .bib file.
     */
    private void uploadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a BibTeX (.bib) file");
        fileChooser.setFileFilter(new FileNameExtensionFilter("BibTeX files (*.bib)", "bib"));

        // Remember last directory
        String lastDir = prefs.get(PREF_LAST_DIR, null);
        if (lastDir != null) {
            fileChooser.setCurrentDirectory(new File(lastDir));
        }

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path path = fileChooser.getSelectedFile().toPath();
            prefs.put(PREF_LAST_DIR, path.getParent().toString());
            addToRecentFiles(path);
            loadFromFileAsync(path);
        }
    }

    /**
     * Loads content from a file path into the input area asynchronously.
     */
    private void loadFromFileAsync(Path path) {
        if (!isLikelyBibFile(path)) {
            setStatus("Selected file doesn't look like a .bib file.", true);
            return;
        }

        setBusy(true, "Loading " + path.getFileName() + "…");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return Files.readString(path);
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    inputArea.setText(content);
                    inputArea.setCaretPosition(0);
                    outputArea.setText("");
                    setStatus("Loaded file: " + path.getFileName(), false);
                } catch (Exception ex) {
                    showError("Error reading file: " + ex.getMessage());
                    setStatus("Failed to load file.", true);
                } finally {
                    setBusy(false, null);
                }
            }
        };

        worker.execute();
    }

    /**
     * Checks if the given file path has a .bib extension.
     */
    private static boolean isLikelyBibFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        return name.endsWith(".bib");
    }

    /**
     * Clears both input and output areas.
     */
    private void clearAll() {
        inputArea.setText("");
        outputArea.setText("");
        setStatus("Cleared all text.", false);
        inputArea.requestFocus();
    }

    /**
     * Copies the content of the output area to the system clipboard.
     */
    private void copyToClipboard() {
        String content = outputArea.getText();
        if (content == null || content.isBlank()) {
            setStatus("Output is empty. Nothing to copy.", true);
            return;
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
        setStatus("Output copied to clipboard.", false);
    }

    /**
     * Sets the status label.
     */
    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setForeground(isError ? new Color(180, 0, 0) : new Color(50, 50, 50));
    }

    /**
     * Shows an error message dialog.
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Saves the output to a file.
     */
    private void saveOutputToFile() {
        String content = outputArea.getText();
        if (content == null || content.isBlank()) {
            setStatus("Output is empty. Nothing to save.", true);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Output As");
        fileChooser.setFileFilter(new FileNameExtensionFilter("BibTeX files (*.bib)", "bib"));
        fileChooser.setSelectedFile(new File("unique_references.bib"));

        String lastDir = prefs.get(PREF_LAST_DIR, null);
        if (lastDir != null) {
            fileChooser.setCurrentDirectory(new File(lastDir));
        }

        int result = fileChooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path path = fileChooser.getSelectedFile().toPath();

            // Ensure .bib extension
            if (!path.toString().toLowerCase().endsWith(".bib")) {
                path = Path.of(path + ".bib");
            }

            try {
                Files.writeString(path, content);
                prefs.put(PREF_LAST_DIR, path.getParent().toString());
                setStatus("Saved to: " + path.getFileName(), false);
            } catch (IOException ex) {
                showError("Error saving file: " + ex.getMessage());
            }
        }
    }

    /**
     * Filters the output based on the search field content.
     */
    private void filterOutput() {
        String filter = searchField.getText().toLowerCase().trim();

        if (filter.isEmpty()) {
            outputArea.setText(lastFullOutput);
            return;
        }

        // Split the full output into entries and filter
        String[] parts = lastFullOutput.split("\n\n");
        StringBuilder filtered = new StringBuilder();
        int matchCount = 0;

        for (String part : parts) {
            if (part.toLowerCase().contains(filter)) {
                filtered.append(part).append("\n\n");
                if (!part.startsWith("%")) {
                    matchCount++;
                }
            }
        }

        outputArea.setText(filtered.toString());
        outputArea.setCaretPosition(0);
        setStatus("Showing " + matchCount + " matching entries.", false);
    }

    /**
     * Adds a file to the recent files list.
     */
    private void addToRecentFiles(Path path) {
        recentFiles.remove(path);
        recentFiles.add(0, path);

        while (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.remove(recentFiles.size() - 1);
        }

        updateRecentFilesMenu();
    }

    /**
     * Updates the recent files menu.
     */
    private void updateRecentFilesMenu() {
        if (recentFilesMenu == null) return;

        recentFilesMenu.removeAll();

        if (recentFiles.isEmpty()) {
            JMenuItem emptyItem = new JMenuItem("(No Recent Files)");
            emptyItem.setEnabled(false);
            recentFilesMenu.add(emptyItem);
        } else {
            for (Path path : recentFiles) {
                JMenuItem item = new JMenuItem(path.getFileName().toString());
                item.setToolTipText(path.toString());
                item.addActionListener(e -> loadFromFileAsync(path));
                recentFilesMenu.add(item);
            }

            recentFilesMenu.addSeparator();
            JMenuItem clearItem = new JMenuItem("Clear Recent Files");
            clearItem.addActionListener(e -> {
                recentFiles.clear();
                updateRecentFilesMenu();
            });
            recentFilesMenu.add(clearItem);
        }
    }

    /**
     * Loads preferences from storage.
     */
    private void loadPreferences() {
        // Load recent files
        String recentFilesStr = prefs.get(PREF_RECENT_FILES, "");
        if (!recentFilesStr.isEmpty()) {
            for (String pathStr : recentFilesStr.split("\n")) {
                Path path = Path.of(pathStr);
                if (Files.exists(path)) {
                    recentFiles.add(path);
                }
            }
        }
    }

    /**
     * Saves preferences to storage.
     */
    private void savePreferences() {
        // Save sort preference
        prefs.putBoolean(PREF_SORT_BY_KEY, sortCheckBox.isSelected());

        // Save recent files
        StringBuilder sb = new StringBuilder();
        for (Path path : recentFiles) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(path.toString());
        }
        prefs.put(PREF_RECENT_FILES, sb.toString());
    }

    /**
     * Shows the About dialog.
     */
    private void showAboutDialog() {
        String message = """
            Unique LaTeX References
            Version 1.0
            
            A tool for deduplicating BibTeX references.
            
            Features:
            • Upload or drag-and-drop .bib files
            • Deduplicate entries by key (first occurrence wins)
            • Optional alphabetical sorting by key
            • Search/filter output
            • Save output to file
            • Recent files menu
            """;

        JOptionPane.showMessageDialog(frame, message, "About", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows the Keyboard Shortcuts dialog.
     */
    private void showShortcutsDialog() {
        String cmdKey = System.getProperty("os.name").toLowerCase().contains("mac") ? "⌘" : "Ctrl+";

        String message = String.format("""
            Keyboard Shortcuts:
            
            %sO        Open file
            %sS        Save output
            %sEnter    Process references
            %sL        Clear all
            %sC        Copy (standard) / Copy output (from output area)
            %sShift+C  Copy entire output
            %sZ        Undo
            %sShift+Z  Redo
            %sF        Focus search field
            %sQ        Quit
            """, cmdKey, cmdKey, cmdKey, cmdKey, cmdKey, cmdKey, cmdKey, cmdKey, cmdKey, cmdKey);

        JOptionPane.showMessageDialog(frame, message, "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
    }
}
