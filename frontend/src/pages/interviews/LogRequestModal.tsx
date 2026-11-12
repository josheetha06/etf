import { Button, Group, Modal, SegmentedControl, Select, Stack, Text, Textarea } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useLogReschedule } from '../../api/hooks';
import type { InterviewRow, RescheduleReason, TimeOfDay } from '../../api/types';
import { dayjs } from '../../lib/format';
import { rescheduleReason, timeOfDay } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';

/** The candidate replied to the invitation asking for a different time; HR records what they said. */
export function LogRequestModal({ interview, onClose }: { interview: InterviewRow | null; onClose: () => void }) {
  const log = useLogReschedule();
  const navigate = useNavigate();
  const [reason, setReason] = useState<RescheduleReason | null>('CLASH');
  const [note, setNote] = useState('');
  const [days, setDays] = useState<string[]>([]);
  const [tod, setTod] = useState<TimeOfDay>('ANY');

  const submit = () => {
    if (!interview || !reason || days.length === 0) return;
    log.mutate(
      { interviewId: interview.id, reason, note, availableDays: days, timeOfDay: tod },
      {
        onSuccess: (request) => {
          notifySuccess('The original time stays held until you reply.', 'Request logged');
          onClose();
          navigate(`/reschedule/${request.id}`);
        },
        onError: (e) => notifyError(e),
      },
    );
  };

  return (
    <Modal opened={!!interview} onClose={onClose} title="Log a request for a different time" size="lg" centered>
      {interview && (
        <Stack>
          <Text size="sm" c="dimmed">
            Record what {interview.candidateName} asked for in their reply. You'll answer on the next screen.
          </Text>
          <Select
            label="Reason"
            data={Object.entries(rescheduleReason).map(([value, label]) => ({ value, label }))}
            value={reason}
            onChange={(value) => setReason(value as RescheduleReason)}
            allowDeselect={false}
          />
          <Textarea label="Their note (optional)" maxLength={500} autosize minRows={2} value={note} onChange={(e) => setNote(e.currentTarget.value)} />
          <DatePickerInput
            type="multiple"
            label="Days they're free"
            placeholder="Pick one or more days"
            minDate={dayjs().add(1, 'day').format('YYYY-MM-DD')}
            maxDate={dayjs().add(60, 'day').format('YYYY-MM-DD')}
            value={days}
            onChange={(value) => setDays(value as string[])}
            withAsterisk
          />
          <div>
            <Text size="sm" fw={500} mb={4}>
              Best time of day
            </Text>
            <SegmentedControl
              fullWidth
              value={tod}
              onChange={(value) => setTod(value as TimeOfDay)}
              data={Object.entries(timeOfDay).map(([value, label]) => ({ value, label }))}
            />
          </div>
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={submit} disabled={!reason || days.length === 0} loading={log.isPending}>
              Log request
            </Button>
          </Group>
        </Stack>
      )}
    </Modal>
  );
}
