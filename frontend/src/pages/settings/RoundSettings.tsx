import { ActionIcon, Alert, Button, Card, Grid, Group, Stack, Text, TextInput, Title, Tooltip } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconInfoCircle, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useMeasures, useRoundPlan, useSaveMeasures, useSaveRoundPlan } from '../../api/hooks';
import type { Round } from '../../api/types';
import { PlanPreview, RoundsBuilder, roundsReady, toDrafts, toInputs, type RoundDraft } from '../../components/job/RoundsBuilder';
import { Loading } from '../../components/QueryState';
import { notifyError, notifySuccess } from '../../lib/notify';

/** The default interview rounds a new job starts with, and the measures every panel review scores. */
export function RoundSettings({ admin }: { admin: boolean }) {
  const plan = useRoundPlan();
  const measures = useMeasures();
  if (!plan.data || !measures.data) return <Loading />;
  return (
    <Stack gap="xl">
      <PlanForm admin={admin} initial={plan.data} />
      <MeasuresForm admin={admin} initial={measures.data} />
    </Stack>
  );
}

function PlanForm({ admin, initial }: { admin: boolean; initial: Round[] }) {
  const save = useSaveRoundPlan();
  const [rounds, setRounds] = useState<RoundDraft[]>(() => toDrafts(initial));
  const [dirty, setDirty] = useState(false);
  const submit = () =>
    !save.isPending &&
    save.mutate(toInputs(rounds), {
      onSuccess: () => {
        setDirty(false);
        notifySuccess('Default rounds saved. New jobs start with this plan; existing jobs keep theirs.');
      },
      onError: (e) => notifyError(e),
    });
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 8 }}>
        <Stack>
          <div>
            <Title order={4} className="section-title">
              Default interview rounds
            </Title>
            <Text size="sm" c="dimmed">
              Every new job starts with these rounds. Recruiters can change them for each job.
            </Text>
          </div>
          {admin ? (
            <RoundsBuilder
              value={rounds}
              onChange={(r) => {
                setRounds(r);
                setDirty(true);
              }}
            />
          ) : (
            <Card>
              <PlanPreview rounds={toInputs(rounds)} aiShortlisting />
            </Card>
          )}
          {admin && (
            <Group justify="flex-end">
              <Button onClick={submit} disabled={!dirty || !roundsReady(rounds)} loading={save.isPending}>
                Save default rounds
              </Button>
            </Group>
          )}
        </Stack>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 4 }}>
        <Alert variant="light" icon={<IconInfoCircle size={18} />} title="How rounds work">
          <Stack gap={6}>
            <Text size="sm">Up to 8 rounds, in any order. The same type can be used more than once.</Text>
            <Text size="sm">After each round you read the report and decide: progress, hold or not progressing.</Text>
            <Text size="sm">The target time flags a candidate as overdue on the Overview if there's no decision in time.</Text>
          </Stack>
        </Alert>
      </Grid.Col>
    </Grid>
  );
}

function MeasuresForm({ admin, initial }: { admin: boolean; initial: string[] }) {
  const save = useSaveMeasures();
  const [list, setList] = useState<string[]>(initial);
  const [adding, setAdding] = useState('');
  const dirty = JSON.stringify(list) !== JSON.stringify(initial);
  const clean = list.map((m) => m.trim());
  const duplicate = clean.find((m, i) => m && clean.findIndex((x) => x.toLowerCase() === m.toLowerCase()) !== i);
  const valid = clean.length > 0 && clean.every((m) => m.length > 0 && m.length <= 60) && !duplicate && clean.length <= 15;
  const add = () => {
    const value = adding.trim();
    if (!value || list.length >= 15) return;
    setList([...list, value]);
    setAdding('');
  };
  const move = (i: number, by: number) => {
    const next = [...list];
    const [item] = next.splice(i, 1);
    next.splice(i + by, 0, item);
    setList(next);
  };
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 8 }}>
        <Card>
          <Title order={4} className="section-title">
            Review measures
          </Title>
          <Text size="sm" c="dimmed" mb="md">
            Panel members score each of these from 1 to 5, alongside the job's own skills. Changes apply to reviews started
            from now on.
          </Text>
          <Stack gap={6}>
            {list.map((m, i) => (
              <Group key={i} gap="xs" wrap="nowrap">
                <Text size="sm" c="dimmed" w={20} ta="right">
                  {i + 1}
                </Text>
                <TextInput
                  value={m}
                  onChange={(e) => setList(list.map((x, j) => (j === i ? e.currentTarget.value : x)))}
                  maxLength={60}
                  disabled={!admin}
                  style={{ flex: 1 }}
                  aria-label={`Measure ${i + 1}`}
                />
                {admin && (
                  <Group gap={0} wrap="nowrap">
                    <ActionIcon variant="subtle" color="gray" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
                      <IconArrowUp size={15} />
                    </ActionIcon>
                    <ActionIcon variant="subtle" color="gray" disabled={i === list.length - 1} onClick={() => move(i, 1)} aria-label="Move down">
                      <IconArrowDown size={15} />
                    </ActionIcon>
                    <Tooltip label="Remove">
                      <ActionIcon variant="subtle" color="red" disabled={list.length === 1} onClick={() => setList(list.filter((_, j) => j !== i))} aria-label="Remove measure">
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
              <Group mt="sm" gap="xs" wrap="nowrap">
                <TextInput
                  placeholder="e.g. Ownership"
                  value={adding}
                  onChange={(e) => setAdding(e.currentTarget.value)}
                  onKeyDown={(e) => e.key === 'Enter' && add()}
                  maxLength={60}
                  style={{ flex: 1 }}
                  ml={28}
                  aria-label="New measure"
                />
                <Button variant="light" leftSection={<IconPlus size={14} />} onClick={add} disabled={!adding.trim() || list.length >= 15}>
                  Add
                </Button>
              </Group>
              {duplicate && (
                <Text size="xs" c="red" mt={4}>
                  {duplicate} is in the list twice.
                </Text>
              )}
              <Group justify="flex-end" mt="md">
                <Button
                  onClick={() =>
                    save.mutate(clean, {
                      onSuccess: () => notifySuccess('Review measures saved.'),
                      onError: (e) => notifyError(e),
                    })
                  }
                  disabled={!dirty || !valid}
                  loading={save.isPending}
                >
                  Save measures
                </Button>
              </Group>
            </>
          )}
        </Card>
      </Grid.Col>
    </Grid>
  );
}
