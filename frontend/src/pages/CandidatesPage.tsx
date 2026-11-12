import {
  Badge,
  Button,
  Card,
  Group,
  Pagination,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconAlertTriangle, IconFilterOff, IconSearch } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useCandidates, useJobs } from '../api/hooks';
import { ScorePill, ToneBadge } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { LocationCell } from '../components/candidate/LocationCell';
import { ChangesCell } from '../components/common/ChangesLine';
import { istDay } from '../lib/format';
import { applicationStatus, nextAction, stage as stageLabel, suggestion } from '../lib/labels';

const PAGE_SIZE = 25;

const STAGES = Object.entries(stageLabel)
  .filter(([key]) => key !== 'CLOSED')
  .map(([value, label]) => ({ value, label }));

const SORTS = [
  { value: 'action', label: 'Needs action first' },
  { value: 'added_desc', label: 'Newest first' },
  { value: 'added_asc', label: 'Oldest first' },
  { value: 'score_desc', label: 'Highest score' },
  { value: 'score_asc', label: 'Lowest score' },
  { value: 'name', label: 'Name A–Z' },
  { value: 'stage', label: 'Stage' },
  { value: 'job', label: 'Job' },
];

/** Every candidate across your jobs. Search by name, email, phone, candidate ID or job ID. */
export function CandidatesPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const [text, setText] = useState(params.get('q') ?? '');
  const [q] = useDebouncedValue(text.trim(), 300);
  const get = (key: string) => params.get(key) ?? undefined;
  const page = Number(params.get('page') ?? '1');
  const filter = {
    q: q || undefined,
    jobId: get('jobId'),
    stage: get('stage'),
    status: get('status'),
    suggestion: get('suggestion'),
    needsDecision: params.get('needsDecision') === 'true',
    action: get('action'),
    sort: get('sort') ?? 'action',
    page: page - 1,
    size: PAGE_SIZE,
  };
  const { data, isLoading, error, refetch, isFetching } = useCandidates(filter);
  const { data: jobs } = useJobs();

  useEffect(() => {
    if ((params.get('q') ?? '') !== q) update({ q: q || null, page: null });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  function update(values: Record<string, string | null>) {
    const next = new URLSearchParams(params);
    Object.entries(values).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    if (!('page' in values)) next.delete('page');
    setParams(next, { replace: true });
  }

  const filtered = ['jobId', 'stage', 'status', 'suggestion', 'needsDecision', 'action', 'q'].some((k) => params.get(k));

  return (
    <>
      <PageHeader
        title="Candidates"
        subtitle="Everyone across your jobs. Click a candidate to see their reports and decide the next step."
      />
      <Card mb="lg" padding="md">
        <Stack gap="sm">
          <TextInput
            placeholder="Search by name, email, phone, location, candidate ID or job ID"
            leftSection={<IconSearch size={16} />}
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
            size="md"
            aria-label="Search candidates"
          />
          <Group gap="sm" align="flex-end" wrap="wrap">
            <Select
              label="Job"
              placeholder="All jobs"
              data={(jobs ?? []).map((j) => ({ value: j.id, label: `${j.title} · ${j.jobRef}` }))}
              value={get('jobId') ?? null}
              onChange={(v) => update({ jobId: v })}
              clearable
              searchable
              w={230}
            />
            <Select label="Stage" placeholder="Any stage" data={STAGES} value={get('stage') ?? null} onChange={(v) => update({ stage: v })} clearable w={140} />
            <Select
              label="Status"
              placeholder="Any status"
              data={Object.entries(applicationStatus).map(([value, t]) => ({ value, label: t.label }))}
              value={get('status') ?? null}
              onChange={(v) => update({ status: v })}
              clearable
              w={140}
            />
            <Select
              label="AI suggestion"
              placeholder="Any"
              data={Object.entries(suggestion).map(([value, t]) => ({ value, label: t.label }))}
              value={get('suggestion') ?? null}
              onChange={(v) => update({ suggestion: v })}
              clearable
              w={150}
            />
          </Group>
          <Group justify="space-between">
            <Group gap="lg">
              <Switch
                label="Needs a decision"
                checked={filter.needsDecision}
                onChange={(e) => update({ needsDecision: e.currentTarget.checked ? 'true' : null })}
              />
              <Switch
                label="Past target time"
                color="red"
                checked={filter.action === 'overdue'}
                onChange={(e) => update({ action: e.currentTarget.checked ? 'overdue' : null })}
              />
              {filtered && (
                <Button
                  variant="subtle"
                  size="xs"
                  color="gray"
                  leftSection={<IconFilterOff size={14} />}
                  onClick={() => {
                    setText('');
                    setParams(new URLSearchParams(), { replace: true });
                  }}
                >
                  Clear filters
                </Button>
              )}
            </Group>
            <Select
              aria-label="Sort"
              data={SORTS}
              value={filter.sort}
              onChange={(v) => update({ sort: v })}
              allowDeselect={false}
              w={170}
              size="sm"
            />
          </Group>
        </Stack>
      </Card>

      {isLoading && <Loading />}
      {error && <ErrorBlock error={error} onRetry={refetch} />}
      {data && data.total === 0 && (
        <Card>
          <Empty title={filtered ? 'No candidates match' : 'No candidates yet'}>
            {filtered ? 'Try a different search or clear the filters.' : 'Upload CVs to a job to get started.'}
          </Empty>
        </Card>
      )}
      {data && data.total > 0 && (
        <Card padding={0} style={{ opacity: isFetching ? 0.75 : 1 }}>
          <Table.ScrollContainer minWidth={1240}>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Candidate</Table.Th>
                  <Table.Th>Location</Table.Th>
                  <Table.Th>Job</Table.Th>
                  <Table.Th>Stage</Table.Th>
                  <Table.Th ta="center">Score</Table.Th>
                  <Table.Th>Next step</Table.Th>
                  <Table.Th>Added</Table.Th>
                  <Table.Th>Last updated</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {data.items.map((row) => (
                  <Table.Tr
                    key={row.applicationId}
                    className="clickable-row"
                    tabIndex={0}
                    onClick={() => navigate(`/applications/${row.applicationId}`)}
                    onKeyDown={(e) => e.key === 'Enter' && navigate(`/applications/${row.applicationId}`)}
                  >
                    <Table.Td>
                      <Group gap={6} wrap="nowrap">
                        <Text fw={600} size="sm">
                          {row.candidateName}
                        </Text>
                        {row.openRedFlags > 0 && (
                          <Tooltip label={`${row.openRedFlags} red flag${row.openRedFlags === 1 ? '' : 's'} to check`}>
                            <IconAlertTriangle size={15} color="var(--mantine-color-red-6)" />
                          </Tooltip>
                        )}
                      </Group>
                      <Text size="xs" c="dimmed">
                        {row.candidateRef}
                      </Text>
                    </Table.Td>
                    <Table.Td maw={200}>
                      <LocationCell location={row.location} />
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{row.jobTitle}</Text>
                      <Text size="xs" c="dimmed">
                        {row.jobRef}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Stack gap={2}>
                        <Text size="sm" lineClamp={1}>
                          {row.currentRound && row.status === 'ACTIVE' ? row.roundName ?? `Round ${row.currentRound}` : stageLabel[row.stage]}
                        </Text>
                        {row.status !== 'ACTIVE' && <ToneBadge tone={applicationStatus[row.status]} size="xs" />}
                        {row.status === 'ACTIVE' && (row.currentRound || row.dueAt) && (
                          <Text size="xs" c={row.overdue ? 'red.7' : 'dimmed'} fw={row.overdue ? 600 : undefined} style={{ whiteSpace: 'nowrap' }}>
                            {row.currentRound ? `Round ${row.currentRound}` : ''}
                            {row.currentRound && row.dueAt ? ' · ' : ''}
                            {row.dueAt ? (row.overdue ? `overdue since ${istDay(row.dueAt)}` : `due ${istDay(row.dueAt)}`) : ''}
                          </Text>
                        )}
                      </Stack>
                    </Table.Td>
                    <Table.Td ta="center">
                      <ScorePill score={row.matchScore} />
                    </Table.Td>
                    <Table.Td>
                      {row.nextAction === 'none' || row.nextAction === 'waiting' ? (
                        <Text size="sm" c="dimmed">
                          {row.nextAction === 'none' && row.status === 'ACTIVE' && row.decision === 'REJECTED'
                            ? 'Not shortlisted'
                            : row.nextAction === 'none' && row.status === 'ACTIVE' && row.decision === 'HOLD'
                              ? 'In reserve'
                              : row.nextAction === 'none' && row.status !== 'ACTIVE'
                                ? '—'
                                : nextAction[row.nextAction].label}
                        </Text>
                      ) : (
                        <Badge color={nextAction[row.nextAction].color} variant="light" style={{ whiteSpace: 'nowrap' }}>
                          {nextAction[row.nextAction].label}
                        </Badge>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                        {istDay(row.addedAt)}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <ChangesCell changes={row.changes} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          <Group justify="space-between" p="md">
            <Text size="sm" c="dimmed">
              {data.total} candidate{data.total === 1 ? '' : 's'}
            </Text>
            {data.total > PAGE_SIZE && (
              <Pagination
                total={Math.ceil(data.total / PAGE_SIZE)}
                value={page}
                onChange={(p) => update({ page: String(p) })}
                size="sm"
              />
            )}
          </Group>
        </Card>
      )}
    </>
  );
}
