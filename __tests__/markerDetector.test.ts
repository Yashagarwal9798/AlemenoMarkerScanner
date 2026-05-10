test('returns safe no-detection result when native module is unavailable', async () => {
  await jest.isolateModulesAsync(async () => {
    const { NativeModules } = require('react-native');

    delete NativeModules.NativeMarkerDetector;

    const { detectMarker } = require('../src/native/markerDetector');
    const result = await detectMarker({ frameId: 12 });

    expect(result).toMatchObject({
      found: false,
      frameId: 12,
      confidence: 0,
      rotationDegrees: 0,
      imageUri: undefined,
    });
    expect(result.corners).toHaveLength(4);
    expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
  });
});

test('normalizes native no-detection response', async () => {
  await jest.isolateModulesAsync(async () => {
    const { NativeModules } = require('react-native');
    const nativeDetectMarker = jest.fn().mockResolvedValue({
      found: false,
      frameId: 18,
      confidence: 0,
      corners: [
        { x: 0, y: 0 },
        { x: 0, y: 0 },
        { x: 0, y: 0 },
        { x: 0, y: 0 },
      ],
      rotationDegrees: 0,
      imageUri: null,
      processingTimeMs: 1.5,
    });

    NativeModules.NativeMarkerDetector = {
      detectMarker: nativeDetectMarker,
    };

    const { detectMarker } = require('../src/native/markerDetector');
    const result = await detectMarker({ frameId: 18 });

    expect(nativeDetectMarker).toHaveBeenCalledWith({ frameId: 18 });
    expect(result).toMatchObject({
      found: false,
      frameId: 18,
      confidence: 0,
      rotationDegrees: 0,
      processingTimeMs: 1.5,
    });
    expect(result.corners).toHaveLength(4);
  });
});

test('preserves native detected marker image uri', async () => {
  await jest.isolateModulesAsync(async () => {
    const { NativeModules } = require('react-native');
    const nativeDetectMarker = jest.fn().mockResolvedValue({
      found: true,
      frameId: 22,
      confidence: 0.91,
      corners: [
        { x: 10, y: 20 },
        { x: 110, y: 20 },
        { x: 110, y: 120 },
        { x: 10, y: 120 },
      ],
      rotationDegrees: 90,
      imageUri: 'file:///tmp/marker_22.jpg',
      processingTimeMs: 42,
    });

    NativeModules.NativeMarkerDetector = {
      detectMarker: nativeDetectMarker,
    };

    const { detectMarker } = require('../src/native/markerDetector');
    const result = await detectMarker({
      frameId: 22,
      imageUri: '/tmp/camera-frame.jpg',
    });

    expect(nativeDetectMarker).toHaveBeenCalledWith({
      frameId: 22,
      imageUri: '/tmp/camera-frame.jpg',
    });
    expect(result).toMatchObject({
      found: true,
      frameId: 22,
      confidence: 0.91,
      rotationDegrees: 90,
      imageUri: 'file:///tmp/marker_22.jpg',
      processingTimeMs: 42,
    });
    expect(result.corners).toHaveLength(4);
  });
});
