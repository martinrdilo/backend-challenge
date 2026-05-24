#!/usr/bin/env bash
# Simulates CI environment: strips local env vars that mask configuration
# problems. Catches ${DB_URL} placeholders, missing profiles, etc.
#
# Usage: ./run-tests-ci.sh
#
# Unlike ./run-tests.sh (which runs with Docker), this runs without any
# pre-set DB credentials so Spring's placeholder resolution fails loudly
# if a test loads the default profile by mistake.

set -euo pipefail

echo "==> Running tests in CI-like environment (no DB env vars)..."

# Strip potentially troublesome env vars
unset DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET EXTERNAL_API_BASE_URL

# Run the same command CircleCI uses
./gradlew test --no-daemon

echo "==> All tests passed in CI-like environment."
