#!/usr/bin/env bash
# Starts the ETT HR portal. Press Ctrl+C to stop it.
cd "$(dirname "$0")" || exit 1

if [ "$(id -u)" -eq 0 ]; then
  echo "Run the portal as a normal user, not root: its database refuses to run as root."
  exit 1
fi

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
major=$("$JAVA" -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print (v[1] == "1" ? v[2] : v[1]); exit }')
if [ -z "$major" ] || [ "$major" -lt 25 ]; then
  echo "The ETT HR portal needs Java 25 or later: https://adoptium.net/temurin/releases/?version=25"
  exit 1
fi

exec "$JAVA" -jar ett-hr-portal.jar "$@"
