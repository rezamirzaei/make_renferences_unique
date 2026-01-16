#!/bin/bash
# Test runner for Unique LaTeX References (Maven-based)

set -euo pipefail

cd "$(dirname "$0")"

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: Maven (mvn) is required to run tests."
  echo "Install Maven, then run: ./test.sh"
  exit 1
fi

echo "Running JUnit tests via Maven..."
mvn test "$@"
