import { Group, Text } from '@mantine/core';
import { IconWifiOff } from '@tabler/icons-react';
import { useEffect, useState } from 'react';

/** A thin bar while the browser is offline, so failed saves make sense. */
export function OfflineBanner() {
  const [online, setOnline] = useState(() => navigator.onLine);
  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => {
      window.removeEventListener('online', up);
      window.removeEventListener('offline', down);
    };
  }, []);
  if (online) return null;
  return (
    <Group className="offline-banner" justify="center" gap={8} role="status">
      <IconWifiOff size={16} />
      <Text size="sm" fw={500}>
        You're offline. Changes can't be saved until your connection is back.
      </Text>
    </Group>
  );
}
