import { Alert, Badge, Button, Card, Group, List, Modal, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core';
import { IconInfoCircle, IconSend, IconSparkles } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useMe, useRound1Plan, useSaveRound1 } from '../api/hooks';
import { ToneBadge } from '../components/Badges';
import { DateTimeFields } from '../components/common/DateTimeFields';
import { formatDuration } from '../components/common/DurationInput';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { dateTimeIn, now, otherZone, timeIn, zoneLabel } from '../lib/format';
import { interviewStatus, roundLabel } from '../lib/labels';
import { notifyError, notifySuccess } from '../lib/notify';

/** Sets the times for everyone waiting for one of a job's AI interview rounds, then sends the invitations together. */
export function AiInterviewsPage() {
  const { jobId = '' } = useParams();
  const [params] = useSearchParams();
  const round = Number(params.get('round')) || undefined;
  const navigate = useNavigate();
  const { data: plan, isLoading, error, refetch } = useRound1Plan(jobId, round);
  const { data: me } = useMe();
  const save = useSaveRound1(jobId, round);
  // Changes made on this page, laid over the suggested or saved times from the plan.
  const [edits, setEdits] = useState<Record<string, string | null>>({});
  const [replyByEdit, setReplyBy] = useState<string | null>();
  const [confirming, setConfirming] = useState(false);

  const times = useMemo<Record<string, string | null>>(
    () => ({ ...Object.fromEntries(plan?.items.map((item) => [item.applicationId, item.startAt]) ?? []), ...edits }),
    [plan, edits],
  );
  const replyBy = replyByEdit === undefined ? (plan?.defaultReplyBy ?? null) : replyByEdit;
  const editable = useMemo(() => plan?.items.filter((item) => !item.status || item.status === 'DRAFT') ?? [], [plan]);
  const ready = editable.filter((item) => times[item.applicationId]);

  if (isLoading) return <Loading />;
  if (error || !plan) return <ErrorBlock error={error} onRetry={refetch} />;

  const body = (send: boolean) => ({
    send,
    replyBy: replyBy ?? plan.defaultReplyBy,
    items: ready.map((item) => ({
      applicationId: item.applicationId,
      startAt: times[item.applicationId]!,
      candidateZone: item.candidateZone,
    })),
  });

  const submit = (send: boolean) =>
    save.mutate(body(send), {
      onSuccess: (result) => {
        setConfirming(false);
        if (send) {
          notifySuccess(`${result.sent} invitations are on their way. Replies will appear in Interviews.`, 'Invitations sent');
          navigate('/interviews');
        } else {
          notifySuccess(`${result.saved} times saved as drafts. Candidates haven't been told yet.`, 'Draft saved');
        }
      },
      onError: (e) => notifyError(e),
    });

  return (
    <>
      <PageHeader
        eyebrow={
          <Link to={`/jobs/${plan.jobId}`} style={{ color: 'inherit' }}>
            {plan.jobRef} · {plan.jobTitle}
          </Link>
        }
        title={`Set times for ${roundLabel(plan.roundName, plan.round)}`}
        subtitle={`${plan.items.length} candidate${plan.items.length === 1 ? '' : 's'} · AI interview, ${formatDuration(plan.durationMin)}`}
        actions={
          me?.canManageHiring &&
          editable.length > 0 && (
            <>
              <Button variant="default" onClick={() => submit(false)} loading={save.isPending && !confirming} disabled={ready.length === 0}>
                Save as draft
              </Button>
              <Button leftSection={<IconSend size={18} />} onClick={() => setConfirming(true)} disabled={ready.length === 0}>
                Send {ready.length} invitation{ready.length === 1 ? '' : 's'}
              </Button>
            </>
          )
        }
      />
      {plan.items.length === 0 ? (
        <Empty title="No one is waiting for this round">
          Candidates appear here when they reach {roundLabel(plan.roundName, plan.round)}.
        </Empty>
      ) : (
        <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="lg">
          <Card p={0} style={{ gridColumn: 'span 2' }}>
            <Table.ScrollContainer minWidth={620}>
              <Table verticalSpacing="md">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Candidate</Table.Th>
                    <Table.Th>Date and time (their zone)</Table.Th>
                    <Table.Th>Their other zone</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {plan.items.map((item) => {
                    const locked = !!item.status && item.status !== 'DRAFT';
                    const value = times[item.applicationId] ?? null;
                    return (
                      <Table.Tr key={item.applicationId}>
                        <Table.Td>
                          <Text fw={600} size="sm">
                            {item.candidateName}
                          </Text>
                          <Text size="xs" c="dimmed">
                            {[item.city, item.country].filter(Boolean).join(', ')} · {zoneLabel(item.candidateZone)}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          {locked ? (
                            <Stack gap={4}>
                              <Text size="sm">{dateTimeIn(item.startAt, item.candidateZone)}</Text>
                              <ToneBadge tone={interviewStatus[item.status!]} size="sm" />
                            </Stack>
                          ) : (
                            <Stack gap={4}>
                              <div style={{ width: 300 }}>
                                <DateTimeFields
                                  compact
                                  value={value}
                                  onChange={(next) => setEdits((current) => ({ ...current, [item.applicationId]: next }))}
                                  zone={item.candidateZone}
                                  minDate={now()}
                                  disabled={!me?.canManageHiring}
                                />
                              </div>
                              {item.suggested && value === item.startAt && (
                                <Badge color="violet" variant="light" size="sm" leftSection={<IconSparkles size={12} />}>
                                  Suggested by the AI agent
                                </Badge>
                              )}
                              {item.status === 'DRAFT' && (
                                <Badge color="gray" size="sm">
                                  Saved as draft
                                </Badge>
                              )}
                            </Stack>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" c="dimmed">
                            {timeIn(value, otherZone(item.candidateZone))}
                          </Text>
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          </Card>
          <Stack>
            <Card>
              <Title order={4} mb="xs">
                Reply deadline
              </Title>
              <DateTimeFields
                value={replyBy}
                onChange={setReplyBy}
                zone={plan.hrZone}
                dateLabel="Reply by"
                minDate={now()}
                description={`Your time (${zoneLabel(plan.hrZone)}). Candidates get one reminder the day before.`}
              />
            </Card>
            <Alert icon={<IconInfoCircle size={18} />} title="How invitations work" variant="light">
              <List size="sm" spacing={6}>
                <List.Item>You set one time per candidate. Suggested times are spread across your working day.</List.Item>
                <List.Item>Each candidate gets a link to their own page, where they accept the time or ask for another and say when they're free.</List.Item>
                <List.Item>Requests for a new time appear on your Overview for you to answer.</List.Item>
              </List>
            </Alert>
          </Stack>
        </SimpleGrid>
      )}
      <Modal opened={confirming} onClose={() => setConfirming(false)} title="Send invitations now?" centered>
        <Stack>
          <Text size="sm">
            {ready.length} candidate{ready.length === 1 ? '' : 's'} will get an email with their interview time in their own
            time zone. They're asked to reply by {dateTimeIn(replyBy, plan.hrZone)}.
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirming(false)}>
              Not yet
            </Button>
            <Button leftSection={<IconSend size={16} />} loading={save.isPending} onClick={() => submit(true)}>
              Send invitations
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
