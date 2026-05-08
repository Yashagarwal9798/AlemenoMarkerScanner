import { useCallback, useMemo, useReducer } from 'react';

import type { MarkerCapture } from '../types';
import {
  MARKER_CAPTURE_TARGET,
  createMarkerCaptureState,
  markerCaptureReducer,
} from '../store/markerCaptureStore';

export function useMarkerScanner(targetCount = MARKER_CAPTURE_TARGET) {
  const [state, dispatch] = useReducer(
    markerCaptureReducer,
    targetCount,
    createMarkerCaptureState,
  );

  const addCapture = useCallback((capture: MarkerCapture) => {
    dispatch({ type: 'capture/add', capture });
  }, []);

  const reset = useCallback(() => {
    dispatch({ type: 'capture/reset' });
  }, []);

  return useMemo(
    () => ({
      captures: state.captures,
      captureCount: state.captures.length,
      targetCount: state.targetCount,
      isComplete: state.captures.length >= state.targetCount,
      addCapture,
      reset,
    }),
    [addCapture, reset, state.captures, state.targetCount],
  );
}
