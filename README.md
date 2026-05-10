# Alemeno Marker Scanner

> **Alemeno Frontend Internship Assignment** — Android app that detects a custom visual marker from a live camera, extracts it, corrects its orientation, and displays 20 processed `300×300` marker crops.

---

## 📋 About This Assignment

This project is a submission for the **Alemeno Frontend Internship Assignment**. The task was to build a React Native Android app that:

1. Opens the phone camera and shows a live preview.
2. Detects a custom **Marker 1** — a closed black square with a small black orientation cell inside one corner — in real time.
3. Extracts only the marker region (no padding, no skew).
4. Corrects its orientation so the inner cell is always in the top-left.
5. Collects **20 valid marker captures** from 20 different frames.
6. Shows all 20 processed `300×300` images in a results grid.

The marker looks like a QR code but uses a proprietary design — a closed square border with a tiny black square in one inner corner that acts as an orientation indicator. The app must accept the 3 correct marker test images and reject the 4 incorrect ones provided in the assignment.

---

## 📱 What the App Does

### Screen 1 — Camera Scanner

- Requests camera permission on first launch.
- Shows a full-screen live camera preview.
- Runs a **native Android detection pipeline** (written in Kotlin + OpenCV) on every camera frame.
- Draws a corner overlay around the detected marker in real time.
- Shows a `n/20` progress counter as valid markers are captured.
- Automatically navigates to results once 20 unique markers are collected.

### Screen 2 — Results Grid

- Displays all 20 captured markers in a `4 × 5` grid.
- Every image is exactly `300 × 300 px`, perspective-corrected and orientation-normalized.
- A **Scan Again** button resets everything and returns to the camera.

---

## 🔍 How Detection Works

The detection pipeline runs entirely in native Kotlin and processes each camera frame through these stages:

```
Camera Frame
  → ImagePreprocessor      Grayscale → resize (max 960px side) → Gaussian blur → Otsu threshold → morphological close
  → CandidateDetector      findContours → approxPolyDP → filter by 4 vertices, convexity, area, and aspect ratio
  → PerspectiveWarper      getPerspectiveTransform → warpPerspective → 300×300 output (4 rotations tried)
  → Marker1Validator       Border black-ratio check + inner orientation cell check + interior guard + confidence score
  → OrientationCorrector   Rotate so cell is top-left → re-validate → final 300×300 image
  → Result                 found / confidence / corners / imageUri sent to React Native
```

**Why Marker 1?** Its closed square border gives a strong contour that is easy to detect with OpenCV. The small inner orientation cell at one corner is a simple, deterministic way to rotate every extracted image to the same canonical orientation.

### Confidence Score

A detection is accepted only if its confidence is ≥ 0.75. The score combines:

| Factor | Weight |
|--------|--------|
| Quadrilateral geometry | 25% |
| Outer border black ratio | 25% |
| Inner cell size and position | 35% |
| Extraction quality | 15% |

### What Gets Rejected

- Generic squares without a closed border.
- Shapes with an oversized or centered black block.
- Shapes missing the small orientation cell.
- Shapes where the border is incomplete (open corners, two-sided borders).

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| App framework | React Native 0.85.3 |
| Camera | `react-native-vision-camera` |
| Native image processing | OpenCV 4.9.0 (via Maven, Kotlin) |
| State management | Custom reducer hook |
| Testing | Jest (unit) + Kotlin instrumented tests |
| Language | TypeScript (JS side), Kotlin (native side) |

---

## ⚙️ Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Node.js | ≥ 22.11.0 | `node -v` |
| npm | ≥ 10 | `npm -v` |
| Java JDK | 17 or 21 | `java -version` |
| Android Studio | Latest | With SDK 34+ and build-tools |
| Android device | API 28+ | Physical device recommended for real scanning |

### Environment Setup

```sh
# Windows
set ANDROID_HOME=C:\Users\<you>\AppData\Local\Android\Sdk
set PATH=%PATH%;%ANDROID_HOME%\platform-tools

# macOS / Linux
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

> **Windows note**: Use `npm.cmd` and `npx.cmd` in PowerShell if the `.ps1` scripts are blocked.

---

## 🚀 Quick Start

```sh
# 1. Clone the repo and enter the app folder
cd AlemenoMarkerScanner

# 2. Install JS dependencies
npm install

# 3. Start Metro bundler (Terminal 1)
npm start

# 4. Build and run on Android (Terminal 2)
npm run android
```

The `postinstall` script automatically patches the React Native Gradle plugin's Foojay resolver from `0.5.0` → `1.0.0` (required for Gradle 9.3.1+).

---

## 📦 Build the APK

### Debug APK (easiest for evaluation)

```sh
cd android
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release APK

```sh
cd android
./gradlew assembleRelease
```

Output: `android/app/build/outputs/apk/release/app-release.apk`

> **Note**: Release builds require a signing keystore configured in `android/app/build.gradle`. The debug APK works fine for evaluation.

---

## 📂 Project Structure

```text
AlemenoMarkerScanner/
  App.tsx                       App entry — routes between scanner and results
  src/
    components/
      MarkerGrid.tsx            4×5 grid of 300×300 marker images
      MarkerOverlay.tsx         Draws detected corners on the camera preview
    hooks/
      useMarkerScanner.ts       Capture state (reducer, 20-cap, frame dedup)
      useDetectionLoop.ts       Polling detector with busy guard
    native/
      markerDetector.ts         JS → native bridge with timeout guard
      markerTestCases.ts        7 static test image definitions
    screens/
      CameraScannerScreen.tsx   Camera + detection + overlay + capture
      ResultsScreen.tsx         20-marker grid + scan again
    store/
      markerCaptureStore.ts     Reducer with frameId dedup
    types/
      marker.ts                 MarkerCapture, MarkerDetectionResult, Point

  android/app/src/main/java/com/alemenomarkerscanner/marker/
    NativeMarkerDetectorModule.kt   React Native bridge
    MarkerDetectorConfig.kt         All tunable thresholds in one place
    ImagePreprocessor.kt            Grayscale → resize → blur → Otsu → morph
    CandidateDetector.kt            Contour → polygon approx → quad filter
    SquareCandidate.kt              Data class (corners, area, aspect)
    PerspectiveWarper.kt            Warp to 300×300, tries 4 rotations
    Marker1Validator.kt             Border + inner cell + guard + confidence
    OrientationCorrector.kt         Rotate so cell is top-left + re-validate

  __tests__/                    Jest unit and integration tests (42 tests)
  android/app/src/androidTest/  Kotlin instrumented tests
```

---

## 🧪 Test Images

The assignment provided 7 test images for Marker 1:

| Image | Expected |
|-------|----------|
| `Marker1-TestImage1-Correct.jpg` | ✅ Accept |
| `Marker1-TestImage2-Correct.jpg` | ✅ Accept |
| `Marker1-TestImage3-Correct.jpg` | ✅ Accept |
| `Marker1-TestImage4-Incorrect.jpg` | ❌ Reject |
| `Marker1-TestImage5-Incorrect.jpg` | ❌ Reject |
| `Marker1-TestImage6-Incorrect.jpg` | ❌ Reject |
| `Marker1-TestImage7-Incorrect.jpg` | ❌ Reject |

Test images live in `android/app/src/main/assets/marker_test_images/` and are also run by the Jest harness.

---

## ✅ Verification

```sh
# TypeScript type-check
npx tsc --noEmit

# Jest tests (42 tests across 5 suites)
npm test

# ESLint
npm run lint

# Android instrumented tests (requires connected device/emulator)
cd android && ./gradlew :app:connectedAndroidTest
```

---


