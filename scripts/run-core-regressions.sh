#!/usr/bin/env bash
# Standalone safety net for environments without Android SDK/Gradle. Not an Android build.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
KOTLINC="${KOTLINC:-$(command -v kotlinc)}"
KLIB="$(cd "$(dirname "$(readlink -f "$KOTLINC")")/../lib" && pwd)"
COROUTINES="${COROUTINES_JAR:-$KLIB/kotlinx-coroutines-core-jvm.jar}"
[[ -f "$COROUTINES" ]] || { echo 'Set COROUTINES_JAR to a kotlinx-coroutines-core JVM jar.' >&2; exit 1; }
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
# These are annotation-only stubs, never replacements for business logic or Android implementations.
cat > "$TMP/Inject.kt" <<'KT'
package javax.inject
annotation class Inject
annotation class Singleton
KT
# The UI contract only references resource IDs for filters. Generate IDs from the real XML.
python3 - "$ROOT" "$TMP/R.kt" <<'PY'
import sys,xml.etree.ElementTree as E
from pathlib import Path
r=Path(sys.argv[1]);names=sorted({e.attrib['name'] for p in (r/'app/src/main/res/values').glob('*.xml') for e in E.parse(p).getroot() if e.tag=='string'})
Path(sys.argv[2]).write_text('package com.queukat.sbsgeorgia\nobject R { object string {\n'+'\n'.join(f'const val {n}: Int = {i}' for i,n in enumerate(names,1))+'\n} }\n')
PY
B=app/src/main/java/com/queukat/sbsgeorgia
mapfile -t SOURCES < <(find "$B/domain/model" "$B/domain/service" "$B/domain/repository" -name '*.kt' | sort)
"$KOTLINC" -jvm-target 17 -cp "$COROUTINES" -include-runtime -d "$TMP/checks.jar" \
  "$TMP/Inject.kt" "$TMP/R.kt" "${SOURCES[@]}" \
  "$B/data/importer/DocumentImportPorts.kt" \
  "$B/domain/usecase/DeclarationUseCases.kt" "$B/domain/usecase/FxUseCases.kt" \
  "$B/domain/usecase/StatementImportUseCases.kt" "$B/domain/usecase/CompleteMonthlyDeclarationUseCase.kt" \
  "$B/ui/importstatement/ImportStatementContract.kt" app/src/test/java/com/queukat/sbsgeorgia/review/CoreRegressionChecks.kt
java -cp "$TMP/checks.jar:$COROUTINES" com.queukat.sbsgeorgia.review.CoreRegressionChecksKt "$ROOT/app/src/test/resources/fixtures"
