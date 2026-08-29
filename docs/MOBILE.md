# 薪流 Mobile

薪流 keeps the existing React/Vite application as the single UI/codebase and uses Capacitor as the native shell.

## App identity

- App name: `薪流`
- App ID / Android application ID: `com.salaryflow.app`
- Web bundle: `apps/web/dist`
- Capacitor: `8.5.0` (pinned by CI)

## Android APK

The repository contains `.github/workflows/build-android-apk.yml`.

It builds the current web application, creates a Capacitor Android project on the CI runner, synchronizes the web assets, builds an installable APK, verifies its signature, and generates a SHA-256 checksum. The APK has no in-app update checker or remote APK installer.

### Pull requests and manual builds

PR and manual workflow runs upload an Actions artifact named:

- `salaryflow-android-apk`

The artifact contains:

- `salaryflow-android.apk`
- `salaryflow-android.apk.sha256`

PR builds use a throwaway CI signing key and compile both debug and release variants; they are not the long-term Android release identity.

### Permanent release signing

Tagged production APKs must always use the same release keystore. The keystore is never committed to Git and is restored only inside GitHub Actions from repository secrets.

Required Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

If any secret is missing, a `v*` tag build fails before publishing an APK. This is deliberate: publishing one release with a different key would break future in-place upgrades.

Back up the release keystore securely. If it is lost, a future APK cannot update existing installations signed with that key.

### Versioning

Production tags must use `vMAJOR.MINOR.PATCH`, for example `v0.2.0`.

The workflow derives:

- Android `versionName` = `0.2.0`
- Android `versionCode` = `major * 1,000,000 + minor * 1,000 + patch`

This keeps Android's numeric upgrade ordering stable while the UI can compare normal semantic versions.

### Optional manual releases

If you connect the project to GitHub, pushing a tag matching `v*` can create a GitHub Release containing the APK and checksum. This is a manual distribution archive only; the installed app does not contact GitHub or any other release endpoint to discover updates.

For example:

```bash
git tag v0.3.0
git push origin v0.3.0
```

### First migration from old debug APKs

Previously generated 薪流 APKs used ephemeral debug signing. Those APKs do **not** share the permanent release certificate.

Therefore, users who already installed an old debug APK need to uninstall it once before installing the first fixed-signed release. From that first fixed-signed release onward, newer APKs can update in place without uninstalling, as long as the permanent keystore is preserved.

Because uninstalling removes Android WebView local app data, export/backup should be considered before a broad migration if real user data already exists.

### Local Android development

Requirements: Node.js 22+, JDK 21, Android SDK / Android Studio.

```bash
npm ci
npm run build -w @salary-flow/core
npm run build -w @salary-flow/web
npm install --no-save --package-lock=false @capacitor/core@8.5.0 @capacitor/android@8.5.0 @capacitor/cli@8.5.0
cd apps/web
npx cap add android
npx cap sync android
node native/android/prepare-android.mjs
npx cap open android
```

The generated `android/` directory is intentionally reproducible from source and does not need to be committed for CI APK builds.

## iOS without a paid Apple Developer account

The web app is installable as a PWA from Safari through **Add to Home Screen**. It includes standalone display metadata, viewport safe-area support, an app manifest, icon, and an offline service worker.

Normal UI and feature deployments do not require users to add the PWA to the home screen again. The current service worker uses a network-first strategy, so reopening the installed PWA fetches the current deployed app when the network is available.

A Capacitor iOS project can also be generated later on macOS with Xcode using the same `capacitor.config.json`. A free Apple ID can be used for limited Personal Team device testing; App Store / TestFlight distribution is intentionally out of scope until a paid Apple Developer membership exists.

## Data behavior

The mobile shell currently preserves the existing local-first model. Salary profile, wishes, slacking sessions, assets, and unexpected-money records remain device-local. The ledger/accounting page is intentionally out of product scope. Installing the native APK does not automatically import localStorage data from a hosted Web version because the native WebView has its own storage container.

## Native overrides template (desktop widgets)

`apps/web/native/android/template/` holds the full native override tree that
must survive every `cap add android` regeneration:

- `app/src/main/java/com/salaryflow/app/widget/` — home-screen widget
  engine (2×2 / 4×2 / 4×3, per-second live ticker, salary math port, bridge)
- `app/src/main/AndroidManifest.xml` — receivers, ticker service, permissions
- `app/src/main/res/` — widget layouts, drawables, colors, strings
- `MainActivity.java` — registers the `WidgetSync` bridge plugin

`native/android/prepare-android.mjs` copies this template over the generated
project, then applies `versionName` / `versionCode` and the release signing
config. When changing widget native code, edit the files under
`apps/web/android/` for local iteration **and** mirror them into
`template/` (or copy back) so the next CI tag build keeps the widgets.
See `docs/WIDGETS.md` for the widget architecture.

## Local release build

```bash
npm run build
cd apps/web && npx cap copy android && cd ../..
ANDROID_VERSION_NAME=0.1.0 ANDROID_VERSION_CODE=1000000 \
  ANDROID_RELEASE_SIGNING=true node apps/web/native/android/prepare-android.mjs
cd apps/web/android && ./gradlew assembleRelease
```

Release signing reads `apps/web/android/keystore.properties` (never
committed) pointing at `apps/web/android/app/salaryflow-release.jks`.
**The keystore is the permanent Android identity of this app** — losing it
makes every future update uninstall-only. Back up together:

- `apps/web/android/app/salaryflow-release.jks`
- `apps/web/android/keystore.properties` (store/key password + alias)
- Certificate SHA-256: `abbe3d26f36b5a8e480a8710424c84562da5f8ae8b4576be18d5d8526885cefa`

For the tag-driven GitHub Release, upload the `.jks` as base64 into the four
`ANDROID_KEYSTORE_*` repository secrets described above.
