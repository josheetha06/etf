import {
  Alert,
  Anchor,
  Badge,
  Box,
  Button,
  Card,
  Collapse,
  Container,
  Divider,
  Group,
  Paper,
  Progress,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertTriangle,
  IconCheck,
  IconCircleCheck,
  IconClock,
  IconExternalLink,
  IconFileCv,
  IconFileDescription,
  IconLock,
  IconLogout,
  IconMapPin,
  IconVideo,
} from '@tabler/icons-react';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useLogout, useMe, usePanelAccess, usePanelExit, usePanelReview, usePublicBranding, useSavePanelReview } from '../api/hooks';
import type { PanelRecommendation, PanelReviewPage, ReviewInput, ReviewScore } from '../api/types';
import { ClientLogo } from '../components/ClientLogo';
import { formatDuration } from '../components/common/DurationInput';
import { ErrorBlock, Loading } from '../components/QueryState';
import { dateTimeIn, INDIA, istLong } from '../lib/format';
import { panelRecommendation, roundLabel } from '../lib/labels';
import { notifyError, notifySuccess } from '../lib/notify';

/** The shared panel login: enter an employee ID and interview code, then fill in the review for that one interview. */
export function PanelPage() {
  const { data: review, isLoading, error, refetch } = usePanelReview(true);
  return (
    <PanelShell review={review ?? null}>
      {isLoading ? (
        <Loading />
      ) : error ? (
        <ErrorBlock error={error} onRetry={refetch} />
      ) : review ? (
        <ReviewForm key={review.interviewId + (review.submittedAt ?? '')} page={review} />
      ) : (
        <AccessForm />
      )}
    </PanelShell>
  );
}

function PanelShell({ review, children }: { review: PanelReviewPage | null; children: ReactNode }) {
  const { data: me } = useMe();
  const { data: branding } = usePublicBranding();
  const logout = useLogout();
  const navigate = useNavigate();
  return (
    <Box mih="100vh" bg="var(--brand-surface)">
      <Paper component="header" radius={0} className="app-header" px="md" h={64}>
        <Group h="100%" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
            {branding?.companyName ? <ClientLogo branding={branding} height={32} publicLogo /> : <Text fw={700}>{me?.company.name}</Text>}
            <Divider orientation="vertical" my={16} visibleFrom="xs" />
            <Text size="sm" c="dimmed" fw={500} visibleFrom="xs">
              Panel review
            </Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            {review && (
              <Text size="sm" c="dimmed" visibleFrom="sm">
                {review.memberName} · {review.employeeId}
              </Text>
            )}
            <Button
              variant="subtle"
              color="gray"
              size="xs"
              leftSection={<IconLogout size={14} />}
              onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })}
            >
              Sign out
            </Button>
          </Group>
        </Group>
      </Paper>
      <Container size={review ? 'md' : 440} py="xl" px="md">
        {children}
      </Container>
    </Box>
  );
}

/** "abcd2345" → "ABCD-2345" while typing. */
function formatCode(raw: string) {
  const clean = raw.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
  return clean.length > 4 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
}

function AccessForm() {
  const access = usePanelAccess();
  const [employeeId, setEmployeeId] = useState('');
  const [code, setCode] = useState('');
  const ready = employeeId.trim().length > 0 && code.replace('-', '').length === 8;
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!ready || access.isPending) return;
    access.mutate({ employeeId: employeeId.trim(), code });
  };
  return (
    <Card padding="xl" mt={40}>
      <form onSubmit={submit}>
        <Stack>
          <div>
            <Title order={2} className="page-title" fz={26}>
              Your interview review
            </Title>
            <Text size="sm" c="dimmed" mt={4}>
              Enter your employee ID and the interview code from your invitation email. The code opens the review for that
              interview only.
            </Text>
          </div>
          <TextInput
            label="Employee ID"
            placeholder="e.g. EMP-1043"
            value={employeeId}
            onChange={(e) => setEmployeeId(e.currentTarget.value)}
            autoComplete="off"
            data-autofocus
            required
          />
          <TextInput
            label="Interview code"
            placeholder="XXXX-XXXX"
            value={code}
            onChange={(e) => setCode(formatCode(e.currentTarget.value))}
            autoComplete="off"
            styles={{ input: { fontFamily: 'var(--mantine-font-family-monospace)', letterSpacing: 2, fontSize: 18 } }}
            required
          />
          {access.error && (
            <Alert color="red" variant="light" icon={<IconAlertTriangle size={16} />}>
              <Text size="sm">{access.error.message}</Text>
            </Alert>
          )}
          <Button type="submit" size="md" disabled={!ready} loading={access.isPending}>
            Open my review
          </Button>
          <Text size="xs" c="dimmed">
            The code works from an hour before the interview until 48 hours after it, or until HR makes their decision.
            Several wrong tries lock the panel login for 15 minutes.
          </Text>
        </Stack>
      </form>
    </Card>
  );
}

const SCALE = [
  { value: 1, label: 'Weak' },
  { value: 2, label: 'Below the bar' },
  { value: 3, label: 'Meets the bar' },
  { value: 4, label: 'Strong' },
  { value: 5, label: 'Outstanding' },
];

const RECOMMENDATIONS: PanelRecommendation[] = ['strong_hire', 'hire', 'no_hire', 'strong_no_hire'];

function emptyReview(page: PanelReviewPage): ReviewInput {
  const merge = (names: string[], saved: ReviewScore[] | undefined) =>
    names.map((name) => saved?.find((s) => s.name === name) ?? { name, score: null, comment: null });
  return {
    skills: merge(page.skills.map((s) => s.name), page.review?.skills),
    measures: merge(page.measures, page.review?.measures),
    strengths: page.review?.strengths ?? '',
    concerns: page.review?.concerns ?? '',
    questions: page.review?.questions ?? '',
    summary: page.review?.summary ?? '',
    nextRoundNotes: page.review?.nextRoundNotes ?? '',
    recommendation: page.review?.recommendation ?? null,
  };
}

function average(scores: ReviewScore[]) {
  const done = scores.filter((s) => s.score !== null);
  return done.length ? done.reduce((sum, s) => sum + (s.score ?? 0), 0) / done.length : null;
}

function ReviewForm({ page }: { page: PanelReviewPage }) {
  const [values, setValues] = useState<ReviewInput>(() => emptyReview(page));
  const [dirty, setDirty] = useState(false);
  const [jdOpen, jd] = useDisclosure(false);
  const save = useSavePanelReview();
  const exit = usePanelExit();
  const zone = INDIA;
  const [openedAt] = useState(() => Date.now());
  const started = new Date(page.startAt).getTime() <= openedAt;
  const canEdit = page.editable && started;

  // Auto-save: every 30 seconds, any changes are saved as a draft (until the review is sent).
  const [savedAt, setSavedAt] = useState<number | null>(page.review && !page.submittedAt ? Date.now() : null);
  const [autoFailed, setAutoFailed] = useState(false);
  const edits = useRef(0);
  const latest = useRef(values);
  latest.current = values;
  const saving = useRef(false);
  saving.current = save.isPending;

  const set = <K extends keyof ReviewInput>(key: K, value: ReviewInput[K]) => {
    setValues((v) => ({ ...v, [key]: value }));
    setDirty(true);
    edits.current += 1;
  };

  useEffect(() => {
    if (!canEdit || page.submittedAt) return;
    const timer = window.setInterval(() => {
      if (saving.current || edits.current === 0) return;
      const version = edits.current;
      save.mutate(
        { ...latest.current, submit: false },
        {
          onSuccess: () => {
            if (edits.current === version) {
              edits.current = 0;
              setDirty(false);
            }
            setSavedAt(Date.now());
            setAutoFailed(false);
          },
          onError: () => setAutoFailed(true),
        },
      );
    }, 30_000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canEdit, page.submittedAt]);
  const setScore = (list: 'skills' | 'measures', name: string, patch: Partial<ReviewScore>) =>
    set(list, values[list].map((s) => (s.name === name ? { ...s, ...patch } : s)));

  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const total = values.skills.length + values.measures.length;
  const scored = [...values.skills, ...values.measures].filter((s) => s.score !== null).length;
  const overall = useMemo(() => {
    const s = average(values.skills);
    const m = average(values.measures);
    if (s === null && m === null) return null;
    if (s === null) return m;
    if (m === null) return s;
    return 0.6 * s + 0.4 * m;
  }, [values.skills, values.measures]);

  const missing: string[] = [];
  if (scored < total) missing.push(`${total - scored} score${total - scored === 1 ? '' : 's'}`);
  if ((values.strengths ?? '').trim().length < 10) missing.push('strengths');
  if ((values.concerns ?? '').trim().length < 10) missing.push('concerns');
  if ((values.summary ?? '').trim().length < 30) missing.push('summary');
  if (!values.recommendation) missing.push('recommendation');

  const send = (submit: boolean) => {
    if (save.isPending) return;
    save.mutate(
      { ...values, submit },
      {
        onSuccess: () => {
          setDirty(false);
          edits.current = 0;
          setSavedAt(Date.now());
          setAutoFailed(false);
          notifySuccess(submit ? 'Thank you. Your review has been sent to HR.' : 'Draft saved. Only you can see it until you send it.');
          if (submit) window.scrollTo({ top: 0, behavior: 'smooth' });
        },
        onError: (e) => notifyError(e, submit ? "Your review wasn't sent" : "Your draft wasn't saved"),
      },
    );
  };

  return (
    <Stack gap="lg" pb={96}>
      <Card padding="lg">
        <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
          <Stack gap={4} style={{ minWidth: 0 }}>
            <Text size="sm" c="dimmed">
              {page.companyName} · {page.jobTitle}
            </Text>
            <Title order={1} className="page-title" fz={{ base: 24, sm: 30 }}>
              {page.candidateName}
            </Title>
            <Group gap="xs">
              <Badge variant="light" color="blue">
                {roundLabel(page.roundName, page.round)}
              </Badge>
              <Text size="sm">
                {dateTimeIn(page.startAt, zone)} · {formatDuration(page.durationMin)}
              </Text>
            </Group>
            <Text size="sm" c="dimmed">
              {page.mode === 'IN_PERSON' ? <IconMapPin size={14} style={{ verticalAlign: -2 }} /> : <IconVideo size={14} style={{ verticalAlign: -2 }} />}{' '}
              {page.mode === 'IN_PERSON' ? page.venue ?? 'In person' : 'Online'}
              {page.meetingLink && (
                <>
                  {' · '}
                  <Anchor href={page.meetingLink} target="_blank" rel="noreferrer" size="sm">
                    Join the meeting <IconExternalLink size={12} />
                  </Anchor>
                </>
              )}
            </Text>
          </Stack>
          <Group gap="xs">
            {page.cvAvailable && (
              <Button component="a" href="/api/panel/review/cv" target="_blank" rel="noreferrer" variant="default" leftSection={<IconFileCv size={16} />}>
                View CV
              </Button>
            )}
            {page.jobDescription && (
              <Button variant="default" leftSection={<IconFileDescription size={16} />} onClick={jd.toggle}>
                {jdOpen ? 'Hide job description' : 'Job description'}
              </Button>
            )}
          </Group>
        </Group>
        <Collapse expanded={jdOpen}>
          <Paper withBorder p="md" mt="md" radius="md" bg="gray.0">
            <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
              {page.jobDescription}
            </Text>
          </Paper>
        </Collapse>
        <Divider my="md" />
        <Group justify="space-between" wrap="wrap" gap="xs">
          <Text size="sm">
            Reviewing as <b>{page.memberName}</b> ({page.employeeId})
          </Text>
          <Button
            variant="subtle"
            size="xs"
            color="gray"
            onClick={() => exit.mutate()}
            loading={exit.isPending}
          >
            Not you, or another interview? Enter a different code
          </Button>
        </Group>
      </Card>

      {page.submittedAt && (
        <Alert color="ett" variant="light" icon={<IconCircleCheck size={18} />} title="Review sent">
          <Text size="sm">
            Sent {istLong(page.submittedAt)}.{' '}
            {page.editable ? `You can still change it until ${istLong(page.editableUntil)}.` : ''}
          </Text>
        </Alert>
      )}
      {!page.editable && (
        <Alert color="gray" variant="light" icon={<IconLock size={18} />}>
          <Text size="sm">{page.closedReason ?? 'This review is closed.'}</Text>
        </Alert>
      )}
      {page.editable && !started && (
        <Alert color="blue" variant="light" icon={<IconClock size={18} />}>
          <Text size="sm">The form opens when the interview starts, at {dateTimeIn(page.startAt, zone)}. You can read the CV and job description now.</Text>
        </Alert>
      )}

      <ScoreCard
        title="Skills for this job"
        help="Score each skill on what you saw in the interview, from 1 (weak) to 5 (outstanding)."
        scores={values.skills}
        mustHave={new Set(page.skills.filter((s) => s.mustHave).map((s) => s.name))}
        disabled={!canEdit}
        onChange={(name, patch) => setScore('skills', name, patch)}
      />
      <ScoreCard
        title="General measures"
        help="How the candidate came across overall."
        scores={values.measures}
        disabled={!canEdit}
        onChange={(name, patch) => setScore('measures', name, patch)}
      />

      <Card padding="lg">
        <Title order={2} fz="lg" className="section-title" mb="md">
          Your notes
        </Title>
        <Stack>
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <Textarea
              label="Strengths"
              placeholder="What did they do well?"
              autosize
              minRows={3}
              maxLength={3000}
              value={values.strengths ?? ''}
              onChange={(e) => set('strengths', e.currentTarget.value)}
              disabled={!canEdit}
              required
            />
            <Textarea
              label="Concerns or areas to improve"
              placeholder="Anything that worried you?"
              autosize
              minRows={3}
              maxLength={3000}
              value={values.concerns ?? ''}
              onChange={(e) => set('concerns', e.currentTarget.value)}
              disabled={!canEdit}
              required
            />
          </SimpleGrid>
          <Textarea
            label="Overall summary"
            description="A few sentences HR can read on their own."
            autosize
            minRows={3}
            maxLength={4000}
            value={values.summary ?? ''}
            onChange={(e) => set('summary', e.currentTarget.value)}
            disabled={!canEdit}
            required
          />
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <Textarea
              label="Questions you asked (optional)"
              autosize
              minRows={2}
              maxLength={3000}
              value={values.questions ?? ''}
              onChange={(e) => set('questions', e.currentTarget.value)}
              disabled={!canEdit}
            />
            <Textarea
              label="Notes for the next round (optional)"
              placeholder="What should the next interviewer dig into?"
              autosize
              minRows={2}
              maxLength={3000}
              value={values.nextRoundNotes ?? ''}
              onChange={(e) => set('nextRoundNotes', e.currentTarget.value)}
              disabled={!canEdit}
            />
          </SimpleGrid>
        </Stack>
      </Card>

      <Card padding="lg">
        <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
          <div>
            <Title order={2} fz="lg" className="section-title">
              Your recommendation
            </Title>
            <Text size="sm" c="dimmed">
              HR sees each panel member's recommendation and the panel's combined view.
            </Text>
          </div>
          <Box ta="right">
            <Text size="xs" c="dimmed">
              Overall score
            </Text>
            <Text fw={700} fz={26} c="navy.9" lh={1.1}>
              {overall === null ? '—' : overall.toFixed(1)}
              <Text span size="sm" c="dimmed" fw={400}>
                {' '}
                / 5
              </Text>
            </Text>
            <Text size="xs" c="dimmed">
              Skills 60%, measures 40%
            </Text>
          </Box>
        </Group>
        <SegmentedControl
          mt="md"
          fullWidth
          size="md"
          value={values.recommendation ?? ''}
          onChange={(v) => set('recommendation', v as PanelRecommendation)}
          disabled={!canEdit}
          color={values.recommendation ? panelRecommendation[values.recommendation].color : undefined}
          data={RECOMMENDATIONS.map((r) => ({ value: r, label: panelRecommendation[r].label }))}
        />
      </Card>

      {canEdit && (
        <Paper className="panel-footer" shadow="md" p="sm">
          <Container size="md" px={0}>
            <Group justify="space-between" wrap="nowrap" gap="sm">
              <Box style={{ minWidth: 0, flex: 1 }}>
                <Group gap={8} wrap="nowrap">
                  <Progress value={total ? (100 * scored) / total : 100} w={120} size="sm" visibleFrom="xs" />
                  <Text size="sm" truncate>
                    {missing.length === 0 ? (
                      <Text span c="ett.7" fw={600} size="sm">
                        <IconCheck size={14} style={{ verticalAlign: -2 }} /> Ready to send
                      </Text>
                    ) : (
                      `Still to do: ${missing.join(', ')}`
                    )}
                  </Text>
                </Group>
              </Box>
              <Group gap="xs" wrap="nowrap">
                {!page.submittedAt && (savedAt || autoFailed) && (
                  <Text size="xs" c={autoFailed ? 'red.7' : 'dimmed'} visibleFrom="xs" style={{ whiteSpace: 'nowrap' }}>
                    {autoFailed
                      ? "Couldn't save automatically"
                      : `Saved at ${new Date(savedAt!).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`}
                  </Text>
                )}
                <Button variant="default" onClick={() => send(false)} loading={save.isPending && !save.variables?.submit} disabled={!dirty && !!page.review}>
                  Save draft
                </Button>
                <Tooltip label={missing.length ? `Still to do: ${missing.join(', ')}` : 'Send your review to HR'}>
                  <Button onClick={() => send(true)} loading={save.isPending && !!save.variables?.submit} disabled={missing.length > 0}>
                    {page.submittedAt ? 'Send changes' : 'Send review'}
                  </Button>
                </Tooltip>
              </Group>
            </Group>
          </Container>
        </Paper>
      )}
    </Stack>
  );
}

function ScoreCard({ title, help, scores, mustHave, disabled, onChange }: {
  title: string;
  help: string;
  scores: ReviewScore[];
  mustHave?: Set<string>;
  disabled: boolean;
  onChange: (name: string, patch: Partial<ReviewScore>) => void;
}) {
  if (scores.length === 0) return null;
  return (
    <Card padding="lg">
      <Title order={2} fz="lg" className="section-title">
        {title}
      </Title>
      <Text size="sm" c="dimmed" mb="md">
        {help}
      </Text>
      <Stack gap={0}>
        {scores.map((s, i) => (
          <ScoreRow
            key={s.name}
            score={s}
            first={i === 0}
            mustHave={!!mustHave?.has(s.name)}
            disabled={disabled}
            onChange={(patch) => onChange(s.name, patch)}
          />
        ))}
      </Stack>
    </Card>
  );
}

function ScoreRow({ score: s, first, mustHave, disabled, onChange }: {
  score: ReviewScore;
  first: boolean;
  mustHave: boolean;
  disabled: boolean;
  onChange: (patch: Partial<ReviewScore>) => void;
}) {
  const [commenting, setCommenting] = useState(!!s.comment);
  return (
    <Box>
      {!first && <Divider my="sm" />}
      <Group justify="space-between" align="center" wrap="wrap" gap="sm">
        <Group gap={6} style={{ minWidth: 180, flex: 1 }}>
          <Text fw={500} size="sm">
            {s.name}
          </Text>
          {mustHave && (
            <Badge size="xs" variant="light" color="ett">
              Must-have
            </Badge>
          )}
          {!commenting && !disabled && (
            <Anchor component="button" type="button" size="xs" c="dimmed" onClick={() => setCommenting(true)}>
              + Comment
            </Anchor>
          )}
        </Group>
        <ScoreScale value={s.score} onChange={(score) => onChange({ score })} disabled={disabled} />
      </Group>
      {commenting && (
        <TextInput
          mt={6}
          size="xs"
          variant="filled"
          placeholder={`Comment on ${s.name} (optional)`}
          maxLength={500}
          value={s.comment ?? ''}
          onChange={(e) => onChange({ comment: e.currentTarget.value || null })}
          disabled={disabled}
          autoFocus={!s.comment}
        />
      )}
    </Box>
  );
}

/** Five buttons, 1 to 5. Click the chosen one again to clear it. */
function ScoreScale({ value, onChange, disabled }: { value: number | null; onChange: (v: number | null) => void; disabled: boolean }) {
  return (
    <Group gap={4} wrap="nowrap" role="radiogroup">
      {SCALE.map((s) => (
        <Tooltip key={s.value} label={`${s.value} · ${s.label}`}>
          <UnstyledButton
            role="radio"
            aria-checked={value === s.value}
            aria-label={`${s.value}, ${s.label}`}
            className="score-button"
            data-selected={value === s.value || undefined}
            data-level={s.value}
            disabled={disabled}
            onClick={() => onChange(value === s.value ? null : s.value)}
          >
            {s.value}
          </UnstyledButton>
        </Tooltip>
      ))}
      <Text size="xs" c="dimmed" w={96} ml={4} visibleFrom="xs">
        {value ? SCALE[value - 1].label : 'Not scored'}
      </Text>
    </Group>
  );
}
