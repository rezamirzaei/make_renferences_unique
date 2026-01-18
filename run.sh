#!/bin/bash
# Run script for Unique LaTeX References

cd "$(dirname "$0")"

# Find Maven - try common locations
if command -v mvn &> /dev/null; then
    MVN="mvn"
elif [ -f "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" ]; then
    MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
elif [ -f "/opt/homebrew/bin/mvn" ]; then
    MVN="/opt/homebrew/bin/mvn"
elif [ -f "/usr/local/bin/mvn" ]; then
    MVN="/usr/local/bin/mvn"
else
    echo "Maven not found. Please install Maven or run from IntelliJ."
    echo "You can install it with: brew install maven"
    exit 1
fi

echo "Using Maven: $MVN"

# Compile with Maven (handles dependencies like PDFBox)
echo "Compiling..."
"$MVN" compile -q

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Run the application with Maven (includes all dependencies)
echo "Starting Unique LaTeX References..."
"$MVN" exec:java -Dexec.mainClass="com.uniquereferences.UniqueReferencesApp" -q
