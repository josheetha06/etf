import { Alert, Anchor, Badge, Box, Button, Card, Divider, Grid, Group, Stack, Stepper, Switch, Tabs, Text, Title } from '@mantine/core';
import { IconMail, IconMapPin, IconPencil, IconPhone, IconUserShare } from '@tabler/icons-react';
import { LocationEditor } from '../components/candidate/LocationEditor';
import { useEffect, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router';
import { useApplication, useMe } from '../api/hooks';
import type { ApplicationDetail, RoundDetail } from '../api/types';
import { SampleBadge, ToneBadge } from '../components/Badges';
import { NextStepPanel } from '../components/candidate/NextStepPanel';
import { RescheduleModal } from '../components/candidate/RescheduleModal';
import { RoundReportCard, ShortlistReport } from '../components/candidate/ReportCards';
import { HistoryList } from '../components/common/HistoryList';
import { ErrorBlock, Loading } from '../components/QueryState';
import { istLong } from '../lib/format';
import { applicationStatus, jobStatus, journey, locationSource, locationStatus, roundKind, roundLabel, stage as stageLabel } from '../lib/labels';
import { CandidateActions } from './application/CandidateActions';
import { InterviewsTab } from './application/InterviewsTab';
import { KeyFactsCard } from './application/KeyFactsCard';
import { MessagesTab } from './application/MessagesTab';
import { NotesTab } from './application/NotesTab';
import { TimelineTab } from './application/TimelineTab';

export function ApplicationPage() {
  const { applicationId = '' } = useParams();
  const { data, isLoading, error, refetch } = useApplication(applicationId);
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  // The old Timeline tab is now part of History.
  const tab = params.get('tab') === 'timeline' ? 'history' : (params.get('tab') ?? 'reports');
  const rescheduleId = params.get('reschedule');

  useEffect(() => {
    if (location.hash === '#decide') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [location.hash, applicationId]);

  if (isLoading) return <Loading />;
  if (error || !data) return <ErrorBlock error={error} onRetry={refetch} />;

  const setTab = (value: string | null) => {
    const next = new URLSearchParams(params);
    next.set('tab', value ?? 'reports');
    setParams(next, { replace: true });
  };
  const closeReschedule = () => {
    const next = new URLSearchParams(params);
    next.delete('reschedule');
    setParams(next, { replace: true });
  };

  return (
    <Stack gap="lg">
      <Header detail={data} />
      <StageTracker detail={data} />
      <Grid gap="lg">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Stack gap="lg">
            <NextStepPanel detail={data} />
            <Tabs value={tab} onChange={setTab} keepMounted={false}>
              <Tabs.List mb="md">
                <Tabs.Tab value="reports">Reports</Tabs.Tab>
                <Tabs.Tab value="interviews">
                  Interviews {data.interviews.length > 0 && <Badge size="xs" variant="light" ml={4}>{data.interviews.length}</Badge>}
                </Tabs.Tab>
                <Tabs.Tab value="notes">Notes</Tabs.Tab>
                <Tabs.Tab value="messages">
                  Messages {data.messages.length > 0 && <Badge size="xs" variant="light" ml={4}>{data.messages.length}</Badge>}
                </Tabs.Tab>
                <Tabs.Tab value="history">History</Tabs.Tab>
              </Tabs.List>
              <Tabs.Panel value="reports">
                <Reports detail={data} />
              </Tabs.Panel>
              <Tabs.Panel value="interviews">
                <InterviewsTab detail={data} />
              </Tabs.Panel>
              <Tabs.Panel value="notes">
                <NotesTab applicationId={data.id} />
              </Tabs.Panel>
              <Tabs.Panel value="messages">
                <MessagesTab detail={data} />
              </Tabs.Panel>
              <Tabs.Panel value="history">
                <HistoryTab applicationId={data.id} />
              </Tabs.Panel>
            </Tabs>
          </Stack>
        </Grid.Col>
        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Stack gap="lg">
            <KeyFactsCard applicationId={data.id} />
            <AboutCard detail={data} />
          </Stack>
        </Grid.Col>
      </Grid>
      <RescheduleModal requestId={rescheduleId} opened={!!rescheduleId} onClose={closeReschedule} />
    </Stack>
  );
}

function Header({ detail }: { detail: ApplicationDetail }) {
  const c = detail.candidate;
  return (
    <Stack gap={6}>
      <Group gap="xs">
        <Text size="sm" c="dimmed">
          <Anchor component={Link} to={`/jobs/${detail.job.id}`} c="dimmed">
            {detail.job.title} · {detail.job.jobRef}
          </Anchor>
        </Text>
        {(detail.job.status === 'ON_HOLD' || detail.job.status === 'CLOSED') && (
          <ToneBadge tone={jobStatus[detail.job.status]} size="sm" />
        )}
      </Group>
      {detail.coveringFor && (
        <Alert variant="light" color="blue" icon={<IconUserShare size={18} />} py="xs">
          You're covering for {detail.coveringFor} while they're on leave. What you do here is recorded as covering for them.
        </Alert>
      )}
      <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
        <Stack gap={6}>
          <Group gap="sm">
            <Title order={1} className="page-title" fz={{ base: 26, sm: 32 }}>
              {c.fullName}
            </Title>
            <Text c="dimmed" size="sm">
              {c.candidateRef}
            </Text>
            <ToneBadge tone={applicationStatus[detail.status]} />
          </Group>
          <Group gap="lg" c="dimmed">
            <Anchor href={`mailto:${c.email}`} size="sm" c="dimmed">
              <IconMail size={14} style={{ verticalAlign: -2 }} /> {c.email}
            </Anchor>
            {c.mobile && (
              <Anchor href={`tel:${c.mobile.replace(/\s/g, '')}`} size="sm" c="dimmed">
                <IconPhone size={14} style={{ verticalAlign: -2 }} /> {c.mobile}
              </Anchor>
            )}
          </Group>
          <LocationLine detail={detail} />
        </Stack>
        <CandidateActions detail={detail} />
      </Group>
    </Stack>
  );
}

/** Where the candidate is, where that came from, their nearest partner assessment centre city, and an edit button. */
function LocationLine({ detail }: { detail: ApplicationDetail }) {
  const { data: me } = useMe();
  const [editing, setEditing] = useState(false);
  const loc = detail.location;
  if (editing) {
    return (
      <Stack gap={4} maw={520}>
        <LocationEditor
          applicationId={detail.id}
          initial={loc.status === 'known' ? '' : (loc.text ?? '')}
          onSaved={() => setEditing(false)}
          onCancel={() => setEditing(false)}
          autoFocus
        />
        <Text size="xs" c="dimmed">
          A town, city or PIN code. Saved as "Set by HR" and recorded in the audit trail.
        </Text>
      </Stack>
    );
  }
  return (
    <Group gap={8} wrap="wrap">
      <IconMapPin size={15} color="var(--mantine-color-dimmed)" />
      {loc.status === 'known' ? (
        <Text size="sm">{loc.label}</Text>
      ) : (
        <>
          {loc.text && <Text size="sm">{loc.text}</Text>}
          <Badge size="sm" variant="light" color={locationStatus[loc.status].color}>
            {locationStatus[loc.status].label}
          </Badge>
        </>
      )}
      {loc.source && loc.status === 'known' && (
        <Badge size="sm" variant="light" color={loc.source === 'ai_interview' ? 'violet' : loc.source === 'hr' ? 'blue' : 'gray'}>
          {locationSource[loc.source]}
        </Badge>
      )}
      {loc.nearestCity && (
        <Text size="sm" c="dimmed">
          · Nearest partner assessment centres: <b>{loc.nearestCity}</b>
          {loc.nearestCityMinutes !== null ? ` (about ${journey(loc.nearestCityMinutes)})` : ''}
        </Text>
      )}
      {me?.canManageHiring && (
        <Button variant="subtle" size="compact-xs" leftSection={<IconPencil size={12} />} onClick={() => setEditing(true)}>
          {loc.status === 'known' ? 'Change' : 'Add location'}
        </Button>
      )}
    </Group>
  );
}

function StageTracker({ detail }: { detail: ApplicationDetail }) {
  const steps = [
    { key: 'start', label: detail.job.aiShortlisting ? 'Shortlisting' : 'CV added', note: detail.screening ? `Score ${detail.screening.matchScore}` : detail.job.aiShortlisting ? undefined : 'AI shortlisting off' },
    ...detail.rounds.map((r) => ({ key: `r${r.round}`, label: r.name, note: roundKind[r.kind].label })),
    { key: 'offer', label: 'Offer', note: detail.status === 'HIRED' ? 'Accepted' : undefined },
  ];
  const round = detail.stage === 'SHORTLIST' ? 1 : detail.stage.startsWith('ROUND') ? Number(detail.stage.slice(5)) : 0;
  const index =
    detail.status === 'HIRED' ? steps.length : detail.stage === 'OFFER' ? steps.length - 1 : round > 0 ? round : 0;
  return (
    <Card padding="md">
      <Stepper
        active={index}
        size="sm"
        iconSize={28}
        allowNextStepsSelect={false}
        color={detail.status === 'ACTIVE' || detail.status === 'HIRED' ? 'ett' : 'gray'}
        wrap
      >
        {steps.map((s) => (
          <Stepper.Step key={s.key} label={s.label} description={s.note} />
        ))}
      </Stepper>
    </Card>
  );
}

function Reports({ detail }: { detail: ApplicationDetail }) {
  const shown = [...detail.rounds].filter((r) => r.state !== 'upcoming' && r.state !== 'not_reached').reverse();
  return (
    <Stack gap="lg">
      {shown.map((r) => (
        <RoundReportCard key={r.round} detail={detail} round={r} />
      ))}
      {(detail.job.aiShortlisting || detail.screening) && <ShortlistReport detail={detail} />}
      {!detail.job.aiShortlisting && !detail.screening && shown.length === 0 && (
        <Card>
          <Text size="sm" c="dimmed">
            AI shortlisting is off for this job, so the candidate went straight to {roundLabel(detail.rounds[0]?.name, 1)}.
            Reports appear here after each round.
          </Text>
        </Card>
      )}
    </Stack>
  );
}

/**
 * History in plain words (what happened, who did it, when), newest first. "Show exact changes" switches to the
 * field-by-field audit record.
 */
function HistoryTab({ applicationId }: { applicationId: string }) {
  const [exact, setExact] = useState(false);
  return (
    <Stack gap="sm">
      <Group justify="space-between" wrap="wrap">
        <Text size="sm" c="dimmed">
          {exact
            ? 'Every change to this candidate, their progress and their interviews, field by field, with who made it.'
            : 'What has happened to this candidate in this job, newest first.'}
        </Text>
        <Switch label="Show exact changes" checked={exact} onChange={(e) => setExact(e.currentTarget.checked)} />
      </Group>
      {exact ? (
        <Card>
          <HistoryList filter={{ applicationId }} />
        </Card>
      ) : (
        <TimelineTab applicationId={applicationId} />
      )}
    </Stack>
  );
}

const DECIDED: Record<string, string> = { ADVANCE: 'Moved on', HOLD: 'On hold', REJECT: 'Not progressing' };

function AboutCard({ detail }: { detail: ApplicationDetail }) {
  const anySample = detail.rounds.some((r) => r.aiReport?.sample || r.partnerReport?.sample || r.panelReport?.sample);
  const reached = detail.rounds.filter((r) => r.interview || r.decision);
  return (
    <Card>
      <Title order={5} className="section-title" mb="xs">
        Application
      </Title>
      <Stack gap={8}>
        <Row label="Added">{istLong(detail.addedAt)}</Row>
        <Row label="Added by">{detail.addedByName ?? '—'}</Row>
        <Row label="CV">{detail.cvAvailable ? 'On file' : 'Deleted'}</Row>
      </Stack>
      {reached.map((r) => (
        <RoundBlock key={r.round} round={r} />
      ))}
      {anySample && (
        <Group gap="xs" mt="md">
          <SampleBadge />
          <Text size="xs" c="dimmed">
            Some reports are samples for trying out the flow.
          </Text>
        </Group>
      )}
      {detail.otherJobs.length > 0 && (
        <>
          <Text size="sm" fw={600} mt="md" mb={4}>
            Also in
          </Text>
          <Stack gap={4}>
            {detail.otherJobs.map((job) => (
              <Anchor key={job.applicationId} component={Link} to={`/applications/${job.applicationId}`} size="sm">
                {job.jobTitle} · {stageLabel[job.stage]}
              </Anchor>
            ))}
          </Stack>
        </>
      )}
    </Card>
  );
}

/** One round: when the interview is (and where, for a partner centre), and the decision, who made it and when. */
function RoundBlock({ round }: { round: RoundDetail }) {
  const i = round.interview;
  const d = round.decision;
  const booked = i && i.status !== 'CANCELLED';
  return (
    <Box mt="sm">
      <Divider mb="sm" />
      <Group gap={6} mb={6} wrap="nowrap">
        <Badge size="xs" variant="light" color={roundKind[round.kind].color}>
          Round {round.round}
        </Badge>
        <Text size="sm" fw={600} lineClamp={1}>
          {round.name}
        </Text>
      </Group>
      <Stack gap={6}>
        <Row label="Interview">
          {i?.startAt ? istLong(i.startAt) : booked ? 'Time to be set' : '—'}
          {i && (i.status === 'CANCELLED' || i.status === 'NO_SHOW') ? ` (${i.status === 'NO_SHOW' ? 'did not attend' : 'cancelled'})` : ''}
        </Row>
        {round.kind === 'PARTNER' && i?.centreName && <Row label="Centre">{i.centreName}</Row>}
        <Row label="Decision">
          {d ? (
            <>
              {DECIDED[d.decision] ?? d.decision}
              <Text span size="xs" c="dimmed" display="block">
                {d.decidedByName ?? 'Someone'}
                {d.decidedAt ? `, ${istLong(d.decidedAt)}` : ''}
              </Text>
            </>
          ) : booked ? (
            <Text span size="sm" c="orange.8">
              Waiting for decision
            </Text>
          ) : (
            '—'
          )}
        </Row>
      </Stack>
    </Box>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Group justify="space-between" wrap="nowrap" gap="sm" align="flex-start">
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text size="sm" ta="right">
        {children}
      </Text>
    </Group>
  );
}
