# Local Transcribe

A pure-native Android app for **fully on-device** speech-to-text. Record a session, get a live
transcript as you speak, and export the audio and/or text — with **no network access whatsoever**.
The Parakeet-TDT v3 model is bundled inside the APK and runs locally through
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). There is no cloud, no account, no telemetry,
and the app does not even hold the `INTERNET` permission.

## Features

- **Minimal UI**: one "New recording" button and a list of past recordings.
- **Live transcription** while recording (voice-activity waveform + scrolling transcript).
- **Multilingual** transcription (Parakeet-TDT v3 covers 25 European languages; it transcribes the
  spoken language as-is — no translation).
- **Playback with a scrubber**: play a recording, drag the playhead to seek.
- **Export / share** a recording's audio (WAV), text, or both, via the Android share sheet.
- **Recording keeps running in the background**: a foreground service with a Stop action and live
  status in the notification, so a recording survives the screen turning off or another app taking
  over (e.g. while the phone is projecting to a car).
- **100% offline & private**: models bundled, no `INTERNET` permission, `allowBackup="false"`.

## How it works

| Concern | Implementation |
|---|---|
| ASR model | Parakeet-TDT-0.6b-v3 INT8 (`nemo_transducer`), bundled in `assets/models/parakeet/` |
| Segmentation | Silero VAD (`assets/models/vad/silero_vad.onnx`) drives live segment boundaries |
| Runtime | `sherpa-onnx` 1.13.3 (`OfflineRecognizer` + `Vad`) via the prebuilt AAR in `app/libs/` |
| Capture | `AudioRecord` (`VOICE_RECOGNITION`, pinned to the built-in mic) in a foreground service, 16 kHz mono PCM16 |
| Storage | `filesDir/recordings/<id>/{audio.wav, transcript.txt, meta.json}` |
| Core | `RecordingController` + `TranscriptionEngine` drive capture, transcription, and persistence |

On first launch the ~640 MB model is extracted once from the APK to internal storage so onnxruntime
can memory-map it. Expect ~1.4 GB of on-device storage (APK + extracted model).

## Build

Requirements: JDK 17, Android SDK (compileSdk 35). The Parakeet model (~640 MB) and the sherpa-onnx
AAR are **not** committed to git — fetch them once after cloning:

```bash
./scripts/fetch-assets.sh         # downloads the AAR + Parakeet + Silero VAD into place
```

Then build:

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (signed with the debug keystore for sideloading)
```

APKs land in `app/build/outputs/apk/`. The `arm64-v8a` release APK is the recommended one for phones.

### Releases (CI)

Pushing a `v*` tag runs `.github/workflows/release.yml`, which fetches the assets, builds the
release APKs, and publishes a GitHub release with the arm64 and universal APKs attached:

```bash
git tag v1.0.5 && git push origin v1.0.5
```

To keep CI-built releases installable as in-place updates (same signing key as local builds), set a
`DEBUG_KEYSTORE_BASE64` repo secret to the base64 of your `~/.android/debug.keystore`. Without it, CI
generates a fresh debug key and a new install is required.

## Install

1. Download the APK to the device and open it; allow installing from unknown sources.
2. Launch **Local Transcribe**, grant the microphone permission, tap **New recording**.
   (The first recording pauses briefly on "Loading model…" while the model is prepared.)

While recording, you can leave the app or turn the screen off — capture continues in a foreground
service. Pull down the notification to see the elapsed time and live transcript, and to Stop.

## Privacy

Audio and transcripts never leave the device. The app declares no `INTERNET` permission, so network
egress is impossible at the OS level, and `allowBackup` is disabled so transcripts are not swept into
cloud backups. Permissions used: `RECORD_AUDIO`, foreground-service (microphone), notifications, and
`WAKE_LOCK` (to keep capturing reliably while the screen is off).
