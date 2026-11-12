import { Alert, Badge, Button, Card, Group, SegmentedControl, Select, Stack, Text, Textarea, Title } from '@mantine/core';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import {
  IconArrowRight,
  IconCalendarPlus,
  IconCheck,
  IconClockHour4,
  IconFlag,
  IconHourglass,
  IconArrowBackUp,
  IconRefresh,
} from '@tabler/icons-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
  useCandidates,
  useDecisionReasons,
  useMe,
  useOfferOutcome,
  useRecheck,
  useShortlistDecision,
  useStageDecision,
  useUndoDecision,
} from '../../api/hooks';
import type {
  AiSuggestion,
  ApplicationDetail,
  NextAction,
  Recommendation,
  ShortlistDecision,
  StageDecisionType,
  StageDecisionView,
} from '../../api/types';
import { ist, istTime } from '../../lib/format';
import { applicationStatus, interviewStatus, recommendation as recTone, roundKind, roundLabel, suggestion } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';
import { ToneBadge } from '../Badges';
import { InterviewActions } from './InterviewActions';
import { RescheduleModal } from './RescheduleModal';
import { FlagList } from './ReportCards';
import { ScheduleModal } from './ScheduleModal';

const TONE: Partial<Record<NextAction, string>> = {
  answer_reschedule: 'orange',
  review_flags: 'red',
  waiting: 'gray',
  none: 'gray',
};

/**
 * "Decide now": the one thing HR should do next for this candidate, with the controls to do it right here
 * (decide, schedule, answer a request, record the offer), and a way on to the next candidate who needs a decision.
 */
export function NextStepPanel({ detail }: { detail: ApplicationDetail }) {
  const { data: me } = useMe();
  const action = detail.nextStep.action;
  // Hiring managers make decisions; booking interviews and recording offers is for recruiters and HR admins.
  const isDecision = action === 'decide_shortlist' || action === 'review_flags' || action === 'decide_round';
  const canAct = isDecision ? !!me?.canDecide : !!me?.canManageHiring;
  return (
    <Card className="next-step" data-tone={TONE[action] ?? 'ett'} padding="lg" id="decide">
      <Group justify="space-between" align="flex-start" mb="sm" wrap="nowrap">
        <Stack gap={2}>
          <Text className="stat-label">Next step</Text>
          <Title order={3} className="section-title">
            {headline(detail)}
          </Title>
          {detail.nextStep.dueAt && (
            <Text size="xs" c={detail.nextStep.overdue ? 'red.7' : 'dimmed'} fw={detail.nextStep.overdue ? 600 : 400}>
              {detail.nextStep.overdue ? 'Past the round’s target time: due ' : 'Target: by '}
              {ist(detail.nextStep.dueAt)}
            </Text>
          )}
        </Stack>
        <NextCandidate current={detail.id} />
      </Group>
      <UndoBar detail={detail} />
      {!canAct && action !== 'waiting' && action !== 'none' ? (
        <Text size="sm" c="dimmed">
          {isDecision
            ? 'Your role can view this candidate. A recruiter, hiring manager or HR admin makes this decision.'
            : 'A recruiter or HR admin books interviews and records offers. You can make the decisions after each round.'}
        </Text>
      ) : (
        <Body detail={detail} action={action} />
      )}
    </Card>
  );
}

const DECIDED: Record<StageDecisionType, string> = {
  ADVANCE: 'Moved on',
  HOLD: 'On hold',
  REJECT: 'Not progressing',
};

/** Re-renders every few seconds while an undo is possible, so the bar goes away on time. */
function useTick(active: boolean) {
  const [, setTick] = useState(0);
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setTick((t) => t + 1), 5_000);
    return () => window.clearInterval(timer);
  }, [active]);
}

/**
 * For 5 minutes after a round decision: the candidate's email waits, and HR can undo the decision. Undoing puts the
 * candidate back where they were and cancels the email.
 */
function UndoBar({ detail }: { detail: ApplicationDetail }) {
  const latest: StageDecisionView | undefined = detail.stageDecisions[0];
  const until = latest?.undoUntil ? Date.parse(latest.undoUntil) : 0;
  const open = until > Date.now();
  useTick(open);
  const undo = useUndoDecision();
  const { data: me } = useMe();
  if (!latest || !open || !me?.canDecide) return null;
  const round = Number(latest.stage.replace('ROUND', ''));
  const name = detail.rounds.find((r) => r.round === round)?.name ?? `Round ${round}`;
  return (
    <Alert color="blue" variant="light" mb="sm" py="xs">
      <Group justify="space-between" wrap="wrap" gap="xs">
        <Text size="sm">
          <b>{DECIDED[latest.decision]}</b> after {name}.{' '}
          {latest.decision === 'HOLD'
            ? 'You can undo this until '
            : `The email to ${detail.candidate.fullName.split(' ')[0]} goes at `}
          {istTime(latest.undoUntil)}.
        </Text>
        <Button
          size="compact-sm"
          variant="white"
          leftSection={<IconArrowBackUp size={14} />}
          loading={undo.isPending}
          onClick={() =>
            undo.mutate(latest.id, {
              onSuccess: () => notifySuccess('Decision undone. The email was not sent.'),
              onError: (error) => notifyError(error),
            })
          }
        >
          Undo
        </Button>
      </Group>
    </Alert>
  );
}

function headline(detail: ApplicationDetail): string {
  const step = detail.nextStep;
  const label = roundLabel(step.roundName, step.round);
  switch (step.action) {
    case 'answer_reschedule':
      return 'Asked for a different interview time';
    case 'review_flags':
      return 'Check the red flags, then decide';
    case 'decide_shortlist':
      return 'Shortlist decision';
    case 'schedule_round':
      return `Book ${label}`;
    case 'decide_round':
      return step.round === detail.job.roundsTotal ? `Final decision after ${label}` : `Review ${label} and decide`;
    case 'record_offer':
      return 'Offer sent: record the outcome';
    case 'waiting': {
      const current = detail.rounds.find((r) => r.round === step.round);
      const status = current?.interview?.status;
      if (status === 'COMPLETED') return current?.kind === 'PANEL' ? "Waiting for the panel's reviews" : 'Waiting for the report';
      if (status === 'INVITED') return 'Waiting for the candidate to reply';
      return 'Waiting on the interview';
    }
    default:
      return detail.status === 'ACTIVE' ? 'Nothing to do right now' : applicationStatus[detail.status].label;
  }
}

function Body({ detail, action }: { detail: ApplicationDetail; action: NextAction }) {
  switch (action) {
    case 'review_flags':
      return (
        <Stack>
          <Text size="sm">
            The AI found {detail.nextStep.redFlagsOpen} red flag{detail.nextStep.redFlagsOpen === 1 ? '' : 's'} in this CV. Look
            at each one before shortlisting. Flags never change the score.
          </Text>
          <FlagList flags={detail.screening?.flags.filter((f) => f.severity === 'red') ?? []} compact />
          <ShortlistForm detail={detail} blockShortlist />
        </Stack>
      );
    case 'decide_shortlist':
      return <ShortlistForm detail={detail} />;
    case 'schedule_round':
      return <ScheduleBlock detail={detail} />;
    case 'decide_round':
      return <RoundDecisionForm detail={detail} />;
    case 'answer_reschedule':
      return <RescheduleBlock detail={detail} />;
    case 'record_offer':
      return <OfferForm detail={detail} />;
    case 'waiting':
      return <WaitingBlock detail={detail} />;
    default:
      return <DoneBlock detail={detail} />;
  }
}

// ---------------------------------------------------------------- shortlist

const AGREES: Record<AiSuggestion, ShortlistDecision> = { SHORTLIST: 'SHORTLISTED', REVIEW: 'HOLD', REJECT: 'REJECTED' };

function ShortlistForm({ detail, blockShortlist = false }: { detail: ApplicationDetail; blockShortlist?: boolean }) {
  const ai = detail.screening?.suggestion ?? null;
  const [decision, setDecision] = useState<ShortlistDecision>(ai ? AGREES[ai] : 'SHORTLISTED');
  const [reason, setReason] = useState('');
  const decide = useShortlistDecision();
  const compact = useMediaQuery('(max-width: 36em)');
  const disagrees = ai !== null && AGREES[ai] !== decision;
  const reasonOk = !disagrees || reason.trim().length >= 5;
  const submit = () =>
    decide.mutate(
      { applicationId: detail.id, decision, reason: reason.trim() || undefined },
      {
        onSuccess: () =>
          notifySuccess(
            decision === 'SHORTLISTED'
              ? `${detail.candidate.fullName} is shortlisted. Next: book Round 1.`
              : 'Decision saved.',
          ),
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Stack gap="sm">
      {ai && (
        <Group gap="xs">
          <Text size="sm">AI suggests</Text>
          <ToneBadge tone={suggestion[ai]} size="sm" />
          <Text size="sm" c="dimmed">
            · score {detail.screening?.matchScore}
          </Text>
        </Group>
      )}
      <SegmentedControl
        value={decision}
        onChange={(value) => setDecision(value as ShortlistDecision)}
        data={[
          { value: 'SHORTLISTED', label: 'Shortlist', disabled: blockShortlist },
          { value: 'HOLD', label: 'Keep in reserve' },
          { value: 'REJECTED', label: 'Not now' },
        ]}
        maw={460}
        fullWidth
        orientation={compact ? 'vertical' : 'horizontal'}
      />
      {disagrees && (
        <Textarea
          label="Why are you going against the AI's suggestion?"
          description="A short reason helps the team and improves the accuracy figures in Settings."
          placeholder="For example: strong Azure work that maps to AWS; worth a Round 1."
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          autosize
          minRows={2}
          error={reason && !reasonOk ? 'At least 5 characters' : null}
        />
      )}
      <Group>
        <Button
          onClick={submit}
          loading={decide.isPending}
          disabled={!reasonOk || (blockShortlist && decision === 'SHORTLISTED')}
          leftSection={<IconCheck size={16} />}
        >
          Save decision
        </Button>
        {blockShortlist && (
          <Text size="xs" c="dimmed">
            <IconFlag size={12} style={{ verticalAlign: -2 }} /> Shortlisting unlocks once each red flag is reviewed.
          </Text>
        )}
      </Group>
    </Stack>
  );
}

// ---------------------------------------------------------------- rounds

function ScheduleBlock({ detail }: { detail: ApplicationDetail }) {
  const [opened, modal] = useDisclosure(false);
  const [params, setParams] = useSearchParams();
  // ?book=1 opens booking straight away (for example after "Didn't attend: offer a new time").
  useEffect(() => {
    if (params.get('book') === '1' && detail.job.status !== 'ON_HOLD') {
      modal.open();
      const next = new URLSearchParams(params);
      next.delete('book');
      setParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const step = detail.nextStep;
  const plan = detail.rounds.find((r) => r.round === step.round);
  if (!plan) return null;
  const onHold = detail.job.status === 'ON_HOLD';
  const missed = plan.interview?.status === 'NO_SHOW';
  const text = {
    AI_INTERVIEW: 'Pick a time. The candidate is invited to the online AI interview, where their Aadhaar, PAN and room are checked.',
    PARTNER: 'Book a seat at a partner assessment centre in the candidate\'s nearest centre city. The centres there take turns. The assessor gets a secure report link.',
    PANEL: 'Choose a time, online or in person, and type in the panel. Each member is emailed the interview code for their review.',
  }[plan.kind];
  return (
    <Stack gap="sm">
      {missed && (
        <Alert color="orange" variant="light" py="xs">
          {detail.candidate.fullName.split(' ')[0]} didn't attend the last {plan.name}. The new invitation says they missed it and
          offers this new time.
        </Alert>
      )}
      <Text size="sm">{text}</Text>
      {onHold && (
        <Alert color="orange" variant="light">
          This job is on hold, so new interviews can't be booked. Open the job again to book.
        </Alert>
      )}
      <Group>
        <Button leftSection={<IconCalendarPlus size={16} />} onClick={modal.open} disabled={onHold}>
          Book {plan.name}
        </Button>
      </Group>
      <ScheduleModal
        opened={opened}
        onClose={modal.close}
        applicationId={detail.id}
        candidateName={detail.candidate.fullName}
        round={{ round: plan.round, name: plan.name, kind: plan.kind, mode: plan.mode, durationMin: plan.durationMin }}
      />
    </Stack>
  );
}

function reportRecommendation(detail: ApplicationDetail): Recommendation | null {
  const plan = detail.rounds.find((r) => r.round === detail.nextStep.round);
  if (!plan) return null;
  if (plan.kind === 'AI_INTERVIEW') return plan.aiReport?.recommendation ?? null;
  if (plan.kind === 'PARTNER') return plan.partnerReport?.recommendation ?? null;
  return plan.panelReport?.combined ?? null;
}

function RoundDecisionForm({ detail }: { detail: ApplicationDetail }) {
  const round = detail.nextStep.round ?? 1;
  const plan = detail.rounds.find((r) => r.round === round);
  const recommended = reportRecommendation(detail);
  const [decision, setDecision] = useState<StageDecisionType>(recommended ?? 'ADVANCE');
  const [reason, setReason] = useState('');
  const [recheckOpen, setRecheckOpen] = useState(false);
  const [recheckNote, setRecheckNote] = useState('');
  const decide = useStageDecision(detail.id);
  const recheck = useRecheck(detail.id);
  const reasons = useDecisionReasons();
  const compact = useMediaQuery('(max-width: 36em)');
  const overrides = recommended !== null && recommended !== decision;
  const minReason = decision === 'REJECT' ? 10 : overrides ? 5 : 0;
  const reasonOk = reason.trim().length >= minReason;
  const stage = `ROUND${round}` as ApplicationDetail['stage'];
  const last = round >= detail.job.roundsTotal;
  const next = detail.rounds.find((r) => r.round === round + 1);
  const progressLabel = last ? 'Make an offer' : `Move on to ${next?.name ?? `Round ${round + 1}`}`;
  const submit = () =>
    decide.mutate(
      { stage, decision, reason: reason.trim() || undefined },
      {
        onSuccess: () =>
          notifySuccess(
            decision === 'ADVANCE'
              ? last
                ? 'Saved. The offer email goes in 5 minutes, so you can still undo.'
                : `Moving on. Next: book ${next?.name ?? `Round ${round + 1}`}. You can undo for 5 minutes.`
              : decision === 'HOLD'
                ? 'On hold. You can decide later.'
                : 'Saved. The email with your reason goes in 5 minutes, so you can still undo.',
          ),
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Stack gap="sm">
      {recommended && (
        <Group gap="xs">
          <Text size="sm">{plan?.kind === 'PANEL' ? 'Most of the panel recommends' : 'The report recommends'}</Text>
          <ToneBadge tone={recTone[recommended]} size="sm" />
          {plan?.kind === 'AI_INTERVIEW' && plan.aiReport?.identityCheck === 'mismatch' && (
            <Badge color="red" variant="light">
              Identity mismatch
            </Badge>
          )}
          {plan?.kind === 'AI_INTERVIEW' && plan.aiReport?.surroundingsCheck === 'concern' && (
            <Badge color="orange" variant="light">
              Surroundings concern
            </Badge>
          )}
          {plan?.kind === 'PARTNER' && plan.partnerReport?.idChecked === false && (
            <Badge color="red" variant="light">
              ID not confirmed in person
            </Badge>
          )}
          {plan?.kind === 'PANEL' && plan.panelReport && plan.panelReport.submitted < plan.panelReport.members && (
            <Badge color="gray" variant="light">
              {plan.panelReport.submitted} of {plan.panelReport.members} reviews in
            </Badge>
          )}
        </Group>
      )}
      <SegmentedControl
        value={decision}
        onChange={(value) => setDecision(value as StageDecisionType)}
        data={[
          { value: 'ADVANCE', label: progressLabel },
          { value: 'HOLD', label: 'Hold' },
          { value: 'REJECT', label: 'Not progressing' },
        ]}
        maw={560}
        fullWidth
        orientation={compact ? 'vertical' : 'horizontal'}
      />
      {decision === 'REJECT' && (reasons.data?.length ?? 0) > 0 && (
        <Select
          label="Ready-made reason"
          description="Pick one to start from, then edit it below if you like."
          placeholder="Choose a reason"
          data={(reasons.data ?? []).map((r) => ({ value: r, label: r }))}
          value={reasons.data?.includes(reason) ? reason : null}
          onChange={(value) => value && setReason(value)}
          maw={560}
          comboboxProps={{ withinPortal: true }}
          clearable={false}
        />
      )}
      {(overrides || decision === 'REJECT') && (
        <Textarea
          label={decision === 'REJECT' ? 'Reason the candidate will see' : 'Why are you going against the report?'}
          description={
            decision === 'REJECT'
              ? 'Sent to the candidate, who can reply to challenge it. At least 10 characters.'
              : 'Saved with the report review. At least 5 characters.'
          }
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          autosize
          minRows={2}
        />
      )}
      <Group>
        <Button onClick={submit} loading={decide.isPending} disabled={!reasonOk} leftSection={<IconCheck size={16} />}>
          {decision === 'ADVANCE' ? progressLabel : 'Save decision'}
        </Button>
        {plan?.kind === 'AI_INTERVIEW' && !recheckOpen && (
          <Button variant="subtle" color="grape" leftSection={<IconRefresh size={16} />} onClick={() => setRecheckOpen(true)}>
            Ask ETT to re-check
          </Button>
        )}
      </Group>
      {recheckOpen && (
        <Card padding="sm" radius="md" bg="grape.0">
          <Stack gap="xs">
            <Text size="sm">
              ETT looks at the recording again, mainly the identity and surroundings checks, and re-releases the report.
            </Text>
            <Textarea
              placeholder="What should ETT look at? For example: the PAN name doesn't match the CV."
              value={recheckNote}
              onChange={(e) => setRecheckNote(e.currentTarget.value)}
              autosize
              minRows={2}
            />
            <Group gap="xs">
              <Button
                size="xs"
                color="grape"
                disabled={recheckNote.trim().length < 5}
                loading={recheck.isPending}
                onClick={() =>
                  recheck.mutate(recheckNote.trim(), {
                    onSuccess: () => {
                      notifySuccess('ETT has been asked to re-check the report.');
                      setRecheckOpen(false);
                    },
                    onError: (error) => notifyError(error),
                  })
                }
              >
                Send to ETT
              </Button>
              <Button size="xs" variant="subtle" color="gray" onClick={() => setRecheckOpen(false)}>
                Cancel
              </Button>
            </Group>
          </Stack>
        </Card>
      )}
    </Stack>
  );
}

function RescheduleBlock({ detail }: { detail: ApplicationDetail }) {
  const [opened, modal] = useDisclosure(false);
  const interview = detail.interviews.find((i) => i.pendingRequestId === detail.nextStep.pendingRequestId) ??
    detail.interviews.find((i) => i.status === 'RESCHEDULE_REQUESTED');
  return (
    <Stack gap="sm">
      <Text size="sm">
        {detail.candidate.fullName} can't make {roundLabel(interview?.roundName, interview?.round)} on {ist(interview?.startAt)}.
        Pick a new time from the dates they're free, or keep the original. The original time stays held until you answer.
      </Text>
      <Group>
        <Button color="orange" leftSection={<IconCalendarPlus size={16} />} onClick={modal.open}>
          Choose a new time
        </Button>
      </Group>
      <RescheduleModal requestId={detail.nextStep.pendingRequestId} opened={opened} onClose={modal.close} />
    </Stack>
  );
}

function OfferForm({ detail }: { detail: ApplicationDetail }) {
  const record = useOfferOutcome(detail.id);
  const [note, setNote] = useState('');
  const save = (accepted: boolean) =>
    record.mutate(
      { accepted, note: note.trim() || undefined },
      {
        onSuccess: () => notifySuccess(accepted ? `${detail.candidate.fullName} is marked as hired.` : 'Recorded that the offer was declined.'),
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Stack gap="sm">
      <Text size="sm">The offer email went to the candidate. When they reply, record what they said.</Text>
      <Textarea placeholder="Joining date or reason, if you have it (optional)" value={note} onChange={(e) => setNote(e.currentTarget.value)} autosize minRows={1} maw={520} />
      <Group>
        <Button onClick={() => save(true)} loading={record.isPending} leftSection={<IconCheck size={16} />}>
          Accepted the offer
        </Button>
        <Button variant="default" onClick={() => save(false)} loading={record.isPending}>
          Declined
        </Button>
      </Group>
    </Stack>
  );
}

function WaitingBlock({ detail }: { detail: ApplicationDetail }) {
  const current = [...detail.interviews]
    .filter((i) => i.status !== 'CANCELLED')
    .sort((a, b) => b.round - a.round)[0];
  if (!current) {
    return (
      <Text size="sm" c="dimmed">
        The CV is being read and scored. This usually takes a minute or two.
      </Text>
    );
  }
  const started = current.startAt && new Date(current.startAt).getTime() < Date.now();
  const waitingFor =
    current.status === 'INVITED' && !started
      ? `Waiting for ${detail.candidate.fullName.split(' ')[0]} to accept (reply by ${ist(current.replyBy)}).`
      : current.status === 'CONFIRMED' && !started
        ? `Interview confirmed for ${ist(current.startAt)}${current.centreName ? ` at ${current.centreName}` : ''}.`
        : current.roundKind === 'AI_INTERVIEW'
          ? 'Waiting for the ETT reviewer to release the AI interview report.'
          : current.roundKind === 'PARTNER'
            ? 'Waiting for the partner assessment centre to send its report.'
            : `Waiting for the panel's reviews (${current.feedbackCount} of ${current.panelSize} in).`;
  return (
    <Group justify="space-between" align="center">
      <Group gap="sm" wrap="nowrap">
        {current.status === 'CONFIRMED' ? <IconClockHour4 size={20} /> : <IconHourglass size={20} />}
        <Stack gap={0}>
          <Text size="sm">{waitingFor}</Text>
          <Text size="xs" c="dimmed">
            {roundLabel(current.roundName, current.round)} · {current.roundKind ? roundKind[current.roundKind].label : ''} ·{' '}
            {interviewStatus[current.status].label}
          </Text>
        </Stack>
      </Group>
      <InterviewActions interview={current} />
    </Group>
  );
}

function DoneBlock({ detail }: { detail: ApplicationDetail }) {
  if (detail.status === 'HIRED') {
    return (
      <Alert color="green" icon={<IconCheck size={18} />}>
        {detail.candidate.fullName} accepted the offer. {detail.statusReason ?? ''}
      </Alert>
    );
  }
  if (detail.status !== 'ACTIVE') {
    return (
      <Text size="sm" c="dimmed">
        {applicationStatus[detail.status].label}
        {detail.statusReason ? `: ${detail.statusReason}` : '.'}
      </Text>
    );
  }
  const last = detail.shortlistHistory[0];
  return (
    <Stack gap="sm">
      <Text size="sm" c="dimmed">
        {last ? `Shortlist decision: ${last.decision === 'HOLD' ? 'kept in reserve' : 'not now'}.` : 'No action needed.'} You can change it.
      </Text>
      <ShortlistForm detail={detail} />
    </Stack>
  );
}

// ---------------------------------------------------------------- next candidate

function NextCandidate({ current }: { current: string }) {
  const navigate = useNavigate();
  const queue = useCandidates({ needsDecision: true, sort: 'added_asc', size: 200 });
  const next = useMemo(() => {
    const items = queue.data?.items ?? [];
    const index = items.findIndex((i) => i.applicationId === current);
    const rest = index >= 0 ? [...items.slice(index + 1), ...items.slice(0, index)] : items;
    return rest.find((i) => i.applicationId !== current) ?? null;
  }, [queue.data, current]);
  if (!next) return null;
  return (
    <Button
      variant="subtle"
      size="sm"
      rightSection={<IconArrowRight size={16} />}
      onClick={() => navigate(`/applications/${next.applicationId}#decide`)}
      title={`${queue.data?.total ?? 0} candidates need a decision`}
    >
      <Text span visibleFrom="sm" inherit>
        Next: {next.candidateName}
      </Text>
      <Text span hiddenFrom="sm" inherit>
        Next
      </Text>
    </Button>
  );
}
