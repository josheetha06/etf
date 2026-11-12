import { ActionIcon, Button, Card, Group, Stack, Text, Textarea, Tooltip } from '@mantine/core';
import { IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useAddNote, useDeleteNote, useNotes } from '../../api/hooks';
import { Empty } from '../../components/QueryState';
import { istLong } from '../../lib/format';
import { notifyError } from '../../lib/notify';

/** Private notes for the hiring team. Candidates never see them. */
export function NotesTab({ applicationId }: { applicationId: string }) {
  const { data } = useNotes(applicationId);
  const add = useAddNote(applicationId);
  const remove = useDeleteNote(applicationId);
  const [body, setBody] = useState('');
  return (
    <Stack>
      <Card>
        <Stack gap="xs">
          <Textarea
            placeholder="Add a note for the hiring team (the candidate never sees notes)"
            autosize
            minRows={2}
            maxLength={4000}
            value={body}
            onChange={(e) => setBody(e.currentTarget.value)}
          />
          <Group justify="flex-end">
            <Button
              size="sm"
              disabled={!body.trim()}
              loading={add.isPending}
              onClick={() => add.mutate(body.trim(), { onSuccess: () => setBody(''), onError: (e) => notifyError(e) })}
            >
              Add note
            </Button>
          </Group>
        </Stack>
      </Card>
      {data?.length === 0 && <Empty title="No notes yet">Phone calls, references and anything else the team should know.</Empty>}
      {data?.map((note) => (
        <Card key={note.id} padding="md">
          <Group justify="space-between" align="flex-start" wrap="nowrap">
            <Stack gap={4}>
              <Text size="sm" style={{ whiteSpace: 'pre-line' }}>
                {note.body}
              </Text>
              <Text size="xs" c="dimmed">
                {note.authorName} · {istLong(note.createdAt)}
              </Text>
            </Stack>
            {note.mine && (
              <Tooltip label="Delete note">
                <ActionIcon variant="subtle" color="gray" onClick={() => remove.mutate(note.id, { onError: (e) => notifyError(e) })} aria-label="Delete note">
                  <IconTrash size={16} />
                </ActionIcon>
              </Tooltip>
            )}
          </Group>
        </Card>
      ))}
    </Stack>
  );
}
