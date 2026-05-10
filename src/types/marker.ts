export type Point = {
  x: number;
  y: number;
};

export type RotationDegrees = 0 | 90 | 180 | 270;

export type MarkerDetectionResult = {
  found: boolean;
  candidateFound?: boolean;
  frameId: number;
  confidence: number;
  corners: [Point, Point, Point, Point];
  rotationDegrees: RotationDegrees;
  imageUri?: string;
  imageWidth?: number;
  imageHeight?: number;
  processingTimeMs: number;
};

export type MarkerCapture = {
  id: string;
  frameId: number;
  capturedAt: number;
  confidence: number;
  rotationDegrees: RotationDegrees;
  corners: [Point, Point, Point, Point];
  imageUri: string;
};
