#!/bin/bash
# Test runner for Unique LaTeX References (Maven-based)

set -euo pipefail

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
    echo "ERROR: Maven (mvn) is required to run tests."
    echo "Install Maven with: brew install maven"
    exit 1
fi

echo "Using Maven: $MVN"
echo "Running JUnit tests via Maven..."
"$MVN" test "$@"
