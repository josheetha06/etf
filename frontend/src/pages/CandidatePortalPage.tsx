import {
  Alert,
  Anchor,
  Badge,
  Box,
  Button,
  Card,
  Container,
  Divider,
  Group,
  Paper,
  Select,
  Spoiler,
  Stack,
  Text,
  Textarea,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { DatePicker, TimeInput } from '@mantine/dates';
import {
  IconAlertTriangle,
  IconCalendarEvent,
  IconCheck,
  IconClock,
  IconExternalLink,
  IconId,
  IconLink,
  IconMapPin,
  IconMessage,
  IconSend,
  IconVideo,
} from '@tabler/icons-react';
import { useState } from 'react';
import { useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useCandidatePage, useCandidatePageAction, usePublicBranding } from '../api/hooks';
import type { Availability, CandidatePage, RescheduleReason } from '../api/types';
import { ClientLogo } from '../components/ClientLogo';
import { formatDuration } from '../components/common/DurationInput';
import { Loading } from '../components/QueryState';
import { dateTimeIn, dayjs, dayLabel, timeIn } from '../lib/format';
import { rescheduleReason } from '../lib/labels';
import { notifyError, notifySuccess } from '../lib/notify';

/** The candidate's own page, opened from the link in every email: their interview, a new time, and messages to HR. */
export function CandidatePortalPage() {
  const { token = '' } = useParams();
  const { data, isLoading, error } = useCandidatePage(token);
  const { data: branding } = usePublicBranding();
  return (
    <Box mih="100vh" bg="var(--brand-surface)">
      <Paper component="header" radius={0} className="app-header" px="md" h={60}>
        <Container size={640} h="100%" px={0}>
          <Group h="100%" justify="space-between" wrap="nowrap">
            {branding?.companyName ? <ClientLogo branding={branding} height={30} publicLogo /> : <Text fw={700}>{data?.companyName}</Text>}
            <Text size="sm" c="dimmed">
              Your interview
            </Text>
          </Group>
        </Container>
      </Paper>
      <Container size={640} py="lg" px="md">
        {isLoading ? (
          <Loading />
        ) : error || !data ? (
          <LinkProblem error={error} />
        ) : (
          <PageBody token={token} page={data} />
        )}
      </Container>
    </Box>
  );
}

function LinkProblem({ error }: { error: unknown }) {
  const gone = error instanceof ApiError && (error.status === 404 || error.status === 410);
  return (
    <Card padding="xl" mt="xl">
      <Stack align="center" ta="center" gap="sm">
        <ThemeIcon size={52} radius="xl" variant="light" color="orange">
          <IconLink size={26} />
        </ThemeIcon>
        <Title order={2} fz={22}>
          {gone ? 'This link has expired' : "We couldn't open your page"}
        </Title>
        <Text c="dimmed" size="sm" maw={420}>
          {gone
            ? 'Links stop working a day after the interview. If you still need something, reply to the last email you had from us.'
            : 'Please check your connection and try again in a moment.'}
        </Text>
      </Stack>
    </Card>
  );
}

function PageBody({ token, page }: { token: string; page: CandidatePage }) {
  const interview = page.interview;
  const zone = interview?.timeZone ?? page.timeZone;
  const [asking, setAsking] = useState(false);
  const action = useCandidatePageAction(token);
  const accept = () =>
    action.mutate(
      { kind: 'accept' },
      { onSuccess: () => notifySuccess('Thank you. The time is confirmed.'), onError: (e) => notifyError(e) },
    );

  return (
    <Stack gap="lg">
      <div>
        <Title order={1} className="page-title" fz={{ base: 26, sm: 30 }}>
          Hi {page.firstName}
        </Title>
        <Text c="dimmed">
          {page.jobTitle} at {page.companyName}
        </Text>
      </div>

      {!page.applicationActive && (
        <Alert color="gray" variant="light">
          <Text size="sm">Your application is no longer active. You can still send us a message below.</Text>
        </Alert>
      )}

      {interview ? (
        <Card padding="lg">
          <Stack gap={4} mb="md">
            <Group justify="space-between" gap="xs">
              <Text size="sm" c="dimmed">
                {interview.roundLabel}
              </Text>
              <StatusBadge status={interview.status} />
            </Group>
            <Title order={2} fz={24}>
              {interview.startAt ? dayjs(interview.startAt).tz(zone).format('dddd D MMMM') : 'Time to be confirmed'}
            </Title>
            {interview.startAt && (
              <Text size="md" fw={500}>
                {timeIn(interview.startAt, zone)} · {formatDuration(interview.durationMin)}
              </Text>
            )}
          </Stack>
          <Stack gap={8}>
            <Detail icon={interview.meetingLink ? <IconVideo size={16} /> : <IconMapPin size={16} />}>
              {interview.format}
            </Detail>
            {interview.centreName && (
              <Detail icon={<IconMapPin size={16} />}>
                <b>{interview.centreName}</b>
                {interview.centreAddress ? `, ${interview.centreAddress}` : ''}
              </Detail>
            )}
            {interview.venue && <Detail icon={<IconMapPin size={16} />}>{interview.venue}</Detail>}
            {interview.arriveBy && (
              <Detail icon={<IconClock size={16} />}>Please arrive by {timeIn(interview.arriveBy, zone)}.</Detail>
            )}
            {interview.kind === 'PARTNER' && (
              <Detail icon={<IconId size={16} />}>Bring your original Aadhaar and PAN cards.</Detail>
            )}
            {interview.kind === 'AI_INTERVIEW' && (
              <Detail icon={<IconId size={16} />}>Keep your Aadhaar and PAN cards with you. You'll show them on camera.</Detail>
            )}
            {interview.meetingLink && (
              <Detail icon={<IconExternalLink size={16} />}>
                <Anchor href={interview.meetingLink} target="_blank" rel="noreferrer">
                  Join link
                </Anchor>
              </Detail>
            )}
          </Stack>

          {interview.pendingRequest ? (
            <Alert color="orange" variant="light" mt="md" icon={<IconCalendarEvent size={18} />} title="You asked for a new time">
              <Text size="sm">
                Sent {dateTimeIn(interview.pendingRequest.requestedAt, zone)}. We'll reply soon with a time on one of these days:
              </Text>
              <Group gap={6} mt={6}>
                {interview.pendingRequest.availability.map((a) => (
                  <Badge key={a.date} variant="white" color="orange" style={{ textTransform: 'none' }}>
                    {dayLabel(a.date)} · {windowLabel(a)}
                  </Badge>
                ))}
              </Group>
            </Alert>
          ) : (
            (interview.canAccept || interview.canAskNewTime) && (
              <>
                <Divider my="md" />
                {interview.replyBy && interview.canAccept && (
                  <Text size="sm" mb="sm">
                    Please reply by <b>{dateTimeIn(interview.replyBy, zone)}</b>.
                  </Text>
                )}
                <Group gap="sm">
                  {interview.canAccept && (
                    <Button leftSection={<IconCheck size={16} />} onClick={accept} loading={action.isPending && action.variables?.kind === 'accept'}>
                      Accept this time
                    </Button>
                  )}
                  {interview.canAskNewTime && !asking && (
                    <Button variant="default" onClick={() => setAsking(true)}>
                      Ask for a new time
                    </Button>
                  )}
                </Group>
              </>
            )
          )}
          {asking && !interview.pendingRequest && <NewTimeForm token={token} zone={zone} onDone={() => setAsking(false)} />}
        </Card>
      ) : (
        <Card padding="lg">
          <Text size="sm" c="dimmed">
            There's no interview booked right now. We'll email you when there is.
          </Text>
        </Card>
      )}

      <Messages token={token} page={page} zone={zone} />

      <Text size="xs" c="dimmed" ta="center">
        This page is just for you. The link works until {dateTimeIn(page.linkExpiresAt, zone)}.
      </Text>
    </Stack>
  );
}

function Detail({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <Group gap={10} wrap="nowrap" align="flex-start">
      <ThemeIcon variant="light" color="gray" size={26} radius="md" style={{ flexShrink: 0 }}>
        {icon}
      </ThemeIcon>
      <Text size="sm" pt={3}>
        {children}
      </Text>
    </Group>
  );
}

function StatusBadge({ status }: { status: string }) {
  const s = status.toLowerCase();
  const tone =
    s === 'confirmed'
      ? { label: 'Confirmed', color: 'ett' }
      : s === 'invited'
        ? { label: 'Waiting for your reply', color: 'blue' }
        : s === 'reschedule_requested'
          ? { label: 'New time asked for', color: 'orange' }
          : s === 'completed'
            ? { label: 'Done', color: 'grape' }
            : s === 'cancelled'
              ? { label: 'Cancelled', color: 'gray' }
              : { label: status, color: 'gray' };
  return (
    <Badge color={tone.color} variant="light" style={{ flexShrink: 0 }}>
      {tone.label}
    </Badge>
  );
}

type Window = Availability['window'];

const WINDOWS: { value: Window; label: string }[] = [
  { value: 'any', label: 'Any time' },
  { value: 'morning', label: 'Morning' },
  { value: 'afternoon', label: 'Afternoon' },
  { value: 'range', label: 'Between set times' },
];

function windowLabel(a: Availability) {
  if (a.window === 'range' && a.from && a.to) return `${a.from}–${a.to}`;
  return WINDOWS.find((w) => w.value === a.window)?.label ?? 'Any time';
}

function NewTimeForm({ token, zone, onDone }: { token: string; zone: string; onDone: () => void }) {
  const action = useCandidatePageAction(token);
  const [reason, setReason] = useState<RescheduleReason | null>(null);
  const [days, setDays] = useState<Availability[]>([]);
  const [note, setNote] = useState('');
  const today = dayjs().tz(zone);
  const pickDates = (dates: string[]) =>
    setDays(
      dates
        .slice(0, 5)
        .sort()
        .map((date) => days.find((d) => d.date === date) ?? { date, window: 'any', from: null, to: null }),
    );
  const update = (date: string, patch: Partial<Availability>) => setDays(days.map((d) => (d.date === date ? { ...d, ...patch } : d)));
  const rangeBad = (d: Availability) =>
    d.window === 'range' && (!d.from || !d.to || dayjs(`2000-01-01T${d.to}`).diff(dayjs(`2000-01-01T${d.from}`), 'minute') < 30);
  const ready = !!reason && days.length > 0 && !days.some(rangeBad);

  const submit = () => {
    if (!ready || action.isPending) return;
    action.mutate(
      { kind: 'new-time', reason: reason!, note: note.trim(), availability: days },
      {
        onSuccess: () => {
          notifySuccess("Thanks. We've got your dates and will reply soon.");
          onDone();
        },
        onError: (e) => notifyError(e),
      },
    );
  };

  return (
    <Stack mt="md" gap="md">
      <Divider label="Ask for a new time" labelPosition="left" />
      <Select
        label="Why do you need a new time?"
        data={Object.entries(rescheduleReason).map(([value, label]) => ({ value, label }))}
        value={reason}
        onChange={(v) => setReason(v as RescheduleReason)}
        allowDeselect={false}
        placeholder="Choose one"
      />
      <div>
        <Text size="sm" fw={500}>
          Which days are you free?
        </Text>
        <Text size="xs" c="dimmed" mb="xs">
          Choose up to 5 days in the next two months, then say what time suits you on each.
        </Text>
        <Group align="flex-start" gap="lg" wrap="wrap">
          <Paper withBorder radius="md" p="xs">
            <DatePicker
              type="multiple"
              value={days.map((d) => d.date)}
              onChange={(v) => pickDates(v as string[])}
              minDate={today.add(1, 'day').format('YYYY-MM-DD')}
              maxDate={today.add(60, 'day').format('YYYY-MM-DD')}
              size="sm"
            />
          </Paper>
          <Stack gap="xs" style={{ flex: 1, minWidth: 220 }}>
            {days.length === 0 && (
              <Text size="sm" c="dimmed">
                No days chosen yet.
              </Text>
            )}
            {days.map((d) => (
              <Paper key={d.date} withBorder radius="md" p="xs">
                <Text size="sm" fw={600} mb={4}>
                  {dayLabel(d.date)}
                </Text>
                <Select
                  size="xs"
                  data={WINDOWS}
                  value={d.window}
                  onChange={(v) => update(d.date, { window: (v ?? 'any') as Window, from: v === 'range' ? d.from ?? '10:00' : null, to: v === 'range' ? d.to ?? '13:00' : null })}
                  allowDeselect={false}
                  aria-label={`Time on ${dayLabel(d.date)}`}
                />
                {d.window === 'range' && (
                  <Group gap="xs" mt={6} wrap="nowrap">
                    <TimeInput size="xs" aria-label="From" value={d.from ?? ''} onChange={(e) => update(d.date, { from: e.currentTarget.value })} />
                    <Text size="xs">to</Text>
                    <TimeInput size="xs" aria-label="To" value={d.to ?? ''} onChange={(e) => update(d.date, { to: e.currentTarget.value })} />
                  </Group>
                )}
                {rangeBad(d) && (
                  <Text size="xs" c="red" mt={4}>
                    Give a start and end at least 30 minutes apart.
                  </Text>
                )}
              </Paper>
            ))}
          </Stack>
        </Group>
      </div>
      <Textarea
        label="Anything else we should know? (optional)"
        autosize
        minRows={2}
        maxLength={1000}
        value={note}
        onChange={(e) => setNote(e.currentTarget.value)}
      />
      <Group justify="flex-end">
        <Button variant="default" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!ready} loading={action.isPending}>
          Send my dates
        </Button>
      </Group>
    </Stack>
  );
}

function Messages({ token, page, zone }: { token: string; page: CandidatePage; zone: string }) {
  const action = useCandidatePageAction(token);
  const [body, setBody] = useState('');
  const send = () => {
    if (!body.trim() || action.isPending) return;
    action.mutate(
      { kind: 'message', body: body.trim() },
      {
        onSuccess: () => {
          setBody('');
          notifySuccess('Message sent. HR will reply here and by email.');
        },
        onError: (e) => notifyError(e),
      },
    );
  };
  return (
    <Card padding="lg">
      <Group gap="xs" mb="md">
        <IconMessage size={18} />
        <Title order={2} fz="lg">
          Messages
        </Title>
      </Group>
      {page.messages.length === 0 ? (
        <Text size="sm" c="dimmed" mb="md">
          No messages yet. Ask us anything about your interview.
        </Text>
      ) : (
        <Stack gap="sm" mb="md">
          {page.messages.map((m) => (
            <Box key={m.id} className="chat-bubble" data-mine={m.fromCandidate || undefined}>
              <Text size="xs" c="dimmed" mb={2}>
                {m.fromCandidate ? 'You' : m.from} · {dateTimeIn(m.at, zone)}
              </Text>
              {m.subject && !m.fromCandidate && (
                <Text size="sm" fw={600}>
                  {m.subject}
                </Text>
              )}
              {m.body.length > 280 ? (
                <Spoiler maxHeight={88} showLabel="Show more" hideLabel="Show less" styles={{ control: { fontSize: 13 } }}>
                  <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                    {m.body}
                  </Text>
                </Spoiler>
              ) : (
                <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
                  {m.body}
                </Text>
              )}
            </Box>
          ))}
        </Stack>
      )}
      <Textarea
        aria-label="Your message"
        placeholder="Write a message to HR"
        autosize
        minRows={2}
        maxRows={8}
        maxLength={3000}
        value={body}
        onChange={(e) => setBody(e.currentTarget.value)}
      />
      <Group justify="space-between" mt="xs">
        <Text size="xs" c="dimmed">
          <IconAlertTriangle size={12} style={{ verticalAlign: -1 }} /> Please don't send ID numbers or bank details here.
        </Text>
        <Button size="sm" leftSection={<IconSend size={14} />} onClick={send} disabled={!body.trim()} loading={action.isPending && action.variables?.kind === 'message'}>
          Send
        </Button>
      </Group>
    </Card>
  );
}
