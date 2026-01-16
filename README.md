# Unique LaTeX References

A Java Swing application for deduplicating and verifying BibTeX references.

## Features

- **Upload or Drag & Drop** `.bib` files
- **Deduplicate** entries by key (first occurrence wins)
- **Verify & Correct** references online using CrossRef API
- **Sort** output alphabetically by key (optional)
- **Search/Filter** output in real-time
- **Save** output to file
- **Recent files** menu for quick access
- **Undo/Redo** support
- **Keyboard shortcuts** for all major actions

## Requirements

- Java 17 or higher

## Quick Start

### Run the Application
```bash
./run.sh
```

### Run Tests
```bash
./test.sh
```

### Run Tests with Network Tests (requires internet)
```bash
./test.sh --network
```

## Manual Commands

### Compile
```bash
mkdir -p target/classes
javac -d target/classes src/main/java/com/uniquereferences/*.java
```

### Run
```bash
java -cp target/classes com.uniquereferences.UniqueReferencesApp
```

### Compile and Run Tests
```bash
mkdir -p target/test-classes
javac -d target/test-classes -cp target/classes src/test/java/com/uniquereferences/*.java
java -cp target/classes:target/test-classes com.uniquereferences.AllTests
```

## Maven (Optional)

If you have Maven installed:

```bash
# Compile
mvn clean compile

# Package as JAR
mvn clean package

# Run
mvn exec:java
```

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| ⌘O / Ctrl+O | Open file |
| ⌘S / Ctrl+S | Save output |
| ⌘Enter / Ctrl+Enter | Process references |
| ⌘R / Ctrl+R | Verify & correct online |
| ⌘L / Ctrl+L | Clear all |
| ⌘C / Ctrl+C | Copy |
| ⌘⇧C / Ctrl+Shift+C | Copy entire output |
| ⌘Z / Ctrl+Z | Undo |
| ⌘⇧Z / Ctrl+Shift+Z | Redo |
| ⌘F / Ctrl+F | Focus search field |
| ⌘Q / Ctrl+Q | Quit |

## Project Structure

```
src/
├── main/java/com/uniquereferences/
│   ├── UniqueReferencesApp.java   # Main GUI application
│   ├── BibTeXParser.java          # BibTeX parsing logic
│   ├── BibTeXDeduplicator.java    # Deduplication logic
│   └── ReferenceVerifier.java     # CrossRef API verification
└── test/java/com/uniquereferences/
    ├── AllTests.java              # Test runner
    ├── BibTeXParserTest.java      # Parser tests (41 tests)
    ├── BibTeXDeduplicatorTest.java # Deduplicator tests (41 tests)
    └── ReferenceVerifierTest.java  # Verifier tests (18+ tests)
```

## License

MIT License
