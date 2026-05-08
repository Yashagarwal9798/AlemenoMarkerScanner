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
