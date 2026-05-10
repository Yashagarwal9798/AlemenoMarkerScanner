package com.alemenomarkerscanner.marker

/**
 * Centralized thresholds for marker detection.
 * All values can be tuned against the provided test images.
 *
 * LLD §14: Keep these thresholds centralized to allow quick adjustment
 * after running the detector against provided test images and camera captures.
 */
object MarkerDetectorConfig {
    /** Output marker image size in pixels. */
    const val OUTPUT_SIZE = 300

    /** Maximum pixel dimension for the detection frame (longest side). */
    const val MAX_DETECTION_SIDE = 960

    /** Minimum candidate area as a fraction of the frame area. */
    const val MIN_CANDIDATE_AREA_RATIO = 0.002

    /** Maximum candidate area as a fraction of the frame area. */
    const val MAX_CANDIDATE_AREA_RATIO = 0.8

    /** Minimum aspect ratio for a square-like candidate (width / height). */
    const val MIN_SQUARE_ASPECT_RATIO = 0.75

    /** Maximum aspect ratio for a square-like candidate (width / height). */
    const val MAX_SQUARE_ASPECT_RATIO = 1.25

    /** Epsilon ratio for polygon approximation (fraction of perimeter). */
    const val POLYGON_EPSILON_RATIO = 0.02

    /** Gaussian blur kernel size. */
    const val BLUR_KERNEL_SIZE = 3

    /** Morphological kernel size for closing gaps. */
    const val MORPH_KERNEL_SIZE = 3

    // --- Validation thresholds (Part 8) ---

    /** Minimum black pixel ratio in border strips. */
    const val MIN_BORDER_BLACK_RATIO = 0.55

    /** Minimum black pixel ratio in inner orientation cell. */
    const val MIN_INNER_CELL_BLACK_RATIO = 0.55

    /** Maximum black pixel ratio in inner orientation cell. */
    const val MAX_INNER_CELL_BLACK_RATIO = 0.95

    /** Minimum confidence score to accept a detection. */
    const val MIN_CONFIDENCE = 0.75

    // --- Performance thresholds (Part 11) ---

    /** Maximum candidate count to process before giving up (limits CPU time). */
    const val MAX_CANDIDATES_TO_PROCESS = 5

    /** Target scan-to-result time in milliseconds. */
    const val TARGET_PROCESSING_TIME_MS = 3000.0

    /** Warning threshold — log a warning when detection exceeds this. */
    const val SLOW_DETECTION_THRESHOLD_MS = 2000.0

    /**
     * Preferred camera frame width for VisionCamera.
     * Smaller frames = faster detection, less data across the JS bridge.
     */
    const val CAMERA_FRAME_WIDTH = 640

    /**
     * Preferred camera frame height for VisionCamera.
     */
    const val CAMERA_FRAME_HEIGHT = 480
}
