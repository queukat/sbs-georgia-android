# Play release checklist

This is the routine release path. See [setup](play_console_setup.md) for credentials,
package selection, metadata layout and publication semantics.

## Scope and version

1. Review `git status`; preserve unrelated work. Keep private PDFs, backups,
   credentials, local logs and personal screenshots out of this public repository.
2. Inspect Play with `scripts/inspect-play-release.py` before bumping the version.
   Use the maximum uploaded code across all tracks/bundles/APKs.
3. Update `versionCode`/`versionName` in `app/build.gradle.kts`; update both active
   languages' release notes and any changed listing descriptions. Back up metadata.
4. Review the implementation relevant to this release and run targeted local tests.
   FX uses exact requested dates, NBG's official effective rates and manual-override
   priority. Missing or malformed rates must remain visible as unresolved data.

## Quality gate

Run the shared matrix in GitHub Actions, not concurrently on the workstation:
unit tests, debug assembly, Android-test compilation, lint, detekt and ktlint.
For a release branch, push it and dispatch:

```powershell
gh workflow run android-ci.yml --ref <release-branch>
gh run list --workflow android-ci.yml --branch <release-branch>
gh run view <run-id>
```

Fix actual failures and rerun the relevant checks. Review the final diff once.
The verified commit may be fast-forwarded to `main`; a PR is optional. Never stage
all files blindly. The full local equivalent is documented in AGENTS.md for
reproduction, not as an additional duplicate release gate.

## Screenshots

Use an existing phone-like AVD with Google APIs, enough free storage and API 29+.
Start it from Device Manager or the SDK emulator. A headless PowerShell example:

```powershell
$emulator = Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'
& $emulator -list-avds
Start-Process -FilePath $emulator -ArgumentList '-avd <avd-name> -no-window -no-audio -no-snapshot-save' -WindowStyle Hidden
adb devices
adb -s emulator-5554 shell getprop sys.boot_completed
.\scripts\run-play-screenshots.ps1 -Serial emulator-5554
```

Use the actual serial from `adb devices`; boot completion must be `1`.
The script selects only an emulator, runs `PlayStoreScreenshotsTest` for `en-US`
and `ru-RU`, validates six PNGs per locale and restores the previous animation
settings and `ANDROID_SERIAL`. The instrumentation runner can reinstall/clear the
debug package: the emulator must contain disposable data.

Output: `artifacts/play-screenshots/<locale>/`. Review all twelve images for correct
language, current design, clipping and system dialogs. Data is synthetic, generated
by `PlayScreenshotActivity` and `PlayScreenshotScenarios.kt`. Keep scenario states
in sync with the real screen contracts: populate month action state and chart
summary counts/peak amount, not just rendered series. The screens are onboarding, home, months, month detail,
import preview and charts. Copy the six ordered files into
`app/src/main/play/listings/<locale>/graphics/phone-screenshots/1.png` through
`6.png` after review. Keep icon/feature artwork unless the design actually changed.
Do not substitute screenshots from the user's real bank data.

## Release and verification

1. Confirm the full CI matrix is green for the release code and screenshot capture
   passes. Keep build/test evidence under ignored `artifacts/`.
2. Build the signed optimized AAB and APK using the setup commands; record byte sizes
   and SHA-256 hashes. The debug APK size is not the Play download size.
3. Publish the bundle with explicit `--track production` and the localized listing.
   Do not open another Play API edit while Gradle is publishing.
4. Inspect Play again: code/name, completed rollout configuration, both release-note
   languages, descriptions and screenshot hashes. Check publishing/review state in
   Console when needed; distinguish submission from approved public availability.
5. Save the final artifact paths and verification result. Update this runbook only
   for concrete differences found while following it.
