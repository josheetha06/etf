import { Badge, Button, Card, Group, Table, Tabs, Text, TextInput, Tooltip } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconPlus, IconSearch } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useJobs, useMe } from '../api/hooks';
import type { JobSummary } from '../api/types';
import { ToneBadge } from '../components/Badges';
import { ChangesCell } from '../components/common/ChangesLine';
import { JobStar } from '../components/job/JobStar';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { dateOnly } from '../lib/format';
import { contractType, jobStatus, roundKind } from '../lib/labels';

type Filter = 'open' | 'on_hold' | 'closed' | 'draft' | 'starred' | 'all';

const FILTERS: { value: Filter; label: string; match: (job: JobSummary) => boolean; empty: string }[] = [
  { value: 'open', label: 'Open', match: (j) => j.status === 'OPEN' || j.status === 'SHORTLIST_CONFIRMED', empty: 'No open jobs.' },
  { value: 'on_hold', label: 'On hold', match: (j) => j.status === 'ON_HOLD', empty: 'No jobs on hold.' },
  { value: 'closed', label: 'Closed', match: (j) => j.status === 'CLOSED', empty: 'No closed jobs.' },
  { value: 'draft', label: 'Drafts', match: (j) => j.status === 'DRAFT', empty: 'No drafts.' },
  {
    value: 'starred',
    label: 'Starred',
    match: (j) => j.starred,
    empty: 'No starred jobs. Click the star next to a job to keep your priority jobs here.',
  },
  { value: 'all', label: 'All', match: () => true, empty: 'No jobs yet.' },
];

export function JobsPage() {
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState('');
  const [q] = useDebouncedValue(text.trim(), 250);
  const { data: jobs, isLoading, error, refetch } = useJobs(q);
  const { data: me } = useMe();
  const navigate = useNavigate();
  const filter = (FILTERS.find((f) => f.value === params.get('status'))?.value ?? 'open') as Filter;
  const current = FILTERS.find((f) => f.value === filter)!;
  const shown = (jobs ?? []).filter(current.match);

  return (
    <>
      <PageHeader
        title="Jobs"
        subtitle={me?.seesAllJobs ? 'Every job in your company.' : 'Jobs you own or are the hiring manager for, and any you are covering.'}
        actions={
          me?.canManageHiring && (
            <Button component={Link} to="/jobs/new" leftSection={<IconPlus size={18} />}>
              New job
            </Button>
          )
        }
      />
      <Card p={0}>
        <Group px="md" pt="md" justify="space-between" wrap="wrap" gap="sm">
          <Tabs value={filter} onChange={(v) => setParams(v && v !== 'open' ? { status: v } : {}, { replace: true })} variant="pills" radius="xl">
            <Tabs.List>
              {FILTERS.map((f) => {
                const count = (jobs ?? []).filter(f.match).length;
                return (
                  <Tabs.Tab
                    key={f.value}
                    value={f.value}
                    rightSection={
                      <Badge size="xs" variant={filter === f.value ? 'white' : 'light'} color={filter === f.value ? 'dark' : 'gray'} circle={count < 10}>
                        {count}
                      </Badge>
                    }
                  >
                    {f.label}
                  </Tabs.Tab>
                );
              })}
            </Tabs.List>
          </Tabs>
          <TextInput
            placeholder="Search by title, job ID or location"
            leftSection={<IconSearch size={16} />}
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
            w={{ base: '100%', sm: 300 }}
            aria-label="Search jobs"
          />
        </Group>
        {isLoading && <Loading />}
        {error && <ErrorBlock error={error} onRetry={refetch} />}
        {jobs && shown.length === 0 && (
          <Empty title={q ? `No jobs match “${q}”` : current.empty}>
            {q
              ? 'Search by job title, job ID (for example PR-2026-0001) or location.'
              : filter === 'open' && me?.canManageHiring
                ? 'Create a job to set the skills and interview rounds.'
                : undefined}
          </Empty>
        )}
        {shown.length > 0 && (
          <Table.ScrollContainer minWidth={980} mt="sm">
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={44} pr={0}>
                    <span className="visually-hidden">Star</span>
                  </Table.Th>
                  <Table.Th>Job</Table.Th>
                  {(filter === 'all' || filter === 'starred') && <Table.Th>Status</Table.Th>}
                  <Table.Th>Owner</Table.Th>
                  <Table.Th>Candidates by round</Table.Th>
                  <Table.Th ta="right">CVs</Table.Th>
                  <Table.Th>Created</Table.Th>
                  <Table.Th>Last updated</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {shown.map((job) => (
                  <Table.Tr
                    key={job.id}
                    className="clickable-row"
                    tabIndex={0}
                    onClick={() => navigate(`/jobs/${job.id}`)}
                    onKeyDown={(e) => e.key === 'Enter' && navigate(`/jobs/${job.id}`)}
                  >
                    <Table.Td w={44} pr={0}>
                      <JobStar jobId={job.id} starred={job.starred} />
                    </Table.Td>
                    <Table.Td maw={300}>
                      <Text fw={600} c="navy.9" lineClamp={1}>
                        {job.title}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {job.jobRef}
                        {job.location ? ` · ${job.location}` : ''}
                        {job.contractType ? ` · ${contractType[job.contractType]}` : ''}
                      </Text>
                      {job.status === 'ON_HOLD' && job.statusReason && (
                        <Text size="xs" c="orange.8" lineClamp={1}>
                          On hold: {job.statusReason}
                        </Text>
                      )}
                    </Table.Td>
                    {(filter === 'all' || filter === 'starred') && (
                      <Table.Td>
                        <ToneBadge tone={jobStatus[job.status]} size="sm" />
                      </Table.Td>
                    )}
                    <Table.Td>
                      <Text size="sm">{job.owner?.name ?? '—'}</Text>
                      {job.hiringManager?.name && (
                        <Text size="xs" c="dimmed">
                          HM {job.hiringManager.name}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <RoundChips job={job} />
                    </Table.Td>
                    <Table.Td ta="right">
                      <Text size="sm" className="tabular">
                        {job.counts.cvs}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{job.changes?.createdByName ?? '—'}</Text>
                      <Text size="xs" c="dimmed">
                        {dateOnly(job.changes?.createdAt ?? job.createdAt)}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <ChangesCell changes={job.changes} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
    </>
  );
}

/** One small chip per round: how many candidates are in it, red when some are past the target time. */
function RoundChips({ job }: { job: JobSummary }) {
  if (job.rounds.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        No rounds set
      </Text>
    );
  }
  return (
    <Group gap={4} wrap="nowrap">
      {!job.aiShortlisting && (
        <Tooltip label="AI shortlisting is off for this job">
          <Badge size="xs" variant="outline" color="gray" mr={2}>
            No AI
          </Badge>
        </Tooltip>
      )}
      {job.rounds.map((r) => (
        <Tooltip
          key={r.round}
          label={`Round ${r.round} · ${r.name} (${roundKind[r.kind].label}): ${r.count} candidate${r.count === 1 ? '' : 's'}${
            r.overdue ? `, ${r.overdue} past target time` : ''
          }`}
        >
          <Badge
            size="sm"
            radius="sm"
            variant={r.count > 0 ? 'light' : 'outline'}
            color={r.overdue > 0 ? 'red' : r.count > 0 ? roundKind[r.kind].color : 'gray'}
            className="tabular"
          >
            R{r.round} · {r.count}
          </Badge>
        </Tooltip>
      ))}
    </Group>
  );
}
