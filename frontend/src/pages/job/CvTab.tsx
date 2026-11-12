import { ActionIcon, Alert, Badge, Button, Card, Group, SimpleGrid, Stack, Table, Text, Title, Tooltip } from '@mantine/core';
import { Dropzone, type FileRejection } from '@mantine/dropzone';
import { useDisclosure } from '@mantine/hooks';
import { IconAlertCircle, IconFileUpload, IconUpload, IconUserPlus, IconX } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useApplications, useDismissUpload, useUploadCvs, useUploads } from '../../api/hooks';
import type { JobDetail } from '../../api/types';
import { ScorePill, ToneBadge } from '../../components/Badges';
import { Empty, Loading } from '../../components/QueryState';
import { relative } from '../../lib/format';
import { suggestion, uploadStatus } from '../../lib/labels';
import { notifyError } from '../../lib/notify';
import { CV_TYPES } from '../../lib/files';
import { AddCandidateModal } from './AddCandidateModal';
import { flagsToCheck, MissingInformation, missingDetails } from './MissingInformation';

// Five 10 MB files stay under the 60 MB request limit.
const CHUNK = 5;
const MAX_BYTES = 10 * 1024 * 1024;

/**
 * Two ways in: "Add candidate CV" (one CV, read straight away so the details can be checked) and "Upload multiple
 * CVs" (read and scored in the background within 10 minutes). Below, what happened to each file.
 */
export function CvTab({ job, canEdit }: { job: JobDetail; canEdit: boolean }) {
  const [params] = useSearchParams();
  const [batch, setBatch] = useState<string | undefined>(params.get('batch') ?? undefined);
  const { data: uploads, isLoading } = useUploads(job.id);
  const upload = useUploadCvs(job.id);
  const dismiss = useDismissUpload();
  const navigate = useNavigate();
  const [rejected, setRejected] = useState<{ name: string; reason: string }[]>([]);
  const [addOpen, add] = useDisclosure(false);
  const open = job.status === 'OPEN' || job.status === 'SHORTLIST_CONFIRMED';
  // With AI shortlisting on, missing details and red flags are in AI shortlisting; with it off, they're here.
  const showsMissing = !job.aiShortlisting;
  const { data: rows } = useApplications(job.id);

  const onDrop = async (files: File[]) => {
    setRejected([]);
    const batchId = crypto.randomUUID();
    setBatch(batchId);
    for (let i = 0; i < files.length; i += CHUNK) {
      try {
        await upload.mutateAsync({ files: files.slice(i, i + CHUNK), batchId });
      } catch (error) {
        notifyError(error, "Some CVs weren't uploaded");
      }
    }
  };

  const onReject = (rejections: FileRejection[]) =>
    setRejected(
      rejections.map((r) => ({
        name: r.file.name,
        reason:
          r.errors[0]?.code === 'file-too-large'
            ? 'Larger than 10 MB. Save it as a smaller PDF and try again.'
            : r.errors[0]?.code === 'file-invalid-type'
              ? 'Not a CV file. Use PDF, Word (.doc or .docx), RTF or text.'
              : r.errors[0]?.message ?? 'This file was not accepted.',
      })),
    );

  const attention = (uploads ?? []).filter((u) => u.status === 'FAILED' || u.status === 'DUPLICATE');
  const missing = missingDetails(uploads);
  const missingCount = missing.length + flagsToCheck(rows).length;
  const inBatch = batch ? (uploads ?? []).filter((u) => u.batchId === batch) : [];
  const reading = inBatch.filter((u) => u.status === 'QUEUED' || u.status === 'READING').length;
  const recent = (uploads ?? []).filter((u) => !attention.includes(u) && !missing.includes(u)).slice(0, 50);

  return (
    <Stack>
      {canEdit && open && (
        <Stack gap="sm">
          <SimpleGrid cols={{ base: 1, md: 3 }} spacing="md">
            <Card withBorder padding="md">
              <Stack gap="xs" h="100%" justify="space-between">
                <div>
                  <Group gap="xs">
                    <IconUserPlus size={18} color="var(--mantine-color-ett-7)" />
                    <Title order={3} fz="sm">
                      Add candidate CV
                    </Title>
                  </Group>
                  <Text size="sm" c="dimmed" mt={4}>
                    One CV. It's read straight away so you can check the name, email and phone before the candidate is added.
                  </Text>
                </div>
                <Button variant="light" leftSection={<IconUserPlus size={16} />} onClick={add.open} style={{ alignSelf: 'flex-start' }}>
                  Add candidate CV
                </Button>
              </Stack>
            </Card>
            <Card withBorder padding="md" style={{ gridColumn: 'span 2' }} className="cv-bulk-card">
              <Group gap="xs" mb="xs">
                <IconFileUpload size={18} color="var(--mantine-color-ett-7)" />
                <Title order={3} fz="sm">
                  Upload multiple CVs
                </Title>
              </Group>
            <Dropzone onDrop={onDrop} onReject={onReject} accept={CV_TYPES} maxSize={MAX_BYTES} loading={upload.isPending} radius="md">
              <Group justify="center" gap="lg" mih={96} style={{ pointerEvents: 'none' }}>
                <Dropzone.Accept>
                  <IconUpload size={40} stroke={1.4} color="var(--mantine-color-ett-6)" />
                </Dropzone.Accept>
                <Dropzone.Reject>
                  <IconX size={40} stroke={1.4} color="var(--mantine-color-red-6)" />
                </Dropzone.Reject>
                <Dropzone.Idle>
                  <IconFileUpload size={40} stroke={1.4} color="var(--mantine-color-dimmed)" />
                </Dropzone.Idle>
                <div>
                  <Text fw={600}>Drop CVs here or click to choose</Text>
                  <Text size="sm" c="dimmed">
                    PDF, Word, RTF or text · up to 10 MB each · many at once. Each CV is read{job.aiShortlisting ? ' and scored' : ''} within
                    10 minutes.
                  </Text>
                </div>
              </Group>
            </Dropzone>
            </Card>
          </SimpleGrid>
            {rejected.length > 0 && (
              <Alert color="red" icon={<IconAlertCircle size={18} />} title="These files weren't uploaded" withCloseButton onClose={() => setRejected([])}>
                <Stack gap={2}>
                  {rejected.map((r) => (
                    <Text key={r.name} size="sm">
                      <b>{r.name}</b>: {r.reason}
                    </Text>
                  ))}
                </Stack>
              </Alert>
            )}
            {inBatch.length > 0 && (
              <Alert color={reading > 0 ? 'blue' : 'ett'} variant="light">
                {reading > 0
                  ? `Reading ${reading} of ${inBatch.length} CVs… You can leave this page; we'll notify you when they're done.`
                  : `Done: ${inBatch.filter((u) => u.status === 'ADDED').length} added, ${inBatch.filter((u) => u.status !== 'ADDED').length} need a look.`}
              </Alert>
            )}
        </Stack>
      )}

      {showsMissing && missingCount > 0 && (
        <Card>
          <Text fw={600} mb="xs">
            Missing information ({missingCount})
          </Text>
          <MissingInformation job={job} canEdit={canEdit} uploads={uploads} rows={rows} />
        </Card>
      )}

      {attention.length > 0 && (
        <Card>
          <Text fw={600} mb="xs">
            Needs your attention ({attention.length})
          </Text>
          <Table>
            <Table.Tbody>
              {attention.map((u) => (
                <Table.Tr key={u.id}>
                  <Table.Td>
                    <Text size="sm" fw={500}>
                      {u.fileName}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {relative(u.queuedAt)}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <ToneBadge tone={uploadStatus[u.status]} size="sm" />
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{u.message}</Text>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Group gap={6} justify="flex-end" wrap="nowrap">
                      {u.status === 'DUPLICATE' && u.applicationId && (
                        <Button size="compact-sm" variant="light" onClick={() => navigate(`/applications/${u.applicationId}`)}>
                          Open candidate
                        </Button>
                      )}
                      {canEdit && (
                        <Tooltip label="Dismiss">
                          <ActionIcon variant="subtle" color="gray" onClick={() => dismiss.mutate(u.id, { onError: (e) => notifyError(e) })} aria-label="Dismiss">
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
        </Card>
      )}

      {isLoading && <Loading />}
      {!isLoading && recent.length === 0 && attention.length === 0 && missing.length === 0 && (
        <Empty title="No CVs yet">Upload CVs above. Each is read, cleaned of personal details like Aadhaar numbers and scored.</Empty>
      )}
      {recent.length > 0 && (
        <Card padding={0}>
          <Table.ScrollContainer minWidth={760}>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>File</Table.Th>
                  <Table.Th>Status</Table.Th>
                  <Table.Th>Candidate</Table.Th>
                  <Table.Th ta="center">Score</Table.Th>
                  <Table.Th>AI suggestion</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {recent.map((u) => (
                  <Table.Tr
                    key={u.id}
                    className={u.applicationId ? 'clickable-row' : undefined}
                    onClick={() => u.applicationId && navigate(`/applications/${u.applicationId}`)}
                  >
                    <Table.Td>
                      <Text size="sm">{u.fileName}</Text>
                      <Text size="xs" c="dimmed">
                        {relative(u.queuedAt)}
                        {batch && u.batchId === batch && (
                          <Badge size="xs" ml={6} variant="light">
                            This upload
                          </Badge>
                        )}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <ToneBadge tone={uploadStatus[u.status]} size="sm" />
                    </Table.Td>
                    <Table.Td>{u.candidateName ?? '—'}</Table.Td>
                    <Table.Td ta="center">
                      <ScorePill score={u.matchScore} />
                    </Table.Td>
                    <Table.Td>{u.suggestion ? <ToneBadge tone={suggestion[u.suggestion]} size="sm" /> : '—'}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}
      <AddCandidateModal jobId={job.id} opened={addOpen} onClose={add.close} />
    </Stack>
  );
}
