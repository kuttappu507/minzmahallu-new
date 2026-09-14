# Building the Android APK

## Option A — GitHub Actions (recommended)

1. Copy `docs/github-workflow-build-apk.yml` to `.github/workflows/build-apk.yml`
2. Push to GitHub (needs `workflows` permission on the token / a personal push)
3. Open **Actions → Build Android APK** and download the artifact

Or run manually:

```bash
gh workflow run build-apk.yml
gh run download -n minz-mahallu-debug-apk
```

## Option B — Local

```bash
# Requires JDK 17 + Android SDK
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release-unsigned.apk
```

Sign release:

```bash
keytool -genkey -v -keystore minz.keystore -alias minz -keyalg RSA -keysize 2048 -validity 10000
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore minz.keystore app/build/outputs/apk/release/app-release-unsigned.apk minz
```
