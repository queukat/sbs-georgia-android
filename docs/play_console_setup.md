# Play Console Setup

## Current status

Verified on 2026-04-05:

- the Play service account authenticates successfully
- the Play Console app exists at developer id `7248127183478825243` / app id `4975825611506087226`
- Android Publisher API still returns `404 Package not found: com.queukat.sbsgeorgia`
- practical inference: either the package is not yet linked to the Play app shell in Console UI, or the service account does not yet have app-level access to this app
- `bundleRelease` now builds successfully
- the produced bundle is now signed with a locally generated upload key

## Remaining manual Play Console work

The missing piece is no longer local signing. The remaining blocker is Play-side visibility of package:

- `com.queukat.sbsgeorgia`

Practical path:

1. Open Play Console.
2. Open the existing app dashboard.
3. Confirm the service account has access to this app in `Users and permissions`.
4. Confirm the first app setup is fully saved in Console UI and the package `com.queukat.sbsgeorgia` is the package intended for first upload.
5. If Play Console still does not expose the package to the Publishing API, perform the first upload manually in Console UI once; after package linkage exists, follow-up edits and releases can be automated through API.

Official references:

- [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en-ZA)
- [User Data / privacy policy requirement](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Target audience and app content](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en)
- [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Preview assets / screenshots / feature graphic](https://support.google.com/googleplay/android-developer/answer/1078870?hl=en)

## Suggested store listing copy

### App name

`Georgia Small Biz Tracker`

### Short description

`Offline-first tax and declaration tracker for Georgian small businesses.`

### Full description

`Georgia Small Biz Tracker is an independent offline-first Android app for Georgian individual entrepreneurs with small business status.

Track monthly income, convert foreign-currency revenue to GEL using official National Bank of Georgia rates, prepare declaration values for the previous month, and keep your tax workflow in one place.

The app is designed to replace fragile spreadsheet-based tracking with a focused local tool for:

- monthly income records
- GEL conversion using official NBG exchange rates
- graph 15 and graph 20 declaration values
- payment comment generation
- filing and payment status tracking
- reminders for monthly declaration and payment deadlines
- local backup and export

This app is independent and is not affiliated with the Revenue Service of Georgia or any government entity.

Your data stays on your device. The first version is single-user and local-only, without banking APIs or automatic submission to rs.ge.`

## Suggested Play settings

- Category: `Finance`
- Ads: `No`
- Target audience: `18+`
- App access: `No login required`
- Monetization: `Free`

## Likely policy declarations

These are practical draft assumptions, not legal advice:

- Data safety: `No data collected`
  - rationale: the app stores user-entered data locally and fetches public FX rates, but does not operate a backend or transmit taxpayer data to the developer
- Ads: `No`
- Account creation: `No`
- Children: `Not designed for children`

Review these carefully before submitting, especially if app behavior changes.

## Assets checklist

Before publishing the store listing, prepare:

- app icon
- at least 2 phone screenshots
- feature graphic `1024 x 500`
- support email
- privacy policy URL on a public non-PDF page

## Automated Play screenshots

A lightweight localized screenshot pipeline is available for Play listing captures:

```powershell
.\scripts\run-play-screenshots.ps1
```

Default locales:

- `en-US`
- `ru-RU`

Output is pulled to:

- `artifacts/play-screenshots/<locale>`

Technical notes:

- screenshots are captured from a dedicated debug-only screenshot activity
- output is deterministic and does not require manual in-app setup
- this is intentionally only a Play screenshot pipeline, not a golden-regression framework

## Privacy policy page

The repository includes a static English privacy policy page suitable for Play Console:

- site source: `play-privacy-site/index.html`
- security headers: `play-privacy-site/_headers`
- deploy script: `scripts/deploy-privacy-policy.ps1`

Suggested Cloudflare Pages project name:

- `sbs-georgia-privacy`

Suggested deploy command:

```powershell
.\scripts\deploy-privacy-policy.ps1
```

## Remaining blockers before upload

1. Ensure the service account can access this specific Play app.
2. Ensure the package `com.queukat.sbsgeorgia` is fully linked on the Play side.
3. Provide a privacy policy URL.
4. Prepare screenshots and feature graphic.
5. Confirm store listing copy and policy declarations.

## Local signing setup

Generated locally on 2026-04-05:

- upload keystore: local ignored file at `signing/play-upload.jks`
- signing properties: local ignored file at `keystore.properties`
- upload key alias: `upload`
- upload key SHA-256: `05:AD:E8:7C:8B:BA:C7:E6:37:D2:14:E7:03:ED:D3:95:3D:1A:38:B4:22:38:91:89:54:DF:BE:03:A1:D6:02:79`

These files are intentionally not committed. Back them up securely, because future Play updates must use the same upload key unless you later rotate it in Play App Signing.

## Local verification commands

```powershell
./gradlew.bat testDebugUnitTest --console=plain
./gradlew.bat assembleDebug --console=plain
./gradlew.bat bundleRelease --console=plain
./gradlew.bat lintDebug --console=plain
```
