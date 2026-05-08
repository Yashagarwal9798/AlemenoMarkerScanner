import { Image, ScrollView, StyleSheet, Text, View } from 'react-native';

import type { MarkerCapture } from '../types';

export const PROCESSED_MARKER_SIZE = 300;

type MarkerGridProps = {
  captures: MarkerCapture[];
};

export function MarkerGrid({ captures }: MarkerGridProps) {
  return (
    <ScrollView
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      {captures.map((capture, index) => (
        <View key={capture.id} style={styles.item}>
          <Image
            accessibilityLabel={`Processed marker ${index + 1}`}
            resizeMode="stretch"
            source={{ uri: capture.imageUri }}
            style={styles.markerImage}
          />
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{index + 1}</Text>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    alignItems: 'center',
    gap: 14,
    paddingBottom: 18,
  },
  item: {
    backgroundColor: '#f5f5f0',
    height: PROCESSED_MARKER_SIZE,
    position: 'relative',
    width: PROCESSED_MARKER_SIZE,
  },
  markerImage: {
    height: PROCESSED_MARKER_SIZE,
    width: PROCESSED_MARKER_SIZE,
  },
  badge: {
    alignItems: 'center',
    backgroundColor: '#101214',
    borderRadius: 5,
    height: 26,
    justifyContent: 'center',
    left: 8,
    minWidth: 26,
    paddingHorizontal: 7,
    position: 'absolute',
    top: 8,
  },
  badgeText: {
    color: '#f5f5f0',
    fontSize: 12,
    fontWeight: '700',
  },
});
