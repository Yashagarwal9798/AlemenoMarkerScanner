package com.alemenomarkerscanner.marker

import org.opencv.core.Point

/**
 * A detected quadrilateral candidate that may be a marker.
 *
 * @property corners Four corners sorted clockwise.
 * @property area Contour area in pixels (in the detection-scale image).
 * @property aspectRatio Width / height of the bounding rectangle.
 * @property areaRatio Candidate area as a fraction of the total frame area.
 */
data class SquareCandidate(
    val corners: List<Point>,
    val area: Double,
    val aspectRatio: Double,
    val areaRatio: Double,
)
