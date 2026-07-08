# Local Transcribe — project guide

Pure-native **Android** app for fully on-device speech-to-text. Records audio, transcribes locally
with a bundled **Parakeet-TDT v3** model via **sherpa-onnx**, shows a live waveform + transcript,
and lets you play back / export recordings. **Phone-only** (any Android Auto code was removed on
purpose — it can't be sideloaded onto the target device; don't re-add it).

## Non-negotiable: stays offline
The app declares **no `INTERNET` permission** and `allowBackup="false"`. This is a product
guarantee, not an accident. Never add a networking library, analytics, crash reporting, or the
`INTERNET` permission. Model/AAR come from the build host at build time, never from the app at
runtime.

## Stack / versions (source of truth: `app/build.gradle.kts`, `gradle/wrapper`)
- Kotlin 2.0.21, Jetpack **Compose** (BOM 2024.10.01), AGP **8.7.3**, Gradle **8.11.1**.
- `compileSdk`/`targetSdk` **35**, `minSdk` **26**. App bytecode target **Java 17**.
- **Build JDK: 21** in CI (AGP 8.7 supports it); locally JDK 17 or 21 both work (`gw21`/Corretto).
- ASR: **sherpa-onnx 1.13.3** prebuilt AAR in `app/libs/` (package `com.k2fsa.sherpa.onnx`).
- Model: `csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (`modelType = "nemo_transducer"`,
  encoder/decoder/joiner `.int8.onnx` + `tokens.txt`) + Silero VAD (`silero_vad.onnx`, windowSize 512).

## Large assets are NOT in git
The ~640 MB model and the 54 MB AAR are gitignored. Fetch them once before building:
```bash
./scripts/fetch-assets.sh      # idempotent; skips files already present
```
They live in `app/libs/*.aar` and `app/src/main/assets/models/{parakeet,vad}/`.

## Build
```bash
./gradlew :app:assembleDebug       # debug
./gradlew :app:assembleRelease     # per-ABI: arm64-v8a (recommended), x86_64, universal
```
Release is R8-minified with ABI splits; signed with the debug keystore for personal sideloading.
Output: `app/build/outputs/apk/`.

## Architecture
```
core/
  RecordingController   singleton: orchestrates capture -> engine -> WAV -> persistence; exposes
                        StateFlows (isRecording, isPreparing, elapsedMs, level, waveform,
                        committed, partial, lastCompletedId). Auto-stop on max-duration/low-storage.
  asr/TranscriptionEngine   Parakeet OfflineRecognizer + Silero Vad; decoupled inference coroutine
                            (audio thread only feeds a Channel). Emits committed + live partial text.
  asr/ModelInstaller    extracts bundled model assets to filesDir on first run (mmap-friendly).
  audio/                AudioSource iface; PhoneAudioSource (AudioRecord, VOICE_RECOGNITION pinned to
                        built-in mic); WavWriter (streaming PCM16 WAV).
  session/              RecordingRepository (filesDir/recordings/<id>/{audio.wav,transcript.txt,
                        meta.json}) + RecordingMeta (kotlinx.serialization).
  export/ShareHelper    FileProvider share intents (audio / text / both).
phone/
  MainActivity          Compose NavHost; handles configChanges so rotation never recreates it.
  Screens.kt            RecordingList / ActiveRecording (waveform + live transcript) / Detail
                        (playback + scrubber + share/delete).
  RecordingService      foreground service (mic) hosting capture; notification with Stop action +
                        live status; partial wakelock.
  ui/                   Theme, Waveform canvas, time formatting.
```

## Testing
- Instrumented `TranscriptionEngineTest` transcribes the bundled `test_wavs/en.wav` on device:
  ```bash
  ./gradlew :app:connectedDebugAndroidTest      # needs a running emulator/device
  ```
- On Apple Silicon use an **arm64-v8a** system image (native, and the AAR ships arm64 libs):
  `sdkmanager "system-images;android-35;google_apis;arm64-v8a"` → create AVD → boot headless.
- Sanity-check the offline guarantee on any release APK:
  `apkanalyzer manifest permissions <apk>` → must NOT list `android.permission.INTERNET`.

## Releasing
Use the **`/release`** skill (`.claude/skills/release/`). Short version: bump `versionCode` +
`versionName` in `app/build.gradle.kts`, commit/push `main`, then `git tag vX.Y.Z && git push origin
vX.Y.Z`. CI (`.github/workflows/release.yml`) fetches assets (cached), builds, signs with the
`DEBUG_KEYSTORE_BASE64` secret, and publishes the GitHub release with arm64 + universal APKs.

## Code style
No inline comments. Concise `/** … */` doc comments only where intent is non-obvious.

## Environment notes
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (`local.properties` `sdk.dir`).
- macOS has no `timeout`; use per-tool flags. Emulator boots headless with
  `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`.
