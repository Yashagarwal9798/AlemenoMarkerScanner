import { StyleSheet, View } from 'react-native';

import type { Point } from '../types';

/**
 * Draws the detected quadrilateral corners on top of the camera preview.
 *
 * From LLD §12 MarkerOverlay:
 * - Maps frame coordinates to preview coordinates.
 * - Draws small circles at each corner position.
 * - Draws connecting lines between corners (via View borders).
 *
 * Uses absolute positioning — must be a sibling of the Camera within
 * the preview container.
 */
type MarkerOverlayProps = {
  /** Four clockwise corners in image coordinates. */
  corners: [Point, Point, Point, Point];
  /** Camera preview width in dp. */
  previewWidth: number;
  /** Camera preview height in dp. */
  previewHeight: number;
  /** Source image width in pixels. */
  imageWidth: number;
  /** Source image height in pixels. */
  imageHeight: number;
  /** Preferred visible scanning area in preview coordinates. */
  targetRect?: Rect;
};

export function MarkerOverlay({
  corners,
  previewWidth,
  previewHeight,
  imageWidth,
  imageHeight,
  targetRect,
}: MarkerOverlayProps) {
  if (imageWidth === 0 || imageHeight === 0) {
    return null;
  }

  const projectedCorners = chooseBestProjection({
    corners,
    imageWidth,
    imageHeight,
    previewWidth,
    previewHeight,
    targetRect:
      targetRect ?? {
        left: 0,
        top: 0,
        right: previewWidth,
        bottom: previewHeight,
      },
  });

  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      {projectedCorners.map((corner, index) => (
        <View
          key={`corner-${index}`}
          style={[
            styles.cornerDot,
            {
              left: corner.x - CORNER_RADIUS,
              top: corner.y - CORNER_RADIUS,
            },
          ]}
        />
      ))}
    </View>
  );
}

type Rect = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

type ProjectionInput = {
  corners: [Point, Point, Point, Point];
  imageWidth: number;
  imageHeight: number;
  previewWidth: number;
  previewHeight: number;
  targetRect: Rect;
};

type OrientedCorners = {
  corners: Point[];
  width: number;
  height: number;
};

function chooseBestProjection({
  corners,
  imageWidth,
  imageHeight,
  previewWidth,
  previewHeight,
  targetRect,
}: ProjectionInput): Point[] {
  const variants = createOrientationVariants(corners, imageWidth, imageHeight);
  const projected = variants.map(variant =>
    projectCover(variant, previewWidth, previewHeight),
  );

  return projected.reduce((best, current) =>
    scoreProjection(current, previewWidth, previewHeight, targetRect) >
    scoreProjection(best, previewWidth, previewHeight, targetRect)
      ? current
      : best,
  );
}

function createOrientationVariants(
  corners: Point[],
  width: number,
  height: number,
): OrientedCorners[] {
  const rotations: OrientedCorners[] = [
    { width, height, corners },
    {
      width: height,
      height: width,
      corners: corners.map(({ x, y }) => ({ x: height - y, y: x })),
    },
    {
      width,
      height,
      corners: corners.map(({ x, y }) => ({ x: width - x, y: height - y })),
    },
    {
      width: height,
      height: width,
      corners: corners.map(({ x, y }) => ({ x: y, y: width - x })),
    },
  ];

  return rotations.flatMap(variant => [
    variant,
    {
      ...variant,
      corners: variant.corners.map(({ x, y }) => ({
        x: variant.width - x,
        y,
      })),
    },
  ]);
}

function projectCover(
  variant: OrientedCorners,
  previewWidth: number,
  previewHeight: number,
): Point[] {
  // Camera preview uses resizeMode="cover": uniform scale, then centered crop.
  const scale = Math.max(
    previewWidth / variant.width,
    previewHeight / variant.height,
  );
  const renderedWidth = variant.width * scale;
  const renderedHeight = variant.height * scale;
  const offsetX = (previewWidth - renderedWidth) / 2;
  const offsetY = (previewHeight - renderedHeight) / 2;

  return variant.corners.map(({ x, y }) => ({
    x: x * scale + offsetX,
    y: y * scale + offsetY,
  }));
}

function scoreProjection(
  points: Point[],
  previewWidth: number,
  previewHeight: number,
  targetRect: Rect,
): number {
  const bounds = boundsFor(points);
  const center = {
    x: (bounds.left + bounds.right) / 2,
    y: (bounds.top + bounds.bottom) / 2,
  };
  const targetCenter = {
    x: (targetRect.left + targetRect.right) / 2,
    y: (targetRect.top + targetRect.bottom) / 2,
  };
  const targetWidth = targetRect.right - targetRect.left;
  const targetHeight = targetRect.bottom - targetRect.top;
  const targetDiagonal = Math.max(1, Math.hypot(targetWidth, targetHeight));
  const boxWidth = bounds.right - bounds.left;
  const boxHeight = bounds.bottom - bounds.top;
  const boxArea = Math.max(1, boxWidth * boxHeight);
  const previewArea = Math.max(1, previewWidth * previewHeight);

  const visiblePoints = points.filter(
    point =>
      point.x >= 0 &&
      point.x <= previewWidth &&
      point.y >= 0 &&
      point.y <= previewHeight,
  ).length;
  const targetPoints = points.filter(
    point =>
      point.x >= targetRect.left &&
      point.x <= targetRect.right &&
      point.y >= targetRect.top &&
      point.y <= targetRect.bottom,
  ).length;
  const centerDistance =
    Math.hypot(center.x - targetCenter.x, center.y - targetCenter.y) /
    targetDiagonal;
  const areaRatio = boxArea / previewArea;
  const areaScore = areaRatio > 0.002 && areaRatio < 0.75 ? 1 : 0;

  return (
    visiblePoints * 100 +
    targetPoints * 30 +
    areaScore * 20 -
    centerDistance * 50
  );
}

function boundsFor(points: Point[]): Rect {
  return {
    left: Math.min(...points.map(point => point.x)),
    top: Math.min(...points.map(point => point.y)),
    right: Math.max(...points.map(point => point.x)),
    bottom: Math.max(...points.map(point => point.y)),
  };
}

const CORNER_RADIUS = 6;

const styles = StyleSheet.create({
  cornerDot: {
    position: 'absolute',
    width: CORNER_RADIUS * 2,
    height: CORNER_RADIUS * 2,
    borderRadius: CORNER_RADIUS,
    backgroundColor: '#00ff88',
    borderWidth: 2,
    borderColor: '#ffffff',
  },
});
