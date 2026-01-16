#!/bin/bash
# Test runner for Unique LaTeX References

cd "$(dirname "$0")"

# Compile source files
echo "Compiling source files..."
mkdir -p target/classes target/test-classes
javac -d target/classes src/main/java/com/uniquereferences/*.java

# Compile test files
echo "Compiling test files..."
javac -d target/test-classes -cp target/classes src/test/java/com/uniquereferences/*.java

# Run tests
echo ""
echo "Running tests..."
echo ""
java -cp target/classes:target/test-classes com.uniquereferences.AllTests "$@"
