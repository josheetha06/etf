#!/usr/bin/env bash
# Builds the portable edition of the ETT HR portal for Windows, macOS and Linux.
# Output: dist/ETT-HR-Portal-<version>-<system>.zip
#
# Needs Java 25, Maven 3.9, Node.js 22.12+, zip and unzip. Run from anywhere.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=1.0.0
PG_VERSION=18.6.0
DIST="$ROOT/dist"
WORK="$DIST/.work"
NAME="ETT-HR-Portal-$VERSION"

echo "==> Building the web app"
(cd "$ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)
test -f "$ROOT/frontend/dist/index.html" || { echo "Web app build missing"; exit 1; }

echo "==> Building the application and the Windows launcher"
(cd "$ROOT/backend" && mvn -B -q -Pportable -DskipTests package)

echo "==> Fetching PostgreSQL $PG_VERSION for each system"
rm -rf "$WORK" && mkdir -p "$WORK/pg"
fetch_postgres() { # <maven classifier> <archive inside the jar>
  (cd "$ROOT/backend" && mvn -B -q dependency:copy \
    -Dartifact="io.zonky.test.postgres:embedded-postgres-binaries-$1:$PG_VERSION" -DoutputDirectory="$WORK/pg")
  unzip -p "$WORK/pg/embedded-postgres-binaries-$1-$PG_VERSION.jar" "$2" > "$WORK/pg/$2"
}
fetch_postgres windows-amd64 postgres-windows-x86_64.txz
fetch_postgres darwin-amd64 postgres-darwin-x86_64.txz   # universal: Intel and Apple silicon
fetch_postgres linux-amd64 postgres-linux-x86_64.txz

# assemble <system> <postgres archive> <how to start> <launcher files...>
assemble() {
  local system=$1 archive=$2 start=$3
  shift 3
  local dir="$WORK/$system/$NAME"
  mkdir -p "$dir/postgres"
  cp "$ROOT/backend/target/ett-hr-portal.jar" "$dir/"
  cp "$WORK/pg/$archive" "$dir/postgres/"
  for launcher in "$@"; do cp -p "$launcher" "$dir/"; done
  cp "$ROOT/LICENSE" "$dir/LICENSE.txt"
  cp "$ROOT/THIRD_PARTY_NOTICES.md" "$dir/"
  START="$start" VERSION="$VERSION" python3 - "$ROOT/packaging/README.txt" "$dir/README.txt" "$system" <<'PY'
import os, sys
text = open(sys.argv[1]).read().replace('@VERSION@', os.environ['VERSION']).replace('@START@', os.environ['START'])
open(sys.argv[2], 'w', newline='\r\n' if sys.argv[3] == 'windows' else '\n').write(text)
PY
  rm -f "$DIST/$NAME-$system.zip"
  (cd "$WORK/$system" && zip -qr -X "$DIST/$NAME-$system.zip" "$NAME")
  echo "    $DIST/$NAME-$system.zip ($(du -h "$DIST/$NAME-$system.zip" | cut -f1))"
}

L="$ROOT/packaging/launchers"
echo "==> Packaging"
assemble windows postgres-windows-x86_64.txz \
'Double-click "ETT HR Portal.exe".

If Windows says it protected your PC, click "More info", then "Run anyway".
This appears because the program is not yet known to Windows.
If the .exe doesn'"'"'t start, double-click "Start ETT HR Portal.bat" instead.' \
  "$ROOT/backend/target/ETT HR Portal.exe" "$L/Start ETT HR Portal.bat"

assemble macos postgres-darwin-x86_64.txz \
'Double-click "Start ETT HR Portal.command".

If macOS says it can'"'"'t check the file for malicious software, open
System Settings > Privacy & Security, scroll down and click "Open Anyway".
(On macOS 14 or earlier you can instead right-click the file and choose Open.)' \
  "$L/Start ETT HR Portal.command"

assemble linux postgres-linux-x86_64.txz \
'In a terminal, in this folder, run:   ./start.sh
Press Ctrl+C in that terminal to stop the portal.' \
  "$L/start.sh"

rm -rf "$WORK"
echo "==> Done"
