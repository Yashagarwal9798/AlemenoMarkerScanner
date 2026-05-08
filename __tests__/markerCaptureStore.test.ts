import {
  createMarkerCaptureState,
  markerCaptureReducer,
} from '../src/store/markerCaptureStore';
import { createMockMarkerCapture } from '../src/store/mockMarkerCaptures';

test('stops accepting captures at the target count', () => {
  let state = createMarkerCaptureState(20);

  for (let index = 1; index <= 25; index += 1) {
    state = markerCaptureReducer(state, {
      type: 'capture/add',
      capture: createMockMarkerCapture(index),
    });
  }

  expect(state.captures).toHaveLength(20);
  expect(state.captures[state.captures.length - 1]?.frameId).toBe(20);
});

test('ignores duplicate frame ids', () => {
  const firstCapture = createMockMarkerCapture(1);
  let state = createMarkerCaptureState(20);

  state = markerCaptureReducer(state, {
    type: 'capture/add',
    capture: firstCapture,
  });
  state = markerCaptureReducer(state, {
    type: 'capture/add',
    capture: firstCapture,
  });

  expect(state.captures).toHaveLength(1);
});

test('reset clears all captures', () => {
  let state = createMarkerCaptureState(20);

  state = markerCaptureReducer(state, {
    type: 'capture/add',
    capture: createMockMarkerCapture(1),
  });
  state = markerCaptureReducer(state, { type: 'capture/reset' });

  expect(state.captures).toHaveLength(0);
  expect(state.targetCount).toBe(20);
});
