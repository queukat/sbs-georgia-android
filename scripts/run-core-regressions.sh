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
annotation class Qualifier
KT
# Room annotations are compile-time placeholders ONLY: no database engine or generated code is mocked here.
cat > "$TMP/RoomAnnotations.kt" <<'KT'
package androidx.room
annotation class Dao
annotation class Query(val value: String)
annotation class Upsert
annotation class Transaction
annotation class Insert(val onConflict: Int = 0)
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(val value: Array<String>, val unique: Boolean = false)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
object OnConflictStrategy { const val REPLACE = 1; const val IGNORE = 5 }
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
  "$TMP/Inject.kt" "$TMP/R.kt" "$TMP/RoomAnnotations.kt" "${SOURCES[@]}" \
  "$B/data/importer/DocumentImportPorts.kt" \
  "$B/data/io/BoundedInput.kt" "$B/data/local/Daos.kt" "$B/data/local/Entities.kt" \
  "$B/data/remote/OfficialFxRemotePorts.kt" "$B/data/repository/FxRateRepositoryImpl.kt" \
  app/src/test/java/com/queukat/sbsgeorgia/review/HardeningRegressionChecks.kt \
  "$B/di/Dispatchers.kt" "$B/domain/usecase/ObserveDeclarationStateUseCase.kt" \
  app/src/test/java/com/queukat/sbsgeorgia/testing/DeclarationStateFixture.kt \
  "$B/domain/usecase/DeclarationUseCases.kt" "$B/domain/usecase/FxUseCases.kt" \
  "$B/domain/usecase/StatementImportUseCases.kt" "$B/domain/usecase/CompleteMonthlyDeclarationUseCase.kt" \
  "$B/ui/importstatement/ImportStatementContract.kt" app/src/test/java/com/queukat/sbsgeorgia/review/CoreRegressionChecks.kt
java -cp "$TMP/checks.jar:$COROUTINES" com.queukat.sbsgeorgia.review.CoreRegressionChecksKt "$ROOT/app/src/test/resources/fixtures"

java -cp "$TMP/checks.jar:$COROUTINES" com.queukat.sbsgeorgia.review.HardeningRegressionChecks
