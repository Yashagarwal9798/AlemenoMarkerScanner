package com.alemenomarkerscanner.marker

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Rotates a validated 300×300 marker image so that the inner orientation cell
 * is always in the top-left corner.
 *
 * From LLD §10:
 *
 * ```
 * cell in top-left     → rotate 0°
 * cell in top-right    → rotate 270° CW  (= 90° CCW)
 * cell in bottom-right → rotate 180°
 * cell in bottom-left  → rotate 90° CW   (= 270° CCW)
 * ```
 *
 * After rotation, a lightweight re-validation confirms the cell is top-left.
 * Output is always exactly 300×300.
 */
object OrientationCorrector {

    private const val TAG = "OrientationCorrector"
    private const val SIZE = 300

    /**
     * Result of orientation correction.
     *
     * @property corrected  The 300×300 Mat with the orientation cell in top-left.
     *                      Caller must release this Mat.
     * @property rotationApplied  The clockwise rotation in degrees that was applied
     *                            (0, 90, 180, or 270).
     * @property verified   Whether the re-validation confirmed cell is top-left.
     */
    data class CorrectionResult(
        val corrected: Mat,
        val rotationApplied: Int,
        val verified: Boolean,
    )

    /**
     * Rotate [warped] so that the orientation cell is in the top-left corner.
     *
     * @param warped           300×300 grayscale Mat from PerspectiveWarper.
     * @param orientationCell  Corner index where the cell was detected
     *                         (0=top-left, 1=top-right, 2=bottom-right, 3=bottom-left).
     * @return [CorrectionResult] with the rotated image and verification status.
     */
    fun correct(warped: Mat, orientationCell: Int): CorrectionResult {
        require(warped.cols() == SIZE && warped.rows() == SIZE) {
            "Expected ${SIZE}x${SIZE}, got ${warped.cols()}x${warped.rows()}"
        }

        val rotationDegrees = when (orientationCell) {
            0 -> 0     // Already top-left
            1 -> 270   // Top-right → rotate 270° CW
            2 -> 180   // Bottom-right → rotate 180°
            3 -> 90    // Bottom-left → rotate 90° CW
            else -> throw IllegalArgumentException(
                "Invalid orientationCell: $orientationCell (must be 0-3)"
            )
        }

        val corrected = if (rotationDegrees == 0) {
            // No rotation needed — clone so caller always owns the Mat
            warped.clone()
        } else {
            rotateMat(warped, rotationDegrees)
        }

        // Lightweight re-validation: confirm cell is now top-left
        val verified = verifyTopLeft(corrected)

        Log.i(TAG, "Correction: cell=$orientationCell → " +
            "rotate=${rotationDegrees}° → " +
            "verified=$verified → " +
            "size=${corrected.cols()}x${corrected.rows()}")

        return CorrectionResult(
            corrected = corrected,
            rotationApplied = rotationDegrees,
            verified = verified,
        )
    }

    /**
     * Rotate a Mat by 90, 180, or 270 degrees clockwise.
     * Returns a new Mat; the input is not modified.
     */
    private fun rotateMat(src: Mat, degrees: Int): Mat {
        val dst = Mat()
        when (degrees) {
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> throw IllegalArgumentException("Unsupported rotation: $degrees")
        }

        // Ensure output is exactly 300×300 (rotation of a square preserves size,
        // but be defensive)
        if (dst.cols() != SIZE || dst.rows() != SIZE) {
            val resized = Mat()
            Imgproc.resize(dst, resized, Size(SIZE.toDouble(), SIZE.toDouble()))
            dst.release()
            return resized
        }

        return dst
    }

    /**
     * Lightweight re-validation: check that the top-left corner ROI has
     * significant black occupancy and the other three corners do not.
     *
     * This is cheaper than a full Marker1Validator.validate() call.
     */
    private fun verifyTopLeft(corrected: Mat): Boolean {
        val binary = Mat()
        Imgproc.threshold(corrected, binary, 0.0, 255.0,
            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // Top-left ROI (same coordinates as Marker1Validator)
        val tlBlack = measureBlackOccupancy(binary,
            35, 35, 60, 60)

        // The other three corners should have less black
        val trBlack = measureBlackOccupancy(binary,
            205, 35, 60, 60)
        val brBlack = measureBlackOccupancy(binary,
            205, 205, 60, 60)
        val blBlack = measureBlackOccupancy(binary,
            35, 205, 60, 60)

        binary.release()

        // Top-left should be the darkest corner
        val isTopLeftDarkest = tlBlack > trBlack &&
            tlBlack > brBlack &&
            tlBlack > blBlack

        // Top-left should have meaningful black content
        val hasContent = tlBlack >= MarkerDetectorConfig.MIN_INNER_CELL_BLACK_RATIO

        Log.d(TAG, "Re-validation: TL=${"%.2f".format(tlBlack)} " +
            "TR=${"%.2f".format(trBlack)} " +
            "BR=${"%.2f".format(brBlack)} " +
            "BL=${"%.2f".format(blBlack)} " +
            "darkest=$isTopLeftDarkest content=$hasContent")

        return isTopLeftDarkest && hasContent
    }

    /**
     * Measure black pixel ratio in a rectangular region.
     */
    private fun measureBlackOccupancy(
        binary: Mat,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Double {
        val roi = binary.submat(y, y + height, x, x + width)
        val total = roi.rows() * roi.cols()
        if (total == 0) return 0.0
        val nonZero = Core.countNonZero(roi)
        return (total - nonZero).toDouble() / total
    }
}
