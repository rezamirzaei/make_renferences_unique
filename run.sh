#!/bin/bash
# Run script for Unique LaTeX References

cd "$(dirname "$0")"

# Always recompile to ensure latest code
echo "Compiling..."
mkdir -p target/classes
javac -d target/classes src/main/java/com/uniquereferences/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Run the application
echo "Starting Unique LaTeX References..."
java -cp target/classes com.uniquereferences.UniqueReferencesApp
