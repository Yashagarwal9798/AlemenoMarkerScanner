package com.alemenomarkerscanner.marker

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Perspective warp of a quadrilateral region to a square output image.
 *
 * From LLD §7–§8:
 * 1. Corners are already in clockwise order (from CandidateDetector).
 * 2. Scale corners back to original image coordinates if the image was resized.
 * 3. Use getPerspectiveTransform to map to (0,0)→(299,0)→(299,299)→(0,299).
 * 4. Use warpPerspective to produce the 300×300 output.
 *
 * The caller is responsible for releasing the returned Mat.
 */
object PerspectiveWarper {

    /** Destination corners for the 300×300 output (clockwise from top-left). */
    private val DST_CORNERS = arrayOf(
        Point(0.0, 0.0),
        Point(299.0, 0.0),
        Point(299.0, 299.0),
        Point(0.0, 299.0),
    )

    /**
     * Warp [candidate]'s region from [gray] into a 300×300 Mat.
     *
     * @param gray         Full-resolution grayscale image.
     * @param candidate    Detected square candidate with clockwise corners
     *                     (in detection-scale coordinates).
     * @param scaleFactor  Ratio used during preprocessing resize (≥ 1.0).
     *                     Corners are multiplied by this to get original-image coords.
     * @return 300×300 grayscale Mat of the warped marker region, or null if the
     *         transform matrix is empty.
     */
    fun warp(gray: Mat, candidate: SquareCandidate, scaleFactor: Double): Mat? {
        // Scale corners back to original image coordinates
        val srcCorners = candidate.corners.map { pt ->
            Point(pt.x * scaleFactor, pt.y * scaleFactor)
        }

        // Build source and destination point matrices
        val srcMat = cornersToMat(srcCorners.toTypedArray())
        val dstMat = cornersToMat(DST_CORNERS)

        // Compute perspective transform
        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        srcMat.release()
        dstMat.release()

        if (transform.empty()) {
            transform.release()
            return null
        }

        // Apply warp
        val outputSize = MarkerDetectorConfig.OUTPUT_SIZE.toDouble()
        val warped = Mat()
        Imgproc.warpPerspective(
            gray, warped, transform,
            Size(outputSize, outputSize),
        )
        transform.release()

        return warped
    }

    /**
     * Warp with all four rotations of the corner ordering.
     *
     * Because the marker can appear in any orientation, we try each
     * of the four cyclic rotations of the source corners. The validation
     * step (Part 8) picks the one that matches the marker pattern.
     *
     * @return List of four 300×300 grayscale Mats (one per rotation).
     *         Caller must release all returned Mats.
     */
    fun warpAllRotations(
        gray: Mat,
        candidate: SquareCandidate,
        scaleFactor: Double,
    ): List<Mat> {
        val results = mutableListOf<Mat>()

        for (rotation in 0 until 4) {
            val rotatedCandidate = rotateCandidate(candidate, rotation)
            val warped = warp(gray, rotatedCandidate, scaleFactor)
            if (warped != null) {
                results.add(warped)
            }
        }

        return results
    }

    /**
     * Cyclically rotate the corners of a candidate by [steps] positions.
     * rotation=0 → original, rotation=1 → 90° CW, etc.
     */
    private fun rotateCandidate(candidate: SquareCandidate, steps: Int): SquareCandidate {
        if (steps == 0) return candidate
        val n = candidate.corners.size
        val rotated = List(n) { candidate.corners[(it + steps) % n] }
        return candidate.copy(corners = rotated)
    }

    /**
     * Convert an array of 4 Points to a 4×2 Mat of type CV_32F
     * as required by getPerspectiveTransform.
     */
    private fun cornersToMat(points: Array<Point>): Mat {
        val mat = Mat(4, 1, CvType.CV_32FC2)
        for (i in points.indices) {
            mat.put(i, 0, points[i].x, points[i].y)
        }
        return mat
    }
}
