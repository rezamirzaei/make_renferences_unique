# Unique LaTeX References

A Java Swing application for deduplicating and verifying BibTeX references.

## Features

- **Upload or Drag & Drop** `.bib` or `.pdf` files
- **PDF Reference Extraction**: Extract references from PDF files and convert to BibTeX
  - Automatically finds the References/Bibliography section
  - Parses numbered references `[1]`, `[2]`, etc.
  - Extracts DOIs, years, and titles
  - Verifies extracted references using online APIs
- **Deduplicate** entries by key (first occurrence wins)
- **Smart Dedupe (optional)**: de-duplicate based on normalized **Title** (+ Year when present), even when keys differ
- **Duplicates report export**: export why each duplicate was dropped (key duplicate vs title duplicate)
- **Verify & Correct** references online using multiple sources:
  - CrossRef
  - Semantic Scholar
  - OpenAlex
- **Verification modes**:
  - **Safe (default)**: adds missing fields only (never overwrites)
  - **Aggressive (DOI only)**: allows overwriting a small set of scalar fields *only if* the entry has a DOI
- **Month formatting policy** (only applied when inserting/overwriting, depending on mode):
  - Keep original (default)
  - Abbrev (Sep.)
  - Full name (September)
- **Safe verification**: preserves existing fields and LaTeX formatting; only fills missing fields
- **Sort** output alphabetically by key (optional)
- **Search/Filter** output in real-time
- **Export Summary** to a `.txt` file (counts, duplicates, parse errors)
- **Save** output to file
- **Recent files** menu for quick access
- **Undo/Redo** support
- **Keyboard shortcuts** for all major actions

## Logic Guarantees (Correctness/Safety)

- **No BibTeX corruption**: verification preserves existing fields (including `organization`, `publisher`, `month`, etc.) and preserves LaTeX like `\&` and `\text{...}`.
- **No overwrites by default**: Safe mode **adds missing fields only**.
- **Aggressive mode is gated**: overwrites are enabled only when the entry has a DOI.
- **Deterministic output**: deduplication is stable (first occurrence wins). If sorting is enabled, output order is deterministic by key.

## Requirements

- Java 21 or higher (Java 25 recommended)
- Maven (for building and running)

## Quick Start

### Run the Application
```bash
./run.sh
```

### Run Tests
```bash
./test.sh
```

## Maven Commands

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package as JAR
mvn clean package

# Run the application
mvn exec:java
```

## Manual Commands (without Maven)

### Compile
```bash
mkdir -p target/classes
javac -d target/classes src/main/java/com/uniquereferences/*.java
```

### Run
```bash
java -cp target/classes com.uniquereferences.UniqueReferencesApp
```

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| ⌘O / Ctrl+O | Open file |
| ⌘I / Ctrl+I | Import from PDF |
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
│   ├── MonthNormalizer.java       # Month formatting utilities
│   ├── PdfReferenceExtractor.java # PDF reference extraction
│   └── ReferenceVerifier.java     # Online verification
└── test/java/com/uniquereferences/
    └── *JUnitTest.java            # JUnit 5 test suite
```

## License

MIT License
