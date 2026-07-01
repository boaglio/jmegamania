#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

mvn -q package -DskipTests
java -jar target/jmegamania.jar "$@"
