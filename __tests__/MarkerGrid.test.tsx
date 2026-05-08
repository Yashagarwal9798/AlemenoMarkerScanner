import React from 'react';
import { Image, StyleSheet } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';

import {
  MarkerGrid,
  PROCESSED_MARKER_SIZE,
} from '../src/components/MarkerGrid';
import { createMockMarkerCaptures } from '../src/store/mockMarkerCaptures';

test('renders mock marker results as 300x300 images', async () => {
  let tree: ReactTestRenderer.ReactTestRenderer | undefined;

  await ReactTestRenderer.act(() => {
    tree = ReactTestRenderer.create(
      <MarkerGrid captures={createMockMarkerCaptures(20)} />,
    );
  });

  const images = tree?.root.findAllByType(Image) ?? [];

  expect(images).toHaveLength(20);

  for (const image of images) {
    const imageStyle = StyleSheet.flatten(image.props.style);

    expect(imageStyle.width).toBe(PROCESSED_MARKER_SIZE);
    expect(imageStyle.height).toBe(PROCESSED_MARKER_SIZE);
  }
});
