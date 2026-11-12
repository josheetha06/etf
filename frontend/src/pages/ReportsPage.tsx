import { Badge, Card, Group, SegmentedControl, Select, SimpleGrid, Stack, Switch, Table, Tabs, Text, UnstyledButton } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useNavigate, useSearchParams } from 'react-router';
import { useJobs, useReportCounts, useReports } from '../api/hooks';
import type { ReportRow, ReportType } from '../api/types';
import { SampleBadge, ScorePill } from '../components/Badges';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { ist, relative } from '../lib/format';
import { reportType, roundLabel } from '../lib/labels';

const REC_TONE: Record<string, { label: string; color: string }> = {
  shortlist: { label: 'Shortlist', color: 'ett' },
  review: { label: 'Reserve', color: 'yellow' },
  reject: { label: 'Not recommended', color: 'gray' },
  advance: { label: 'Progress', color: 'ett' },
  hold: { label: 'Hold', color: 'yellow' },
};

const DECISION: Record<string, string> = {
  shortlisted: 'Shortlisted',
  hold: 'On hold',
  rejected: 'Not now',
  advance: 'Progressed',
  reject: 'Not progressing',
};

/**
 * Every report HR receives, in one place: 1 AI shortlisting, 2 AI interview, 3 partner assessor, 4 hiring panel.
 * The same reviews are on each candidate's page.
 */
export function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const view = (params.get('view') ?? 'to_review') as 'to_review' | 'reviewed' | 'all';
  const type = params.get('type') ?? undefined;
  const jobId = params.get('jobId') ?? undefined;
  const flagged = params.get('flagged') === 'true';
  const { data, isLoading, error, refetch, isFetching } = useReports({ view, type, jobId, flagged });
  const { data: counts } = useReportCounts();
  const { data: jobs } = useJobs();

  const update = (values: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(values).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
  };

  const tiles: { type: ReportType; count: number }[] = [
    { type: 'shortlisting', count: counts?.shortlisting ?? 0 },
    { type: 'ai_interview', count: counts?.aiInterview ?? 0 },
    { type: 'partner', count: counts?.partner ?? 0 },
    { type: 'panel', count: counts?.panel ?? 0 },
  ];

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Every report in one place: the AI shortlist, and each round's report (AI interview, partner assessment centre or panel). Review it and decide."
      />
      <SimpleGrid cols={{ base: 2, md: 4 }} mb="lg">
        {tiles.map((tile) => (
          <UnstyledButton key={tile.type} onClick={() => update({ type: type === tile.type ? null : tile.type, view: 'to_review' })}>
            <Card padding="md" style={{ borderColor: type === tile.type ? 'var(--mantine-color-ett-5)' : undefined }}>
              <Text className="stat-label">{reportType[tile.type].short}</Text>
              <Text size="sm" fw={500} mt={2}>
                {reportType[tile.type].title}
              </Text>
              <Group justify="space-between" mt="xs">
                <Text fz={26} fw={700} c="navy.9" className="tabular">
                  {tile.count}
                </Text>
                <Text size="xs" c="dimmed">
                  to review
                </Text>
              </Group>
            </Card>
          </UnstyledButton>
        ))}
      </SimpleGrid>
      <Card p={0}>
        <Tabs value={view} onChange={(v) => update({ view: v })}>
          <Tabs.List px="md" pt="xs">
            <Tabs.Tab value="to_review" rightSection={counts?.total ? <Badge size="sm" variant="filled">{counts.total}</Badge> : undefined}>
              To review
            </Tabs.Tab>
            <Tabs.Tab value="reviewed">Reviewed</Tabs.Tab>
            <Tabs.Tab value="all">All</Tabs.Tab>
          </Tabs.List>
        </Tabs>
        <Group p="md" gap="sm" wrap="wrap">
          <SegmentedControl
            size="xs"
            value={type ?? 'all'}
            onChange={(v) => update({ type: v === 'all' ? null : v })}
            data={[
              { value: 'all', label: 'All reports' },
              { value: 'shortlisting', label: '1 · Shortlisting' },
              { value: 'round1', label: '2 · AI interview' },
              { value: 'round2', label: '3 · Assessor' },
              { value: 'round3', label: '4 · Panel' },
            ]}
          />
          <Select
            size="xs"
            placeholder="All jobs"
            data={(jobs ?? []).map((j) => ({ value: j.id, label: `${j.title} · ${j.jobRef}` }))}
            value={jobId ?? null}
            onChange={(v) => update({ jobId: v })}
            clearable
            w={260}
          />
          <Switch size="sm" label="With red flags" checked={flagged} onChange={(e) => update({ flagged: e.currentTarget.checked ? 'true' : null })} />
        </Group>
        {isLoading && <Loading />}
        {error && <ErrorBlock error={error} onRetry={refetch} />}
        {data && data.length === 0 && (
          <Empty title={view === 'to_review' ? 'Nothing to review' : 'No reports here'}>
            {view === 'to_review' ? 'New reports appear here, and you get a notification, as soon as they arrive.' : undefined}
          </Empty>
        )}
        {data && data.length > 0 && (
          <Table.ScrollContainer minWidth={900} style={{ opacity: isFetching ? 0.75 : 1 }}>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Report</Table.Th>
                  <Table.Th>Candidate</Table.Th>
                  <Table.Th>Job</Table.Th>
                  <Table.Th ta="center">Score</Table.Th>
                  <Table.Th>Says</Table.Th>
                  <Table.Th>Arrived</Table.Th>
                  <Table.Th>Your review</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {data.map((row) => (
                  <ReportLine key={`${row.applicationId}-${row.type}`} row={row} onOpen={() => navigate(`/applications/${row.applicationId}?tab=reports#decide`)} />
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
    </>
  );
}

function ReportLine({ row, onOpen }: { row: ReportRow; onOpen: () => void }) {
  const rec = row.recommendation ? REC_TONE[row.recommendation] : null;
  return (
    <Table.Tr className="clickable-row" onClick={onOpen} tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onOpen()}>
      <Table.Td>
        <Group gap={6} wrap="nowrap">
          <Stack gap={0}>
            <Text size="sm">{row.round > 0 ? roundLabel(row.roundName, row.round) : reportType[row.type].short}</Text>
            {row.round > 0 && (
              <Text size="xs" c="dimmed">
                {reportType[row.type].short}
              </Text>
            )}
          </Stack>
          <SampleBadge show={row.sample} />
        </Group>
      </Table.Td>
      <Table.Td>
        <Group gap={6} wrap="nowrap">
          <Text size="sm" fw={600}>
            {row.candidateName}
          </Text>
          {row.redFlagsOpen > 0 && <IconAlertTriangle size={15} color="var(--mantine-color-red-6)" />}
        </Group>
        <Text size="xs" c="dimmed">
          {row.candidateRef}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{row.jobTitle}</Text>
        <Text size="xs" c="dimmed">
          {row.jobRef}
        </Text>
      </Table.Td>
      <Table.Td ta="center">
        <ScorePill score={row.score} />
      </Table.Td>
      <Table.Td>{rec ? <Badge color={rec.color} variant="light">{rec.label}</Badge> : '—'}</Table.Td>
      <Table.Td>
        <Text size="sm" title={ist(row.readyAt)}>
          {relative(row.readyAt)}
        </Text>
      </Table.Td>
      <Table.Td>
        {row.recheckRequested ? (
          <Badge color="grape" variant="light">
            With ETT for re-check
          </Badge>
        ) : row.toReview ? (
          <Badge color="blue" variant="light">
            {row.decision === 'hold' ? 'On hold · decide' : 'To review'}
          </Badge>
        ) : row.decision ? (
          <Text size="sm">{DECISION[row.decision] ?? row.decision}</Text>
        ) : (
          <Text size="sm" c="dimmed">
            —
          </Text>
        )}
      </Table.Td>
    </Table.Tr>
  );
}
