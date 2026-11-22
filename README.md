<div align="center">
  <img src="docs/assets/chroniq-logo.png" width="300" alt="Chroniq" />
</div>

# Chroniq

Date-specific precision alarm app with multi-date scheduling, full-screen lock-screen ringing, and automatic Google Calendar synchronization.

[![Build APK](https://github.com/fadheelahmadalfaiz/chroniq-app/actions/workflows/build-apk.yml/badge.svg)](https://github.com/fadheelahmadalfaiz/chroniq-app/actions/workflows/build-apk.yml)

## Download the app

During development the version number stays fixed. Every push to `main` overwrites the same release, so this link always serves the newest build:

**https://github.com/fadheelahmadalfaiz/chroniq-app/releases/download/latest/Chroniq-latest.apk**

Tagged releases are kept separately: [all releases](https://github.com/fadheelahmadalfaiz/chroniq-app/releases).

Install steps:

1. Download the APK on your phone.
2. Allow "Install unknown apps" for your browser or file manager when prompted.
3. Open the APK and confirm the install. It installs over the previous build, so alarms stay intact.

Android 7.0 (API 24) or newer is required. See [docs/build-and-release.md](docs/build-and-release.md) for how the APK is built and signed.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
