import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Linking,
  Pressable,
  StyleSheet,
  Text,
  View,
  type GestureResponderEvent,
  type LayoutChangeEvent,
} from 'react-native';
import {
  Camera,
  useCameraDevice,
  useCameraPermission,
  usePhotoOutput,
  type CameraRef,
  type PhotoFile,
} from 'react-native-vision-camera';

import { MarkerOverlay } from '../components';
import { useDetectionLoop, type CapturePhotoFn } from '../hooks';
import type { MarkerCapture, MarkerDetectionResult } from '../types';

type CameraScannerScreenProps = {
  captureCount: number;
  targetCount: number;
  isComplete: boolean;
  addCapture: (capture: MarkerCapture) => void;
  onAddMockCapture?: () => void;
};

export function CameraScannerScreen({
  captureCount,
  targetCount,
  isComplete,
  addCapture,
  onAddMockCapture,
}: CameraScannerScreenProps) {
  const { status, hasPermission, canRequestPermission, requestPermission } =
    useCameraPermission();
  const device = useCameraDevice('back');
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [isPreviewReady, setIsPreviewReady] = useState(false);
  const [lastDetection, setLastDetection] = useState<MarkerDetectionResult | null>(null);
  const [previewLayout, setPreviewLayout] = useState({ width: 0, height: 0 });
  const seenFrameIds = useRef(new Set<number>());
  const cameraRef = useRef<CameraRef>(null);
  const photoOutput = usePhotoOutput({ qualityPrioritization: 'speed' });

  useEffect(() => {
    if (status === 'not-determined') {
      requestPermission().catch(() => undefined);
    }
  }, [requestPermission, status]);

  // --- Detection result handler (LLD §11) ---
  const handleDetectionResult = useCallback(
    (result: MarkerDetectionResult) => {
      setLastDetection(result);

      if (!result.found || !result.imageUri) {
        return;
      }

      if (seenFrameIds.current.has(result.frameId)) {
        return;
      }

      seenFrameIds.current.add(result.frameId);

      addCapture({
        id: `${result.frameId}-${Date.now()}`,
        frameId: result.frameId,
        capturedAt: Date.now(),
        confidence: result.confidence,
        rotationDegrees: result.rotationDegrees,
        corners: result.corners,
        imageUri: result.imageUri!,
      });
    },
    [addCapture],
  );

  // Capture a photo from the camera for each detection cycle
  const capturePhoto: CapturePhotoFn = useCallback(async () => {
    if (!cameraRef.current) {
      return undefined;
    }
    try {
      const photo: PhotoFile = await photoOutput.capturePhotoToFile(
        { enableShutterSound: false },
        {},
      );
      return photo.filePath;
    } catch {
      return undefined;
    }
  }, [photoOutput]);

  // Detection loop with busy guard
  useDetectionLoop(
    hasPermission && isPreviewReady,
    isComplete,
    capturePhoto,
    handleDetectionResult,
  );

  const handleRequestPermission = useCallback(
    (_event?: GestureResponderEvent) => {
      requestPermission().catch(() => undefined);
    },
    [requestPermission],
  );

  const handleOpenSettings = useCallback(() => {
    Linking.openSettings().catch(() => undefined);
  }, []);

  const handlePreviewStarted = useCallback(() => {
    setCameraError(null);
    setIsPreviewReady(true);
  }, []);

  const handlePreviewStopped = useCallback(() => {
    setIsPreviewReady(false);
  }, []);

  const handleCameraError = useCallback((error: Error) => {
    setCameraError(error.message);
  }, []);

  const handlePreviewLayout = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setPreviewLayout({ width, height });
  }, []);

  // Status text for the footer
  const footerText = cameraError
    ? cameraError
    : lastDetection?.found
      ? `Marker detected! (${lastDetection.processingTimeMs.toFixed(0)}ms)`
      : lastDetection?.candidateFound
        ? 'Square found - checking marker...'
      : isPreviewReady
        ? 'Scanning for markers...'
        : 'Starting camera...';

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>Alemeno Marker Scanner</Text>
          <Text style={styles.subtitle}>
            {hasPermission ? 'Camera active' : 'Camera permission needed'}
          </Text>
        </View>
        <ProgressPill count={captureCount} target={targetCount} />
      </View>

      <View style={styles.preview} onLayout={handlePreviewLayout}>
        {hasPermission && device ? (
          <>
            <Camera
              ref={cameraRef}
              style={StyleSheet.absoluteFill}
              device={device}
              isActive={!isComplete}
              outputs={[photoOutput]}
              resizeMode="cover"
              onError={handleCameraError}
              onPreviewStarted={handlePreviewStarted}
              onPreviewStopped={handlePreviewStopped}
            />
            <ScannerFrame />
            {(lastDetection?.found || lastDetection?.candidateFound) &&
              previewLayout.width > 0 && (
              <MarkerOverlay
                corners={lastDetection.corners}
                previewWidth={previewLayout.width}
                previewHeight={previewLayout.height}
                imageWidth={lastDetection.imageWidth ?? previewLayout.width}
                imageHeight={lastDetection.imageHeight ?? previewLayout.height}
                targetRect={{
                  left: SCANNER_FRAME_HORIZONTAL_INSET,
                  top: SCANNER_FRAME_VERTICAL_INSET,
                  right: previewLayout.width - SCANNER_FRAME_HORIZONTAL_INSET,
                  bottom: previewLayout.height - SCANNER_FRAME_VERTICAL_INSET,
                }}
              />
            )}
            <View style={styles.previewStatus}>
              <Text style={styles.previewStatusText}>
                {isPreviewReady
                  ? `Scanning • ${captureCount}/${targetCount}`
                  : 'Starting camera'}
              </Text>
            </View>
          </>
        ) : (
          <PermissionPanel
            status={status}
            canRequestPermission={canRequestPermission}
            onRequestPermission={handleRequestPermission}
            onOpenSettings={handleOpenSettings}
          />
        )}
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>{footerText}</Text>
        {onAddMockCapture ? (
          <Pressable
            accessibilityLabel="Add sample marker"
            accessibilityRole="button"
            onPress={onAddMockCapture}
            style={({ pressed }) => [
              styles.debugCaptureButton,
              pressed && styles.debugCaptureButtonPressed,
            ]}
          >
            <Text style={styles.debugCaptureText}>+</Text>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

type ProgressPillProps = {
  count: number;
  target: number;
};

function ProgressPill({ count, target }: ProgressPillProps) {
  return (
    <View style={styles.progressPill}>
      <Text style={styles.progressText}>
        {count}/{target}
      </Text>
    </View>
  );
}

type PermissionPanelProps = {
  status: string;
  canRequestPermission: boolean;
  onRequestPermission: () => void;
  onOpenSettings: () => void;
};

function PermissionPanel({
  status,
  canRequestPermission,
  onRequestPermission,
  onOpenSettings,
}: PermissionPanelProps) {
  const isDenied = status === 'denied' || status === 'restricted';

  return (
    <View style={styles.permissionPanel}>
      <Text style={styles.permissionTitle}>
        {isDenied ? 'Camera access blocked' : 'Camera access required'}
      </Text>
      <Text style={styles.permissionBody}>
        {isDenied
          ? 'Enable camera access in Android settings to scan markers.'
          : 'Allow camera access to start the scanner.'}
      </Text>
      <Pressable
        accessibilityRole="button"
        onPress={canRequestPermission ? onRequestPermission : onOpenSettings}
        style={({ pressed }) => [
          styles.primaryButton,
          pressed && styles.primaryButtonPressed,
        ]}
      >
        <Text style={styles.primaryButtonText}>
          {canRequestPermission ? 'Allow Camera' : 'Open Settings'}
        </Text>
      </Pressable>
    </View>
  );
}

function ScannerFrame() {
  return (
    <View pointerEvents="none" style={styles.scannerFrame}>
      <View style={styles.cornerTopLeft} />
      <View style={styles.cornerTopRight} />
      <View style={styles.cornerBottomLeft} />
      <View style={styles.cornerBottomRight} />
    </View>
  );
}

const cornerBase = {
  position: 'absolute' as const,
  width: 42,
  height: 42,
  borderColor: '#f5f5f0',
};

const SCANNER_FRAME_HORIZONTAL_INSET = 24;
const SCANNER_FRAME_VERTICAL_INSET = 36;

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#101214',
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 14,
  },
  title: {
    color: '#f5f5f0',
    fontSize: 20,
    fontWeight: '700',
  },
  subtitle: {
    color: '#aab4bd',
    fontSize: 13,
    fontWeight: '600',
    marginTop: 3,
  },
  progressPill: {
    alignItems: 'center',
    backgroundColor: '#f5f5f0',
    borderRadius: 6,
    height: 34,
    justifyContent: 'center',
    minWidth: 62,
  },
  progressText: {
    color: '#101214',
    fontSize: 15,
    fontWeight: '700',
  },
  preview: {
    backgroundColor: '#1d2227',
    borderColor: '#323b43',
    borderRadius: 8,
    borderWidth: 1,
    flex: 1,
    overflow: 'hidden',
  },
  previewStatus: {
    alignSelf: 'center',
    backgroundColor: 'rgba(16, 18, 20, 0.78)',
    borderRadius: 6,
    bottom: 16,
    paddingHorizontal: 12,
    paddingVertical: 8,
    position: 'absolute',
  },
  previewStatusText: {
    color: '#f5f5f0',
    fontSize: 13,
    fontWeight: '700',
  },
  permissionPanel: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  permissionTitle: {
    color: '#f5f5f0',
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
  },
  permissionBody: {
    color: '#c8d0d8',
    fontSize: 15,
    lineHeight: 21,
    marginTop: 10,
    maxWidth: 300,
    textAlign: 'center',
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: '#f5f5f0',
    borderRadius: 7,
    height: 44,
    justifyContent: 'center',
    marginTop: 20,
    minWidth: 148,
    paddingHorizontal: 18,
  },
  primaryButtonPressed: {
    opacity: 0.82,
  },
  primaryButtonText: {
    color: '#101214',
    fontSize: 15,
    fontWeight: '700',
  },
  scannerFrame: {
    bottom: SCANNER_FRAME_VERTICAL_INSET,
    left: SCANNER_FRAME_HORIZONTAL_INSET,
    position: 'absolute',
    right: SCANNER_FRAME_HORIZONTAL_INSET,
    top: SCANNER_FRAME_VERTICAL_INSET,
  },
  cornerTopLeft: {
    ...cornerBase,
    borderLeftWidth: 4,
    borderTopWidth: 4,
    left: 0,
    top: 0,
  },
  cornerTopRight: {
    ...cornerBase,
    borderRightWidth: 4,
    borderTopWidth: 4,
    right: 0,
    top: 0,
  },
  cornerBottomLeft: {
    ...cornerBase,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
    bottom: 0,
    left: 0,
  },
  cornerBottomRight: {
    ...cornerBase,
    borderBottomWidth: 4,
    borderRightWidth: 4,
    bottom: 0,
    right: 0,
  },
  footer: {
    alignItems: 'center',
    backgroundColor: '#f5f5f0',
    borderRadius: 8,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 14,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  footerText: {
    color: '#101214',
    flex: 1,
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'left',
  },
  debugCaptureButton: {
    alignItems: 'center',
    backgroundColor: '#101214',
    borderRadius: 6,
    height: 34,
    justifyContent: 'center',
    marginLeft: 12,
    width: 34,
  },
  debugCaptureButtonPressed: {
    opacity: 0.82,
  },
  debugCaptureText: {
    color: '#f5f5f0',
    fontSize: 22,
    fontWeight: '700',
  },
});
