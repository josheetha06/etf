import { ActionIcon, Autocomplete, Badge, Box, Button, Card, Group, NumberInput, SegmentedControl, Select, Stack, Text, Timeline, Tooltip } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconFileSearch, IconLock, IconPlus, IconTrash, IconTrophy } from '@tabler/icons-react';
import type { InterviewMode, Round, RoundInput, RoundKind } from '../../api/types';
import { roundKind } from '../../lib/labels';
import { DurationInput, formatDuration } from '../common/DurationInput';

export const MAX_ROUNDS = 8;

/** A round being edited. `key` only keeps React rows stable while rounds move around. */
export interface RoundDraft extends RoundInput {
  key: string;
}

let counter = 0;
const nextKey = () => `r${++counter}`;

const NAME_IDEAS = [
  'Technical Level 1',
  'Technical Level 2',
  'Technical Level 3',
  'Managerial round',
  'HR round',
  'Final round',
  'AI interview',
  'Partner assessment centre',
  'Coding exercise review',
  'Case study presentation',
];

const KIND_OPTIONS = (Object.keys(roundKind) as RoundKind[]).map((k) => ({ value: k, label: roundKind[k].label }));

function defaultName(kind: RoundKind, others: RoundDraft[]) {
  if (kind === 'AI_INTERVIEW') return 'AI interview';
  if (kind === 'PARTNER') return 'Partner assessment centre';
  return `Technical Level ${others.filter((r) => r.kind === 'PANEL').length + 1}`;
}

const isDefaultName = (name: string) => !name.trim() || name === 'AI interview' || name === 'Partner assessment' || name === 'Partner assessment centre' || /^Technical Level \d$/.test(name);

function modeFor(kind: RoundKind, current: InterviewMode): InterviewMode {
  if (kind === 'AI_INTERVIEW') return 'ONLINE';
  if (kind === 'PARTNER') return 'IN_PERSON';
  return current === 'IN_PERSON' ? 'IN_PERSON' : 'ONLINE';
}

export function toDrafts(rounds: Round[]): RoundDraft[] {
  return rounds.map((r) => ({
    key: nextKey(),
    name: r.name,
    kind: r.kind,
    mode: modeFor(r.kind, r.mode ?? 'ONLINE'),
    durationMin: r.durationMin ?? 60,
    targetDays: r.targetDays,
  }));
}

export function toInputs(drafts: RoundDraft[]): RoundInput[] {
  return drafts.map(({ name, kind, mode, durationMin, targetDays }) => ({
    name: name.trim(),
    kind,
    mode: modeFor(kind, mode),
    durationMin,
    targetDays,
  }));
}

export function roundProblem(round: RoundDraft): string | null {
  if (!round.name.trim()) return 'Give the round a name.';
  if (round.name.trim().length > 60) return 'Keep the name under 60 characters.';
  if (round.targetDays !== null && (round.targetDays < 1 || round.targetDays > 90)) return 'Target time is 1 to 90 days.';
  return null;
}

export function roundsReady(rounds: RoundDraft[]) {
  return rounds.length > 0 && rounds.length <= MAX_ROUNDS && rounds.every((r) => !roundProblem(r));
}

/**
 * The interview rounds for a job (or the company's default plan): up to 8, in any order, each with a type, a name,
 * a length and a target time. Rounds candidates have already reached are locked in place.
 */
export function RoundsBuilder({ value, onChange, lockedUpTo = 0 }: {
  value: RoundDraft[];
  onChange: (value: RoundDraft[]) => void;
  lockedUpTo?: number;
}) {
  const update = (index: number, patch: Partial<RoundDraft>) =>
    onChange(value.map((r, i) => (i === index ? { ...r, ...patch } : r)));

  const changeKind = (index: number, kind: RoundKind) => {
    const round = value[index];
    const others = value.filter((_, i) => i !== index && i < index);
    update(index, {
      kind,
      mode: modeFor(kind, round.mode),
      name: isDefaultName(round.name) ? defaultName(kind, others) : round.name,
      durationMin: kind === 'AI_INTERVIEW' && round.durationMin > 90 ? 45 : round.durationMin,
    });
  };

  const move = (index: number, by: number) => {
    const next = [...value];
    const [item] = next.splice(index, 1);
    next.splice(index + by, 0, item);
    onChange(next);
  };

  const add = () =>
    onChange([
      ...value,
      { key: nextKey(), name: defaultName('PANEL', value), kind: 'PANEL', mode: 'ONLINE', durationMin: 60, targetDays: 5 },
    ]);

  return (
    <Stack gap="sm">
      {value.map((round, index) => {
        const locked = index < lockedUpTo;
        const problem = roundProblem(round);
        const meta = roundKind[round.kind];
        return (
          <Card key={round.key} withBorder padding="md" radius="md" className="round-card" data-kind={round.kind}>
            <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
              <Group gap="sm" align="flex-start" wrap="nowrap" style={{ flex: 1, minWidth: 0 }}>
                <Box className="round-number" mt={26}>
                  {index + 1}
                </Box>
                <Group gap="sm" align="flex-start" wrap="wrap" style={{ flex: 1, minWidth: 0 }}>
                  <Autocomplete
                    label="Round name"
                    placeholder="e.g. Technical Level 1"
                    data={NAME_IDEAS}
                    value={round.name}
                    onChange={(name) => update(index, { name })}
                    error={round.name.trim() ? null : 'Name the round'}
                    maxLength={60}
                    style={{ flex: '1 1 200px' }}
                  />
                  <Select
                    label="Type"
                    data={KIND_OPTIONS}
                    value={round.kind}
                    onChange={(v) => v && changeKind(index, v as RoundKind)}
                    allowDeselect={false}
                    disabled={locked}
                    w={{ base: '100%', xs: 190 }}
                  />
                </Group>
              </Group>
              <Group gap={2} wrap="nowrap" mt={24}>
                {locked ? (
                  <Tooltip label="Candidates have reached this round, so its type and place are fixed. You can still rename it or change the times." multiline w={240}>
                    <ActionIcon variant="subtle" color="gray" aria-label="Locked round">
                      <IconLock size={16} />
                    </ActionIcon>
                  </Tooltip>
                ) : (
                  <>
                    <Tooltip label="Move up">
                      <ActionIcon variant="subtle" color="gray" aria-label="Move up" disabled={index <= lockedUpTo} onClick={() => move(index, -1)}>
                        <IconArrowUp size={16} />
                      </ActionIcon>
                    </Tooltip>
                    <Tooltip label="Move down">
                      <ActionIcon variant="subtle" color="gray" aria-label="Move down" disabled={index === value.length - 1} onClick={() => move(index, 1)}>
                        <IconArrowDown size={16} />
                      </ActionIcon>
                    </Tooltip>
                    <Tooltip label="Remove round">
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        aria-label="Remove round"
                        disabled={value.length === 1}
                        onClick={() => onChange(value.filter((_, i) => i !== index))}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Tooltip>
                  </>
                )}
              </Group>
            </Group>

            <Group gap="lg" align="flex-start" wrap="wrap" mt="sm" ml={{ base: 0, xs: 40 }}>
              <Box>
                <Text size="sm" fw={500} mb={4}>
                  Where
                </Text>
                {round.kind === 'PANEL' ? (
                  <SegmentedControl
                    size="sm"
                    value={round.mode === 'IN_PERSON' ? 'IN_PERSON' : 'ONLINE'}
                    onChange={(v) => update(index, { mode: v as InterviewMode })}
                    data={[
                      { value: 'ONLINE', label: 'Online' },
                      { value: 'IN_PERSON', label: 'In person' },
                    ]}
                  />
                ) : (
                  <Text size="sm" c="dimmed" lh="36px">
                    {meta.where}
                  </Text>
                )}
              </Box>
              <DurationInput value={round.durationMin} onChange={(durationMin) => update(index, { durationMin })} />
              <NumberInput
                label="Target time"
                suffix={round.targetDays === 1 ? ' day' : ' days'}
                placeholder="No target"
                min={1}
                max={90}
                w={130}
                value={round.targetDays ?? ''}
                onChange={(v) => update(index, { targetDays: v === '' ? null : Number(v) })}
              />
            </Group>
            <Text size="xs" c="dimmed" mt="xs" ml={{ base: 0, xs: 40 }}>
              {meta.help}
              {round.targetDays ? ` Flagged as overdue if there's no decision ${round.targetDays} ${round.targetDays === 1 ? 'day' : 'days'} after the candidate reaches it.` : ''}
            </Text>
            {problem && round.name.trim() && (
              <Text size="xs" c="red" mt={4}>
                {problem}
              </Text>
            )}
          </Card>
        );
      })}
      <Group justify="space-between">
        <Button variant="light" leftSection={<IconPlus size={16} />} onClick={add} disabled={value.length >= MAX_ROUNDS}>
          Add a round
        </Button>
        <Text size="xs" c="dimmed">
          {value.length} of {MAX_ROUNDS} rounds
        </Text>
      </Group>
    </Stack>
  );
}

/** The candidate's path through the job, top to bottom. */
export function PlanPreview({ rounds, aiShortlisting }: { rounds: RoundInput[]; aiShortlisting: boolean }) {
  return (
    <Timeline bulletSize={24} lineWidth={2} active={rounds.length + 1}>
      <Timeline.Item bullet={<IconFileSearch size={13} />} title={aiShortlisting ? 'AI shortlisting' : 'CV added'}>
        <Text size="xs" c="dimmed">
          {aiShortlisting ? 'CVs are scored on the skills; you confirm the shortlist.' : 'Shortlisting is off: candidates go straight to Round 1.'}
        </Text>
      </Timeline.Item>
      {rounds.map((r, i) => (
        <Timeline.Item key={i} bullet={<Text size="xs" fw={700}>{i + 1}</Text>} title={r.name || `Round ${i + 1}`}>
          <Group gap={6} mt={2}>
            <Badge size="xs" variant="light" color={roundKind[r.kind].color}>
              {roundKind[r.kind].short}
            </Badge>
            <Text size="xs" c="dimmed">
              {r.kind === 'PANEL' ? (r.mode === 'IN_PERSON' ? 'In person' : 'Online') : roundKind[r.kind].where} · {formatDuration(r.durationMin)}
              {r.targetDays ? ` · decide within ${r.targetDays} ${r.targetDays === 1 ? 'day' : 'days'}` : ''}
            </Text>
          </Group>
        </Timeline.Item>
      ))}
      <Timeline.Item bullet={<IconTrophy size={13} />} title="Offer">
        <Text size="xs" c="dimmed">
          After the last round, you record the offer outcome.
        </Text>
      </Timeline.Item>
    </Timeline>
  );
}
