import { Alert, Badge, Box, Button, Card, Divider, Group, SimpleGrid, Stack, Text, ThemeIcon, Title, Tooltip, UnstyledButton } from '@mantine/core';
import {
  IconAlertTriangle,
  IconCalendarEvent,
  IconCalendarPlus,
  IconChecklist,
  IconCircleCheck,
  IconExternalLink,
  IconMail,
  IconMapPin,
  IconUserShare,
  IconVideo,
} from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useDashboard } from '../api/hooks';
import type { InterviewStatus, MessagePreview, ToDo, ToDoGroup, TodayInterview } from '../api/types';
import { ToneBadge } from '../components/Badges';
import { formatDuration } from '../components/common/DurationInput';
import { PageHeader } from '../components/PageHeader';
import { ErrorBlock, Loading } from '../components/QueryState';
import { dayjs, greeting, relative, todayLong } from '../lib/format';
import { interviewStatus, roundKind } from '../lib/labels';

const GROUPS: Record<ToDoGroup['key'], { title: string; empty: string; icon: typeof IconChecklist; color: string }> = {
  needs_attention: {
    title: 'Needs attention',
    empty: 'Nothing needs you right now.',
    icon: IconChecklist,
    color: 'blue',
  },
  to_book: { title: 'To book', empty: 'Nothing to book or rebook.', icon: IconCalendarPlus, color: 'violet' },
};

/** Join links light up this long before the start. */
const JOIN_EARLY_MS = 15 * 60 * 1000;

/**
 * The first page after signing in: what needs attention, who to book, the newest candidate messages, and today's
 * interviews (with a Join button for the ones you're on the panel of).
 */
export function OverviewPage() {
  const { data, isLoading, error, refetch } = useDashboard();
  if (isLoading) return <Loading />;
  if (error || !data) return <ErrorBlock error={error} onRetry={refetch} />;
  const zone = data.timeZone;
  const waiting = data.needsAttention.count + data.toBook.count;

  return (
    <>
      <PageHeader
        eyebrow={todayLong(zone)}
        title={`${greeting(zone)}, ${data.firstName}`}
        subtitle={
          waiting === 0 && data.messages.unread === 0
            ? "You're all caught up."
            : [
                waiting > 0 ? `${waiting} ${waiting === 1 ? 'thing needs' : 'things need'} you` : null,
                data.messages.unread > 0
                  ? `${data.messages.unread} unread ${data.messages.unread === 1 ? 'message' : 'messages'}`
                  : null,
              ]
                .filter(Boolean)
                .join(' · ')
        }
      />

      {data.covering.length > 0 && (
        <Alert variant="light" color="blue" icon={<IconUserShare size={18} />} mb="lg">
          <Text size="sm">
            You're covering for <b>{joinNames(data.covering)}</b>. Their jobs, candidates and interviews are included below,
            and you get their notifications.
          </Text>
        </Alert>
      )}

      <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
        <ToDoCard group={data.needsAttention} />
        <ToDoCard group={data.toBook} />
        <MessagesCard unread={data.messages.unread} latest={data.messages.latest} />
      </SimpleGrid>

      <TodayCard interviews={data.today} zone={zone} />
    </>
  );
}

function joinNames(names: string[]) {
  if (names.length <= 1) return names.join('');
  return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

const SHOWN = 3;
const DAY = 24 * 60 * 60 * 1000;

function CardHeading({ icon: Icon, color, title, count }: { icon: typeof IconChecklist; color: string; title: string; count: number }) {
  return (
    <Group justify="space-between" mb="md" wrap="nowrap">
      <Group gap="sm" wrap="nowrap">
        <ThemeIcon variant="light" color={color} size={32} radius="md">
          <Icon size={18} />
        </ThemeIcon>
        <Title order={2} fz="md" className="section-title">
          {title}
        </Title>
      </Group>
      <Badge size="lg" variant={count > 0 ? 'filled' : 'light'} color={count > 0 ? color : 'gray'} circle={count < 10}>
        {count}
      </Badge>
    </Group>
  );
}

function AllClear({ text }: { text: string }) {
  return (
    <Group gap={8} c="dimmed" py="sm">
      <IconCircleCheck size={18} color="var(--mantine-color-ett-6)" />
      <Text size="sm">{text}</Text>
    </Group>
  );
}

function ToDoCard({ group }: { group: ToDoGroup }) {
  const [all, setAll] = useState(false);
  const meta = GROUPS[group.key];
  const items = all ? group.items : group.items.slice(0, SHOWN);
  return (
    <Card padding="lg">
      <CardHeading icon={meta.icon} color={meta.color} title={meta.title} count={group.count} />
      {group.items.length === 0 ? (
        <AllClear text={meta.empty} />
      ) : (
        <Stack gap={0}>
          {items.map((item, index) => (
            <ToDoRow key={`${item.link}-${index}`} item={item} first={index === 0} />
          ))}
        </Stack>
      )}
      {group.items.length > SHOWN && (
        <Button variant="subtle" size="xs" mt="sm" onClick={() => setAll(!all)} style={{ alignSelf: 'flex-start' }}>
          {all ? 'Show fewer' : `Show all ${group.items.length}`}
        </Button>
      )}
    </Card>
  );
}

function ToDoRow({ item, first }: { item: ToDo; first: boolean }) {
  return (
    <>
      {!first && <Divider />}
      <UnstyledButton component={Link} to={item.link} className="todo-row" py="sm" px={6}>
        <Group justify="space-between" wrap="nowrap" align="flex-start" gap="sm">
          <Box style={{ minWidth: 0 }}>
            <Text size="sm" fw={600} lineClamp={2}>
              {item.title}
            </Text>
            <Text size="xs" c="dimmed" lineClamp={2}>
              {item.detail}
            </Text>
          </Box>
          {item.overdue ? (
            <Badge size="sm" color="red" variant="light" leftSection={<IconAlertTriangle size={11} />} style={{ flexShrink: 0 }}>
              Overdue
            </Badge>
          ) : item.at && Date.parse(item.at) < Date.now() - DAY ? (
            <Text size="xs" c="dimmed" style={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
              {relative(item.at).replace(' ago', '')}
            </Text>
          ) : null}
        </Group>
      </UnstyledButton>
    </>
  );
}

/** The newest unread messages from candidates. Each one also rings the bell for the job's owner and hiring manager. */
function MessagesCard({ unread, latest }: { unread: number; latest: MessagePreview[] }) {
  return (
    <Card padding="lg">
      <CardHeading icon={IconMail} color="teal" title="Messages" count={unread} />
      {latest.length === 0 ? (
        <AllClear text="No unread messages from candidates." />
      ) : (
        <Stack gap={0}>
          {latest.map((m, index) => (
            <Box key={m.id}>
              {index > 0 && <Divider />}
              <UnstyledButton component={Link} to={`/applications/${m.applicationId}?tab=messages`} className="todo-row" py="sm" px={6}>
                <Group justify="space-between" wrap="nowrap" align="flex-start" gap="sm">
                  <Box style={{ minWidth: 0 }}>
                    <Text size="sm" fw={600} lineClamp={1}>
                      {m.candidateName}
                    </Text>
                    <Text size="xs" c="dimmed" lineClamp={2}>
                      {m.preview || m.subject}
                    </Text>
                    <Text size="xs" c="dimmed" lineClamp={1}>
                      {m.jobTitle}
                    </Text>
                  </Box>
                  <Text size="xs" c="dimmed" style={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
                    {relative(m.at)}
                  </Text>
                </Group>
              </UnstyledButton>
            </Box>
          ))}
        </Stack>
      )}
      {unread > 0 && (
        <Button component={Link} to="/messages" variant="subtle" size="xs" mt="sm" style={{ alignSelf: 'flex-start' }}>
          Show all{unread > latest.length ? ` ${unread}` : ''}
        </Button>
      )}
    </Card>
  );
}

/** Re-renders every 30 seconds, so Join buttons light up on time. */
function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}

function TodayCard({ interviews, zone }: { interviews: TodayInterview[]; zone: string }) {
  const now = useNow();
  return (
    <Card padding="lg" mt="lg">
      <Group justify="space-between" mb="md">
        <Group gap="sm">
          <ThemeIcon variant="light" color="ett" size={32} radius="md">
            <IconCalendarEvent size={18} />
          </ThemeIcon>
          <Title order={2} fz="md" className="section-title">
            Today's interviews
          </Title>
        </Group>
        <Button component={Link} to="/interviews" variant="subtle" size="xs">
          All interviews
        </Button>
      </Group>
      {interviews.length === 0 ? (
        <Text size="sm" c="dimmed" py="xs">
          No interviews today.
        </Text>
      ) : (
        <Stack gap={0}>
          {interviews.map((i, index) => {
            const status = i.status.toUpperCase() as InterviewStatus;
            return (
              <Box key={i.interviewId}>
                {index > 0 && <Divider />}
                <Group wrap="nowrap" gap="sm" align="center">
                  <UnstyledButton
                    component={Link}
                    to={`/applications/${i.applicationId}?tab=interviews`}
                    className="todo-row"
                    py="sm"
                    px={6}
                    style={{ flex: 1, minWidth: 0 }}
                  >
                    <Group wrap="nowrap" gap="md" align="flex-start">
                      <Box w={64} style={{ flexShrink: 0 }}>
                        <Text fw={700} size="sm">
                          {dayjs(i.startAt).tz(zone).format('HH:mm')}
                        </Text>
                        <Text size="xs" c="dimmed">
                          {formatDuration(i.durationMin)}
                        </Text>
                      </Box>
                      <Box style={{ flex: 1, minWidth: 0 }}>
                        <Group gap={6} wrap="wrap">
                          <Text size="sm" fw={600}>
                            {i.candidateName}
                          </Text>
                          {i.kind && (
                            <Badge size="xs" variant="light" color={roundKind[i.kind].color}>
                              {roundKind[i.kind].short}
                            </Badge>
                          )}
                          {i.onPanel && (
                            <Badge size="xs" variant="outline" color="ett">
                              You're on the panel
                            </Badge>
                          )}
                        </Group>
                        <Text size="xs" c="dimmed" lineClamp={1}>
                          {i.roundLabel} · {i.jobTitle}
                        </Text>
                        {i.where && (
                          <Text size="xs" c="dimmed" lineClamp={1}>
                            {i.mode === 'online' || i.mode === 'ONLINE' ? (
                              <IconVideo size={12} style={{ verticalAlign: -2 }} />
                            ) : (
                              <IconMapPin size={12} style={{ verticalAlign: -2 }} />
                            )}{' '}
                            {i.where}
                            {i.panelSize > 0 ? ` · panel of ${i.panelSize}` : ''}
                          </Text>
                        )}
                      </Box>
                      {interviewStatus[status] && <ToneBadge tone={interviewStatus[status]} size="sm" />}
                    </Group>
                  </UnstyledButton>
                  {i.meetingLink && <JoinButton interview={i} now={now} />}
                </Group>
              </Box>
            );
          })}
        </Stack>
      )}
    </Card>
  );
}

/** Only for online panel interviews you're on. Highlighted from 15 minutes before the start until it ends. */
function JoinButton({ interview, now }: { interview: TodayInterview; now: number }) {
  const start = Date.parse(interview.startAt);
  const end = start + (interview.durationMin ?? 60) * 60_000;
  if (now > end) return null;
  const live = now >= start - JOIN_EARLY_MS;
  return (
    <Tooltip label={live ? 'Opens the meeting in a new tab' : 'Lights up 15 minutes before the start'}>
      <Button
        component="a"
        href={interview.meetingLink ?? undefined}
        target="_blank"
        rel="noopener noreferrer"
        size="xs"
        variant={live ? 'filled' : 'light'}
        color={live ? 'ett' : 'gray'}
        leftSection={<IconVideo size={14} />}
        rightSection={live ? <IconExternalLink size={12} /> : undefined}
        style={{ flexShrink: 0 }}
        className={live ? 'join-live' : undefined}
      >
        Join
      </Button>
    </Tooltip>
  );
}
