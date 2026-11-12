import { Anchor, Button, Group, Stack, Table, Text } from '@mantine/core';
import { IconExternalLink, IconMapPin } from '@tabler/icons-react';
import { useState } from 'react';
import { Link } from 'react-router';
import type { InterviewRow } from '../../api/types';
import { ToneBadge } from '../../components/Badges';
import { InterviewActions } from '../../components/candidate/InterviewActions';
import { RescheduleModal } from '../../components/candidate/RescheduleModal';
import { Empty } from '../../components/QueryState';
import { ist, istTime } from '../../lib/format';
import { interviewStatus, roundKind, roundLabel } from '../../lib/labels';
import { ChangesCell } from '../../components/common/ChangesLine';
import { formatDuration } from '../../components/common/DurationInput';

/** Interviews in IST, with where they happen and what HR can do next. */
export function InterviewsTable({ rows, showJob = true, emptyTitle }: { rows: InterviewRow[]; showJob?: boolean; emptyTitle: string }) {
  const [requestId, setRequestId] = useState<string | null>(null);
  if (rows.length === 0) return <Empty title={emptyTitle} />;
  return (
    <>
      <Table.ScrollContainer minWidth={1040}>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>When (IST)</Table.Th>
              <Table.Th>Candidate</Table.Th>
              {showJob && <Table.Th>Job</Table.Th>}
              <Table.Th>Round</Table.Th>
              <Table.Th>Where</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Last updated</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((row) => (
              <Table.Tr key={row.id}>
                <Table.Td>
                  <Text size="sm" fw={600}>
                    {ist(row.startAt)}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {formatDuration(row.durationMin ?? 45)}
                    {row.status === 'INVITED' && row.replyBy ? ` · reply by ${ist(row.replyBy)}` : ''}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Anchor component={Link} to={`/applications/${row.applicationId}#decide`} size="sm" fw={600}>
                    {row.candidateName}
                  </Anchor>
                </Table.Td>
                {showJob && (
                  <Table.Td>
                    <Anchor component={Link} to={`/jobs/${row.jobId}`} size="sm" c="inherit">
                      {row.jobTitle}
                    </Anchor>
                  </Table.Td>
                )}
                <Table.Td>
                  <Text size="sm">{roundLabel(row.roundName, row.round)}</Text>
                  <Text size="xs" c="dimmed">
                    {row.roundKind ? roundKind[row.roundKind].label : ''}
                  </Text>
                </Table.Td>
                <Table.Td>
                  {row.centreName ? (
                    <Stack gap={0}>
                      <Text size="sm">
                        <IconMapPin size={13} style={{ verticalAlign: -2 }} /> {row.centreName}
                      </Text>
                      <Text size="xs" c="dimmed">
                        Arrive by {istTime(row.arriveBy)}
                        {row.assessorName ? ` · ${row.assessorName}` : ''}
                      </Text>
                    </Stack>
                  ) : row.meetingLink ? (
                    <Anchor href={row.meetingLink} target="_blank" rel="noreferrer" size="sm">
                      Meeting link <IconExternalLink size={12} />
                    </Anchor>
                  ) : row.venue ? (
                    <Text size="sm">
                      <IconMapPin size={13} style={{ verticalAlign: -2 }} /> {row.venue}
                    </Text>
                  ) : (
                    <Text size="sm" c="dimmed">
                      {row.roundKind === 'AI_INTERVIEW' ? 'Online (AI interview)' : 'Online'}
                    </Text>
                  )}
                  {row.roundKind === 'PANEL' && (
                    <Text size="xs" c="dimmed">
                      Panel of {row.panelSize} · {row.feedbackCount} reviews in
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <ToneBadge tone={interviewStatus[row.status]} size="sm" />
                </Table.Td>
                <Table.Td>
                  <ChangesCell changes={row.changes} />
                </Table.Td>
                <Table.Td ta="right">
                  <Group gap={6} justify="flex-end" wrap="nowrap">
                    {row.pendingRequestId && (
                      <Button size="compact-sm" color="orange" onClick={() => setRequestId(row.pendingRequestId)}>
                        Answer request
                      </Button>
                    )}
                    <InterviewActions interview={row} compact />
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      <RescheduleModal requestId={requestId} opened={!!requestId} onClose={() => setRequestId(null)} />
    </>
  );
}
