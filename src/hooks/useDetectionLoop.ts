import { useCallback, useEffect, useRef } from 'react';

import { detectMarker } from '../native';
import type { MarkerDetectionResult } from '../types';

/** Target processing time in ms — warn if exceeded. */
const TARGET_PROCESSING_TIME_MS = 3000;

/**
 * A function that captures a photo from the camera and returns the file path.
 * Returns undefined if capture is not possible (e.g., camera not ready).
 */
export type CapturePhotoFn = () => Promise<string | undefined>;

/**
 * Continuously captures photos from the camera and runs the native marker
 * detector on each captured frame with a busy guard to prevent overlapping
 * calls.
 *
 * From LLD §11:
 * - Skips frames while the previous detection is still running.
 * - Increments frameId for each call.
 * - Calls `onResult` with each detection result.
 * - Stops polling when `isActive` is false or `isComplete` is true.
 *
 * Part 11 additions:
 * - Logs scan-to-result time for every detection.
 * - Warns when processing time exceeds target.
 * - Tracks and logs frame drop count.
 */
export function useDetectionLoop(
  isActive: boolean,
  isComplete: boolean,
  capturePhoto: CapturePhotoFn | undefined,
  onResult: (result: MarkerDetectionResult) => void,
  intervalMs = 500,
) {
  const busyRef = useRef(false);
  const frameIdRef = useRef(0);
  const droppedFramesRef = useRef(0);
  const onResultRef = useRef(onResult);
  const capturePhotoRef = useRef(capturePhoto);

  // Keep the callback refs up-to-date without re-triggering the effect
  useEffect(() => {
    onResultRef.current = onResult;
  }, [onResult]);

  useEffect(() => {
    capturePhotoRef.current = capturePhoto;
  }, [capturePhoto]);

  const runDetection = useCallback(async () => {
    if (busyRef.current) {
      droppedFramesRef.current++;
      return; // Frame drop — previous detection still running
    }

    busyRef.current = true;
    const currentFrameId = ++frameIdRef.current;
    const jsStart = Date.now();

    try {
      // Capture a photo from the camera
      const imageUri = await capturePhotoRef.current?.();

      const result = await detectMarker({
        frameId: currentFrameId,
        imageUri,
      });

      const totalMs = Date.now() - jsStart;

      if (__DEV__) {
        const status = result.found ? 'FOUND' : 'miss';
        const warning =
          totalMs > TARGET_PROCESSING_TIME_MS ? ' ⚠️ SLOW' : '';
        console.log(
          `[DetectionLoop] #${currentFrameId} ${status} | ` +
            `${totalMs}ms (native: ${result.processingTimeMs.toFixed(0)}ms) | ` +
            `dropped: ${droppedFramesRef.current}${warning}`,
        );
      }

      onResultRef.current(result);
    } catch {
      // Swallow errors — the detector wrapper already handles them
    } finally {
      busyRef.current = false;
    }
  }, []);

  useEffect(() => {
    if (!isActive || isComplete) {
      return;
    }

    // Reset dropped frame counter when (re)starting
    droppedFramesRef.current = 0;

    const timer = setInterval(runDetection, intervalMs);
    return () => clearInterval(timer);
  }, [isActive, isComplete, runDetection, intervalMs]);
}
