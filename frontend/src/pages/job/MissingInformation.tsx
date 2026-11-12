import { ActionIcon, Badge, Button, Card, Group, Stack, Table, Text, Tooltip } from '@mantine/core';
import { IconAlertTriangle, IconCircleCheck, IconUserQuestion, IconX } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useDismissUpload } from '../../api/hooks';
import type { ApplicationRow, JobDetail, UploadRow } from '../../api/types';
import { relative } from '../../lib/format';
import { notifyError } from '../../lib/notify';
import { AddCandidateModal } from './AddCandidateModal';

/** CVs whose name, email or phone couldn't be read. */
export const missingDetails = (uploads: UploadRow[] | undefined) => (uploads ?? []).filter((u) => u.status === 'NEEDS_DETAILS');

/** Candidates with red flags still to check (they can't be shortlisted until then). */
export const flagsToCheck = (rows: ApplicationRow[] | undefined) =>
  (rows ?? []).filter((r) => r.status === 'ACTIVE' && r.redFlagsOpen > 0 && !r.decision);

/**
 * "Missing information": CVs missing a name, email or phone (add the details to score them) and CVs with red flags
 * to check. These live here, in the job, rather than on the Overview.
 */
export function MissingInformation({ job, canEdit, uploads, rows }: {
  job: JobDetail;
  canEdit: boolean;
  uploads: UploadRow[] | undefined;
  rows: ApplicationRow[] | undefined;
}) {
  const navigate = useNavigate();
  const dismiss = useDismissUpload();
  const [finishing, setFinishing] = useState<UploadRow | null>(null);
  const details = missingDetails(uploads);
  const flagged = flagsToCheck(rows);

  if (details.length === 0 && flagged.length === 0) {
    return (
      <Group gap={8} c="dimmed" py="md" justify="center">
        <IconCircleCheck size={18} color="var(--mantine-color-ett-6)" />
        <Text size="sm">Nothing missing. Every CV has a name, email and phone, and no red flags are waiting.</Text>
      </Group>
    );
  }

  return (
    <Stack>
      {details.length > 0 && (
        <Card withBorder padding="md">
          <Group gap="xs" mb="xs">
            <IconUserQuestion size={18} color="var(--mantine-color-orange-7)" />
            <Text fw={600}>Missing name, email or phone</Text>
            <Badge size="sm" variant="light" color="orange">
              {details.length}
            </Badge>
          </Group>
          <Text size="sm" c="dimmed" mb="sm">
            We couldn't read these details from the CV. Add them and the CV is scored straight away.
          </Text>
          <Table.ScrollContainer minWidth={560}>
            <Table>
              <Table.Tbody>
                {details.map((u) => (
                  <Table.Tr key={u.id}>
                    <Table.Td>
                      <Text size="sm" fw={500}>
                        {u.detected?.fullName || u.fileName}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {u.fileName} · {relative(u.queuedAt)}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{u.message ?? 'Some details are missing'}</Text>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Group gap={6} justify="flex-end" wrap="nowrap">
                        {canEdit && (
                          <Button size="compact-sm" onClick={() => setFinishing(u)}>
                            Add details
                          </Button>
                        )}
                        {canEdit && (
                          <Tooltip label="Dismiss this CV">
                            <ActionIcon
                              variant="subtle"
                              color="gray"
                              aria-label="Dismiss"
                              onClick={() => dismiss.mutate(u.id, { onError: (e) => notifyError(e) })}
                            >
                              <IconX size={16} />
                            </ActionIcon>
                          </Tooltip>
                        )}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}

      {flagged.length > 0 && (
        <Card withBorder padding="md">
          <Group gap="xs" mb="xs">
            <IconAlertTriangle size={18} color="var(--mantine-color-red-6)" />
            <Text fw={600}>Red flags to check</Text>
            <Badge size="sm" variant="light" color="red">
              {flagged.length}
            </Badge>
          </Group>
          <Text size="sm" c="dimmed" mb="sm">
            Check each flag (for example an overlap in dates or a gap) before deciding. Once checked, the CV moves to To decide.
          </Text>
          <Table.ScrollContainer minWidth={560}>
            <Table>
              <Table.Tbody>
                {flagged.map((r) => (
                  <Table.Tr key={r.id} className="clickable-row" onClick={() => navigate(`/applications/${r.id}#decide`)}>
                    <Table.Td>
                      <Text size="sm" fw={500}>
                        {r.candidateName}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">
                        {r.redFlagsOpen} red flag{r.redFlagsOpen === 1 ? '' : 's'} to check
                      </Text>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Button size="compact-sm" variant="light" onClick={(e) => (e.stopPropagation(), navigate(`/applications/${r.id}#decide`))}>
                        Check
                      </Button>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}
      <AddCandidateModal jobId={job.id} opened={!!finishing} onClose={() => setFinishing(null)} upload={finishing} />
    </Stack>
  );
}
