import { Pressable, StyleSheet, Text, View } from 'react-native';

import { MarkerGrid } from '../components';
import type { MarkerCapture } from '../types';

type ResultsScreenProps = {
  captures: MarkerCapture[];
  targetCount: number;
  onScanAgain: () => void;
};

export function ResultsScreen({
  captures,
  targetCount,
  onScanAgain,
}: ResultsScreenProps) {
  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>Processed Markers</Text>
          <Text style={styles.subtitle}>
            {captures.length}/{targetCount} captured
          </Text>
        </View>
        <Pressable
          accessibilityLabel="Scan again"
          accessibilityRole="button"
          onPress={onScanAgain}
          style={({ pressed }) => [
            styles.scanAgainButton,
            pressed && styles.scanAgainButtonPressed,
          ]}
        >
          <Text style={styles.scanAgainText}>Scan Again</Text>
        </Pressable>
      </View>

      <MarkerGrid captures={captures} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#101214',
    paddingHorizontal: 10,
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
  scanAgainButton: {
    alignItems: 'center',
    backgroundColor: '#f5f5f0',
    borderRadius: 7,
    height: 38,
    justifyContent: 'center',
    minWidth: 104,
    paddingHorizontal: 12,
  },
  scanAgainButtonPressed: {
    opacity: 0.82,
  },
  scanAgainText: {
    color: '#101214',
    fontSize: 14,
    fontWeight: '700',
  },
});
