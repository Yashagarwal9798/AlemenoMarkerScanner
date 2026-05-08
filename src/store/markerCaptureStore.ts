import type { MarkerCapture } from '../types';

export const MARKER_CAPTURE_TARGET = 20;

export type MarkerCaptureState = {
  captures: MarkerCapture[];
  targetCount: number;
};

export type MarkerCaptureAction =
  | {
      type: 'capture/add';
      capture: MarkerCapture;
    }
  | {
      type: 'capture/reset';
    };

export function createMarkerCaptureState(
  targetCount = MARKER_CAPTURE_TARGET,
): MarkerCaptureState {
  return {
    captures: [],
    targetCount,
  };
}

export function markerCaptureReducer(
  state: MarkerCaptureState,
  action: MarkerCaptureAction,
): MarkerCaptureState {
  switch (action.type) {
    case 'capture/add':
      return addCapture(state, action.capture);
    case 'capture/reset':
      return createMarkerCaptureState(state.targetCount);
    default:
      return state;
  }
}

export function addCapture(
  state: MarkerCaptureState,
  capture: MarkerCapture,
): MarkerCaptureState {
  if (state.captures.length >= state.targetCount) {
    return state;
  }

  if (state.captures.some(item => item.frameId === capture.frameId)) {
    return state;
  }

  return {
    ...state,
    captures: [...state.captures, capture].slice(0, state.targetCount),
  };
}
