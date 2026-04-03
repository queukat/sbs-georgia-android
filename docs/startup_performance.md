# Startup Performance

## Goal

Establish a repeatable startup-performance path for SBS Georgia without another architectural refactor.

## What was added

- A release-like benchmark path via `:baselineprofile`
- Macrobenchmark startup tests
- Baseline Profile generation scaffold
- Startup Profile generation via `includeInStartupProfile = true`
- Explicit `androidx.profileinstaller` dependency in the app
- `benchmarkRelease` and `nonMinifiedRelease` signing overrides so local connected-device runs stay installable

## Commands

Generate Baseline and Startup Profiles on a supported device:

```powershell
./gradlew.bat :app:generateBaselineProfile --console=plain
```

Run startup macrobenchmarks on a connected device:

```powershell
./gradlew.bat :baselineprofile:connectedCheck `
  -Pandroid.testInstrumentationRunnerArguments.class=com.queukat.sbsgeorgia.baselineprofile.StartupBenchmarks `
  --console=plain
```

Run individual startup benchmarks on a connected device:

```powershell
./gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.queukat.sbsgeorgia.baselineprofile.StartupBenchmarks#coldStartupNoCompilation" `
  --console=plain
```

## Device constraint

Baseline Profile collection generally requires either:

- Android 13+ device, or
- rooted Android 9+ device

The currently attached Android 9 phone is suitable for macrobenchmark measurement, but profile generation may still be blocked by platform requirements if the device is not rooted.

For this Android 9 device, the startup benchmark uses a repeatable `process-cold` flow:

- `killProcess()`
- launch activity

This avoids the root-only page-cache drop requirement while still measuring startup after a full app-process restart.

## Current measurements

Device:

- Sony H4413
- Android 9 / API 28
- non-rooted

Release-like `benchmarkRelease` results:

- `coldStartupNoCompilation`: `timeToInitialDisplayMs = 905.6`
- `coldStartupWithFullCompilation`: `timeToInitialDisplayMs = 894.2`
- `coldStartupWithBaselineProfilesIfAvailable`: `timeToInitialDisplayMs = 826.4`

Interpretation:

- Existing available baseline profiles already improve TTID by about `79 ms` vs no compilation on this device
- Full compilation barely changes startup here, so the main Android 9 launch pain is not primarily ART compilation of app code
- Debug `am start -W` results around `7.7s` are not directly comparable to the release-like benchmark result above; they include a much harsher system-cold path outside the app runtime itself

Profile collection status on this device:

- `collectNonMinifiedReleaseBaselineProfile` fails as expected
- reason: Baseline Profile collection requires Android 13+ or rooted Android 9+

## Android 13 follow-up

Device:

- Samsung SM-N985F
- Android 13 / API 33

Before wiring the generated app-specific startup profile into the app source set:

- `coldStartupNoCompilation`: `timeToInitialDisplayMs median = 418.3`
- `coldStartupWithBaselineProfilesIfAvailable`: `timeToInitialDisplayMs median = 320.4`

What was generated:

- `:app:generateReleaseBaselineProfile` still fails overall because the benchmark runner cleanup times out on `androidx.benchmark.IsolationActivity`
- however, the generation step did produce a human-readable startup profile artifact:
  - `baselineprofile/build/outputs/connected_android_test_additional_output/nonMinifiedRelease/connected/SM-N985F - 13/BaselineProfileGenerator_startupJourney-startup-prof.txt`

Pragmatic integration used:

- copied the generated artifact into:
  - `app/src/release/generated/baselineProfiles/startup-prof.txt`
- deliberately did **not** keep a copied `baseline-prof.txt`, because the generated artifact was explicitly a startup profile and duplicating it into both files did not produce a trustworthy improvement signal

Verification after integration:

- `mergeBenchmarkReleaseStartupProfile` contains `com/queukat/sbsgeorgia` classes
- `mergeBenchmarkReleaseArtProfile` does not contain app-specific rules from this generated artifact, which matches the decision above to keep only `startup-prof.txt`

After wiring `startup-prof.txt` only:

- `coldStartupNoCompilation`: `timeToInitialDisplayMs median = 481.4`

Interpretation:

- on this Android 13 device, at `15-16%` battery, the measurements remained noisy and did **not** show a reliable win from the generated startup profile
- the release-like startup path is still well under one second, so the earlier multi-second delay observed on old hardware/debug runs remains primarily a debug/device-path problem rather than an ordinary shipping startup path problem
- the startup-profile infrastructure is now in place, but any claim of measured improvement should be revalidated on a charged device

Operational issues observed on Android 13:

- UTP benchmark tasks still report a failing cleanup phase around `androidx.benchmark.IsolationActivity`, even when the actual benchmark output files are produced
- if a differently signed `com.queukat.sbsgeorgia` build is already installed on the device, install tasks can fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; resolving that requires uninstalling the existing app from the device, which is intentionally not automated here because it would remove user data
