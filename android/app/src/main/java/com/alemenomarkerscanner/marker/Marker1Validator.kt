package com.alemenomarkerscanner.marker

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * Validates whether a 300×300 warped grayscale image is Marker 1.
 *
 * From LLD §9:
 * 1. Outer border check — all four border strips must have high black occupancy.
 * 2. Inner orientation cell — exactly one of the four corner ROIs must contain
 *    a square-ish black component of the expected size.
 * 3. Interior guard — reject if a large black block is centered or oversized.
 * 4. Confidence score — weighted combination of geometry, border, inner cell,
 *    and extraction quality.
 */
object Marker1Validator {

    private const val TAG = "Marker1Validator"
    private const val SIZE = 300

    // Border strip geometry (LLD §9.1)
    private const val BORDER_WIDTH = 30

    // Inner corner ROI geometry (LLD §9.2)
    private const val ROI_START = 35
    private const val ROI_END = 95
    private const val ROI_FAR_START = 205
    private const val ROI_FAR_END = 265

    // Expected inner cell size ≈ 43 px (300 * 20/140)
    private const val EXPECTED_CELL_SIZE = 43.0
    private const val CELL_SIZE_TOLERANCE = 20.0

    // Interior guard (LLD §9.3): center region
    private const val CENTER_START = 100
    private const val CENTER_END = 200

    /**
     * Result of marker validation.
     *
     * @property accepted        Whether the image passes as Marker 1.
     * @property confidence      Overall confidence score [0, 1].
     * @property orientationCell Which corner ROI contains the orientation cell
     *                           (0=top-left, 1=top-right, 2=bottom-right, 3=bottom-left),
     *                           or -1 if none found.
     * @property borderScore     Average black occupancy across all four borders.
     * @property cellScore       Quality score of the detected orientation cell.
     * @property rejectReason    Human-readable reason for rejection, or null.
     */
    data class ValidationResult(
        val accepted: Boolean,
        val confidence: Double,
        val orientationCell: Int,
        val borderScore: Double,
        val cellScore: Double,
        val rejectReason: String?,
    )

    /**
     * Validate a 300×300 warped grayscale image.
     *
     * @param warped  300×300 single-channel Mat.
     * @param geometryScore  Score from candidate geometry (aspect ratio, convexity) [0,1].
     * @return [ValidationResult]
     */
    fun validate(warped: Mat, geometryScore: Double = 1.0): ValidationResult {
        require(warped.cols() == SIZE && warped.rows() == SIZE) {
            "Expected ${SIZE}x${SIZE}, got ${warped.cols()}x${warped.rows()}"
        }

        // Threshold the warped grayscale image
        val binary = Mat()
        Imgproc.threshold(warped, binary, 0.0, 255.0,
            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // In the binary image, black=0, white=255.
        // We measure "black occupancy" as fraction of pixels == 0.

        // --- Step 1: Outer border check (LLD §9.1) ---
        val borderScores = measureBorderOccupancy(binary)
        val avgBorderScore = borderScores.average()
        val minBorderScore = borderScores.min()

        if (minBorderScore < MarkerDetectorConfig.MIN_BORDER_BLACK_RATIO) {
            binary.release()
            return reject(
                "Border incomplete: min=${"%.2f".format(minBorderScore)} " +
                    "< ${MarkerDetectorConfig.MIN_BORDER_BLACK_RATIO}",
                avgBorderScore, 0.0,
            )
        }

        // --- Step 2: Inner orientation cell check (LLD §9.2) ---
        val cellResults = detectOrientationCells(binary)
        val validCells = cellResults.filter { it.found }

        if (validCells.isEmpty()) {
            binary.release()
            return reject("No orientation cell found in any corner ROI",
                avgBorderScore, 0.0)
        }

        if (validCells.size > 1) {
            binary.release()
            return reject(
                "Multiple orientation cells found: ${validCells.map { it.corner }}",
                avgBorderScore, 0.0,
            )
        }

        val cell = validCells[0]

        // --- Step 3: Interior guard (LLD §9.3) ---
        val centerBlack = measureBlackOccupancy(binary,
            Rect(CENTER_START, CENTER_START,
                CENTER_END - CENTER_START, CENTER_END - CENTER_START))
        if (centerBlack > 0.5) {
            binary.release()
            return reject(
                "Center area has too much black: ${"%.2f".format(centerBlack)}",
                avgBorderScore, cell.score,
            )
        }

        binary.release()

        // --- Step 4: Confidence score (LLD §9.4) ---
        val borderNorm = avgBorderScore.coerceIn(0.0, 1.0)
        val cellNorm = cell.score.coerceIn(0.0, 1.0)
        val extractionScore = 1.0 // Placeholder — refine later with warp quality metrics

        val confidence = 0.25 * geometryScore +
            0.25 * borderNorm +
            0.35 * cellNorm +
            0.15 * extractionScore

        if (confidence < MarkerDetectorConfig.MIN_CONFIDENCE) {
            return reject(
                "Low confidence: ${"%.3f".format(confidence)} < ${MarkerDetectorConfig.MIN_CONFIDENCE}",
                avgBorderScore, cell.score,
            )
        }

        Log.i(TAG, "ACCEPTED: confidence=${"%.3f".format(confidence)} " +
            "border=${"%.2f".format(avgBorderScore)} " +
            "cell=${cell.corner} cellScore=${"%.2f".format(cell.score)}")

        return ValidationResult(
            accepted = true,
            confidence = confidence,
            orientationCell = cell.corner,
            borderScore = avgBorderScore,
            cellScore = cell.score,
            rejectReason = null,
        )
    }

    // -----------------------------------------------------------------------
    // Border occupancy (LLD §9.1)
    // -----------------------------------------------------------------------

    /**
     * Measure black pixel occupancy in the four border strips.
     * Returns [top, bottom, left, right] scores in [0, 1].
     */
    private fun measureBorderOccupancy(binary: Mat): List<Double> {
        val top = measureBlackOccupancy(binary,
            Rect(0, 0, SIZE, BORDER_WIDTH))
        val bottom = measureBlackOccupancy(binary,
            Rect(0, SIZE - BORDER_WIDTH, SIZE, BORDER_WIDTH))
        val left = measureBlackOccupancy(binary,
            Rect(0, 0, BORDER_WIDTH, SIZE))
        val right = measureBlackOccupancy(binary,
            Rect(SIZE - BORDER_WIDTH, 0, BORDER_WIDTH, SIZE))

        return listOf(top, bottom, left, right)
    }

    /**
     * Fraction of pixels that are black (value == 0) in a ROI.
     */
    private fun measureBlackOccupancy(binary: Mat, roi: Rect): Double {
        val subMat = binary.submat(roi)
        val totalPixels = subMat.rows() * subMat.cols()
        if (totalPixels == 0) return 0.0

        // Count non-zero (white) pixels, black = total - nonZero
        val nonZero = Core.countNonZero(subMat)
        val blackCount = totalPixels - nonZero

        return blackCount.toDouble() / totalPixels
    }

    // -----------------------------------------------------------------------
    // Orientation cell detection (LLD §9.2)
    // -----------------------------------------------------------------------

    data class CellResult(
        val corner: Int,
        val found: Boolean,
        val score: Double,
    )

    /**
     * Check each of the four corner ROIs for a square-ish black component.
     */
    private fun detectOrientationCells(binary: Mat): List<CellResult> {
        val rois = listOf(
            Rect(ROI_START, ROI_START, ROI_END - ROI_START, ROI_END - ROI_START),         // 0: top-left
            Rect(ROI_FAR_START, ROI_START, ROI_FAR_END - ROI_FAR_START, ROI_END - ROI_START), // 1: top-right
            Rect(ROI_FAR_START, ROI_FAR_START, ROI_FAR_END - ROI_FAR_START, ROI_FAR_END - ROI_FAR_START), // 2: bottom-right
            Rect(ROI_START, ROI_FAR_START, ROI_END - ROI_START, ROI_FAR_END - ROI_FAR_START), // 3: bottom-left
        )

        return rois.mapIndexed { index, roi -> analyzeCornerROI(binary, roi, index) }
    }

    /**
     * Analyze a single corner ROI for a square-ish black component.
     */
    private fun analyzeCornerROI(binary: Mat, roi: Rect, corner: Int): CellResult {
        val subMat = binary.submat(roi)
        val blackOccupancy = measureBlackOccupancy(binary, roi)

        // The orientation cell should have significant black content
        if (blackOccupancy < MarkerDetectorConfig.MIN_INNER_CELL_BLACK_RATIO) {
            return CellResult(corner, false, 0.0)
        }

        if (blackOccupancy > MarkerDetectorConfig.MAX_INNER_CELL_BLACK_RATIO) {
            return CellResult(corner, false, 0.0)
        }

        // Check that the black region is square-ish by measuring occupancy balance
        // Split the ROI into quadrants and check they're roughly balanced
        val roiW = roi.width
        val roiH = roi.height
        val halfW = roiW / 2
        val halfH = roiH / 2

        val q1 = measureBlackOccupancy(binary, Rect(roi.x, roi.y, halfW, halfH))
        val q2 = measureBlackOccupancy(binary, Rect(roi.x + halfW, roi.y, roiW - halfW, halfH))
        val q3 = measureBlackOccupancy(binary, Rect(roi.x, roi.y + halfH, halfW, roiH - halfH))
        val q4 = measureBlackOccupancy(binary, Rect(roi.x + halfW, roi.y + halfH, roiW - halfW, roiH - halfH))

        val quadrants = listOf(q1, q2, q3, q4)
        val quadMin = quadrants.min()
        val quadMax = quadrants.max()

        // A square-ish cell should have balanced quadrants
        val balance = if (quadMax > 0) quadMin / quadMax else 0.0

        // Score: combine occupancy quality and balance
        val occupancyScore = 1.0 - Math.abs(blackOccupancy - 0.75) / 0.25
        val score = (occupancyScore * 0.6 + balance * 0.4).coerceIn(0.0, 1.0)

        val found = balance > 0.3 // Reasonably balanced across quadrants

        Log.d(TAG, "Corner $corner: occupancy=${"%.2f".format(blackOccupancy)} " +
            "balance=${"%.2f".format(balance)} score=${"%.2f".format(score)} found=$found")

        return CellResult(corner, found, score)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun reject(
        reason: String,
        borderScore: Double,
        cellScore: Double,
    ): ValidationResult {
        Log.d(TAG, "REJECTED: $reason")
        return ValidationResult(
            accepted = false,
            confidence = 0.0,
            orientationCell = -1,
            borderScore = borderScore,
            cellScore = cellScore,
            rejectReason = reason,
        )
    }
}
