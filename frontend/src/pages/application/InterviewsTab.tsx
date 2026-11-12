import { ActionIcon, Anchor, Badge, Button, Card, CopyButton, Group, Modal, Stack, Table, Text, Title, Tooltip } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCheck, IconCopy, IconExternalLink, IconMapPin, IconPencil, IconUsersGroup } from '@tabler/icons-react';
import { useState } from 'react';
import { useMe, useUpdatePanel } from '../../api/hooks';
import type { ApplicationDetail, InterviewRow, MemberInput } from '../../api/types';
import { ToneBadge } from '../../components/Badges';
import { InterviewActions } from '../../components/candidate/InterviewActions';
import { PanelMembersEditor, panelReady } from '../../components/candidate/PanelMembersEditor';
import { ChangesLine } from '../../components/common/ChangesLine';
import { formatDuration } from '../../components/common/DurationInput';
import { HistoryList } from '../../components/common/HistoryList';
import { Empty } from '../../components/QueryState';
import { ist, istTime } from '../../lib/format';
import { interviewStatus, roundKind, roundLabel } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';

export function InterviewsTab({ detail }: { detail: ApplicationDetail }) {
  const interviews = [...detail.interviews].sort((a, b) => b.round - a.round);
  if (interviews.length === 0) {
    return <Empty title="No interviews yet">Rounds are booked from the Next step panel when the candidate reaches them.</Empty>;
  }
  return (
    <Stack>
      {interviews.map((interview) => (
        <InterviewCard key={interview.id} interview={interview} />
      ))}
    </Stack>
  );
}

function InterviewCard({ interview }: { interview: InterviewRow }) {
  const [historyOpen, history] = useDisclosure(false);
  return (
    <Card>
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={4} style={{ minWidth: 0 }}>
          <Group gap="xs">
            <Title order={5} className="section-title">
              {roundLabel(interview.roundName, interview.round)}
            </Title>
            {interview.roundKind && (
              <Badge size="sm" variant="light" color={roundKind[interview.roundKind].color}>
                {roundKind[interview.roundKind].label}
              </Badge>
            )}
            <ToneBadge tone={interviewStatus[interview.status]} size="sm" />
          </Group>
          <Text size="sm">
            {ist(interview.startAt)} · {formatDuration(interview.durationMin ?? 45)}
          </Text>
          {interview.centreName && (
            <Text size="sm" c="dimmed">
              <IconMapPin size={13} style={{ verticalAlign: -2 }} /> {interview.centreName}
              {interview.centreAddress ? `, ${interview.centreAddress}` : ''} · arrive by {istTime(interview.arriveBy)}
              {interview.assessorName ? ` · assessor ${interview.assessorName}` : ''}
            </Text>
          )}
          {interview.venue && (
            <Text size="sm" c="dimmed">
              <IconMapPin size={13} style={{ verticalAlign: -2 }} /> {interview.venue}
            </Text>
          )}
          {interview.meetingLink && (
            <Anchor href={interview.meetingLink} target="_blank" rel="noreferrer" size="sm">
              Meeting link <IconExternalLink size={12} />
            </Anchor>
          )}
          {interview.centreNote && (
            <Text size="xs" c="orange.8">
              {interview.centreNote}
            </Text>
          )}
          <Text size="xs" c="dimmed">
            Booked by {interview.scheduledByName ?? 'HR'}
            {interview.status === 'INVITED' && interview.replyBy ? ` · reply due ${ist(interview.replyBy)}` : ''}
          </Text>
        </Stack>
        <InterviewActions interview={interview} />
      </Group>
      {interview.roundKind === 'PANEL' && interview.status !== 'CANCELLED' && <PanelBlock interview={interview} />}
      <Group justify="space-between" mt="sm" gap="xs">
        <ChangesLine changes={interview.changes} createdLabel="Booked" />
        <Button variant="subtle" size="compact-xs" color="gray" onClick={history.toggle}>
          {historyOpen ? 'Hide history' : 'History'}
        </Button>
      </Group>
      {historyOpen && (
        <Card withBorder={false} bg="gray.0" mt="xs" padding="sm">
          <HistoryList filter={{ interviewId: interview.id, size: 20 }} />
        </Card>
      )}
    </Card>
  );
}

/** The panel for a panel interview: who's on it, whose review is in, the interview code, and editing the panel. */
function PanelBlock({ interview }: { interview: InterviewRow }) {
  const { data: me } = useMe();
  const [editOpen, edit] = useDisclosure(false);
  return (
    <Card mt="md" padding="md" radius="md" bg="gray.0" withBorder={false}>
      <Group justify="space-between" mb="xs" wrap="wrap" gap="xs">
        <Group gap={6}>
          <IconUsersGroup size={16} />
          <Text size="sm" fw={600}>
            Panel · {interview.feedbackCount} of {interview.panelSize} reviews in
          </Text>
        </Group>
        <Group gap="xs">
          {interview.panelCode && (
            <Group gap={4}>
              <Text size="sm" c="dimmed">
                Interview code
              </Text>
              <Badge size="lg" variant="outline" color="dark" ff="monospace" style={{ letterSpacing: 1 }}>
                {interview.panelCode}
              </Badge>
              <CopyButton value={interview.panelCode}>
                {({ copied, copy }) => (
                  <Tooltip label={copied ? 'Copied' : 'Copy the code'}>
                    <ActionIcon variant="subtle" color={copied ? 'ett' : 'gray'} onClick={copy} aria-label="Copy the interview code">
                      {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                    </ActionIcon>
                  </Tooltip>
                )}
              </CopyButton>
            </Group>
          )}
          {me?.canManageHiring && interview.status !== 'COMPLETED' && (
            <Button size="compact-sm" variant="default" leftSection={<IconPencil size={14} />} onClick={edit.open}>
              Edit panel
            </Button>
          )}
        </Group>
      </Group>
      <Table.ScrollContainer minWidth={420}>
        <Table verticalSpacing={4} withRowBorders={false}>
          <Table.Tbody>
            {interview.panel.map((m) => (
              <Table.Tr key={m.id}>
                <Table.Td w={110}>
                  <Text size="sm" ff="monospace">
                    {m.employeeId}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{m.fullName}</Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {m.email}
                  </Text>
                </Table.Td>
                <Table.Td ta="right">
                  {m.reviewSubmitted ? (
                    <Badge color="ett" variant="light">
                      Review sent
                    </Badge>
                  ) : m.reviewStarted ? (
                    <Badge color="yellow" variant="light">
                      Draft
                    </Badge>
                  ) : (
                    <Badge color="gray" variant="light">
                      Waiting
                    </Badge>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      <Text size="xs" c="dimmed" mt={4}>
        Panel members sign in with the panel login, then enter their employee ID and this code.
        {interview.panelCodeFrom && interview.panelCodeUntil
          ? ` The code works from ${ist(interview.panelCodeFrom)} until ${ist(interview.panelCodeUntil)}, or until you make the decision.`
          : ' The code opens an hour before the interview and closes after it, or when you make the decision.'}
      </Text>
      <EditPanelModal interview={interview} opened={editOpen} onClose={edit.close} />
    </Card>
  );
}

function EditPanelModal({ interview, opened, onClose }: { interview: InterviewRow; opened: boolean; onClose: () => void }) {
  const [panel, setPanel] = useState<MemberInput[]>(() =>
    interview.panel.map((m) => ({ employeeId: m.employeeId, fullName: m.fullName, email: m.email })),
  );
  const update = useUpdatePanel();
  const locked = interview.panel.filter((m) => m.reviewSubmitted).map((m) => m.employeeId);
  const save = () =>
    update.mutate(
      { interviewId: interview.id, panel: panel.map((m) => ({ employeeId: m.employeeId.trim(), fullName: m.fullName.trim(), email: m.email.trim() })) },
      {
        onSuccess: () => {
          notifySuccess('Panel saved. New members (and changed email addresses) have been sent the interview code.');
          onClose();
        },
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Modal opened={opened} onClose={onClose} size="lg" title={<Text fw={600}>Edit the panel</Text>}>
      <Stack>
        <PanelMembersEditor value={panel} onChange={setPanel} locked={locked} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} disabled={!panelReady(panel)} loading={update.isPending}>
            Save panel
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
