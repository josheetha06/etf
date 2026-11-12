import { ActionIcon, Button, Group, Menu, Modal, Stack, Text, Textarea } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCalendarQuestion, IconCalendarRepeat, IconCheck, IconDots, IconFileCheck, IconUserOff, IconX } from '@tabler/icons-react';
import { useState } from 'react';
import { useBranding, useInterviewAction, useMe, useNoShow, useSampleReport } from '../../api/hooks';
import type { InterviewRow } from '../../api/types';
import { hasStarted } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useNavigate } from 'react-router';
import { LogRequestModal } from '../../pages/interviews/LogRequestModal';

/**
 * What HR can do with one interview: record the reply, a new-time request, a no-show (and offer a new time in one
 * step), or cancel it.
 */
export function InterviewActions({ interview, compact = false }: { interview: InterviewRow; compact?: boolean }) {
  const { data: me } = useMe();
  const { data: branding } = useBranding();
  const action = useInterviewAction();
  const sample = useSampleReport();
  const noShow = useNoShow();
  const [requestOpen, request] = useDisclosure(false);
  const [cancelOpen, cancelModal] = useDisclosure(false);
  const [reason, setReason] = useState('');
  const navigate = useNavigate();
  if (!me?.canManageHiring) return null;
  const upcoming = ['INVITED', 'CONFIRMED', 'RESCHEDULE_REQUESTED'].includes(interview.status);
  const started = hasStarted(interview.startAt);
  if (!upcoming) return null;
  const roundName = interview.roundName ?? `Round ${interview.round}`;

  // Records the no-show, then opens booking for the same round on the candidate's page.
  const missedAndRebook = () =>
    noShow.mutate(interview.id, {
      onSuccess: () => {
        notifySuccess('Recorded as not attended. Pick a new time: the invitation will say they missed the last one.');
        navigate(`/applications/${interview.applicationId}?book=1`);
      },
      onError: (error) => notifyError(error),
    });

  const run = (promise: Promise<unknown>, message: string) =>
    promise.then(() => notifySuccess(message)).catch((error) => notifyError(error));

  const items = (
    <>
      {interview.status === 'INVITED' && (
        <Menu.Item leftSection={<IconCheck size={16} />} onClick={() => run(action.mutateAsync({ id: interview.id, action: 'accept' }), 'Recorded that the candidate accepted.')}>
          Candidate accepted (by email)
        </Menu.Item>
      )}
      {interview.status !== 'RESCHEDULE_REQUESTED' && (
        <Menu.Item leftSection={<IconCalendarQuestion size={16} />} onClick={request.open}>
          Candidate asked for another time
        </Menu.Item>
      )}
      {branding?.sampleReports && interview.status !== 'RESCHEDULE_REQUESTED' && (
        <Menu.Item
          leftSection={<IconFileCheck size={16} />}
          onClick={() => run(sample.mutateAsync(interview.id), 'Sample report added. It is labelled SAMPLE.')}
        >
          Complete with sample report
        </Menu.Item>
      )}
      {started && (
        <>
          <Menu.Item leftSection={<IconCalendarRepeat size={16} />} onClick={missedAndRebook}>
            Didn't attend: offer a new time
          </Menu.Item>
          <Menu.Item leftSection={<IconUserOff size={16} />} onClick={() => run(noShow.mutateAsync(interview.id), 'Recorded as not attended.')}>
            Didn't attend (no new time)
          </Menu.Item>
        </>
      )}
      <Menu.Divider />
      <Menu.Item color="red" leftSection={<IconX size={16} />} onClick={cancelModal.open}>
        Cancel interview
      </Menu.Item>
    </>
  );

  return (
    <>
      <Menu position="bottom-end" withinPortal shadow="md" width={280}>
        <Menu.Target>
          {compact ? (
            <ActionIcon variant="subtle" color="gray" aria-label="Interview actions" onClick={(e) => e.stopPropagation()}>
              <IconDots size={18} />
            </ActionIcon>
          ) : (
            <Button variant="default" size="xs" rightSection={<IconDots size={14} />}>
              Update
            </Button>
          )}
        </Menu.Target>
        <Menu.Dropdown onClick={(e) => e.stopPropagation()}>{items}</Menu.Dropdown>
      </Menu>
      <LogRequestModal interview={requestOpen ? interview : null} onClose={request.close} />
      <Modal opened={cancelOpen} onClose={cancelModal.close} title="Cancel this interview?">
        <Stack>
          <Text size="sm">
            {interview.candidateName} will get an email saying {roundName} is cancelled.
            {interview.panelSize > 0 ? ' The panel is told as well.' : interview.assessorName ? ' The assessor is told as well.' : ''}
          </Text>
          <Textarea label="Reason for the candidate (optional)" value={reason} onChange={(e) => setReason(e.currentTarget.value)} autosize minRows={2} />
          <Group justify="flex-end">
            <Button variant="default" onClick={cancelModal.close}>
              Keep it
            </Button>
            <Button
              color="red"
              loading={action.isPending}
              onClick={() =>
                action.mutate(
                  { id: interview.id, action: 'cancel', reason },
                  {
                    onSuccess: () => {
                      notifySuccess('Interview cancelled.');
                      cancelModal.close();
                    },
                    onError: (e) => notifyError(e),
                  },
                )
              }
            >
              Cancel interview
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
