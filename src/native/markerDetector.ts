import { NativeModules } from 'react-native';

import type { MarkerDetectionResult, Point, RotationDegrees } from '../types';

type NativeMarkerDetectorModule = {
  detectMarker(request: NativeMarkerDetectRequest): Promise<NativeMarkerDetectionResult>;
};

export type NativeMarkerDetectRequest = {
  frameId?: number;
  imageUri?: string;
};

type NativeMarkerDetectionResult = MarkerDetectionResult & {
  error?: string;
};

const EMPTY_CORNERS: [Point, Point, Point, Point] = [
  { x: 0, y: 0 },
  { x: 0, y: 0 },
  { x: 0, y: 0 },
  { x: 0, y: 0 },
];

const nativeModule = NativeModules.NativeMarkerDetector as
  | NativeMarkerDetectorModule
  | undefined;

/** Safety timeout for native calls (ms). */
const NATIVE_TIMEOUT_MS = 5000;

export async function detectMarker(
  request: NativeMarkerDetectRequest = {},
): Promise<MarkerDetectionResult> {
  const startedAt = Date.now();
  const frameId = request.frameId ?? 0;

  if (!nativeModule) {
    return createNoDetectionResult(frameId, startedAt);
  }

  try {
    const result = await Promise.race([
      nativeModule.detectMarker(request),
      new Promise<never>((_resolve, reject) =>
        setTimeout(
          () => reject(new Error('Native detector timeout')),
          NATIVE_TIMEOUT_MS,
        ),
      ),
    ]);
    return normalizeDetectionResult(result, frameId, startedAt);
  } catch {
    return createNoDetectionResult(frameId, startedAt);
  }
}

function normalizeDetectionResult(
  result: Partial<NativeMarkerDetectionResult> | null | undefined,
  fallbackFrameId: number,
  startedAt: number,
): MarkerDetectionResult {
  if (!result) {
    return createNoDetectionResult(fallbackFrameId, startedAt);
  }

  return {
    found: result.found === true,
    candidateFound: result.candidateFound === true || result.found === true,
    frameId: Number.isFinite(result.frameId)
      ? Number(result.frameId)
      : fallbackFrameId,
    confidence: Number.isFinite(result.confidence)
      ? Number(result.confidence)
      : 0,
    corners: normalizeCorners(result.corners),
    rotationDegrees: normalizeRotation(result.rotationDegrees),
    imageUri: typeof result.imageUri === 'string' ? result.imageUri : undefined,
    imageWidth: Number.isFinite(result.imageWidth)
      ? Number(result.imageWidth)
      : undefined,
    imageHeight: Number.isFinite(result.imageHeight)
      ? Number(result.imageHeight)
      : undefined,
    processingTimeMs: Number.isFinite(result.processingTimeMs)
      ? Number(result.processingTimeMs)
      : elapsedMs(startedAt),
  };
}

function createNoDetectionResult(
  frameId: number,
  startedAt: number,
): MarkerDetectionResult {
  return {
    found: false,
    candidateFound: false,
    frameId,
    confidence: 0,
    corners: EMPTY_CORNERS,
    rotationDegrees: 0,
    imageUri: undefined,
    imageWidth: undefined,
    imageHeight: undefined,
    processingTimeMs: elapsedMs(startedAt),
  };
}

function normalizeCorners(corners: unknown): [Point, Point, Point, Point] {
  if (!Array.isArray(corners) || corners.length !== 4) {
    return EMPTY_CORNERS;
  }

  const normalized = corners.map(point => {
    if (!point || typeof point !== 'object') {
      return { x: 0, y: 0 };
    }

    const pointRecord = point as Partial<Point>;

    return {
      x: Number.isFinite(pointRecord.x) ? Number(pointRecord.x) : 0,
      y: Number.isFinite(pointRecord.y) ? Number(pointRecord.y) : 0,
    };
  });

  return normalized as [Point, Point, Point, Point];
}

function normalizeRotation(rotation: unknown): RotationDegrees {
  return rotation === 90 || rotation === 180 || rotation === 270 ? rotation : 0;
}

function elapsedMs(startedAt: number): number {
  return Math.max(0, Date.now() - startedAt);
}
