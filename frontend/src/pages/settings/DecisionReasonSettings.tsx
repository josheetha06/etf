import { ActionIcon, Alert, Button, Card, Group, Stack, Text, Textarea, Title, Tooltip } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconInfoCircle, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useDecisionReasons, useSaveDecisionReasons } from '../../api/hooks';
import { Loading } from '../../components/QueryState';
import { notifyError, notifySuccess } from '../../lib/notify';

const MAX = 20;

/**
 * Ready-made reasons HR can pick from when a candidate isn't progressing after a round. HR can still edit the
 * wording before sending. The candidate sees the reason, so keep it kind and specific.
 */
export function DecisionReasonSettings({ admin }: { admin: boolean }) {
  const reasons = useDecisionReasons();
  if (!reasons.data) return <Loading />;
  return <ReasonsForm key={reasons.data.join('|')} admin={admin} initial={reasons.data} />;
}

function ReasonsForm({ admin, initial }: { admin: boolean; initial: string[] }) {
  const [list, setList] = useState<string[]>(initial);
  const [adding, setAdding] = useState('');
  const save = useSaveDecisionReasons();
  const clean = list.map((r) => r.trim().replace(/\s+/g, ' ')).filter(Boolean);
  const seen = new Set<string>();
  const duplicate = clean.find((r) => {
    const key = r.toLowerCase();
    if (seen.has(key)) return true;
    seen.add(key);
    return false;
  });
  const tooShort = clean.find((r) => r.length < 10);
  const dirty = clean.join('\n') !== initial.join('\n');
  const valid = !duplicate && !tooShort && clean.length <= MAX;

  const move = (i: number, by: number) => {
    const next = [...list];
    const [item] = next.splice(i, 1);
    next.splice(i + by, 0, item);
    setList(next);
  };
  const add = () => {
    if (!adding.trim() || list.length >= MAX) return;
    setList([...list, adding.trim()]);
    setAdding('');
  };

  return (
    <Card maw={820}>
      <Title order={4} className="section-title">
        Reasons for "Not progressing"
      </Title>
      <Text size="sm" c="dimmed" mb="md">
        HR picks one of these when a candidate isn't progressing after a round, and can edit the wording before sending. The
        candidate sees the reason in their email and can reply to challenge it.
      </Text>
      <Alert variant="light" color="blue" icon={<IconInfoCircle size={18} />} mb="md" py="xs">
        <Text size="sm">Write to the candidate ("Your notice period…"), and keep each reason kind and specific.</Text>
      </Alert>
      <Stack gap={6}>
        {list.length === 0 && (
          <Text size="sm" c="dimmed">
            No ready-made reasons. HR types a reason each time.
          </Text>
        )}
        {list.map((r, i) => (
          <Group key={i} gap="xs" wrap="nowrap" align="flex-start">
            <Text size="sm" c="dimmed" w={20} ta="right" mt={8}>
              {i + 1}
            </Text>
            <Textarea
              value={r}
              onChange={(e) => setList(list.map((x, j) => (j === i ? e.currentTarget.value : x)))}
              maxLength={300}
              disabled={!admin}
              autosize
              minRows={1}
              style={{ flex: 1 }}
              aria-label={`Reason ${i + 1}`}
              error={r.trim() && r.trim().length < 10 ? 'At least 10 characters' : null}
            />
            {admin && (
              <Group gap={0} wrap="nowrap" mt={4}>
                <ActionIcon variant="subtle" color="gray" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
                  <IconArrowUp size={15} />
                </ActionIcon>
                <ActionIcon variant="subtle" color="gray" disabled={i === list.length - 1} onClick={() => move(i, 1)} aria-label="Move down">
                  <IconArrowDown size={15} />
                </ActionIcon>
                <Tooltip label="Remove">
                  <ActionIcon variant="subtle" color="red" onClick={() => setList(list.filter((_, j) => j !== i))} aria-label="Remove reason">
                    <IconTrash size={15} />
                  </ActionIcon>
                </Tooltip>
              </Group>
            )}
          </Group>
        ))}
      </Stack>
      {admin && (
        <>
          <Group mt="sm" gap="xs" wrap="nowrap" align="flex-start">
            <Textarea
              placeholder="e.g. We were looking for more experience leading a team."
              value={adding}
              onChange={(e) => setAdding(e.currentTarget.value)}
              maxLength={300}
              autosize
              minRows={1}
              style={{ flex: 1 }}
              ml={28}
              aria-label="New reason"
            />
            <Button variant="light" leftSection={<IconPlus size={14} />} onClick={add} disabled={adding.trim().length < 10 || list.length >= MAX}>
              Add
            </Button>
          </Group>
          {duplicate && (
            <Text size="xs" c="red" mt={4}>
              This reason is in the list twice: "{duplicate}"
            </Text>
          )}
          <Group justify="flex-end" mt="md">
            <Button
              onClick={() =>
                save.mutate(clean, {
                  onSuccess: () => notifySuccess('Reasons saved.'),
                  onError: (e) => notifyError(e),
                })
              }
              disabled={!dirty || !valid}
              loading={save.isPending}
            >
              Save reasons
            </Button>
          </Group>
        </>
      )}
      {!admin && (
        <Text size="xs" c="dimmed" mt="md">
          Only HR admins can change these.
        </Text>
      )}
    </Card>
  );
}
