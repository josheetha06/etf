#!/bin/bash
# Starts the ETT HR portal. Keep this window open while you use the portal; close it to stop.
cd "$(dirname "$0")" || exit 1

JAVA_HOME_25=$(/usr/libexec/java_home -v 25+ 2>/dev/null)
if [ -z "$JAVA_HOME_25" ]; then
  echo "The ETT HR portal needs Java 25 or later."
  echo "The download page will open. Install Java, then start the portal again."
  open "https://adoptium.net/temurin/releases/?version=25"
  read -r -p "Press Return to close this window. "
  exit 1
fi

"$JAVA_HOME_25/bin/java" -jar ett-hr-portal.jar "$@"
