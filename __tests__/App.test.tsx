/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';

jest.mock('react-native-vision-camera', () => {
  const ReactMock = require('react');
  const { View } = require('react-native');

  return {
    Camera: () => ReactMock.createElement(View, { testID: 'camera-preview' }),
    useCameraPermission: () => ({
      status: 'authorized',
      hasPermission: true,
      canRequestPermission: false,
      requestPermission: jest.fn().mockResolvedValue(true),
    }),
  };
});

import App from '../App';

test('renders correctly', async () => {
  await ReactTestRenderer.act(() => {
    ReactTestRenderer.create(<App />);
  });
});
