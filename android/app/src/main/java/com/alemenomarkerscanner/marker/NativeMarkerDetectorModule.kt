package com.alemenomarkerscanner.marker

import android.os.SystemClock
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import org.opencv.android.OpenCVLoader
import org.opencv.imgcodecs.Imgcodecs

class NativeMarkerDetectorModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

  private var opencvInitialized = false

  override fun getName(): String = NAME

  @ReactMethod
  fun detectMarker(request: ReadableMap?, promise: Promise) {
    val startedAtNanos = SystemClock.elapsedRealtimeNanos()
    var frameId = 0

    try {
      frameId = readFrameId(request)
      val imageUri = readImageUri(request)

      if (imageUri != null) {
        ensureOpenCV()
        val result = detectFromImage(imageUri, frameId, startedAtNanos)
        promise.resolve(result)
      } else {
        promise.resolve(createNoDetectionResult(frameId, startedAtNanos, null))
      }
    } catch (throwable: Throwable) {
      Log.e(TAG, "detectMarker error: ${throwable.message}", throwable)
      // Always resolve (never reject) so the JS side doesn't crash
      promise.resolve(createNoDetectionResult(frameId, startedAtNanos, throwable.message))
    }
  }

  /**
   * Load an image and run the full detection pipeline:
   * preprocessing → candidate detection → perspective warp → validation → orientation.
   *
   * Performance guards (Part 11):
   * - Limits candidates processed to [MarkerDetectorConfig.MAX_CANDIDATES_TO_PROCESS].
   * - Logs warnings when detection exceeds [MarkerDetectorConfig.SLOW_DETECTION_THRESHOLD_MS].
   * - Each pipeline step is timed individually for profiling.
   */
  private fun detectFromImage(
      imagePath: String,
      frameId: Int,
      startedAtNanos: Long,
  ): WritableMap {
    val image = Imgcodecs.imread(imagePath)
    if (image.empty()) {
      Log.w(TAG, "Could not load image: $imagePath")
      return createNoDetectionResult(frameId, startedAtNanos, "Failed to load image")
    }

    try {
      // --- Step 1: Preprocessing (LLD §5) ---
      val preStart = SystemClock.elapsedRealtimeNanos()
      val preprocessResult = ImagePreprocessor.preprocess(image)
      val preDurationMs = nanosToMs(SystemClock.elapsedRealtimeNanos() - preStart)

      // --- Step 2: Candidate detection (LLD §6) ---
      val candStart = SystemClock.elapsedRealtimeNanos()
      val allCandidates = CandidateDetector.findSquareCandidates(preprocessResult.binary)
      val candDurationMs = nanosToMs(SystemClock.elapsedRealtimeNanos() - candStart)

      // Cap candidates to limit CPU time (Part 11)
      val candidates = allCandidates.take(MarkerDetectorConfig.MAX_CANDIDATES_TO_PROCESS)

      // --- Step 3+4: Warp + validate each candidate ---
      val warpValStart = SystemClock.elapsedRealtimeNanos()
      for (candidate in candidates) {
        val rotations: List<org.opencv.core.Mat>
        try {
          rotations = PerspectiveWarper.warpAllRotations(
              preprocessResult.gray, candidate, preprocessResult.scaleFactor,
          )
        } catch (e: Exception) {
          Log.w(TAG, "Warp failed for candidate: ${e.message}")
          continue
        }

        for ((rotIdx, warped) in rotations.withIndex()) {
          val geometryScore = computeGeometryScore(candidate)
          val validation: Marker1Validator.ValidationResult
          try {
            validation = Marker1Validator.validate(warped, geometryScore)
          } catch (e: Exception) {
            Log.w(TAG, "Validation failed: ${e.message}")
            continue
          }

          if (validation.accepted) {
            // --- Step 5: Orientation correction (LLD §10) ---
            val correction: OrientationCorrector.CorrectionResult
            try {
              correction = OrientationCorrector.correct(
                  warped, validation.orientationCell,
              )
            } catch (e: Exception) {
              Log.w(TAG, "Orientation correction failed: ${e.message}")
              continue
            }

            val warpValDurationMs = nanosToMs(
                SystemClock.elapsedRealtimeNanos() - warpValStart
            )
            val totalMs = elapsedMs(startedAtNanos)

            // Total rotation = warp rotation + orientation correction
            val warpRotation = rotIdx * 90
            val totalRotation = (warpRotation + correction.rotationApplied) % 360

            // Structured performance log
            Log.i(TAG, "DETECTED: frameId=$frameId | " +
                "confidence=${"%.3f".format(validation.confidence)} | " +
                "totalRot=${totalRotation}° | " +
                "verified=${correction.verified} | " +
                "pre=${"%.0f".format(preDurationMs)}ms | " +
                "cand=${"%.0f".format(candDurationMs)}ms (${allCandidates.size} found, ${candidates.size} tried) | " +
                "warpVal=${"%.0f".format(warpValDurationMs)}ms | " +
                "total=${"%.0f".format(totalMs)}ms")

            if (totalMs > MarkerDetectorConfig.SLOW_DETECTION_THRESHOLD_MS) {
              Log.w(TAG, "SLOW DETECTION: ${"%.0f".format(totalMs)}ms " +
                  "(target: ${MarkerDetectorConfig.TARGET_PROCESSING_TIME_MS.toInt()}ms)")
            }

            // Build the positive result — minimal data across bridge
            val cornersArray = Arguments.createArray()
            for (pt in candidate.corners) {
              val point = Arguments.createMap()
              point.putDouble("x", pt.x * preprocessResult.scaleFactor)
              point.putDouble("y", pt.y * preprocessResult.scaleFactor)
              cornersArray.pushMap(point)
            }

            val result = Arguments.createMap()
            result.putBoolean("found", true)
            result.putInt("frameId", frameId)
            result.putDouble("confidence", validation.confidence)
            result.putArray("corners", cornersArray)
            result.putInt("rotationDegrees", totalRotation)
            result.putNull("imageUri") // Will be set when saving
            result.putDouble("processingTimeMs", totalMs)

            // Release all Mats
            correction.corrected.release()
            rotations.forEach { it.release() }
            preprocessResult.binary.release()
            preprocessResult.gray.release()
            image.release()

            return result
          }
        }

        // Release warped Mats for this candidate
        rotations.forEach { it.release() }
      }

      val totalMs = elapsedMs(startedAtNanos)
      Log.i(TAG, "NOT FOUND: frameId=$frameId | " +
          "candidates=${allCandidates.size} (tried ${candidates.size}) | " +
          "pre=${"%.0f".format(preDurationMs)}ms | " +
          "cand=${"%.0f".format(candDurationMs)}ms | " +
          "total=${"%.0f".format(totalMs)}ms")

      // Release OpenCV Mats
      preprocessResult.binary.release()
      preprocessResult.gray.release()
      image.release()

      return createNoDetectionResult(frameId, startedAtNanos, null)
    } catch (throwable: Throwable) {
      image.release()
      throw throwable
    }
  }

  /**
   * Compute a geometry quality score for a candidate based on how square it is.
   * Perfect square = 1.0, threshold edge = 0.5.
   */
  private fun computeGeometryScore(candidate: SquareCandidate): Double {
    val deviation = Math.abs(candidate.aspectRatio - 1.0)
    return (1.0 - deviation * 4.0).coerceIn(0.0, 1.0)
  }

  private fun ensureOpenCV() {
    if (!opencvInitialized) {
      if (!OpenCVLoader.initLocal()) {
        throw RuntimeException("OpenCV initialization failed")
      }
      opencvInitialized = true
      Log.i(TAG, "OpenCV initialized successfully")
    }
  }

  private fun readFrameId(request: ReadableMap?): Int {
    if (request == null || !request.hasKey("frameId") || request.isNull("frameId")) {
      return 0
    }

    return request.getDouble("frameId").toInt()
  }

  private fun readImageUri(request: ReadableMap?): String? {
    if (request == null || !request.hasKey("imageUri") || request.isNull("imageUri")) {
      return null
    }

    return request.getString("imageUri")
  }

  private fun createNoDetectionResult(
      frameId: Int,
      startedAtNanos: Long,
      errorMessage: String?,
  ): WritableMap {
    val elapsed = elapsedMs(startedAtNanos)
    val result = Arguments.createMap()

    result.putBoolean("found", false)
    result.putInt("frameId", frameId)
    result.putDouble("confidence", 0.0)
    result.putArray("corners", createEmptyCorners())
    result.putInt("rotationDegrees", 0)
    result.putNull("imageUri")
    result.putDouble("processingTimeMs", elapsed)

    if (errorMessage != null) {
      result.putString("error", errorMessage)
    }

    return result
  }

  private fun createEmptyCorners(): WritableArray {
    val corners = Arguments.createArray()

    repeat(4) {
      val point = Arguments.createMap()
      point.putDouble("x", 0.0)
      point.putDouble("y", 0.0)
      corners.pushMap(point)
    }

    return corners
  }

  private fun elapsedMs(startedAtNanos: Long): Double {
    return (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000.0
  }

  private fun nanosToMs(nanos: Long): Double {
    return nanos / 1_000_000.0
  }

  companion object {
    const val NAME = "NativeMarkerDetector"
    private const val TAG = "MarkerDetector"
  }
}
