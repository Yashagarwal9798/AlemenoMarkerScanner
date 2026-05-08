import type { MarkerCapture } from '../types';

const MOCK_MARKER_IMAGE_URI =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lS3b5wAAAABJRU5ErkJggg==';

export function createMockMarkerCapture(index: number): MarkerCapture {
  const frameId = Math.max(1, index);

  return {
    id: `mock-marker-${frameId}`,
    frameId,
    capturedAt: Date.now(),
    confidence: 0.95,
    rotationDegrees: 0,
    corners: [
      { x: 0, y: 0 },
      { x: 300, y: 0 },
      { x: 300, y: 300 },
      { x: 0, y: 300 },
    ],
    imageUri: MOCK_MARKER_IMAGE_URI,
  };
}

export function createMockMarkerCaptures(count: number): MarkerCapture[] {
  return Array.from({ length: count }, (_, index) =>
    createMockMarkerCapture(index + 1),
  );
}
