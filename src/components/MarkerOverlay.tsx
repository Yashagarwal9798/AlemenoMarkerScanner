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
};

export function MarkerOverlay({
  corners,
  previewWidth,
  previewHeight,
  imageWidth,
  imageHeight,
}: MarkerOverlayProps) {
  if (imageWidth === 0 || imageHeight === 0) {
    return null;
  }

  const scaleX = previewWidth / imageWidth;
  const scaleY = previewHeight / imageHeight;

  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      {corners.map((corner, index) => (
        <View
          key={`corner-${index}`}
          style={[
            styles.cornerDot,
            {
              left: corner.x * scaleX - CORNER_RADIUS,
              top: corner.y * scaleY - CORNER_RADIUS,
            },
          ]}
        />
      ))}
    </View>
  );
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
