import { Group, Text } from '@mantine/core';
import type { Branding } from '../api/types';

/**
 * The client's own logo (uploaded in Settings), or their name when there isn't one.
 * The version in the URL makes the browser fetch a new logo as soon as it changes.
 */
export function ClientLogo({ branding, height = 34, publicLogo = false }: {
  branding: Branding | null | undefined;
  height?: number;
  publicLogo?: boolean;
}) {
  if (!branding?.companyName) return null;
  if (!branding.hasLogo) {
    return (
      <Group gap={8} wrap="nowrap">
        <Text fw={700} c="navy.9" size="lg" lh={1.1} style={{ whiteSpace: 'nowrap' }}>
          {branding.companyName}
        </Text>
      </Group>
    );
  }
  const version = branding.logoUpdatedAt ? new Date(branding.logoUpdatedAt).getTime() : 0;
  return (
    <img
      src={`/api/${publicLogo ? 'public/' : ''}branding/logo?v=${version}`}
      alt={branding.companyName}
      style={{ height, width: 'auto', maxWidth: 220, objectFit: 'contain', display: 'block' }}
    />
  );
}
