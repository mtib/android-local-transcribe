# Local Transcribe

A pure-native Android app for **fully on-device** speech-to-text. Record a session, get a live
transcript as you speak, and export the audio and/or text — with **no network access whatsoever**.
The Parakeet-TDT v3 model is bundled inside the APK and runs locally through
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). There is no cloud, no account, no telemetry,
and the app does not even hold the `INTERNET` permission.

It also works on **Android Auto**: a live voice-activity waveform and scrolling transcript on the
car screen, with a single Start/Stop control.

## Features

- **Minimal phone UI**: one "New recording" button and a list of past recordings.
- **Live transcription** while recording (waveform + scrolling transcript), on phone and in the car.
- **Multilingual** transcription (Parakeet-TDT v3 covers 25 European languages; it transcribes the
  spoken language as-is — no translation).
- **Export / share** a recording's audio (WAV), text, or both, via the Android share sheet.
- **Android Auto** surface with the same live waveform + transcript; no history browsing in the car.
- **100% offline & private**: models bundled, no `INTERNET` permission, `allowBackup="false"`.

## How it works

| Concern | Implementation |
|---|---|
| ASR model | Parakeet-TDT-0.6b-v3 INT8 (`nemo_transducer`), bundled in `assets/models/parakeet/` |
| Segmentation | Silero VAD (`assets/models/vad/silero_vad.onnx`) drives live segment boundaries |
| Runtime | `sherpa-onnx` 1.13.3 (`OfflineRecognizer` + `Vad`) via the prebuilt AAR in `app/libs/` |
| Capture | Phone: `AudioRecord` in a foreground service. Car: `CarAudioRecord`. Both 16 kHz mono PCM16 |
| Storage | `filesDir/recordings/<id>/{audio.wav, transcript.txt, meta.json}` |
| Shared core | `RecordingController` + `TranscriptionEngine` drive both the phone and the car UI |

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

APKs land in `app/build/outputs/apk/`.

## Install on a phone

1. Download the APK to the device and open it; allow installing from unknown sources.
2. Launch **Local Transcribe**, grant the microphone permission, tap **New recording**.
   (The first recording pauses briefly on "Loading model…" while the model is prepared.)

## Enable in Android Auto (sideloaded)

Sideloaded car apps require developer mode in Android Auto:

1. Phone → **Android Auto** settings → tap the *Version* repeatedly to unlock **Developer settings**.
2. In Developer settings, enable **Unknown sources**.
3. Connect to the car (or the [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu)
   for testing). **Local Transcribe** appears in the Auto launcher.
4. Tap **Start** to begin transcribing; the waveform and live transcript render on the car screen.

> The car app registers under the navigation category purely to obtain a drawing surface for the
> waveform — it is not a navigation app.

## Privacy

Audio and transcripts never leave the device. The app declares no `INTERNET` permission, so network
egress is impossible at the OS level, and `allowBackup` is disabled so transcripts are not swept into
cloud backups. Permissions used: `RECORD_AUDIO`, foreground-service (microphone), notifications, and
`androidx.car.app.ACCESS_SURFACE` for the car drawing surface.
