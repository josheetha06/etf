import { Alert, Badge, Button, Card, Group, Modal, Select, Stack, Text, Textarea, Title } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconBeach, IconCalendar, IconUserShare } from '@tabler/icons-react';
import { useState } from 'react';
import { useChangeCover, useColleagues, useCreateCover, useEndCover, useLeaveCover, useMe } from '../../api/hooks';
import type { LeaveCover } from '../../api/types';
import { dateOnly, dayjs } from '../../lib/format';
import { roleLabel } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';
import { Loading } from '../QueryState';

const STATE: Record<LeaveCover['state'], { label: string; color: string }> = {
  upcoming: { label: 'Coming up', color: 'blue' },
  active: { label: 'Covering now', color: 'ett' },
  finished: { label: 'Finished', color: 'gray' },
  ended: { label: 'Ended early', color: 'gray' },
};

const today = () => dayjs().format('YYYY-MM-DD');

/** "Going on leave" from the name menu: hand your jobs, candidates, interviews and notifications to a colleague. */
export function GoingOnLeaveModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const { data: me } = useMe();
  const { data: covers, isLoading } = useLeaveCover();
  const mine = (covers ?? []).filter((c) => c.userId === me?.id && (c.state === 'upcoming' || c.state === 'active'));
  const coveringFor = (covers ?? []).filter((c) => c.coverUserId === me?.id && (c.state === 'upcoming' || c.state === 'active'));
  return (
    <Modal opened={opened} onClose={onClose} size="lg" title={<Text fw={600}>Going on leave</Text>}>
      {isLoading ? (
        <Loading />
      ) : (
        <Stack>
          <Text size="sm" c="dimmed">
            Choose who covers for you and when. While you're away they see all your jobs, candidates and interviews, can act
            on them, and get your notifications. Nothing changes hands permanently.
          </Text>
          {mine.length > 0 && <CoverList covers={mine} showPerson={false} />}
          {coveringFor.length > 0 && (
            <Alert variant="light" color="blue" icon={<IconUserShare size={16} />}>
              <Text size="sm">
                You're covering for {coveringFor.map((c) => `${c.userName} (${dateOnly(c.startsOn)} to ${dateOnly(c.endsOn)})`).join(', ')}.
              </Text>
            </Alert>
          )}
          <Card withBorder padding="md">
            <Title order={5} className="section-title" mb="sm">
              {mine.length > 0 ? 'Add another period' : 'Set up cover'}
            </Title>
            <CoverForm onDone={onClose} />
          </Card>
        </Stack>
      )}
    </Modal>
  );
}

/** Leave cover for the whole team, for HR admins in Settings. */
export function LeaveCoverSettings() {
  const { data: covers, isLoading } = useLeaveCover();
  const [adding, setAdding] = useState(false);
  if (isLoading) return <Loading />;
  const current = (covers ?? []).filter((c) => c.state === 'upcoming' || c.state === 'active');
  const past = (covers ?? []).filter((c) => c.state === 'finished' || c.state === 'ended');
  return (
    <Stack>
      <Group justify="space-between" align="flex-start">
        <div>
          <Title order={4} className="section-title">
            Leave cover
          </Title>
          <Text size="sm" c="dimmed" maw={620}>
            When someone is away, the person covering sees all their jobs, candidates and interviews, and gets their
            notifications. People can set this up themselves from their name menu; HR admins can set it up for anyone.
          </Text>
        </div>
        {!adding && (
          <Button leftSection={<IconBeach size={16} />} onClick={() => setAdding(true)}>
            Set up cover
          </Button>
        )}
      </Group>
      {adding && (
        <Card withBorder padding="md">
          <CoverForm forAnyone onDone={() => setAdding(false)} onCancel={() => setAdding(false)} />
        </Card>
      )}
      {current.length === 0 && !adding ? (
        <Text size="sm" c="dimmed">
          No one is away or about to be.
        </Text>
      ) : (
        <CoverList covers={current} showPerson />
      )}
      {past.length > 0 && (
        <>
          <Text size="sm" fw={600} mt="md">
            Last 30 days
          </Text>
          <CoverList covers={past} showPerson />
        </>
      )}
    </Stack>
  );
}

function CoverForm({ forAnyone = false, onDone, onCancel }: { forAnyone?: boolean; onDone: () => void; onCancel?: () => void }) {
  const { data: me } = useMe();
  const { data: colleagues } = useColleagues();
  const create = useCreateCover();
  const [userId, setUserId] = useState<string | null>(forAnyone ? null : me?.id ?? null);
  const [coverUserId, setCoverUserId] = useState<string | null>(null);
  const [range, setRange] = useState<[string | null, string | null]>([null, null]);
  const [note, setNote] = useState('');
  const people = (colleagues ?? []).map((p) => ({ value: p.id, label: `${p.fullName} · ${roleLabel[p.role]}` }));
  const away = forAnyone ? userId : me?.id;
  const ready = !!away && !!coverUserId && coverUserId !== away && !!range[0] && !!range[1];

  const submit = () => {
    if (!ready || create.isPending) return;
    create.mutate(
      {
        userId: forAnyone ? userId : null,
        coverUserId: coverUserId!,
        startsOn: dayjs(range[0]).format('YYYY-MM-DD'),
        endsOn: dayjs(range[1]).format('YYYY-MM-DD'),
        note: note.trim() || undefined,
      },
      {
        onSuccess: (c) => {
          notifySuccess(`${c.coverUserName} will cover from ${dateOnly(c.startsOn)} to ${dateOnly(c.endsOn)}. They've been told.`, 'Cover set up');
          onDone();
        },
        onError: (e) => notifyError(e),
      },
    );
  };

  return (
    <Stack gap="sm">
      {forAnyone && (
        <Select label="Who's away" data={people} value={userId} onChange={setUserId} searchable placeholder="Choose a person" />
      )}
      <Select
        label="Who covers"
        data={people.filter((p) => p.value !== away)}
        value={coverUserId}
        onChange={setCoverUserId}
        searchable
        placeholder="Choose a colleague"
        nothingFoundMessage="No one found"
      />
      <DatePickerInput
        type="range"
        label="Dates"
        description="First and last day away, inclusive. Up to 120 days."
        placeholder="Choose the dates"
        leftSection={<IconCalendar size={16} />}
        value={range}
        onChange={(v) => setRange(v as [string | null, string | null])}
        minDate={today()}
        allowSingleDateInRange
      />
      <Textarea
        label="Note for your cover (optional)"
        placeholder="e.g. The Java Developer offer is waiting on the candidate's reply."
        autosize
        minRows={2}
        maxLength={500}
        value={note}
        onChange={(e) => setNote(e.currentTarget.value)}
      />
      <Group justify="flex-end">
        {onCancel && (
          <Button variant="default" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button onClick={submit} disabled={!ready} loading={create.isPending}>
          Set up cover
        </Button>
      </Group>
    </Stack>
  );
}

function CoverList({ covers, showPerson }: { covers: LeaveCover[]; showPerson: boolean }) {
  return (
    <Stack gap="xs">
      {covers.map((c) => (
        <CoverRow key={c.id} cover={c} showPerson={showPerson} />
      ))}
    </Stack>
  );
}

function CoverRow({ cover, showPerson }: { cover: LeaveCover; showPerson: boolean }) {
  const end = useEndCover();
  const change = useChangeCover();
  const [editing, setEditing] = useState(false);
  const [range, setRange] = useState<[string | null, string | null]>([cover.startsOn, cover.endsOn]);
  const started = cover.state === 'active';
  const save = () =>
    range[0] &&
    range[1] &&
    change.mutate(
      { id: cover.id, startsOn: dayjs(range[0]).format('YYYY-MM-DD'), endsOn: dayjs(range[1]).format('YYYY-MM-DD') },
      {
        onSuccess: () => {
          notifySuccess('Dates changed.');
          setEditing(false);
        },
        onError: (e) => notifyError(e),
      },
    );
  return (
    <Card withBorder padding="sm" radius="md">
      <Group justify="space-between" wrap="wrap" gap="sm">
        <Stack gap={2}>
          <Group gap={8}>
            <Text size="sm" fw={600}>
              {showPerson ? `${cover.userName} → ${cover.coverUserName}` : `${cover.coverUserName} covers for you`}
            </Text>
            <Badge size="sm" variant="light" color={STATE[cover.state].color}>
              {STATE[cover.state].label}
            </Badge>
          </Group>
          <Text size="sm" c="dimmed">
            {dateOnly(cover.startsOn)} to {dateOnly(cover.endsOn)}
            {cover.createdByName && showPerson ? ` · set up by ${cover.createdByName}` : ''}
          </Text>
          {cover.note && (
            <Text size="xs" c="dimmed" lineClamp={2}>
              “{cover.note}”
            </Text>
          )}
        </Stack>
        {cover.canChange && !editing && (
          <Group gap="xs">
            <Button size="compact-sm" variant="default" onClick={() => setEditing(true)}>
              Change dates
            </Button>
            <Button
              size="compact-sm"
              variant="subtle"
              color="red"
              loading={end.isPending}
              onClick={() =>
                end.mutate(cover.id, {
                  onSuccess: () => notifySuccess(started ? 'Cover ended. Everything is back with its owner.' : 'Cover cancelled.'),
                  onError: (e) => notifyError(e),
                })
              }
            >
              {started ? 'End now' : 'Cancel'}
            </Button>
          </Group>
        )}
      </Group>
      {editing && (
        <Group mt="sm" align="flex-end" gap="sm">
          <DatePickerInput
            type="range"
            label={started ? 'New end date' : 'New dates'}
            value={range}
            onChange={(v) => {
              const next = v as [string | null, string | null];
              // Once cover has started only the end date can move.
              setRange(started ? [cover.startsOn, next[1] ?? next[0]] : next);
            }}
            minDate={started ? cover.startsOn : today()}
            allowSingleDateInRange
            w={260}
          />
          <Button size="sm" onClick={save} loading={change.isPending}>
            Save
          </Button>
          <Button size="sm" variant="default" onClick={() => setEditing(false)}>
            Cancel
          </Button>
        </Group>
      )}
    </Card>
  );
}
