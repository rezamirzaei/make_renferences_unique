#!/bin/bash
# Run script for Unique LaTeX References

cd "$(dirname "$0")"

# Compile if needed
if [ ! -d "target/classes" ]; then
    echo "Compiling..."
    mkdir -p target/classes target/test-classes
    javac -d target/classes src/main/java/com/uniquereferences/*.java
fi

# Run the application
echo "Starting Unique LaTeX References..."
java -cp target/classes com.uniquereferences.UniqueReferencesApp
