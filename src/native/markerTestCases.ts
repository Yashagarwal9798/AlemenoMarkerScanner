/**
 * Static test case definitions for Marker 1 detection.
 *
 * Each entry maps a provided test image to the expected detection result.
 * These definitions are used by both the Jest test harness and the
 * Android instrumented test harness.
 *
 * Image paths are relative to the workspace root
 * (`Alemeno Frontend Assignment Marker Images/`).
 */

export type MarkerTestCase = {
  /** Short identifier for logging. */
  id: string;
  /** File name inside the Correct or Incorrect subdirectory. */
  fileName: string;
  /** Subdirectory under `Marker1-TestImages/`. */
  subDir: 'Correct Marker Images' | 'Incorrect Marker Images';
  /** Whether the detector should accept this image. */
  expectedAccepted: boolean;
  /** Human-readable reason for acceptance or rejection. */
  reason: string;
};

/**
 * All 7 provided Marker 1 test images with expected outcomes.
 *
 * Correct images (3):
 *   - Have a closed black square border.
 *   - Have a small orientation cell in one corner.
 *   - Should be accepted by the detector.
 *
 * Incorrect images (4):
 *   - Missing borders, wrong cell position/size, or other defects.
 *   - Should be rejected by the detector.
 */
export const MARKER1_TEST_CASES: MarkerTestCase[] = [
  {
    id: 'correct-1',
    fileName: 'Marker1-TestImage1-Correct.jpg',
    subDir: 'Correct Marker Images',
    expectedAccepted: true,
    reason: 'Valid Marker 1 with complete border and correctly placed orientation cell.',
  },
  {
    id: 'correct-2',
    fileName: 'Marker1-TestImage2-Correct.jpg',
    subDir: 'Correct Marker Images',
    expectedAccepted: true,
    reason: 'Valid Marker 1 with complete border and correctly placed orientation cell.',
  },
  {
    id: 'correct-3',
    fileName: 'Marker1-TestImage3-Correct.jpg',
    subDir: 'Correct Marker Images',
    expectedAccepted: true,
    reason: 'Valid Marker 1 with complete border and correctly placed orientation cell.',
  },
  {
    id: 'incorrect-4',
    fileName: 'Marker1-TestImage4-Incorrect.jpg',
    subDir: 'Incorrect Marker Images',
    expectedAccepted: false,
    reason: 'Incorrect marker — does not match valid Marker 1 pattern.',
  },
  {
    id: 'incorrect-5',
    fileName: 'Marker1-TestImage5-Incorrect.jpg',
    subDir: 'Incorrect Marker Images',
    expectedAccepted: false,
    reason: 'Incorrect marker — does not match valid Marker 1 pattern.',
  },
  {
    id: 'incorrect-6',
    fileName: 'Marker1-TestImage6-Incorrect.jpg',
    subDir: 'Incorrect Marker Images',
    expectedAccepted: false,
    reason: 'Incorrect marker — does not match valid Marker 1 pattern.',
  },
  {
    id: 'incorrect-7',
    fileName: 'Marker1-TestImage7-Incorrect.jpg',
    subDir: 'Incorrect Marker Images',
    expectedAccepted: false,
    reason: 'Incorrect marker — does not match valid Marker 1 pattern.',
  },
];

/**
 * Root directory name for marker assets (relative to workspace root).
 */
export const MARKER_ASSETS_DIR = 'Alemeno Frontend Assignment Marker Images';

/**
 * Subdirectory inside MARKER_ASSETS_DIR for Marker 1 test images.
 */
export const MARKER1_TEST_DIR = 'Marker1-TestImages';

/**
 * Expected image dimensions for test images (as noted in PRD).
 */
export const TEST_IMAGE_SIZE = 500;

/**
 * Expected output dimensions for processed markers.
 */
export const PROCESSED_MARKER_SIZE = 300;

/**
 * Helper to build the relative path for a test case image.
 */
export function testCaseRelativePath(testCase: MarkerTestCase): string {
  return `${MARKER_ASSETS_DIR}/${MARKER1_TEST_DIR}/${testCase.subDir}/${testCase.fileName}`;
}

/**
 * Helper to build an Android asset name for a test case image.
 * Android assets cannot have spaces, so we flatten the path.
 */
export function testCaseAssetName(testCase: MarkerTestCase): string {
  return testCase.fileName.toLowerCase().replace(/\s+/g, '_');
}
