package com.alemenomarkerscanner.marker

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

/**
 * Finds quadrilateral candidates that may be markers in a binary image.
 *
 * Pipeline (from LLD §6):
 * 1. findContours on the binary image.
 * 2. For each contour: compute perimeter, approximate polygon.
 * 3. Keep only 4-vertex convex polygons.
 * 4. Filter by area ratio and aspect ratio.
 * 5. Sort by area descending (larger candidates first).
 */
object CandidateDetector {

    /**
     * Find square-like candidates in [binary].
     *
     * @param binary  Preprocessed binary image (white foreground).
     * @return List of [SquareCandidate] sorted by area descending.
     */
    fun findSquareCandidates(binary: Mat): List<SquareCandidate> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        Imgproc.findContours(
            binary, contours, hierarchy,
            Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
        )
        hierarchy.release()

        val frameArea = (binary.rows() * binary.cols()).toDouble()
        val candidates = mutableListOf<SquareCandidate>()

        for (contour in contours) {
            val candidate = evaluateContour(contour, frameArea)
            if (candidate != null) {
                candidates.add(candidate)
            }
            contour.release()
        }

        // Sort by area descending — larger candidates first (LLD §6 Sorting).
        candidates.sortByDescending { it.area }

        return candidates
    }

    /**
     * Evaluate a single contour and return a [SquareCandidate] if it passes
     * all geometric filters, or null otherwise.
     */
    private fun evaluateContour(contour: MatOfPoint, frameArea: Double): SquareCandidate? {
        val contour2f = MatOfPoint2f(*contour.toArray())

        // Perimeter
        val perimeter = Imgproc.arcLength(contour2f, true)
        if (perimeter <= 0) {
            contour2f.release()
            return null
        }

        // Approximate polygon
        val epsilon = MarkerDetectorConfig.POLYGON_EPSILON_RATIO * perimeter
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
        contour2f.release()

        val vertices = approx.toArray()

        // Must have exactly 4 vertices
        if (vertices.size != 4) {
            approx.release()
            return null
        }

        // Must be convex
        val approxAsPoints = MatOfPoint(*vertices)
        if (!Imgproc.isContourConvex(approxAsPoints)) {
            approx.release()
            approxAsPoints.release()
            return null
        }

        // Area check
        val area = Imgproc.contourArea(approx)
        val areaRatio = area / frameArea

        if (areaRatio < MarkerDetectorConfig.MIN_CANDIDATE_AREA_RATIO ||
            areaRatio > MarkerDetectorConfig.MAX_CANDIDATE_AREA_RATIO
        ) {
            approx.release()
            approxAsPoints.release()
            return null
        }

        // Aspect ratio from bounding rectangle
        val boundingRect = Imgproc.boundingRect(approxAsPoints)
        val aspectRatio = boundingRect.width.toDouble() / boundingRect.height.toDouble()

        if (aspectRatio < MarkerDetectorConfig.MIN_SQUARE_ASPECT_RATIO ||
            aspectRatio > MarkerDetectorConfig.MAX_SQUARE_ASPECT_RATIO
        ) {
            approx.release()
            approxAsPoints.release()
            return null
        }

        // Side-length balance: reject if any side is much shorter than others
        val sides = sideDistances(vertices)
        val maxSide = sides.max()
        val minSide = sides.min()
        if (maxSide > 0 && minSide / maxSide < 0.5) {
            approx.release()
            approxAsPoints.release()
            return null
        }

        // Order corners clockwise
        val orderedCorners = orderCornersClockwise(vertices)

        approx.release()
        approxAsPoints.release()

        return SquareCandidate(
            corners = orderedCorners,
            area = area,
            aspectRatio = aspectRatio,
            areaRatio = areaRatio,
        )
    }

    /**
     * Compute the four side distances of a quadrilateral.
     */
    private fun sideDistances(pts: Array<Point>): List<Double> {
        return listOf(
            distance(pts[0], pts[1]),
            distance(pts[1], pts[2]),
            distance(pts[2], pts[3]),
            distance(pts[3], pts[0]),
        )
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return Math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Sort four points into clockwise order starting from top-left.
     *
     * 1. Compute centroid.
     * 2. Sort by angle around centroid.
     * 3. Rotate so top-left is first.
     */
    fun orderCornersClockwise(pts: Array<Point>): List<Point> {
        val topLeft = pts.minByOrNull { it.x + it.y } ?: pts[0]
        val bottomRight = pts.maxByOrNull { it.x + it.y } ?: pts[0]
        val topRight = pts.maxByOrNull { it.x - it.y } ?: pts[0]
        val bottomLeft = pts.minByOrNull { it.x - it.y } ?: pts[0]

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }
}
