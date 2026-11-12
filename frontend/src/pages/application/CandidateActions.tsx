import { Alert, Button, Group, Menu, Modal, Select, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconBriefcase, IconChevronDown, IconDownload, IconPencil, IconUserMinus } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAddToJob, useJobs, useMe, useUpdateDetails, useWithdraw } from '../../api/hooks';
import type { ApplicationDetail } from '../../api/types';
import { notifyError, notifySuccess } from '../../lib/notify';

export function CandidateActions({ detail }: { detail: ApplicationDetail }) {
  const { data: me } = useMe();
  const [editOpen, edit] = useDisclosure(false);
  const [jobOpen, job] = useDisclosure(false);
  const [withdrawOpen, withdraw] = useDisclosure(false);
  const canAct = !!me?.canManageHiring;
  return (
    <>
      <Menu position="bottom-end" shadow="md" width={240}>
        <Menu.Target>
          <Button variant="default" rightSection={<IconChevronDown size={14} />}>
            More
          </Button>
        </Menu.Target>
        <Menu.Dropdown>
          {detail.cvAvailable && (
            <Menu.Item component="a" href={`/api/applications/${detail.id}/cv`} leftSection={<IconDownload size={16} />}>
              Download CV
            </Menu.Item>
          )}
          {canAct && (
            <>
              <Menu.Item leftSection={<IconPencil size={16} />} onClick={edit.open}>
                Edit candidate details
              </Menu.Item>
              <Menu.Item leftSection={<IconBriefcase size={16} />} onClick={job.open}>
                Add to another job
              </Menu.Item>
              {detail.status === 'ACTIVE' && (
                <>
                  <Menu.Divider />
                  <Menu.Item color="red" leftSection={<IconUserMinus size={16} />} onClick={withdraw.open}>
                    Candidate withdrew
                  </Menu.Item>
                </>
              )}
            </>
          )}
        </Menu.Dropdown>
      </Menu>
      {editOpen && <EditDetailsModal detail={detail} onClose={edit.close} />}
      {jobOpen && <AddToJobModal detail={detail} onClose={job.close} />}
      {withdrawOpen && <WithdrawModal detail={detail} onClose={withdraw.close} />}
    </>
  );
}

function EditDetailsModal({ detail, onClose }: { detail: ApplicationDetail; onClose: () => void }) {
  const save = useUpdateDetails(detail.id);
  const [value, setValue] = useState({
    fullName: detail.candidate.fullName,
    email: detail.candidate.email,
    mobile: detail.candidate.mobile ?? '',
    city: detail.candidate.city ?? '',
    country: detail.candidate.country ?? 'India',
  });
  const field = (key: keyof typeof value, label: string) => (
    <TextInput label={label} value={value[key]} onChange={(e) => setValue({ ...value, [key]: e.currentTarget.value })} />
  );
  return (
    <Modal opened onClose={onClose} title="Edit candidate details">
      <Stack>
        {field('fullName', 'Full name')}
        {field('email', 'Email')}
        {field('mobile', 'Mobile')}
        <Group grow>
          {field('city', 'City')}
          {field('country', 'Country')}
        </Group>
        <Text size="xs" c="dimmed">
          Changes apply to this candidate in all your jobs.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={save.isPending}
            onClick={() =>
              save.mutate(value, {
                onSuccess: () => {
                  notifySuccess('Details saved.');
                  onClose();
                },
                onError: (e) => notifyError(e),
              })
            }
          >
            Save
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function AddToJobModal({ detail, onClose }: { detail: ApplicationDetail; onClose: () => void }) {
  const { data: jobs } = useJobs('', 'OPEN');
  const add = useAddToJob(detail.id);
  const navigate = useNavigate();
  const [jobId, setJobId] = useState<string | null>(null);
  const taken = new Set([detail.job.id, ...detail.otherJobs.map((j) => j.jobId)]);
  const options = (jobs ?? []).filter((j) => !taken.has(j.id)).map((j) => ({ value: j.id, label: `${j.title} · ${j.jobRef}` }));
  return (
    <Modal opened onClose={onClose} title={`Add ${detail.candidate.fullName} to another job`}>
      <Stack>
        <Select label="Open job" data={options} value={jobId} onChange={setJobId} searchable nothingFoundMessage="No other open jobs" />
        <Text size="xs" c="dimmed">
          The same CV is scored against the other job's criteria straight away.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={!jobId}
            loading={add.isPending}
            onClick={() =>
              jobId &&
              add.mutate(jobId, {
                onSuccess: (result) => {
                  notifySuccess('Added and scored.');
                  onClose();
                  navigate(`/applications/${result.applicationId}`);
                },
                onError: (e) => notifyError(e),
              })
            }
          >
            Add to job
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function WithdrawModal({ detail, onClose }: { detail: ApplicationDetail; onClose: () => void }) {
  const withdraw = useWithdraw(detail.id);
  const [reason, setReason] = useState('');
  return (
    <Modal opened onClose={onClose} title="The candidate withdrew">
      <Stack>
        <Alert color="orange" variant="light">
          Any upcoming interviews are cancelled and the candidate leaves this job's process.
        </Alert>
        <Textarea label="What did they say?" placeholder="For example: accepted another offer" value={reason} onChange={(e) => setReason(e.currentTarget.value)} autosize minRows={2} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            color="red"
            loading={withdraw.isPending}
            onClick={() =>
              withdraw.mutate(reason.trim(), {
                onSuccess: () => {
                  notifySuccess('Recorded as withdrawn.');
                  onClose();
                },
                onError: (e) => notifyError(e),
              })
            }
          >
            Record withdrawal
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
