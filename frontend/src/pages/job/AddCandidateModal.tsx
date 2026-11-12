import { Alert, Button, FileInput, Group, Modal, Stack, Stepper, Text, TextInput } from '@mantine/core';
import { IconFileCv, IconInfoCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useCandidateDraft, useCompleteUpload } from '../../api/hooks';
import type { UploadRow } from '../../api/types';
import { CV_TYPES } from '../../lib/files';
import { notifyError, notifySuccess } from '../../lib/notify';

/**
 * "Add candidate CV": one CV (no cover letter needed), read straight away so the name, email and phone can be
 * checked before the candidate is added. Also used to fill in details a CV was missing.
 */
export function AddCandidateModal({ jobId, opened, onClose, upload: existing }: {
  jobId: string;
  opened: boolean;
  onClose: () => void;
  /** Finish an upload that needs details instead of starting from a new file. */
  upload?: UploadRow | null;
}) {
  return (
    <Modal opened={opened} onClose={onClose} size="lg" title={<Text fw={600}>{existing ? 'Add the missing details' : 'Add candidate CV'}</Text>}>
      {opened && <AddCandidateForm jobId={jobId} onClose={onClose} existing={existing ?? null} />}
    </Modal>
  );
}

function AddCandidateForm({ jobId, onClose, existing }: { jobId: string; onClose: () => void; existing: UploadRow | null }) {
  const draft = useCandidateDraft(jobId);
  const complete = useCompleteUpload();
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [upload, setUpload] = useState<UploadRow | null>(existing);
  const [details, setDetails] = useState(() => fromUpload(existing));
  const emailOk = /^\S+@\S+\.\S+$/.test(details.email.trim());

  const read = () =>
    file &&
    draft.mutate(
      { file },
      {
        onSuccess: (row) => {
          setUpload(row);
          setDetails(fromUpload(row));
        },
        onError: (e) => notifyError(e, "We couldn't read that CV"),
      },
    );

  const save = () =>
    upload &&
    complete.mutate(
      {
        uploadId: upload.id,
        fullName: details.fullName.trim(),
        email: details.email.trim(),
        mobile: details.mobile.trim() || undefined,
        city: details.city.trim() || undefined,
        country: details.country.trim() || undefined,
      },
      {
        onSuccess: (row) => {
          notifySuccess(`${details.fullName} has been added and scored${row.matchScore !== null ? ` (${row.matchScore})` : ''}.`);
          onClose();
          if (row.applicationId) navigate(`/applications/${row.applicationId}#decide`);
        },
        onError: (e) => notifyError(e),
      },
    );

  return (
    <Stack>
      <Stepper active={upload ? 1 : 0} size="xs">
        <Stepper.Step label="CV" />
        <Stepper.Step label="Check details" />
      </Stepper>
      {!upload ? (
        <>
          <FileInput
            label="CV or resume"
            description="PDF, Word or text, up to 10 MB. A cover letter isn't needed."
            placeholder="Choose the CV"
            accept={CV_TYPES.join(',')}
            leftSection={<IconFileCv size={16} />}
            value={file}
            onChange={setFile}
            required
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={read} disabled={!file} loading={draft.isPending}>
              Read CV
            </Button>
          </Group>
        </>
      ) : (
        <>
          <Text size="sm" c="dimmed">
            From <b>{upload.fileName}</b>. Check the details we found and fill in anything missing.
          </Text>
          {upload.existingCandidate && (
            <Alert color="orange" icon={<IconInfoCircle size={18} />}>
              This email already belongs to {upload.existingCandidate.fullName} ({upload.existingCandidate.candidateRef}), who is in{' '}
              {upload.existingCandidate.jobs} of your jobs. Saving adds the same person to this job.
            </Alert>
          )}
          <TextInput label="Full name" required value={details.fullName} onChange={(e) => setDetails({ ...details, fullName: e.currentTarget.value })} />
          <Group grow>
            <TextInput
              label="Email"
              required
              value={details.email}
              onChange={(e) => setDetails({ ...details, email: e.currentTarget.value })}
              error={details.email && !emailOk ? 'Enter a valid email' : null}
            />
            <TextInput label="Mobile" value={details.mobile} onChange={(e) => setDetails({ ...details, mobile: e.currentTarget.value })} />
          </Group>
          <Group grow>
            <TextInput label="City" value={details.city} onChange={(e) => setDetails({ ...details, city: e.currentTarget.value })} />
            <TextInput label="Country" value={details.country} onChange={(e) => setDetails({ ...details, country: e.currentTarget.value })} />
          </Group>
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={save} disabled={!details.fullName.trim() || !emailOk} loading={complete.isPending}>
              Add and score
            </Button>
          </Group>
        </>
      )}
    </Stack>
  );
}

function fromUpload(upload: UploadRow | null) {
  const d = upload?.detected;
  return {
    fullName: d?.fullName ?? '',
    email: d?.email ?? '',
    mobile: d?.mobile ?? '',
    city: d?.city ?? '',
    country: d?.country ?? 'India',
  };
}
