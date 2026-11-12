import {
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Group,
  Menu,
  Modal,
  ScrollArea,
  SimpleGrid,
  Spoiler,
  Stack,
  Tabs,
  Text,
  Textarea,
  Title,
  UnstyledButton,
} from '@mantine/core';
import {
  IconCalendarEvent,
  IconChevronDown,
  IconCopy,
  IconEdit,
  IconLock,
  IconLockOpen,
  IconPlayerPause,
  IconRocket,
  IconUserShare,
} from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useApplications, useCloseImpact, useInterviews, useJob, useJobAction, useJobStatus, useMe } from '../api/hooks';
import type { ApplicationRow, CriterionKind, JobDetail, JobStatus } from '../api/types';
import { ScorePill, ToneBadge } from '../components/Badges';
import { ChangesLine } from '../components/common/ChangesLine';
import { HistoryList } from '../components/common/HistoryList';
import { JobStar } from '../components/job/JobStar';
import { PlanPreview } from '../components/job/RoundsBuilder';
import { PageHeader } from '../components/PageHeader';
import { ErrorBlock, Loading } from '../components/QueryState';
import { istLong, lpa } from '../lib/format';
import { contractType, criterionKind, jobStatus, nextAction, roundKind } from '../lib/labels';
import { notifyError, notifySuccess } from '../lib/notify';
import { InterviewsTable } from './interviews/InterviewsTable';
import { CvTab } from './job/CvTab';
import { ShortlistTab } from './job/ShortlistTab';

type StatusMove = { status: JobStatus; title: string; verb: string; needsReason: boolean; help: string };

const MOVES: Record<'hold' | 'close' | 'reopen', StatusMove> = {
  hold: {
    status: 'ON_HOLD',
    title: 'Put this job on hold?',
    verb: 'Put on hold',
    needsReason: true,
    help: 'Nothing is cancelled: interviews already booked go ahead, and you can still add CVs and make decisions. New interviews can\'t be booked, and reminder emails to candidates pause until you reopen the job.',
  },
  close: {
    status: 'CLOSED',
    title: 'Close this job?',
    verb: 'Close job',
    needsReason: true,
    help: 'No more CVs can be added. Candidates keep their history, and you can reopen the job later.',
  },
  reopen: {
    status: 'OPEN',
    title: 'Reopen this job?',
    verb: 'Reopen job',
    needsReason: false,
    help: 'The job goes back to open and you can carry on where you left off. Paused reminder emails start again.',
  },
};

const isLive = (status: JobStatus) => status === 'OPEN' || status === 'SHORTLIST_CONFIRMED';

export function JobPage() {
  const { jobId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const { data: job, isLoading, error, refetch } = useJob(jobId);
  const { data: me } = useMe();
  const action = useJobAction();
  const [move, setMove] = useState<StatusMove | null>(null);
  if (isLoading) return <Loading />;
  if (error || !job) return <ErrorBlock error={error} onRetry={refetch} />;
  const canEdit = !!me?.canManageHiring;
  const showShortlist = job.aiShortlisting || job.counts.screened > 0;
  const tab = params.get('tab') ?? (showShortlist && job.counts.cvs > 0 ? 'shortlist' : 'pipeline');
  const aiRound = job.rounds.find((r) => r.kind === 'AI_INTERVIEW');

  const publish = () =>
    action.mutate(
      { id: job.id, action: 'publish' },
      { onSuccess: () => notifySuccess('The job is open for CVs.'), onError: (e) => notifyError(e) },
    );

  return (
    <>
      <PageHeader
        eyebrow={
          <Group gap="xs">
            <JobStar jobId={job.id} starred={job.starred} size="sm" />
            <Text size="sm" c="dimmed">
              {job.jobRef}
            </Text>
            <ToneBadge tone={jobStatus[job.status]} size="sm" />
            {!job.aiShortlisting && (
              <Badge size="sm" variant="light" color="gray">
                AI shortlisting off
              </Badge>
            )}
          </Group>
        }
        title={job.title}
        subtitle={[
          job.location,
          job.contractType && contractType[job.contractType],
          job.openings && `${job.openings} opening${job.openings > 1 ? 's' : ''}`,
        ]
          .filter(Boolean)
          .join(' · ')}
        actions={
          canEdit && (
            <>
              {job.status === 'DRAFT' && (
                <Button leftSection={<IconRocket size={18} />} onClick={publish} loading={action.isPending}>
                  Publish
                </Button>
              )}
              {aiRound && isLive(job.status) && (
                <Button
                  variant="light"
                  component={Link}
                  to={`/jobs/${job.id}/ai-interviews?round=${aiRound.round}`}
                  leftSection={<IconCalendarEvent size={18} />}
                >
                  Plan AI interviews
                </Button>
              )}
              <Menu position="bottom-end">
                <Menu.Target>
                  <Button variant="default" rightSection={<IconChevronDown size={16} />}>
                    More
                  </Button>
                </Menu.Target>
                <Menu.Dropdown>
                  {job.status !== 'CLOSED' && (
                    <Menu.Item leftSection={<IconEdit size={16} />} component={Link} to={`/jobs/${job.id}/edit`}>
                      Edit job
                    </Menu.Item>
                  )}
                  <Menu.Item leftSection={<IconCopy size={16} />} component={Link} to={`/jobs/new?from=${job.id}`}>
                    Clone job
                  </Menu.Item>
                  {isLive(job.status) && (
                    <Menu.Item leftSection={<IconPlayerPause size={16} />} onClick={() => setMove(MOVES.hold)}>
                      Put on hold
                    </Menu.Item>
                  )}
                  {(job.status === 'ON_HOLD' || job.status === 'CLOSED') && (
                    <Menu.Item leftSection={<IconLockOpen size={16} />} onClick={() => setMove(MOVES.reopen)}>
                      Reopen job
                    </Menu.Item>
                  )}
                  {job.status !== 'CLOSED' && job.status !== 'ARCHIVED' && (
                    <Menu.Item color="red" leftSection={<IconLock size={16} />} onClick={() => setMove({ ...MOVES.close, needsReason: job.status !== 'DRAFT' })}>
                      {job.status === 'DRAFT' ? 'Discard draft' : 'Close job'}
                    </Menu.Item>
                  )}
                </Menu.Dropdown>
              </Menu>
            </>
          )
        }
      />

      <Stack gap="sm" mb="lg">
        {job.coveringFor && (
          <Alert variant="light" color="blue" icon={<IconUserShare size={18} />} py="xs">
            <Text size="sm">You can see this job because you're covering for {job.coveringFor}.</Text>
          </Alert>
        )}
        {(job.status === 'ON_HOLD' || job.status === 'CLOSED') && (
          <Alert
            variant="light"
            color={job.status === 'ON_HOLD' ? 'orange' : 'gray'}
            icon={job.status === 'ON_HOLD' ? <IconPlayerPause size={18} /> : <IconLock size={18} />}
            py="xs"
          >
            <Text size="sm">
              <b>{job.status === 'ON_HOLD' ? 'On hold' : 'Closed'}</b>
              {job.statusChangedByName ? ` by ${job.statusChangedByName}` : ''}
              {job.statusChangedAt ? `, ${istLong(job.statusChangedAt)}` : ''}
              {job.statusReason ? `: “${job.statusReason}”` : ''}
            </Text>
          </Alert>
        )}
        <Group gap="lg" wrap="wrap">
          <Text size="sm">
            <Text span c="dimmed" size="sm">
              Owner{' '}
            </Text>
            {job.owner?.name ?? '—'}
          </Text>
          <Text size="sm">
            <Text span c="dimmed" size="sm">
              Hiring manager{' '}
            </Text>
            {job.hiringManager?.name ?? 'Not set'}
          </Text>
          <ChangesLine changes={job.changes} />
        </Group>
      </Stack>

      <PipelineStrip job={job} />

      <Card p={0} mt="lg">
        <Tabs value={tab} onChange={(value) => setParams({ tab: value ?? 'pipeline' }, { replace: true })} keepMounted={false}>
          <ScrollArea type="never">
            <Tabs.List px="md" pt="xs" style={{ flexWrap: 'nowrap' }}>
              {showShortlist && (
                <Tabs.Tab value="shortlist" rightSection={<Badge size="sm" variant="light">{job.counts.screened}</Badge>}>
                  AI shortlisting
                </Tabs.Tab>
              )}
              <Tabs.Tab value="cvs" rightSection={<Badge size="sm" variant="light" color="gray">{job.counts.cvs}</Badge>}>
                CVs
              </Tabs.Tab>
              <Tabs.Tab value="pipeline">Pipeline</Tabs.Tab>
              <Tabs.Tab value="interviews" rightSection={<Badge size="sm" variant="light" color="gray">{job.counts.interviews}</Badge>}>
                Interviews
              </Tabs.Tab>
              <Tabs.Tab value="details">Job details</Tabs.Tab>
              <Tabs.Tab value="history">History</Tabs.Tab>
            </Tabs.List>
          </ScrollArea>
          {showShortlist && (
            <Tabs.Panel value="shortlist" p="md">
              <ShortlistTab job={job} canEdit={canEdit} />
            </Tabs.Panel>
          )}
          <Tabs.Panel value="cvs" p="md">
            <CvTab job={job} canEdit={canEdit} />
          </Tabs.Panel>
          <Tabs.Panel value="pipeline" p="md">
            <PipelineBoard job={job} />
          </Tabs.Panel>
          <Tabs.Panel value="interviews" p="md">
            <JobInterviews jobId={job.id} />
          </Tabs.Panel>
          <Tabs.Panel value="details" p="md">
            <JobDetails job={job} />
          </Tabs.Panel>
          <Tabs.Panel value="history" p="md">
            <HistoryList filter={{ jobId: job.id, type: 'job', size: 30 }} empty="No changes to this job yet." />
          </Tabs.Panel>
        </Tabs>
      </Card>

      {move && <StatusModal job={job} move={move} onClose={() => setMove(null)} />}
    </>
  );
}

function StatusModal({ job, move, onClose }: { job: JobDetail; move: StatusMove; onClose: () => void }) {
  const [reason, setReason] = useState('');
  const change = useJobStatus();
  const closing = move.status === 'CLOSED' && job.status !== 'DRAFT';
  const impact = useCloseImpact(job.id, closing);
  const short = move.needsReason && reason.trim().length < 5;
  const submit = () => {
    if (short || change.isPending) return;
    change.mutate(
      { id: job.id, status: move.status, reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          const i = impact.data;
          notifySuccess(
            move.status === 'ON_HOLD'
              ? 'The job is on hold. Reminder emails are paused.'
              : move.status === 'CLOSED'
                ? i && (i.interviewsToCancel > 0 || i.otherCandidates > 0)
                  ? `The job is closed. ${plural(i.interviewsToCancel, 'interview')} cancelled and ${plural(
                      i.candidatesWithInterviews + i.otherCandidates,
                      'candidate',
                    )} told.`
                  : 'The job is closed.'
                : 'The job is open again.',
          );
          onClose();
        },
        onError: (e) => notifyError(e),
      },
    );
  };
  return (
    <Modal opened onClose={onClose} title={<Text fw={600}>{move.title}</Text>}>
      <Stack>
        <Text size="sm">{move.help}</Text>
        {closing && impact.data && (
          <Alert variant="light" color={impact.data.interviewsToCancel + impact.data.otherCandidates > 0 ? 'orange' : 'gray'} py="xs">
            {impact.data.interviewsToCancel + impact.data.otherCandidates === 0 ? (
              <Text size="sm">No interviews are booked and no candidates are part-way through, so no one is emailed.</Text>
            ) : (
              <Stack gap={4}>
                <Text size="sm" fw={600}>
                  Closing the job will:
                </Text>
                {impact.data.interviewsToCancel > 0 && (
                  <Text size="sm">
                    • Cancel {plural(impact.data.interviewsToCancel, 'upcoming interview')}, release their seats and tell the panel
                    or assessor
                  </Text>
                )}
                {impact.data.candidatesWithInterviews > 0 && (
                  <Text size="sm">
                    • Email {plural(impact.data.candidatesWithInterviews, 'candidate')} that their interview is cancelled
                  </Text>
                )}
                {impact.data.otherCandidates > 0 && (
                  <Text size="sm">• Email {plural(impact.data.otherCandidates, 'other candidate')} in progress that the position has closed</Text>
                )}
                <Text size="xs" c="dimmed">
                  The emails are sent automatically; the wording is under Settings, Email wording. Everything is recorded in history.
                </Text>
              </Stack>
            )}
          </Alert>
        )}
        <Textarea
          label={move.needsReason ? 'Reason' : 'Note (optional)'}
          description={move.needsReason ? 'Shown on the job and in the audit trail.' : undefined}
          placeholder={move.status === 'ON_HOLD' ? 'e.g. Budget review until January' : move.status === 'CLOSED' ? 'e.g. Position filled' : ''}
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          maxLength={500}
          autosize
          minRows={2}
          data-autofocus
          error={move.needsReason && reason.length > 0 && short ? 'At least 5 characters' : null}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button color={move.status === 'CLOSED' ? 'red' : move.status === 'ON_HOLD' ? 'orange' : undefined} onClick={submit} disabled={short} loading={change.isPending}>
            {move.verb}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function plural(n: number, word: string) {
  return `${n} ${word}${n === 1 ? '' : 's'}`;
}

/** How many candidates are at each stage, with each of the job's own rounds. */
function PipelineStrip({ job }: { job: JobDetail }) {
  const p = job.pipeline;
  return (
    <ScrollArea type="auto" offsetScrollbars>
      <Group gap="sm" wrap="nowrap" align="stretch">
        {job.aiShortlisting && <StripCard label="CV screening" value={p.screening} />}
        {p.rounds.map((r) => (
          <StripCard
            key={r.round}
            label={`Round ${r.round}`}
            sub={r.name}
            value={r.count}
            color={roundKind[r.kind].color}
            overdue={r.overdue}
          />
        ))}
        <StripCard label="Offer" value={p.offer} />
        <StripCard label="Hired" value={p.hired} color="green" />
        <StripCard label="Not progressing" value={p.notProgressing + p.withdrawn} muted />
      </Group>
    </ScrollArea>
  );
}

function StripCard({ label, sub, value, color, overdue = 0, muted = false }: {
  label: string;
  sub?: string;
  value: number;
  color?: string;
  overdue?: number;
  muted?: boolean;
}) {
  return (
    <Card padding="sm" miw={128} style={{ flex: '1 0 128px', borderTop: color ? `3px solid var(--mantine-color-${color}-5)` : undefined }}>
      <Text className="stat-label">{label}</Text>
      <Text size="xs" c="dimmed" truncate maw={160} style={{ visibility: sub ? 'visible' : 'hidden' }}>
        {sub ?? '—'}
      </Text>
      <Group gap={6} align="baseline" mt={2}>
        <Text fw={700} fz={24} c={muted ? 'dimmed' : 'navy.9'} className="tabular">
          {value}
        </Text>
        {overdue > 0 && (
          <Text size="xs" c="red.7" fw={600}>
            {overdue} overdue
          </Text>
        )}
      </Group>
    </Card>
  );
}

function PipelineBoard({ job }: { job: JobDetail }) {
  const { data: rows, isLoading } = useApplications(job.id);
  const navigate = useNavigate();
  if (isLoading || !rows) return <Loading />;
  const active = (r: ApplicationRow) => r.status === 'ACTIVE';
  const columns: { key: string; label: string; sub?: string; color?: string; match: (r: ApplicationRow) => boolean }[] = [
    ...(job.aiShortlisting || rows.some((r) => r.stage === 'SCREENING' && active(r))
      ? [{ key: 'screening', label: 'CV screening', match: (r: ApplicationRow) => r.stage === 'SCREENING' && active(r) && r.decision !== 'REJECTED' }]
      : []),
    ...job.rounds.map((round) => ({
      key: `round${round.round}`,
      label: `Round ${round.round}`,
      sub: round.name,
      color: roundKind[round.kind].color,
      match: (r: ApplicationRow) => active(r) && (r.stage === `ROUND${round.round}` || (round.round === 1 && r.stage === 'SHORTLIST')),
    })),
    { key: 'offer', label: 'Offer', match: (r) => r.stage === 'OFFER' && active(r) },
    { key: 'hired', label: 'Hired', color: 'green', match: (r) => r.status === 'HIRED' },
  ];
  const closed = rows.filter((r) => r.status === 'REJECTED' || r.status === 'WITHDRAWN' || (r.decision === 'REJECTED' && r.stage === 'SCREENING'));
  return (
    <Stack>
      <ScrollArea type="auto" offsetScrollbars>
        <Group align="flex-start" wrap="nowrap" gap="sm">
          {columns.map((col) => {
            const items = rows.filter(col.match).sort((a, b) => (b.matchScore ?? 0) - (a.matchScore ?? 0));
            return (
              <Stack key={col.key} className="pipeline-col" p="sm" gap="xs" w={220} style={{ borderTop: col.color ? `3px solid var(--mantine-color-${col.color}-5)` : undefined }}>
                <Group justify="space-between" wrap="nowrap" align="flex-start">
                  <Box style={{ minWidth: 0 }}>
                    <Text size="sm" fw={600}>
                      {col.label}
                    </Text>
                    {col.sub && (
                      <Text size="xs" c="dimmed" truncate>
                        {col.sub}
                      </Text>
                    )}
                  </Box>
                  <Badge variant="light" color="gray" size="sm">
                    {items.length}
                  </Badge>
                </Group>
                {items.length === 0 && (
                  <Text size="xs" c="dimmed">
                    No one here
                  </Text>
                )}
                {items.map((r) => (
                  <UnstyledButton key={r.id} onClick={() => navigate(`/applications/${r.id}`)}>
                    <Card padding="xs" radius="md" shadow="xs">
                      <Group justify="space-between" wrap="nowrap" gap={6}>
                        <Text size="sm" fw={500} truncate>
                          {r.candidateName}
                        </Text>
                        {r.matchScore !== null && <ScorePill score={r.matchScore} />}
                      </Group>
                      {r.nextAction !== 'none' && (
                        <Text size="xs" c={r.nextAction === 'waiting' ? 'dimmed' : `${nextAction[r.nextAction].color}.7`} mt={2}>
                          {nextAction[r.nextAction].label}
                        </Text>
                      )}
                    </Card>
                  </UnstyledButton>
                ))}
              </Stack>
            );
          })}
        </Group>
      </ScrollArea>
      {closed.length > 0 && (
        <Text size="sm" c="dimmed">
          Not progressing or withdrawn: {closed.map((r) => r.candidateName).join(', ')}
        </Text>
      )}
    </Stack>
  );
}

function JobInterviews({ jobId }: { jobId: string }) {
  const upcoming = useInterviews('upcoming', jobId);
  const done = useInterviews('completed', jobId);
  if (upcoming.isLoading || done.isLoading) return <Loading />;
  return (
    <Stack>
      <Title order={4} className="section-title">
        Upcoming
      </Title>
      <InterviewsTable rows={upcoming.data ?? []} showJob={false} emptyTitle="No upcoming interviews" />
      <Title order={4} mt="md" className="section-title">
        Completed and cancelled
      </Title>
      <InterviewsTable rows={done.data ?? []} showJob={false} emptyTitle="None yet" />
    </Stack>
  );
}

function JobDetails({ job }: { job: JobDetail }) {
  const kinds: CriterionKind[] = ['MUST_HAVE', 'NICE_TO_HAVE', 'CERTIFICATION'];
  return (
    <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
      <Stack>
        <Card withBorder>
          <Title order={4} mb="sm" className="section-title">
            Skills
          </Title>
          <Stack gap="sm">
            {kinds.map((kind) => (
              <div key={kind}>
                <Text size="sm" c="dimmed">
                  {criterionKind[kind]}
                </Text>
                <Group gap={6} mt={4}>
                  {job.criteria.filter((c) => c.kind === kind).length === 0 && <Text size="sm">—</Text>}
                  {job.criteria
                    .filter((c) => c.kind === kind)
                    .map((c) => (
                      <Badge
                        key={c.id}
                        variant="outline"
                        color={kind === 'MUST_HAVE' ? 'ett' : 'gray'}
                        title={c.alsoMatches.length ? `Also matches: ${c.alsoMatches.join(', ')}` : undefined}
                      >
                        {c.value}
                        {c.minYears ? ` · ${c.minYears}+ yrs` : ''}
                      </Badge>
                    ))}
                </Group>
              </div>
            ))}
            <Text size="sm">
              Minimum experience: {job.minExperienceYrs !== null ? `${job.minExperienceYrs} years` : 'not set'}
            </Text>
            <Text size="sm">
              Notice period up to: {job.maxNoticeDays !== null ? `${job.maxNoticeDays} days` : 'any'} · CTC budget up to:{' '}
              {job.maxCtcLpa !== null ? lpa(job.maxCtcLpa) : 'not set'}
            </Text>
            <Text size="sm">Locations: {job.acceptedLocations.length ? job.acceptedLocations.join(', ') : 'any'}</Text>
            <Text size="xs" c="dimmed">
              {job.aiShortlisting
                ? 'These limits only raise points to check on a CV. They never change the score.'
                : 'AI shortlisting is off for this job. Panel members score candidates on the must-haves.'}
            </Text>
          </Stack>
        </Card>
        <Card withBorder>
          <Title order={4} mb="sm" className="section-title">
            Job description
          </Title>
          {job.description ? (
            <>
              {job.descriptionFileName && (
                <Text size="xs" c="dimmed" mb="xs">
                  From {job.descriptionFileName}
                </Text>
              )}
              <Spoiler maxHeight={180} showLabel="Show all" hideLabel="Show less">
                <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                  {job.description}
                </Text>
              </Spoiler>
            </>
          ) : (
            <Text size="sm" c="dimmed">
              No job description yet. Add one with Edit job.
            </Text>
          )}
        </Card>
      </Stack>
      <Card withBorder>
        <Title order={4} mb="md" className="section-title">
          Interview rounds
        </Title>
        <PlanPreview rounds={job.rounds.map((r) => ({ name: r.name, kind: r.kind, mode: r.mode ?? 'ONLINE', durationMin: r.durationMin ?? 60, targetDays: r.targetDays }))} aiShortlisting={job.aiShortlisting} />
      </Card>
    </SimpleGrid>
  );
}
