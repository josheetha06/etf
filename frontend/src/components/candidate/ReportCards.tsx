import {
  Accordion,
  Alert,
  Badge,
  Button,
  Card,
  Group,
  List,
  Progress,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Textarea,
  ThemeIcon,
  Title,
  Tooltip,
} from '@mantine/core';
import {
  IconAlertTriangle,
  IconCheck,
  IconCircleCheck,
  IconCircleX,
  IconFlag,
  IconHome,
  IconId,
  IconMapPin,
  IconQuestionMark,
  IconStar,
  IconStarFilled,
} from '@tabler/icons-react';
import { useState } from 'react';
import { useReviewFlag } from '../../api/hooks';
import type {
  ApplicationDetail,
  Evidence,
  Flag,
  ReportType,
  PanelReport,
  ReviewScore,
  Round1Report,
  Round2Report,
  RoundDetail,
} from '../../api/types';
import { ist, years } from '../../lib/format';
import {
  criterionKind,
  panelRecommendation,
  recommendation as recommendationTone,
  reportType,
  roundKind,
  roundLabel,
  shortlistDecision,
  strength,
  suggestion,
} from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';
import { SampleBadge, ScoreRing, ToneBadge } from '../Badges';

function ReportHeader({ type, round, sample, right }: {
  type: ReportType;
  round?: RoundDetail;
  sample?: boolean;
  right?: React.ReactNode;
}) {
  const meta = reportType[type];
  return (
    <Group justify="space-between" align="flex-start" mb="sm" wrap="wrap" gap="xs">
      <Stack gap={2}>
        <Group gap="xs">
          <Title order={4} className="section-title">
            {round ? roundLabel(round.name, round.round) : meta.title}
          </Title>
          {round && (
            <Badge size="sm" variant="light" color={roundKind[round.kind].color}>
              {roundKind[round.kind].label}
            </Badge>
          )}
          <SampleBadge show={!!sample} />
        </Group>
        <Text size="xs" c="dimmed">
          {round ? `${meta.title} · ${meta.by}` : `By ${meta.by}`}
        </Text>
      </Stack>
      {right}
    </Group>
  );
}

/** "Reviewed by Charlotte · agreed" etc., so both places that show a report agree on its review status. */
export function ReviewLine({ detail, type, round }: { detail: ApplicationDetail; type: ReportType; round?: number }) {
  const key = round ? `round${round}` : type;
  const decision = round ? detail.rounds.find((r) => r.round === round)?.decision : null;
  const review = detail.reviews.find((r) => r.reportType === key);
  if (round && !decision && !review && detail.nextStep.round === round && detail.nextStep.action === 'waiting') {
    const kind = detail.rounds.find((r) => r.round === round)?.kind;
    return (
      <Badge color="gray" variant="light">
        {kind === 'PANEL' ? 'Waiting for reviews' : 'Waiting for report'}
      </Badge>
    );
  }
  if (decision && !review) {
    return (
      <Stack gap={0} align="flex-end">
        <Badge color={decision.decision === 'REJECT' ? 'gray' : decision.decision === 'HOLD' ? 'yellow' : 'ett'} variant="light">
          {decision.decision === 'ADVANCE' ? 'Moved on' : decision.decision === 'HOLD' ? 'On hold' : 'Not progressing'}
        </Badge>
        <Text size="xs" c="dimmed" ta="right" maw={260}>
          {decision.decidedByName ?? 'HR'}
        </Text>
      </Stack>
    );
  }
  if (type === 'shortlisting') {
    const last = detail.shortlistHistory[0];
    if (!last) return <Badge color="blue" variant="light">To review</Badge>;
    return (
      <Stack gap={2} align="flex-end">
        <ToneBadge tone={shortlistDecision[last.decision]} size="sm" />
        <Text size="xs" c="dimmed" ta="right" maw={260}>
          {last.decidedByName ?? 'HR'}
          {last.reason ? ` · “${last.reason}”` : ''}
        </Text>
      </Stack>
    );
  }
  if (!review) return <Badge color="blue" variant="light">To review</Badge>;
  const label = review.outcome === 'agree' ? 'Agreed' : review.outcome === 'override' ? 'Overrode' : 'Re-check asked';
  return (
    <Stack gap={0} align="flex-end">
      <Badge color={review.outcome === 'override' ? 'orange' : review.outcome === 'recheck' ? 'grape' : 'ett'} variant="light">
        {label}
      </Badge>
      <Text size="xs" c="dimmed" ta="right" maw={260}>
        {review.reviewedByName ?? 'HR'}
        {review.note ? ` · “${review.note}”` : ''}
      </Text>
    </Stack>
  );
}

// ---------------------------------------------------------------- Report 1

export function ShortlistReport({ detail }: { detail: ApplicationDetail }) {
  const s = detail.screening;
  if (!s) {
    return (
      <Card>
        <ReportHeader type="shortlisting" />
        <Text size="sm" c="dimmed">
          The CV hasn't been scored yet. It's usually ready within a few minutes of upload.
        </Text>
      </Card>
    );
  }
  return (
    <Card>
      <ReportHeader type="shortlisting" right={<ReviewLine detail={detail} type="shortlisting" />} />
      <Group align="flex-start" gap="lg" wrap="nowrap">
        <ScoreRing score={s.matchScore} size={84} />
        <Stack gap={6} style={{ flex: 1 }}>
          <Group gap="xs">
            <ToneBadge tone={suggestion[s.suggestion]} />
            <Text size="xs" c="dimmed">
              Shortlist from {s.shortlistCutoff ?? 70} · reserve from {s.reserveCutoff ?? 50}
            </Text>
          </Group>
          {s.reason && (
            <Text size="sm" fw={500}>
              {s.reason}
            </Text>
          )}
          {s.summary && (
            <Text size="sm" c="dimmed">
              {s.summary}
            </Text>
          )}
        </Stack>
      </Group>
      {s.readingQuality === 'check' && (
        <Alert color="orange" mt="md" icon={<IconAlertTriangle size={16} />}>
          Parts of this CV were hard to read (for example a scanned page or an unusual layout). Check the CV itself before deciding.
        </Alert>
      )}
      <SimpleGrid cols={{ base: 2, sm: 4 }} mt="md" spacing="sm">
        <Points label="Must-haves" value={s.mustHavePts} max={50} />
        <Points label="Nice-to-haves" value={s.niceToHavePts} max={20} />
        <Points label="Experience" value={s.experiencePts} max={20} hint={`${years(s.relevantYears)} relevant of ${years(s.totalYears)}`} />
        <Points label="Certifications" value={s.certificationPts} max={10} />
      </SimpleGrid>
      <Text size="xs" c="dimmed" mt={6}>
        Points are out of each section's share; sections a job doesn't use are left out and the rest scaled to 100.
      </Text>
      {s.flags.length > 0 && <FlagList flags={s.flags} />}
      <EvidenceTable evidence={s.evidence} />
      <Text size="xs" c="dimmed" mt="sm">
        Scored {ist(s.screenedAt)} · {s.modelVersion}
        {s.criteriaVersion ? ` · criteria version ${s.criteriaVersion}` : ''}
      </Text>
    </Card>
  );
}

function Points({ label, value, max, hint }: { label: string; value: number; max: number; hint?: string }) {
  return (
    <Stack gap={4}>
      <Text className="stat-label">{label}</Text>
      <Text fw={600} className="tabular">
        {value}
      </Text>
      <Progress value={max ? Math.min(100, (value / max) * 100) : 0} size="sm" color="ett" />
      {hint && (
        <Text size="xs" c="dimmed">
          {hint}
        </Text>
      )}
    </Stack>
  );
}

function EvidenceTable({ evidence }: { evidence: Evidence[] }) {
  if (evidence.length === 0) return null;
  return (
    <Table mt="md" verticalSpacing={8} highlightOnHover={false}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Criterion</Table.Th>
          <Table.Th>Evidence</Table.Th>
          <Table.Th ta="right">Points</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {evidence.map((item) => (
          <Table.Tr key={item.id}>
            <Table.Td valign="top" w="34%">
              <Text size="sm" fw={500}>
                {item.value}
              </Text>
              <Text size="xs" c="dimmed">
                {criterionKind[item.kind]}
                {item.minYears ? ` · ${item.minYears}+ yrs` : ''}
              </Text>
            </Table.Td>
            <Table.Td valign="top">
              <Stack gap={4}>
                <Group gap={6}>
                  {item.strength && (
                    <ToneBadge
                      tone={item.kind === 'CERTIFICATION' && item.matched ? { label: 'Found', color: 'ett' } : strength[item.strength]}
                      size="sm"
                    />
                  )}
                  {item.cvSection && (
                    <Text size="xs" c="dimmed">
                      in {item.cvSection}
                    </Text>
                  )}
                  {item.matchedTerm && item.matchedTerm.toLowerCase() !== item.value.toLowerCase() && (
                    <Tooltip label="Counted through another name or a related term">
                      <Text size="xs" c="dimmed">
                        via “{item.matchedTerm}”
                      </Text>
                    </Tooltip>
                  )}
                </Group>
                {item.quote && <Text className="quote">{item.quote}</Text>}
                {item.flagged && (
                  <Text size="xs" c="orange.8">
                    <IconFlag size={11} style={{ verticalAlign: -1 }} /> Reported as a wrong match
                    {item.flaggedByName ? ` by ${item.flaggedByName}` : ''}
                    {item.flagNote ? `: ${item.flagNote}` : ''}
                  </Text>
                )}
              </Stack>
            </Table.Td>
            <Table.Td valign="top" ta="right" className="tabular">
              {item.points === null ? '—' : Math.round(item.points * 10) / 10}
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}

export function FlagList({ flags, compact = false }: { flags: Flag[]; compact?: boolean }) {
  return (
    <Stack gap="xs" mt={compact ? 0 : 'md'}>
      {!compact && (
        <Text size="sm" fw={600}>
          Things to check in this CV
        </Text>
      )}
      {flags.map((flag) => (
        <FlagRow key={flag.id} flag={flag} />
      ))}
    </Stack>
  );
}

function FlagRow({ flag }: { flag: Flag }) {
  const review = useReviewFlag();
  const [note, setNote] = useState('');
  const [open, setOpen] = useState(false);
  const red = flag.severity === 'red';
  const save = (outcome: 'fine' | 'concern') =>
    review.mutate(
      { flagId: flag.id, outcome, note },
      { onSuccess: () => notifySuccess('Flag reviewed.'), onError: (e) => notifyError(e) },
    );
  return (
    <Card padding="sm" radius="md" bg={red && !flag.reviewOutcome ? 'red.0' : undefined}>
      <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
        <Group gap="sm" align="flex-start" wrap="nowrap" style={{ flex: 1, minWidth: 0 }}>
          <ThemeIcon size="sm" radius="xl" color={red ? 'red' : 'yellow'} variant="light" mt={2} style={{ flexShrink: 0 }}>
            {red ? <IconAlertTriangle size={12} /> : <IconQuestionMark size={12} />}
          </ThemeIcon>
          <Stack gap={2}>
            <Text size="sm">
              <Text span fw={600} c={red ? 'red.8' : 'yellow.9'}>
                {red ? 'Red flag' : 'Check'}
              </Text>{' '}
              · {flag.message}
            </Text>
            {flag.quote && <Text className="quote quote-warning">{flag.quote}</Text>}
            {flag.reviewOutcome && (
              <Text size="xs" c="dimmed">
                {flag.reviewOutcome === 'fine' ? 'Checked, fine' : 'Concern'} · {flag.reviewedByName}
                {flag.reviewNote ? ` · “${flag.reviewNote}”` : ''}
              </Text>
            )}
          </Stack>
        </Group>
        {!flag.reviewOutcome && !open && (
          <Button size="compact-sm" variant="light" color={red ? 'red' : 'gray'} onClick={() => setOpen(true)} style={{ flexShrink: 0 }}>
            Review
          </Button>
        )}
      </Group>
      {!flag.reviewOutcome && open && (
        <Stack gap="xs" mt="sm" ml={34}>
          <Textarea
            size="sm"
            placeholder="What did you find? (optional)"
            autosize
            minRows={1}
            value={note}
            onChange={(e) => setNote(e.currentTarget.value)}
          />
          <Group gap="xs">
            <Button size="compact-sm" leftSection={<IconCheck size={14} />} onClick={() => save('fine')} loading={review.isPending}>
              Checked, fine
            </Button>
            <Button size="compact-sm" color="red" variant="light" onClick={() => save('concern')} loading={review.isPending}>
              Concern
            </Button>
            <Button size="compact-sm" variant="subtle" color="gray" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- Report 2

const CHECK_TONE = {
  verified: { color: 'ett', label: 'Verified', icon: IconCircleCheck },
  clear: { color: 'ett', label: 'Clear', icon: IconCircleCheck },
  mismatch: { color: 'red', label: 'Mismatch', icon: IconCircleX },
  concern: { color: 'orange', label: 'Concern', icon: IconAlertTriangle },
  not_done: { color: 'gray', label: 'Not done', icon: IconQuestionMark },
} as const;

function StatusLine({ icon: Icon, label, status, note }: {
  icon: typeof IconId;
  label: string;
  status: keyof typeof CHECK_TONE | null;
  note: string | null;
}) {
  const tone = CHECK_TONE[status ?? 'not_done'];
  return (
    <Group align="flex-start" wrap="nowrap" gap="sm">
      <ThemeIcon variant="light" color="gray" size="md" radius="md">
        <Icon size={16} />
      </ThemeIcon>
      <Stack gap={2} style={{ flex: 1 }}>
        <Group gap="xs">
          <Text size="sm" fw={600}>
            {label}
          </Text>
          <Badge color={tone.color} leftSection={<tone.icon size={12} />}>
            {tone.label}
          </Badge>
        </Group>
        {note && (
          <Text size="sm" c="dimmed">
            {note}
          </Text>
        )}
      </Stack>
    </Group>
  );
}

export function Round1ReportCard({ report, detail, round }: {
  report: Round1Report | null;
  detail: ApplicationDetail;
  round: RoundDetail;
}) {
  if (!report) {
    return (
      <Card>
        <ReportHeader type="ai_interview" round={round} />
        <Text size="sm" c="dimmed">
          Arrives after the AI interview, once an ETT reviewer has checked it.
        </Text>
      </Card>
    );
  }
  const inReview = report.status !== 'released';
  return (
    <Card>
      <ReportHeader
        type="ai_interview"
        round={round}
        sample={report.sample}
        right={<ReviewLine detail={detail} type="ai_interview" round={round.round} />}
      />
      {inReview && (
        <Alert color="grape" mb="md" title="ETT is re-checking this report">
          You asked on {ist(report.recheckRequestedAt)}: “{report.recheckNote}”. You'll get a notification when it's back.
        </Alert>
      )}
      {!inReview && report.recheckRequestedAt && (
        <Alert color="grape" variant="light" mb="md">
          Re-checked after your request (“{report.recheckNote}”). See ETT's note below.
        </Alert>
      )}
      <Group align="flex-start" gap="lg" wrap="nowrap" mb="md">
        <ScoreRing score={report.finalOverall} size={84} />
        <Stack gap={6} style={{ flex: 1 }}>
          <Group gap="xs">
            {report.recommendation && <ToneBadge tone={recommendationTone[report.recommendation]} />}
            <Text size="xs" c="dimmed">
              AI draft {report.aiOverall ?? '—'} · after ETT review {report.finalOverall ?? '—'}
            </Text>
          </Group>
          {report.taskSummary && <Text size="sm">{report.taskSummary}</Text>}
          <Text size="xs" c="dimmed">
            Interview {ist(report.interviewAt)} · released {ist(report.releasedAt)}
          </Text>
        </Stack>
      </Group>
      <Stack gap="sm">
        <StatusLine icon={IconId} label="Identity (Aadhaar and PAN)" status={report.identityCheck} note={report.identityNote} />
        <StatusLine icon={IconHome} label="Surroundings" status={report.surroundingsCheck} note={report.surroundingsNote} />
        {report.candidateLocation && (
          <Group align="flex-start" wrap="nowrap" gap="sm">
            <ThemeIcon variant="light" color="gray" size="md" radius="md">
              <IconMapPin size={16} />
            </ThemeIcon>
            <Stack gap={2} style={{ flex: 1 }}>
              <Text size="sm" fw={600}>
                Where the candidate is
              </Text>
              <Text size="sm" c="dimmed">
                {report.candidateLocation}
                {report.candidatePin ? ` ${report.candidatePin}` : ''}, as the candidate told the AI interviewer.
              </Text>
            </Stack>
          </Group>
        )}
        {(report.candidateNoticeDays !== null || report.candidateExpectedCtcLpa !== null) && (
          <Group align="flex-start" wrap="nowrap" gap="sm">
            <ThemeIcon variant="light" color="gray" size="md" radius="md">
              <IconId size={16} />
            </ThemeIcon>
            <Stack gap={2} style={{ flex: 1 }}>
              <Text size="sm" fw={600}>
                Asked because the CV left it out
              </Text>
              <Text size="sm" c="dimmed">
                {[
                  report.candidateNoticeDays !== null ? `Notice period: ${report.candidateNoticeDays} days` : null,
                  report.candidateExpectedCtcLpa !== null ? `Expected CTC: ${report.candidateExpectedCtcLpa} lakh a year` : null,
                ]
                  .filter(Boolean)
                  .join(' · ')}
                . Added to the key facts, labelled "From AI interview".
              </Text>
            </Stack>
          </Group>
        )}
      </Stack>
      {report.highlights.length > 0 && (
        <>
          <Text size="sm" fw={600} mt="md" mb={4}>
            Highlights
          </Text>
          <List size="sm" spacing={4}>
            {report.highlights.map((h) => (
              <List.Item key={h}>{h}</List.Item>
            ))}
          </List>
        </>
      )}
      {report.competencies.length > 0 && (
        <Table mt="md" verticalSpacing={6}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Area</Table.Th>
              <Table.Th ta="center">AI draft</Table.Th>
              <Table.Th ta="center">Final</Table.Th>
              <Table.Th>Reviewer comment</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {report.competencies.map((c) => (
              <Table.Tr key={c.competency}>
                <Table.Td>{c.competency}</Table.Td>
                <Table.Td ta="center" className="tabular">
                  {c.aiDraftScore ?? '—'}/5
                </Table.Td>
                <Table.Td ta="center" className="tabular" fw={600}>
                  {c.finalScore ?? '—'}/5
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed">
                    {c.reviewerComment}
                  </Text>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
      {report.reviewerNotes && (
        <Text size="sm" c="dimmed" mt="md" style={{ whiteSpace: 'pre-line' }}>
          <b>ETT reviewer:</b> {report.reviewerNotes}
        </Text>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- Report 3

export function Round2ReportCard({ report, detail, round }: {
  report: Round2Report | null;
  detail: ApplicationDetail;
  round: RoundDetail;
}) {
  if (!report) {
    return (
      <Card>
        <ReportHeader type="partner" round={round} />
        <Text size="sm" c="dimmed">
          Arrives when the partner assessment centre's assessor submits it after the in-person interview.
        </Text>
      </Card>
    );
  }
  return (
    <Card>
      <ReportHeader
        type="partner"
        round={round}
        sample={report.sample}
        right={<ReviewLine detail={detail} type="partner" round={round.round} />}
      />
      <Group align="flex-start" gap="lg" wrap="nowrap" mb="md">
        <ScoreRing score={report.score} size={84} />
        <Stack gap={6}>
          {report.recommendation && <ToneBadge tone={recommendationTone[report.recommendation]} />}
          <Text size="sm">
            {report.assessorName ?? 'Assessor'} at {report.centreName ?? 'the partner assessment centre'}
          </Text>
          <Text size="xs" c="dimmed">
            Interview {ist(report.interviewAt)} · report sent {ist(report.submittedAt)}
          </Text>
        </Stack>
      </Group>
      <StatusLine
        icon={IconId}
        label="Aadhaar and PAN checked in person"
        status={report.idChecked === null ? 'not_done' : report.idChecked ? 'verified' : 'mismatch'}
        note={report.idCheckNote}
      />
      <Stack gap={8} mt="md">
        {Object.entries(report.areaScores).map(([area, score]) => (
          <Group key={area} gap="sm" wrap="nowrap">
            <Text size="sm" w={150}>
              {area}
            </Text>
            <Progress value={(score / 5) * 100} style={{ flex: 1 }} color={score >= 4 ? 'ett' : score === 3 ? 'yellow' : 'red'} />
            <Text size="sm" fw={600} w={34} ta="right" className="tabular">
              {score}/5
            </Text>
          </Group>
        ))}
      </Stack>
      {report.notes && (
        <Text size="sm" mt="md" style={{ whiteSpace: 'pre-line' }}>
          {report.notes}
        </Text>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- panel reviews

function ScoreBar({ label, value, must, scored, total }: {
  label: string;
  value: number | null;
  must?: boolean;
  scored?: number;
  total?: number;
}) {
  return (
    <Group gap="sm" wrap="nowrap">
      <Text size="sm" w={170} lineClamp={1} title={label}>
        {label}
        {must && (
          <Text span size="xs" c="dimmed">
            {' '}
            · must-have
          </Text>
        )}
      </Text>
      <Progress
        value={value ? (value / 5) * 100 : 0}
        style={{ flex: 1 }}
        color={value === null ? 'gray' : value >= 4 ? 'ett' : value >= 3 ? 'yellow' : 'red'}
        aria-label={`${label}: ${value ?? 'not scored'} out of 5`}
      />
      <Text size="sm" fw={600} w={44} ta="right" className="tabular">
        {value === null ? '—' : `${value}/5`}
      </Text>
      {total !== undefined && total > 1 && (
        <Text size="xs" c="dimmed" w={54} visibleFrom="sm">
          {scored} of {total}
        </Text>
      )}
    </Group>
  );
}

function ScoreList({ scores }: { scores: ReviewScore[] }) {
  return (
    <Stack gap={6}>
      {scores.map((s) => (
        <Stack key={s.name} gap={0}>
          <ScoreBar label={s.name} value={s.score} />
          {s.comment && (
            <Text size="xs" c="dimmed" pl={4}>
              {s.comment}
            </Text>
          )}
        </Stack>
      ))}
    </Stack>
  );
}

/** The panel's reviews for one round: averages for each skill and measure, then every reviewer's detailed review. */
export function PanelReportCard({ report, detail, round }: {
  report: PanelReport | null;
  detail: ApplicationDetail;
  round: RoundDetail;
}) {
  if (!report || report.members === 0) {
    return (
      <Card>
        <ReportHeader type="panel" round={round} />
        <Text size="sm" c="dimmed">
          Each panel member sends a detailed review after the interview, using the panel login, their employee ID and the
          interview code.
        </Text>
      </Card>
    );
  }
  const sent = report.reviews.filter((r) => r.submittedAt);
  return (
    <Card>
      <ReportHeader
        type="panel"
        round={round}
        sample={report.sample}
        right={<ReviewLine detail={detail} type="panel" round={round.round} />}
      />
      <Group gap="xl" mb="md" align="flex-start">
        <Stack gap={2}>
          <Text className="stat-label">Overall</Text>
          <Text fw={700} size="xl" className="tabular">
            {report.overallAverage === null ? '—' : `${report.overallAverage}/5`}
          </Text>
        </Stack>
        <Stack gap={2}>
          <Text className="stat-label">Panel says</Text>
          {report.combined ? <ToneBadge tone={recommendationTone[report.combined]} /> : <Text size="sm">—</Text>}
        </Stack>
        <Stack gap={2}>
          <Text className="stat-label">Reviews in</Text>
          <Text fw={600}>
            {report.submitted} of {report.members}
          </Text>
        </Stack>
        <Stack gap={4}>
          <Text className="stat-label">Recommendations</Text>
          <Group gap={4}>
            {Object.entries(report.recommendations)
              .filter(([, n]) => n > 0)
              .map(([key, n]) => (
                <Badge key={key} variant="light" color={panelRecommendation[key].color}>
                  {n} × {panelRecommendation[key].label}
                </Badge>
              ))}
            {sent.length === 0 && <Text size="sm">—</Text>}
          </Group>
        </Stack>
      </Group>
      {sent.length > 0 && (
        <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg" mb="md">
          <Stack gap={6}>
            <Text size="sm" fw={600}>
              Skills for this job
            </Text>
            {report.skills.map((s) => (
              <ScoreBar key={s.name} label={s.name} value={s.average} must={s.mustHave} scored={s.scored} total={report.submitted} />
            ))}
          </Stack>
          <Stack gap={6}>
            <Text size="sm" fw={600}>
              General measures
            </Text>
            {report.measures.map((m) => (
              <ScoreBar key={m.name} label={m.name} value={m.average} scored={m.scored} total={report.submitted} />
            ))}
          </Stack>
        </SimpleGrid>
      )}
      <Text size="sm" fw={600} mb={4}>
        Each reviewer
      </Text>
      <Accordion variant="separated" radius="md" multiple defaultValue={sent.length === 1 ? [sent[0].memberId] : []}>
        {report.reviews.map((r) => (
          <Accordion.Item key={r.memberId} value={r.memberId}>
            <Accordion.Control disabled={!r.submittedAt}>
              <Group justify="space-between" wrap="nowrap" pr="sm">
                <Stack gap={0}>
                  <Text size="sm" fw={600}>
                    {r.fullName}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {r.employeeId} · {r.submittedAt ? `sent ${ist(r.submittedAt)}` : 'review not sent yet'}
                  </Text>
                </Stack>
                {r.submittedAt ? (
                  <Group gap="xs" wrap="nowrap">
                    <Text size="sm" fw={600} className="tabular">
                      {r.overallScore}/5
                    </Text>
                    {r.recommendation && (
                      <Badge variant="light" color={panelRecommendation[r.recommendation].color}>
                        {panelRecommendation[r.recommendation].label}
                      </Badge>
                    )}
                  </Group>
                ) : (
                  <Badge color="gray" variant="light">
                    Waiting
                  </Badge>
                )}
              </Group>
            </Accordion.Control>
            <Accordion.Panel>
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
                <Stack gap="xs">
                  <Text size="xs" className="stat-label">
                    Skills
                  </Text>
                  <ScoreList scores={r.skills} />
                </Stack>
                <Stack gap="xs">
                  <Text size="xs" className="stat-label">
                    General measures
                  </Text>
                  <ScoreList scores={r.measures} />
                </Stack>
              </SimpleGrid>
              <Stack gap={8} mt="md">
                {(
                  [
                    ['Strengths', r.strengths],
                    ['Concerns or areas to improve', r.concerns],
                    ['Questions asked', r.questions],
                    ['Summary', r.summary],
                    ['Notes for the next round', r.nextRoundNotes],
                  ] as const
                )
                  .filter(([, text]) => text)
                  .map(([label, text]) => (
                    <div key={label}>
                      <Text size="xs" className="stat-label">
                        {label}
                      </Text>
                      <Text size="sm" style={{ whiteSpace: 'pre-line' }}>
                        {text}
                      </Text>
                    </div>
                  ))}
              </Stack>
            </Accordion.Panel>
          </Accordion.Item>
        ))}
      </Accordion>
    </Card>
  );
}

/** The right report card for a round, by its type. */
export function RoundReportCard({ detail, round }: { detail: ApplicationDetail; round: RoundDetail }) {
  if (round.kind === 'AI_INTERVIEW') return <Round1ReportCard report={round.aiReport} detail={detail} round={round} />;
  if (round.kind === 'PARTNER') return <Round2ReportCard report={round.partnerReport} detail={detail} round={round} />;
  return <PanelReportCard report={round.panelReport} detail={detail} round={round} />;
}

export function Stars({ value, size = 14 }: { value: number; size?: number }) {
  return (
    <Group gap={1} wrap="nowrap" aria-label={`${value} out of 5`}>
      {[1, 2, 3, 4, 5].map((n) =>
        n <= Math.round(value) ? (
          <IconStarFilled key={n} size={size} color="var(--mantine-color-yellow-5)" />
        ) : (
          <IconStar key={n} size={size} color="var(--mantine-color-gray-4)" />
        ),
      )}
    </Group>
  );
}
