package com.alemenomarkerscanner.marker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * Android instrumented test for Marker 1 detection pipeline.
 *
 * Tests Part 5 (file discovery), Part 6 (preprocessing + candidate detection),
 * and Part 7 (perspective warp).
 *
 * Run with:
 *   ./gradlew :app:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MarkerDetectorInstrumentedTest {

    private lateinit var appContext: Context

    data class TestCase(
        val id: String,
        val fileName: String,
        val expectedAccepted: Boolean,
        val reason: String,
    )

    companion object {
        private const val TAG = "MarkerDetectorTest"
        private const val ASSET_DIR = "marker_test_images"
        private const val TEST_IMAGE_SIZE = 500
        private const val OUTPUT_SIZE = 300

        val TEST_CASES = listOf(
            TestCase("correct-1", "Marker1-TestImage1-Correct.jpg", true,
                "Valid Marker 1 with complete border and correctly placed orientation cell."),
            TestCase("correct-2", "Marker1-TestImage2-Correct.jpg", true,
                "Valid Marker 1 with complete border and correctly placed orientation cell."),
            TestCase("correct-3", "Marker1-TestImage3-Correct.jpg", true,
                "Valid Marker 1 with complete border and correctly placed orientation cell."),
            TestCase("incorrect-4", "Marker1-TestImage4-Incorrect.jpg", false,
                "Incorrect marker — does not match valid Marker 1 pattern."),
            TestCase("incorrect-5", "Marker1-TestImage5-Incorrect.jpg", false,
                "Incorrect marker — does not match valid Marker 1 pattern."),
            TestCase("incorrect-6", "Marker1-TestImage6-Incorrect.jpg", false,
                "Incorrect marker — does not match valid Marker 1 pattern."),
            TestCase("incorrect-7", "Marker1-TestImage7-Incorrect.jpg", false,
                "Incorrect marker — does not match valid Marker 1 pattern."),
        )

        @JvmStatic
        @BeforeClass
        fun initOpenCV() {
            assertTrue("OpenCV must initialize", OpenCVLoader.initLocal())
            Log.i(TAG, "OpenCV initialized for instrumented tests.")
        }
    }

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    // --- Part 5: File Discovery Tests ---

    @Test
    fun allTestImagesExistInAssets() {
        val assetFiles = appContext.assets.list(ASSET_DIR)
        assertNotNull("Asset directory '$ASSET_DIR' should exist", assetFiles)

        for (testCase in TEST_CASES) {
            assertTrue(
                "Asset file '${testCase.fileName}' should exist in '$ASSET_DIR'",
                assetFiles!!.contains(testCase.fileName),
            )
        }
        Log.i(TAG, "All ${TEST_CASES.size} test image assets found.")
    }

    @Test
    fun allTestImagesLoadAsBitmaps() {
        for (testCase in TEST_CASES) {
            val bitmap = loadTestBitmap(testCase)
            assertNotNull("Bitmap should decode for '${testCase.id}'", bitmap)
            Log.i(TAG, "[${testCase.id}] Loaded: ${bitmap!!.width}x${bitmap.height}")
        }
    }

    @Test
    fun testImageDimensionsAre500x500() {
        for (testCase in TEST_CASES) {
            val bitmap = loadTestBitmap(testCase)
            assertNotNull(bitmap)
            assertEquals("${testCase.id}: width", TEST_IMAGE_SIZE, bitmap!!.width)
            assertEquals("${testCase.id}: height", TEST_IMAGE_SIZE, bitmap.height)
        }
        Log.i(TAG, "All test images are ${TEST_IMAGE_SIZE}x${TEST_IMAGE_SIZE}.")
    }

    // --- Part 6: Preprocessing + Candidate Detection Tests ---

    @Test
    fun preprocessingProducesBinaryImage() {
        for (testCase in TEST_CASES) {
            val mat = loadTestMat(testCase)
            assertNotNull("Mat should load for '${testCase.id}'", mat)

            val result = ImagePreprocessor.preprocess(mat!!)

            assertTrue("Binary image should not be empty", !result.binary.empty())
            assertTrue("Gray image should not be empty", !result.gray.empty())
            assertEquals("Binary should be single-channel", 1, result.binary.channels())
            assertEquals("Gray should be single-channel", 1, result.gray.channels())
            assertTrue("Scale factor should be >= 1.0", result.scaleFactor >= 1.0)

            Log.i(TAG, "[${testCase.id}] Preprocessed: " +
                "binarySize=${result.binary.cols()}x${result.binary.rows()} | " +
                "scaleFactor=${"%.2f".format(result.scaleFactor)}")

            result.binary.release()
            result.gray.release()
            mat.release()
        }
    }

    @Test
    fun correctImagesProduceAtLeastOneSquareCandidate() {
        val correctCases = TEST_CASES.filter { it.expectedAccepted }

        for (testCase in correctCases) {
            val mat = loadTestMat(testCase)!!
            val result = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(result.binary)

            Log.i(TAG, "[${testCase.id}] Candidates found: ${candidates.size}")

            for ((i, c) in candidates.withIndex()) {
                Log.i(TAG, "  Candidate $i: area=${"%.0f".format(c.area)} " +
                    "aspect=${"%.3f".format(c.aspectRatio)} " +
                    "areaRatio=${"%.4f".format(c.areaRatio)}")
            }

            assertTrue(
                "${testCase.id} should produce at least 1 candidate, got ${candidates.size}",
                candidates.isNotEmpty(),
            )

            result.binary.release()
            result.gray.release()
            mat.release()
        }
    }

    @Test
    fun candidateDetectionLogsTimingForAllImages() {
        Log.i(TAG, "--- Part 6: Candidate Detection Timing ---")

        for (testCase in TEST_CASES) {
            val mat = loadTestMat(testCase)!!
            val startNanos = System.nanoTime()

            val result = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(result.binary)

            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
            val status = if (testCase.expectedAccepted) "CORRECT" else "INCORRECT"

            Log.i(TAG, "[$status] ${testCase.id}: " +
                "candidates=${candidates.size} | " +
                "time=${"%.1f".format(elapsedMs)}ms | " +
                "file=${testCase.fileName}")

            result.binary.release()
            result.gray.release()
            mat.release()
        }

        Log.i(TAG, "--- End Timing ---")
    }

    // --- Part 7: Perspective Warp Tests ---

    @Test
    fun correctImagesWarpTo300x300() {
        val correctCases = TEST_CASES.filter { it.expectedAccepted }

        for (testCase in correctCases) {
            val mat = loadTestMat(testCase)!!
            val preResult = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

            assertTrue("${testCase.id} needs candidates to warp", candidates.isNotEmpty())

            val warped = PerspectiveWarper.warp(
                preResult.gray, candidates[0], preResult.scaleFactor,
            )

            assertNotNull("${testCase.id}: warped image should not be null", warped)
            assertEquals("${testCase.id}: warped width", OUTPUT_SIZE, warped!!.cols())
            assertEquals("${testCase.id}: warped height", OUTPUT_SIZE, warped.rows())
            assertEquals("${testCase.id}: warped channels", 1, warped.channels())

            Log.i(TAG, "[${testCase.id}] Warped: ${warped.cols()}x${warped.rows()} ✓")

            warped.release()
            preResult.binary.release()
            preResult.gray.release()
            mat.release()
        }
    }

    @Test
    fun warpAllRotationsReturns4Variants() {
        val testCase = TEST_CASES.first { it.expectedAccepted }
        val mat = loadTestMat(testCase)!!
        val preResult = ImagePreprocessor.preprocess(mat)
        val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

        assertTrue("Need at least one candidate", candidates.isNotEmpty())

        val rotations = PerspectiveWarper.warpAllRotations(
            preResult.gray, candidates[0], preResult.scaleFactor,
        )

        assertEquals("Should produce 4 rotation variants", 4, rotations.size)

        for ((i, r) in rotations.withIndex()) {
            assertEquals("Rotation $i width", OUTPUT_SIZE, r.cols())
            assertEquals("Rotation $i height", OUTPUT_SIZE, r.rows())
            Log.i(TAG, "[${testCase.id}] Rotation $i: ${r.cols()}x${r.rows()} ✓")
            r.release()
        }

        preResult.binary.release()
        preResult.gray.release()
        mat.release()
    }

    // --- Part 8: Marker 1 Validation Tests ---

    @Test
    fun correctImagesAreAcceptedByValidator() {
        val correctCases = TEST_CASES.filter { it.expectedAccepted }

        for (testCase in correctCases) {
            val mat = loadTestMat(testCase)!!
            val preResult = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

            assertTrue("${testCase.id} needs candidates", candidates.isNotEmpty())

            var accepted = false
            for (candidate in candidates) {
                val rotations = PerspectiveWarper.warpAllRotations(
                    preResult.gray, candidate, preResult.scaleFactor,
                )
                for ((rotIdx, warped) in rotations.withIndex()) {
                    val result = Marker1Validator.validate(warped)
                    if (result.accepted) {
                        accepted = true
                        Log.i(TAG, "[${testCase.id}] ACCEPTED: " +
                            "confidence=${"%.3f".format(result.confidence)} " +
                            "rotation=${rotIdx * 90}° " +
                            "cell=${result.orientationCell}")
                    }
                }
                rotations.forEach { it.release() }
                if (accepted) break
            }

            assertTrue("${testCase.id} should be ACCEPTED", accepted)

            preResult.binary.release()
            preResult.gray.release()
            mat.release()
        }
    }

    @Test
    fun incorrectImagesAreRejectedByValidator() {
        val incorrectCases = TEST_CASES.filter { !it.expectedAccepted }

        for (testCase in incorrectCases) {
            val mat = loadTestMat(testCase)!!
            val preResult = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

            var accepted = false
            for (candidate in candidates) {
                val rotations = PerspectiveWarper.warpAllRotations(
                    preResult.gray, candidate, preResult.scaleFactor,
                )
                for (warped in rotations) {
                    val result = Marker1Validator.validate(warped)
                    if (result.accepted) {
                        accepted = true
                        Log.w(TAG, "[${testCase.id}] FALSE POSITIVE: " +
                            "confidence=${"%.3f".format(result.confidence)}")
                    }
                }
                rotations.forEach { it.release() }
            }

            assertTrue("${testCase.id} should be REJECTED (got accepted=$accepted)", !accepted)

            preResult.binary.release()
            preResult.gray.release()
            mat.release()
        }
    }

    @Test
    fun fullPipelineTimingForAllImages() {
        Log.i(TAG, "--- Part 8: Full Pipeline Timing ---")

        for (testCase in TEST_CASES) {
            val mat = loadTestMat(testCase)!!
            val startNanos = System.nanoTime()

            val preResult = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

            var foundResult: Marker1Validator.ValidationResult? = null
            var foundRotation = -1

            for (candidate in candidates) {
                val rotations = PerspectiveWarper.warpAllRotations(
                    preResult.gray, candidate, preResult.scaleFactor,
                )
                for ((rotIdx, warped) in rotations.withIndex()) {
                    val result = Marker1Validator.validate(warped)
                    if (result.accepted && foundResult == null) {
                        foundResult = result
                        foundRotation = rotIdx * 90
                    }
                }
                rotations.forEach { it.release() }
            }

            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
            val status = if (testCase.expectedAccepted) "CORRECT" else "INCORRECT"
            val detected = foundResult?.let {
                "ACCEPTED conf=${"%.3f".format(it.confidence)} rot=${foundRotation}°"
            } ?: "REJECTED"

            Log.i(TAG, "[$status] ${testCase.id}: $detected | " +
                "candidates=${candidates.size} | " +
                "time=${"%.1f".format(elapsedMs)}ms")

            preResult.binary.release()
            preResult.gray.release()
            mat.release()
        }

        Log.i(TAG, "--- End Full Pipeline Timing ---")
    }

    // --- Part 9: Orientation Correction Tests ---

    @Test
    fun correctedImagesAre300x300WithCellTopLeft() {
        val correctCases = TEST_CASES.filter { it.expectedAccepted }

        for (testCase in correctCases) {
            val mat = loadTestMat(testCase)!!
            val preResult = ImagePreprocessor.preprocess(mat)
            val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

            assertTrue("${testCase.id} needs candidates", candidates.isNotEmpty())

            var corrected = false
            for (candidate in candidates) {
                val rotations = PerspectiveWarper.warpAllRotations(
                    preResult.gray, candidate, preResult.scaleFactor,
                )
                for ((rotIdx, warped) in rotations.withIndex()) {
                    val validation = Marker1Validator.validate(warped)
                    if (validation.accepted) {
                        val correction = OrientationCorrector.correct(
                            warped, validation.orientationCell,
                        )

                        assertEquals("${testCase.id}: corrected width",
                            OUTPUT_SIZE, correction.corrected.cols())
                        assertEquals("${testCase.id}: corrected height",
                            OUTPUT_SIZE, correction.corrected.rows())
                        assertTrue("${testCase.id}: cell should be verified top-left",
                            correction.verified)

                        val totalRot = (rotIdx * 90 + correction.rotationApplied) % 360
                        Log.i(TAG, "[${testCase.id}] Corrected: " +
                            "warpRot=${rotIdx * 90}° " +
                            "corrRot=${correction.rotationApplied}° " +
                            "totalRot=${totalRot}° " +
                            "verified=${correction.verified} ✓")

                        correction.corrected.release()
                        corrected = true
                    }
                }
                rotations.forEach { it.release() }
                if (corrected) break
            }

            assertTrue("${testCase.id} should have a corrected result", corrected)

            preResult.binary.release()
            preResult.gray.release()
            mat.release()
        }
    }

    @Test
    fun allFourOrientationCellPositionsMapCorrectly() {
        // Verify the rotation mapping: 0→0°, 1→270°, 2→180°, 3→90°
        val testCase = TEST_CASES.first { it.expectedAccepted }
        val mat = loadTestMat(testCase)!!
        val preResult = ImagePreprocessor.preprocess(mat)
        val candidates = CandidateDetector.findSquareCandidates(preResult.binary)

        assertTrue("Need at least one candidate", candidates.isNotEmpty())

        val warped = PerspectiveWarper.warp(
            preResult.gray, candidates[0], preResult.scaleFactor,
        )
        assertNotNull("Warped image needed", warped)

        // Test each orientation cell mapping
        val expectedRotations = mapOf(0 to 0, 1 to 270, 2 to 180, 3 to 90)

        for ((cell, expectedRot) in expectedRotations) {
            val correction = OrientationCorrector.correct(warped!!, cell)
            assertEquals("Cell $cell should rotate by ${expectedRot}°",
                expectedRot, correction.rotationApplied)
            assertEquals("Cell $cell: output width", OUTPUT_SIZE, correction.corrected.cols())
            assertEquals("Cell $cell: output height", OUTPUT_SIZE, correction.corrected.rows())

            Log.i(TAG, "Orientation mapping: cell=$cell → " +
                "rot=${correction.rotationApplied}° ✓")

            correction.corrected.release()
        }

        warped!!.release()
        preResult.binary.release()
        preResult.gray.release()
        mat.release()
    }

    // --- Helpers ---

    private fun loadTestBitmap(testCase: TestCase): Bitmap? {
        val stream = appContext.assets.open("$ASSET_DIR/${testCase.fileName}")
        val bitmap = BitmapFactory.decodeStream(stream)
        stream.close()
        return bitmap
    }

    private fun loadTestMat(testCase: TestCase): Mat? {
        val bitmap = loadTestBitmap(testCase) ?: return null
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }
}
