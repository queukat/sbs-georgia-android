# Google Play release setup

Package: `com.queukat.sbsgeorgia`. The application ID in `app/build.gradle.kts`
is authoritative. A machine-wide `PLAY_PACKAGE_NAME` may refer to another app;
Gradle Play Publisher uses the Android application ID. The inspection script also
pins this package explicitly.

## Credentials and signing

- Keep `keystore.properties`, the upload keystore and service-account JSON outside
  version control. Do not print credentials or include them in support archives.
- `PLAY_KEY_FILE` must point to a service-account JSON with access to this Play app.
- Release signing reads `keystore.properties`; Google Play signs distributed APKs
  with its app-signing key. A locally signed release cannot replace a Play install.
- The default publishing track is **internal**. Production must be explicit.

## Inspect the current release before choosing a version

The helper uses a temporary Play edit, reads tracks, uploaded bundles/APKs,
localized listings and screenshot metadata, then deletes the uncommitted edit.
Do not run this while another publication/edit is in progress.

```powershell
python -m pip install google-auth requests
python scripts/inspect-play-release.py --output artifacts/play-before.json
```

Use a `versionCode` greater than **all uploaded codes**, including testing tracks,
and set the intended `versionName` in `app/build.gradle.kts`. Do not infer Play's
version from a locally installed debug APK. Preserve the snapshot locally.

## Localized metadata

The active listing languages are `en-US` and `ru-RU`; confirm them in the snapshot.
Text lives under `app/src/main/play/listings/<locale>/` (`title.txt`,
`short-description.txt`, `full-description.txt`). Limits: 30, 80 and 4000 characters.
Release notes live in `app/src/main/play/release-notes/<locale>/<track>.txt`, at most
500 characters per locale. Update `production.txt` for production; an internal
release uses `internal.txt`. Verify actual supported behavior against the code.

Back up local Play metadata before editing it. `bootstrapListing` downloads server
metadata and can overwrite local work; it is not part of a normal release.
`app/src/main/play/` is ignored intentionally. Keep listing source/backups locally;
only deliberately publish safe documentation/scripts to the public repository.

## Screenshots

Follow [the release checklist](play-release-checklist.md#screenshots). Capture six
synthetic-data screens for each locale using the existing instrumentation class.
Never run screenshot/connected instrumentation on a phone containing user data.

## Build and publish

After the checks and visual review in the checklist:

```powershell
.\gradlew.bat :app:bundleRelease :app:assembleRelease --console=plain
.\gradlew.bat :app:publishReleaseBundle --track production :app:publishReleaseListing --console=plain
python scripts/inspect-play-release.py --output artifacts/play-after.json
```

To upload the exact bundle already verified, add
`--artifact-dir <absolute-directory-containing-the-verified-aab>` to
`:app:publishReleaseBundle`. Keep a single intended AAB in that directory and
compare its SHA-256 with the uploaded bundle metadata.

`publishReleaseListing` uploads text and graphics. Bundle publication supplies the
track-specific localized release notes. Gradle Play Publisher commits the edit at
the end of the build; do not start a second publisher or API edit concurrently.
The release config enables R8 and resource shrinking. Keep the checked-in startup
profile; the baseline-profile generation module is detached from settings.

Check the server's production code/name/notes, listing text and image hashes against
local artifacts. A successful upload or track status `completed` is not proof that
Google's review has finished or that every user already sees the update. Check Play
Console's publishing overview if review/managed-publishing state is relevant. If
changes require explicit submission there, finish that submission and report the
actual resulting state. Do not silently leave changes unsubmitted.

References: [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher),
[Play edit commit](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit).
