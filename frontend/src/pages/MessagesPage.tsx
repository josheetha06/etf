import { Anchor, Badge, Card, Grid, Group, ScrollArea, SegmentedControl, Stack, Text, Title, UnstyledButton } from '@mantine/core';
import { useState } from 'react';
import { Link } from 'react-router';
import { useMarkRead, useMessages } from '../api/hooks';
import type { MessageView } from '../api/types';
import { ActorBadge } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { browserZone, dateTimeIn, relative } from '../lib/format';

const DELIVERY: Record<string, { label: string; color: string }> = {
  scheduled: { label: 'Sends in a few minutes', color: 'blue' },
  queued: { label: 'Queued', color: 'gray' },
  sent: { label: 'Sent', color: 'ett' },
  delivered: { label: 'Delivered', color: 'ett' },
  opened: { label: 'Opened', color: 'ett' },
  bounced: { label: 'Bounced', color: 'red' },
  failed: { label: 'Not sent', color: 'red' },
};

export function MessagesPage() {
  const [folder, setFolder] = useState<'inbox' | 'sent'>(new URLSearchParams(window.location.search).get('folder') === 'sent' ? 'sent' : 'inbox');
  const { data, isLoading, error, refetch } = useMessages(folder);
  const markRead = useMarkRead();
  const [selected, setSelected] = useState<MessageView | null>(null);

  const open = (message: MessageView) => {
    setSelected(message);
    if (message.direction === 'INBOUND' && !message.readAt) {
      markRead.mutate(message.id);
    }
  };

  return (
    <>
      <PageHeader title="Messages" subtitle="Emails to and from candidates. Messages from the AI agent are labelled AI." />
      <Card p={0}>
        <Group p="md">
          <SegmentedControl
            value={folder}
            onChange={(value) => {
              setFolder(value as 'inbox' | 'sent');
              setSelected(null);
            }}
            data={[
              { value: 'inbox', label: 'From candidates' },
              { value: 'sent', label: 'Sent to candidates' },
            ]}
          />
        </Group>
        {isLoading && <Loading />}
        {error && <ErrorBlock error={error} onRetry={refetch} />}
        {data && data.length === 0 && <Empty title="No messages" />}
        {data && data.length > 0 && (
          <Grid gap={0}>
            <Grid.Col span={{ base: 12, md: 5 }} style={{ borderRight: '1px solid var(--mantine-color-gray-3)' }}>
              <ScrollArea.Autosize mah={620}>
                {data.map((message) => (
                  <UnstyledButton
                    key={message.id}
                    onClick={() => open(message)}
                    w="100%"
                    p="md"
                    style={{
                      borderTop: '1px solid var(--mantine-color-gray-2)',
                      background: selected?.id === message.id ? 'var(--mantine-color-ett-0)' : undefined,
                    }}
                  >
                    <Group justify="space-between" wrap="nowrap" gap="xs">
                      <Group gap={6} wrap="nowrap" style={{ minWidth: 0 }}>
                        {message.direction === 'INBOUND' && !message.readAt && <Badge size="xs" color="orange" circle> </Badge>}
                        <Text size="sm" fw={600} truncate>
                          {message.direction === 'INBOUND' ? message.candidateName : `To ${message.candidateName}`}
                        </Text>
                      </Group>
                      <Text size="xs" c="dimmed" style={{ flexShrink: 0 }}>
                        {relative(message.sentAt)}
                      </Text>
                    </Group>
                    <Text size="sm" truncate>
                      {message.subject}
                    </Text>
                    <Group gap={6} mt={4}>
                      <ActorBadge ai={message.senderKind === 'SYSTEM'} />
                      <Text size="xs" c="dimmed" truncate>
                        {message.jobTitle}
                      </Text>
                    </Group>
                  </UnstyledButton>
                ))}
              </ScrollArea.Autosize>
            </Grid.Col>
            <Grid.Col span={{ base: 12, md: 7 }}>
              {!selected ? (
                <Empty title="Choose a message to read it" />
              ) : (
                <Stack p="lg" gap="sm">
                  <Group gap="xs">
                    <ActorBadge ai={selected.senderKind === 'SYSTEM'} />
                    <Badge color="gray">Email</Badge>
                    {selected.direction === 'OUTBOUND' && selected.deliveryStatus && DELIVERY[selected.deliveryStatus] && (
                      <Badge color={DELIVERY[selected.deliveryStatus].color}>{DELIVERY[selected.deliveryStatus].label}</Badge>
                    )}
                  </Group>
                  <Title order={3} fz="lg">
                    {selected.subject}
                  </Title>
                  <Text size="sm" c="dimmed">
                    {selected.direction === 'INBOUND'
                      ? `From ${selected.candidateName}`
                      : `From ${selected.senderName} to ${selected.candidateName}`}{' '}
                    · {dateTimeIn(selected.sentAt, browserZone())}
                  </Text>
                  <Anchor component={Link} to={`/applications/${selected.applicationId}`} size="sm">
                    {selected.candidateName} · {selected.jobTitle}
                  </Anchor>
                  <Card withBorder bg="gray.0" mt="xs">
                    <Text size="sm" style={{ whiteSpace: 'pre-line' }}>
                      {selected.body}
                    </Text>
                  </Card>
                </Stack>
              )}
            </Grid.Col>
          </Grid>
        )}
      </Card>
    </>
  );
}
