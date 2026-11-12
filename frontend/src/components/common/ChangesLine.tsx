import { Group, Text, Tooltip } from '@mantine/core';
import { IconHistory } from '@tabler/icons-react';
import type { Changes } from '../../api/types';
import { istLong, relative } from '../../lib/format';

/** "Created by Charlotte Hughes, 3 Nov 2026 · Last updated by Oliver Grant, 10 Nov 2026, 14:05" for page headers. */
export function ChangesLine({ changes, createdLabel = 'Created' }: { changes: Changes | null | undefined; createdLabel?: string }) {
  if (!changes || (!changes.createdAt && !changes.lastUpdatedAt)) return null;
  const sameAsCreated = changes.lastUpdatedAt === changes.createdAt;
  return (
    <Group gap={6} wrap="wrap" c="dimmed">
      <IconHistory size={14} />
      <Text size="xs">
        {createdLabel} by {changes.createdByName ?? 'the system'}, {istLong(changes.createdAt)}
      </Text>
      {!sameAsCreated && changes.lastUpdatedAt && (
        <>
          <Text size="xs">·</Text>
          <Tooltip label={istLong(changes.lastUpdatedAt)}>
            <Text size="xs">
              Last updated by {changes.lastUpdatedByName ?? 'the system'}, {relative(changes.lastUpdatedAt)}
            </Text>
          </Tooltip>
        </>
      )}
    </Group>
  );
}

/** Two short lines for a table cell: who last changed it and when. */
export function ChangesCell({ changes }: { changes: Changes | null | undefined }) {
  if (!changes) return <Text size="sm">—</Text>;
  return (
    <Tooltip
      multiline
      w={260}
      label={`Created by ${changes.createdByName ?? 'the system'}, ${istLong(changes.createdAt)}. Last updated by ${
        changes.lastUpdatedByName ?? 'the system'
      }, ${istLong(changes.lastUpdatedAt)}.`}
    >
      <div>
        <Text size="sm" lineClamp={1} style={{ whiteSpace: 'nowrap' }}>
          {changes.lastUpdatedByName ?? 'System'}
        </Text>
        <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
          {relative(changes.lastUpdatedAt)}
        </Text>
      </div>
    </Tooltip>
  );
}
