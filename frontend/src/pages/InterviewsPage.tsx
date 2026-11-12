import { Badge, Button, Card, Group, Modal, Select, Stack, Table, Tabs, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCalendarPlus } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useInterviews, useMe, useReadyToSchedule } from '../api/hooks';
import type { ReadyRow } from '../api/types';
import { LocationCell } from '../components/candidate/LocationCell';
import { ScorePill } from '../components/Badges';
import { ScheduleCurrentRound } from '../components/candidate/ScheduleModal';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { ist, relative } from '../lib/format';
import { roundKind, roundLabel } from '../lib/labels';
import { InterviewsTable } from './interviews/InterviewsTable';

const TABS = [
  { value: 'ready', label: 'Ready to book' },
  { value: 'upcoming', label: 'Upcoming' },
  { value: 'requests', label: 'Reschedule requests' },
  { value: 'completed', label: 'Past' },
];

/** All interviews across rounds. Times are in IST. */
export function InterviewsPage() {
  const [params, setParams] = useSearchParams();
  const jobId = params.get('jobId') ?? undefined;
  const ready = useReadyToSchedule(jobId);
  const requests = useInterviews('requests');
  const tab = params.get('tab') ?? ((ready.data?.length ?? 0) > 0 ? 'ready' : 'upcoming');
  const { data: me } = useMe();
  const [pickerOpen, picker] = useDisclosure(false);
  const [target, setTarget] = useState<ReadyRow | null>(null);

  const setTab = (value: string | null) => {
    const next = new URLSearchParams(params);
    next.set('tab', value ?? 'upcoming');
    setParams(next, { replace: true });
  };

  return (
    <>
      <PageHeader
        title="Interviews"
        subtitle="Every round of every job: AI interviews, partner assessments and panel interviews. All times in IST."
        actions={
          me?.canManageHiring && (
            <Button leftSection={<IconCalendarPlus size={18} />} onClick={picker.open} disabled={(ready.data?.length ?? 0) === 0}>
              Book an interview
            </Button>
          )
        }
      />
      <Card p={0}>
        <Tabs value={tab} onChange={setTab} keepMounted={false}>
          <Tabs.List px="md" pt="xs">
            {TABS.map((t) => (
              <Tabs.Tab
                key={t.value}
                value={t.value}
                rightSection={
                  t.value === 'ready' && (ready.data?.length ?? 0) > 0 ? (
                    <Badge size="sm" color="violet" variant="filled">
                      {ready.data?.length}
                    </Badge>
                  ) : t.value === 'requests' && (requests.data?.length ?? 0) > 0 ? (
                    <Badge size="sm" color="orange" variant="filled">
                      {requests.data?.length}
                    </Badge>
                  ) : undefined
                }
              >
                {t.label}
              </Tabs.Tab>
            ))}
          </Tabs.List>
          <Tabs.Panel value="ready" p="md">
            <ReadyList rows={ready.data} loading={ready.isLoading} onSchedule={setTarget} canAct={!!me?.canManageHiring} />
          </Tabs.Panel>
          {['upcoming', 'requests', 'completed'].map((view) => (
            <Tabs.Panel key={view} value={view} p="md">
              <InterviewList view={view} jobId={jobId} />
            </Tabs.Panel>
          ))}
        </Tabs>
      </Card>
      <PickCandidate opened={pickerOpen} onClose={picker.close} rows={ready.data ?? []} onPick={(row) => { picker.close(); setTarget(row); }} />
      {target && (
        <ScheduleCurrentRound
          onClose={() => setTarget(null)}
          applicationId={target.applicationId}
          candidateName={target.candidateName}
        />
      )}
    </>
  );
}

function InterviewList({ view, jobId }: { view: string; jobId?: string }) {
  const { data, isLoading, error, refetch } = useInterviews(view, jobId);
  if (isLoading) return <Loading />;
  if (error) return <ErrorBlock error={error} onRetry={refetch} />;
  const empty = { upcoming: 'No upcoming interviews', requests: 'No open requests', completed: 'Nothing here yet' }[view] ?? 'Nothing here';
  return <InterviewsTable rows={data ?? []} emptyTitle={empty} />;
}

function ReadyList({ rows, loading, onSchedule, canAct }: { rows?: ReadyRow[]; loading: boolean; onSchedule: (row: ReadyRow) => void; canAct: boolean }) {
  if (loading) return <Loading />;
  if (!rows?.length) {
    return <Empty title="No one is waiting">Candidates appear here when they reach a round: after shortlisting, or when you move them on after a report.</Empty>;
  }
  return (
    <Table.ScrollContainer minWidth={760}>
      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Candidate</Table.Th>
            <Table.Th>Location</Table.Th>
            <Table.Th>Job</Table.Th>
            <Table.Th>Round to book</Table.Th>
            <Table.Th ta="center">CV score</Table.Th>
            <Table.Th>Ready since</Table.Th>
            <Table.Th>Target</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((row) => (
            <Table.Tr key={row.applicationId}>
              <Table.Td>
                <Text component={Link} to={`/applications/${row.applicationId}`} size="sm" fw={600} c="inherit">
                  {row.candidateName}
                </Text>
                <Text size="xs" c="dimmed">
                  {row.candidateRef}
                </Text>
              </Table.Td>
              <Table.Td maw={190}>
                <LocationCell location={row.location} />
              </Table.Td>
              <Table.Td>
                <Text size="sm">{row.jobTitle}</Text>
                <Text size="xs" c="dimmed">
                  {row.jobRef}
                </Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm">{roundLabel(row.roundName, row.round)}</Text>
                {row.roundKind && (
                  <Text size="xs" c="dimmed">
                    {roundKind[row.roundKind].label}
                  </Text>
                )}
              </Table.Td>
              <Table.Td ta="center">
                <ScorePill score={row.matchScore} />
              </Table.Td>
              <Table.Td>
                <Text size="sm" c="dimmed">
                  {relative(row.readySince)}
                </Text>
              </Table.Td>
              <Table.Td>
                {row.dueAt ? (
                  <Text size="sm" c={new Date(row.dueAt) < new Date() ? 'red.7' : undefined} fw={new Date(row.dueAt) < new Date() ? 600 : 400}>
                    {new Date(row.dueAt) < new Date() ? 'Overdue · ' : ''}
                    {ist(row.dueAt)}
                  </Text>
                ) : (
                  <Text size="sm" c="dimmed">
                    —
                  </Text>
                )}
              </Table.Td>
              <Table.Td ta="right">
                {canAct && (
                  <Button size="compact-sm" leftSection={<IconCalendarPlus size={14} />} onClick={() => onSchedule(row)}>
                    Book
                  </Button>
                )}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}

function PickCandidate({ opened, onClose, rows, onPick }: { opened: boolean; onClose: () => void; rows: ReadyRow[]; onPick: (row: ReadyRow) => void }) {
  const [id, setId] = useState<string | null>(null);
  const row = rows.find((r) => r.applicationId === id) ?? null;
  return (
    <Modal opened={opened} onClose={onClose} title="Book an interview">
      <Stack>
        <Select
          label="Candidate"
          placeholder="Choose who to book"
          searchable
          data={rows.map((r) => ({ value: r.applicationId, label: `${r.candidateName} · ${r.jobTitle} · ${roundLabel(r.roundName, r.round)}` }))}
          value={id}
          onChange={setId}
        />
        {row && (
          <Text size="sm" c="dimmed">
            Next: {roundLabel(row.roundName, row.round)}
            {row.roundKind ? ` · ${roundKind[row.roundKind].label}` : ''}
          </Text>
        )}
        <Text size="xs" c="dimmed">
          Only candidates whose previous round is decided are listed, so rounds always happen in order.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!row} onClick={() => row && onPick(row)}>
            Continue
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
