#!/usr/bin/env bash
set -euo pipefail

# ── Java 21+ detection ────────────────────────────────────────────────────────
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "Using JAVA_HOME: $JAVA_HOME"
else
    for candidate in \
        /opt/homebrew/opt/openjdk/bin/java \
        /opt/homebrew/opt/openjdk@21/bin/java; do
        if [ -x "$candidate" ]; then
            export JAVA_HOME="$(dirname "$(dirname "$candidate")")"
            echo "Detected Java at: $JAVA_HOME"
            break
        fi
    done
fi

if ! command -v java &>/dev/null && [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: Java 21+ not found. Install via 'brew install openjdk@21' or set JAVA_HOME." >&2
    exit 1
fi

# ── Gradle wrapper bootstrap (if jar is missing) ──────────────────────────────
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Gradle wrapper jar missing — bootstrapping with 'gradle wrapper --gradle-version 8.5'..."
    gradle wrapper --gradle-version 8.5
fi

# ── Build ─────────────────────────────────────────────────────────────────────
chmod +x ./gradlew
./gradlew build

# ── Install ───────────────────────────────────────────────────────────────────
JAR="build/libs/playergamespaperplugin-0.1.0.jar"
DEST="$HOME/server/plugins"

if [ ! -f "$JAR" ]; then
    echo "ERROR: Build artifact not found at $JAR" >&2
    exit 1
fi

mkdir -p "$DEST"
cp "$JAR" "$DEST/"

echo "✓ Installed $(basename "$JAR") → $DEST/"
