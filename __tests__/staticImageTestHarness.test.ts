import * as fs from 'fs';
import * as path from 'path';

import {
  MARKER1_TEST_CASES,
  MARKER_ASSETS_DIR,
  MARKER1_TEST_DIR,
  TEST_IMAGE_SIZE,
  testCaseRelativePath,
} from '../src/native/markerTestCases';

import { detectMarker } from '../src/native/markerDetector';

/**
 * Static Image Test Harness for Marker 1 Detection
 *
 * This harness ensures:
 * 1. All expected test image files exist on disk.
 * 2. The test case definitions cover the right count of correct/incorrect images.
 * 3. The detector (currently skeleton) returns predictable no-detection results.
 * 4. Results are printed clearly for each test case.
 *
 * When the real detector is connected in later parts, these tests will be
 * updated to assert actual accept/reject behavior against each image.
 */

// Workspace root is one level above AlemenoMarkerScanner/
const WORKSPACE_ROOT = path.resolve(__dirname, '..', '..');

function absolutePathForTestCase(
  testCase: (typeof MARKER1_TEST_CASES)[number],
): string {
  return path.join(WORKSPACE_ROOT, testCaseRelativePath(testCase));
}

// ---------------------------------------------------------------------------
// Test suite: File discovery
// ---------------------------------------------------------------------------
describe('Marker 1 test image discovery', () => {
  test('marker assets directory exists', () => {
    const assetsDir = path.join(WORKSPACE_ROOT, MARKER_ASSETS_DIR);
    expect(fs.existsSync(assetsDir)).toBe(true);
  });

  test('Marker1-TestImages directory exists', () => {
    const testDir = path.join(
      WORKSPACE_ROOT,
      MARKER_ASSETS_DIR,
      MARKER1_TEST_DIR,
    );
    expect(fs.existsSync(testDir)).toBe(true);
  });

  test('Correct Marker Images subdirectory exists', () => {
    const dir = path.join(
      WORKSPACE_ROOT,
      MARKER_ASSETS_DIR,
      MARKER1_TEST_DIR,
      'Correct Marker Images',
    );
    expect(fs.existsSync(dir)).toBe(true);
  });

  test('Incorrect Marker Images subdirectory exists', () => {
    const dir = path.join(
      WORKSPACE_ROOT,
      MARKER_ASSETS_DIR,
      MARKER1_TEST_DIR,
      'Incorrect Marker Images',
    );
    expect(fs.existsSync(dir)).toBe(true);
  });

  test.each(MARKER1_TEST_CASES)(
    'test image file exists: $id ($fileName)',
    testCase => {
      const filePath = absolutePathForTestCase(testCase);
      expect(fs.existsSync(filePath)).toBe(true);
    },
  );

  test.each(MARKER1_TEST_CASES)(
    'test image is a non-empty file: $id',
    testCase => {
      const filePath = absolutePathForTestCase(testCase);
      const stats = fs.statSync(filePath);
      expect(stats.isFile()).toBe(true);
      expect(stats.size).toBeGreaterThan(0);
    },
  );
});

// ---------------------------------------------------------------------------
// Test suite: Test case definition integrity
// ---------------------------------------------------------------------------
describe('Marker 1 test case definitions', () => {
  test('exactly 7 test cases are defined', () => {
    expect(MARKER1_TEST_CASES).toHaveLength(7);
  });

  test('exactly 3 cases expect acceptance', () => {
    const accepted = MARKER1_TEST_CASES.filter(tc => tc.expectedAccepted);
    expect(accepted).toHaveLength(3);
  });

  test('exactly 4 cases expect rejection', () => {
    const rejected = MARKER1_TEST_CASES.filter(tc => !tc.expectedAccepted);
    expect(rejected).toHaveLength(4);
  });

  test('all accepted cases come from Correct Marker Images', () => {
    const accepted = MARKER1_TEST_CASES.filter(tc => tc.expectedAccepted);
    for (const tc of accepted) {
      expect(tc.subDir).toBe('Correct Marker Images');
    }
  });

  test('all rejected cases come from Incorrect Marker Images', () => {
    const rejected = MARKER1_TEST_CASES.filter(tc => !tc.expectedAccepted);
    for (const tc of rejected) {
      expect(tc.subDir).toBe('Incorrect Marker Images');
    }
  });

  test('all test case ids are unique', () => {
    const ids = MARKER1_TEST_CASES.map(tc => tc.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  test('all test case file names are unique', () => {
    const fileNames = MARKER1_TEST_CASES.map(tc => tc.fileName);
    expect(new Set(fileNames).size).toBe(fileNames.length);
  });

  test('every test case has a non-empty reason', () => {
    for (const tc of MARKER1_TEST_CASES) {
      expect(tc.reason.length).toBeGreaterThan(0);
    }
  });

  test(`TEST_IMAGE_SIZE is ${TEST_IMAGE_SIZE}`, () => {
    expect(TEST_IMAGE_SIZE).toBe(500);
  });
});

// ---------------------------------------------------------------------------
// Test suite: Skeleton detector results
// ---------------------------------------------------------------------------
describe('Skeleton detector produces predictable no-detection results', () => {
  // The native module is unavailable in Jest, so detectMarker always returns
  // a no-detection result. This confirms the harness can call the detector
  // for each image and get a well-formed response.

  test.each(MARKER1_TEST_CASES)(
    'detector returns no-detection for $id (skeleton)',
    async testCase => {
      const imagePath = absolutePathForTestCase(testCase);
      const result = await detectMarker({
        frameId: MARKER1_TEST_CASES.indexOf(testCase),
        imageUri: imagePath,
      });

      // Skeleton always returns found=false — this is expected for Part 5.
      expect(result.found).toBe(false);
      expect(result.confidence).toBe(0);
      expect(result.corners).toHaveLength(4);
      expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
      expect(result.imageUri).toBeUndefined();

      // Print clear detection result for each case.
      const status = testCase.expectedAccepted ? 'SHOULD ACCEPT' : 'SHOULD REJECT';
      const actual = result.found ? 'ACCEPTED' : 'REJECTED';
      const match = (result.found === testCase.expectedAccepted) ? '✓' : '✗ (skeleton)';

      console.log(
        `[${match}] ${testCase.id}: ${status} → ${actual} | ` +
        `confidence=${result.confidence} | ` +
        `time=${result.processingTimeMs}ms | ` +
        `file=${testCase.fileName}`,
      );
    },
  );
});

// ---------------------------------------------------------------------------
// Test suite: Harness report summary
// ---------------------------------------------------------------------------
describe('Harness summary report', () => {
  test('prints summary of expected vs skeleton results', async () => {
    const results: Array<{
      id: string;
      expected: boolean;
      actual: boolean;
      match: boolean;
    }> = [];

    for (const tc of MARKER1_TEST_CASES) {
      const result = await detectMarker({
        frameId: MARKER1_TEST_CASES.indexOf(tc),
        imageUri: absolutePathForTestCase(tc),
      });

      results.push({
        id: tc.id,
        expected: tc.expectedAccepted,
        actual: result.found,
        match: result.found === tc.expectedAccepted,
      });
    }

    const accepted = results.filter(r => r.expected).length;
    const rejected = results.filter(r => !r.expected).length;
    const matching = results.filter(r => r.match).length;

    console.log('\n--- Static Image Test Harness Summary ---');
    console.log(`Total cases: ${results.length}`);
    console.log(`Expected accepted: ${accepted}`);
    console.log(`Expected rejected: ${rejected}`);
    console.log(`Matching expectations: ${matching}/${results.length}`);
    console.log(
      `Note: Skeleton detector always returns found=false. ` +
      `Only rejection cases match. Acceptance cases will match ` +
      `after real detector is implemented in Parts 6-9.`,
    );
    console.log('--- End Summary ---\n');

    // Skeleton matches rejections only (4 out of 7).
    expect(matching).toBe(4);
    expect(results.length).toBe(7);
  });
});
