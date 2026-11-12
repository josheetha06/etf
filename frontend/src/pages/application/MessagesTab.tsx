import { Badge, Button, Card, Group, Select, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useMe, useSendMessage, useTemplates } from '../../api/hooks';
import type { ApplicationDetail } from '../../api/types';
import { ActorBadge } from '../../components/Badges';
import { Empty } from '../../components/QueryState';
import { istLong } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';

export function MessagesTab({ detail }: { detail: ApplicationDetail }) {
  const { data: me } = useMe();
  return (
    <Stack>
      {me?.canManageHiring && <Composer detail={detail} />}
      {detail.messages.length === 0 && <Empty title="No messages yet" />}
      {detail.messages.map((message) => (
        <Card key={message.id} padding="md" bg={message.direction === 'INBOUND' ? 'ett.0' : undefined}>
          <Group justify="space-between" mb={4}>
            <Group gap="xs">
              <Text size="sm" fw={600}>
                {message.direction === 'INBOUND' ? detail.candidate.fullName : message.senderName ?? 'ETT'}
              </Text>
              {message.direction === 'OUTBOUND' && <ActorBadge ai={message.senderKind === 'SYSTEM'} />}
              {message.direction === 'INBOUND' && <Badge size="sm" variant="light">From the candidate</Badge>}
              {message.direction === 'OUTBOUND' && message.deliveryStatus === 'scheduled' && (
                <Badge size="sm" variant="light" color="blue">
                  Sends in a few minutes
                </Badge>
              )}
            </Group>
            <Text size="xs" c="dimmed">
              {istLong(message.sentAt)}
            </Text>
          </Group>
          {message.subject && (
            <Text size="sm" fw={500}>
              {message.subject}
            </Text>
          )}
          <Text size="sm" style={{ whiteSpace: 'pre-line' }} mt={4}>
            {message.body}
          </Text>
        </Card>
      ))}
    </Stack>
  );
}

function Composer({ detail }: { detail: ApplicationDetail }) {
  const [open, setOpen] = useState(false);
  const templates = useTemplates(detail.id, open);
  const send = useSendMessage(detail.id);
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  if (!open) {
    return (
      <Group>
        <Button variant="light" onClick={() => setOpen(true)}>
          Write to {detail.candidate.fullName.split(' ')[0]}
        </Button>
      </Group>
    );
  }
  return (
    <Card>
      <Stack gap="sm">
        <Select
          label="Start from a template"
          placeholder="Choose a template"
          data={templates.data?.map((t) => ({ value: t.key, label: t.label })) ?? []}
          onChange={(key) => {
            const t = templates.data?.find((x) => x.key === key);
            if (t) {
              setSubject(t.subject);
              setBody(t.body);
            }
          }}
          clearable
          maw={360}
        />
        <TextInput label="Subject" value={subject} onChange={(e) => setSubject(e.currentTarget.value)} maxLength={200} />
        <Textarea label="Message" autosize minRows={6} value={body} onChange={(e) => setBody(e.currentTarget.value)} maxLength={8000} />
        <Text size="xs" c="dimmed">
          Sent by email to {detail.candidate.email}. Replies arrive in Messages.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            disabled={!subject.trim() || !body.trim()}
            loading={send.isPending}
            onClick={() =>
              send.mutate(
                { subject: subject.trim(), body: body.trim() },
                {
                  onSuccess: () => {
                    notifySuccess('Message sent.');
                    setOpen(false);
                    setSubject('');
                    setBody('');
                  },
                  onError: (e) => notifyError(e),
                },
              )
            }
          >
            Send
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}
