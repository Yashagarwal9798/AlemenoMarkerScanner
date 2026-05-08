import { useCallback } from 'react';
import { StatusBar, StyleSheet, useColorScheme } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import { useMarkerScanner } from './src/hooks';
import { CameraScannerScreen, ResultsScreen } from './src/screens';
import { createMockMarkerCapture } from './src/store';

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  const scanner = useMarkerScanner();

  const handleAddMockCapture = useCallback(() => {
    scanner.addCapture(createMockMarkerCapture(scanner.captureCount + 1));
  }, [scanner]);

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <SafeAreaView style={styles.container}>
        {scanner.isComplete ? (
          <ResultsScreen
            captures={scanner.captures}
            targetCount={scanner.targetCount}
            onScanAgain={scanner.reset}
          />
        ) : (
          <CameraScannerScreen
            captureCount={scanner.captureCount}
            targetCount={scanner.targetCount}
            isComplete={scanner.isComplete}
            addCapture={scanner.addCapture}
            onAddMockCapture={__DEV__ ? handleAddMockCapture : undefined}
          />
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#101214',
  },
});

export default App;
