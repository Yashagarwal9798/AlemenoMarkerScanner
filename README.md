# Alemeno Marker Scanner

React Native Android app for the Alemeno frontend internship assignment: detect a custom visual marker from the live camera, extract it, correct orientation, and display 20 processed `300×300` marker crops.

## Quick Start

```sh
# 1. Clone and enter the app directory
cd AlemenoMarkerScanner

# 2. Install dependencies
npm install

# 3. Start Metro bundler
npm start

# 4. Build and run on Android (separate terminal)
npm run android
```

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Node.js | ≥ 22.11.0 | `node -v` to check |
| npm | ≥ 10 | Bundled with Node |
| Java JDK | 17 or 21 | `java -version` |
| Android Studio | Latest | With SDK 34+ and build-tools |
| Android device/emulator | API 28+ | Camera requires a physical device for real scanning |

### Environment Variables

```sh
# Set ANDROID_HOME to your SDK path
export ANDROID_HOME=$HOME/Library/Android/sdk   # macOS
export ANDROID_HOME=$HOME/Android/Sdk           # Linux
set ANDROID_HOME=C:\Users\<you>\AppData\Local\Android\Sdk  # Windows

# Add platform-tools to PATH
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### Windows-Specific Notes

- Use `npm.cmd` instead of `npm` if PowerShell blocks `npm.ps1`.
- Use `npx.cmd` instead of `npx` for the same reason.

## Install

```sh
npm install
```

The `postinstall` script patches the React Native Gradle plugin's Foojay resolver from `0.5.0` to `1.0.0`. This is required because Gradle 9.3.1 removed a vendor constant that Foojay `0.5.0` depends on.

### Key Dependencies

| Package | Purpose |
|---------|---------|
| `react-native` 0.85.3 | App framework |
| `react-native-vision-camera` | Camera preview and frame access |
| `react-native-safe-area-context` | Safe area insets |
| OpenCV 4.9.0 (Maven) | Native image processing |

## Run

### Development

```sh
# Terminal 1: Metro bundler
npm start

# Terminal 2: Build and run
npm run android
```

### Build Release APK

```sh
cd android
./gradlew assembleRelease
```

The APK will be at: `android/app/build/outputs/apk/release/app-release.apk`

> **Note**: You may need to configure signing in `android/app/build.gradle` for a signed release build. For evaluation, an unsigned debug APK also works:
> ```sh
> cd android
> ./gradlew assembleDebug
> ```
> Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Verification

```sh
# TypeScript type-check
npx tsc --noEmit

# Jest tests (42 tests, 5 suites)
npm test

# ESLint
npm run lint

# Android instrumented tests (requires device/emulator)
cd android && ./gradlew :app:connectedAndroidTest
```

## Project Structure

```text
AlemenoMarkerScanner/
  App.tsx                 App entry — routes between scanner and results
  src/
    components/
      MarkerGrid.tsx      4×5 grid of 300×300 marker images
      MarkerOverlay.tsx   Draws detected corners on camera preview
    hooks/
      useMarkerScanner.ts Capture state (reducer, 20-cap, dedup)
      useDetectionLoop.ts Polling detector with busy guard
    native/
      markerDetector.ts   JS→native bridge with timeout guard
      markerTestCases.ts  7 static test image definitions
    screens/
      CameraScannerScreen.tsx  Camera + detection + overlay + capture
      ResultsScreen.tsx        20-marker grid + scan again
    store/
      markerCaptureStore.ts    Reducer with frameId dedup
    types/
      marker.ts               MarkerCapture, MarkerDetectionResult, Point

  android/app/src/main/java/com/alemenomarkerscanner/marker/
    NativeMarkerDetectorModule.kt   React Native bridge module
    MarkerDetectorConfig.kt         All tunable thresholds
    ImagePreprocessor.kt            Grayscale → resize → blur → Otsu → morph
    CandidateDetector.kt            Contour → polygon approx → quad filter
    SquareCandidate.kt              Data class (corners, area, aspect)
    PerspectiveWarper.kt            Warp to 300×300 + 4-rotation variants
    Marker1Validator.kt             Border + inner cell + guard + confidence
    OrientationCorrector.kt         Rotate so cell is top-left + verify

  android/app/src/androidTest/      Instrumented tests
  __tests__/                        Jest unit and integration tests
```

## Detection Pipeline

See [`docs/approach.md`](../docs/approach.md) for a detailed explanation.

```text
Camera Frame
  → ImagePreprocessor      Gray → resize → blur → Otsu → invert → morph
  → CandidateDetector      Contours → polygon approx → 4-vertex quads
  → PerspectiveWarper      4 rotations × 300×300 grayscale
  → Marker1Validator       Border check + inner cell + interior guard
  → OrientationCorrector   Rotate cell to top-left + re-validate
  → Result                 found=true/false + confidence + corners
```

## Test Images

The `Alemeno Frontend Assignment Marker Images/` directory (one level up) contains:

| Image | Expected |
|-------|----------|
| Marker1-TestImage1-Correct.jpg | Accept ✓ |
| Marker1-TestImage2-Correct.jpg | Accept ✓ |
| Marker1-TestImage3-Correct.jpg | Accept ✓ |
| Marker1-TestImage4-Incorrect.jpg | Reject ✗ |
| Marker1-TestImage5-Incorrect.jpg | Reject ✗ |
| Marker1-TestImage6-Incorrect.jpg | Reject ✗ |
| Marker1-TestImage7-Incorrect.jpg | Reject ✗ |

## Documentation

| File | Contents |
|------|----------|
| [`docs/PRD.md`](../docs/PRD.md) | Product requirements |
| [`docs/HLD.md`](../docs/HLD.md) | High-level architecture |
| [`docs/LLD.md`](../docs/LLD.md) | Low-level design (algorithms) |
| [`docs/approach.md`](../docs/approach.md) | Approach document (for submission) |
| `part-00.md` … `part-12.md` | Per-part implementation checkpoints |
