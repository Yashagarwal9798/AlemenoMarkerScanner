package com.alemenomarkerscanner.marker

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Preprocesses an image for marker candidate detection.
 *
 * Pipeline (from LLD §5):
 * 1. Convert to grayscale (if colour input).
 * 2. Resize so the longest side ≤ [MarkerDetectorConfig.MAX_DETECTION_SIDE].
 * 3. Apply 3×3 Gaussian blur.
 * 4. Apply Otsu thresholding.
 * 5. Invert so black marker strokes become white foreground.
 * 6. Apply morphological close to repair small border gaps.
 *
 * The caller is responsible for releasing the returned Mats.
 */
object ImagePreprocessor {

    /**
     * @property binary  Thresholded + inverted + morphed image for contour search.
     * @property gray    Full-resolution grayscale image (used later for perspective warp).
     * @property scaleFactor  Ratio of original size to detection size (≥ 1.0).
     */
    data class PreprocessResult(
        val binary: Mat,
        val gray: Mat,
        val scaleFactor: Double,
    )

    /**
     * Run the full preprocessing pipeline on [input].
     *
     * @param input BGR or grayscale Mat.
     * @return [PreprocessResult] – caller must release [PreprocessResult.binary]
     *         and [PreprocessResult.gray] when done.
     */
    fun preprocess(input: Mat): PreprocessResult {
        // 1. Grayscale
        val gray = if (input.channels() > 1) {
            val g = Mat()
            Imgproc.cvtColor(input, g, Imgproc.COLOR_BGR2GRAY)
            g
        } else {
            input.clone()
        }

        // 2. Resize for detection speed
        val maxSide = maxOf(gray.rows(), gray.cols())
        val scaleFactor: Double
        val smallGray: Mat

        if (maxSide > MarkerDetectorConfig.MAX_DETECTION_SIDE) {
            scaleFactor = maxSide.toDouble() / MarkerDetectorConfig.MAX_DETECTION_SIDE
            val newW = (gray.cols() / scaleFactor).toInt()
            val newH = (gray.rows() / scaleFactor).toInt()
            smallGray = Mat()
            Imgproc.resize(gray, smallGray, Size(newW.toDouble(), newH.toDouble()))
        } else {
            scaleFactor = 1.0
            smallGray = gray.clone()
        }

        // 3. Gaussian blur
        val blurred = Mat()
        val kSize = MarkerDetectorConfig.BLUR_KERNEL_SIZE.toDouble()
        Imgproc.GaussianBlur(smallGray, blurred, Size(kSize, kSize), 0.0)

        // 4. Otsu threshold
        val binary = Mat()
        Imgproc.threshold(blurred, binary, 0.0, 255.0,
            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // 5. Invert: dark marker → white foreground for findContours
        Core.bitwise_not(binary, binary)

        // 6. Morphological close to fill small border gaps
        val morphKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(
                MarkerDetectorConfig.MORPH_KERNEL_SIZE.toDouble(),
                MarkerDetectorConfig.MORPH_KERNEL_SIZE.toDouble(),
            ),
        )
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, morphKernel)

        // Release intermediates
        smallGray.release()
        blurred.release()
        morphKernel.release()

        return PreprocessResult(binary = binary, gray = gray, scaleFactor = scaleFactor)
    }
}
