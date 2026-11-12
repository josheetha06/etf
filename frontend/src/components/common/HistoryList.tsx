import { Anchor, Badge, Button, Group, Stack, Text, Timeline } from '@mantine/core';
import { IconBriefcase, IconCalendarEvent, IconUser } from '@tabler/icons-react';
import { Link } from 'react-router';
import { useAudit, type AuditFilter } from '../../api/hooks';
import type { AuditEntry } from '../../api/types';
import { istLong } from '../../lib/format';
import { ErrorBlock, Loading } from '../QueryState';

const TYPE_ICON = { job: IconBriefcase, candidate: IconUser, interview: IconCalendarEvent };

/** Every change to a job, candidate or interview: who, when and exactly what changed. */
export function HistoryList({ filter, showRecord = false, empty = 'No changes recorded yet.' }: {
  filter: AuditFilter;
  showRecord?: boolean;
  empty?: string;
}) {
  const { data, isLoading, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useAudit(filter);
  if (isLoading) return <Loading />;
  if (error) return <ErrorBlock error={error} onRetry={refetch} />;
  const items = data?.pages.flatMap((p) => p.items) ?? [];
  if (items.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        {empty}
      </Text>
    );
  }
  return (
    <Stack gap="md">
      <Timeline bulletSize={26} lineWidth={2}>
        {items.map((entry) => (
          <Timeline.Item key={entry.id} bullet={<EntryIcon entry={entry} />} title={<EntryTitle entry={entry} showRecord={showRecord} />}>
            <EntryChanges entry={entry} />
            <Text size="xs" c="dimmed" mt={4}>
              {istLong(entry.at)}
            </Text>
          </Timeline.Item>
        ))}
      </Timeline>
      {hasNextPage && (
        <Group>
          <Button variant="default" size="xs" onClick={() => fetchNextPage()} loading={isFetchingNextPage}>
            Show older changes
          </Button>
        </Group>
      )}
    </Stack>
  );
}

function EntryIcon({ entry }: { entry: AuditEntry }) {
  const Icon = TYPE_ICON[entry.type];
  return <Icon size={14} />;
}

function EntryTitle({ entry, showRecord }: { entry: AuditEntry; showRecord: boolean }) {
  // Inline only: the timeline puts the title inside a paragraph.
  return (
    <Text span size="sm">
      <Text span size="sm" fw={600}>
        {entry.who}
      </Text>{' '}
      {entry.action.charAt(0).toLowerCase() + entry.action.slice(1)}
      {showRecord && entry.recordName && (
        <>
          {' '}
          {entry.link ? (
            <Anchor component={Link} to={entry.link} size="sm">
              {entry.recordName}
            </Anchor>
          ) : (
            entry.recordName
          )}
        </>
      )}
    </Text>
  );
}

export function EntryChanges({ entry }: { entry: AuditEntry }) {
  if (entry.changes.length === 0) {
    return entry.detail ? (
      <Text size="sm" c="dimmed">
        {entry.detail}
      </Text>
    ) : null;
  }
  return (
    <Stack gap={2}>
      {entry.changes.slice(0, 8).map((c, i) => (
        <Text key={i} size="sm">
          <Text span c="dimmed" size="sm">
            {c.field}:{' '}
          </Text>
          {c.from !== null && (
            <>
              <Text span td="line-through" c="dimmed" size="sm">
                {c.from}
              </Text>{' '}
              →{' '}
            </>
          )}
          {c.to ?? <Badge component="span" size="xs" color="gray" variant="light">removed</Badge>}
        </Text>
      ))}
      {entry.changes.length > 8 && (
        <Text size="xs" c="dimmed">
          and {entry.changes.length - 8} more
        </Text>
      )}
    </Stack>
  );
}
