import { Alert, Badge, Button, Chip, Divider, Group, Modal, Stack, Text, Textarea } from '@mantine/core';
import { IconCheck } from '@tabler/icons-react';
import { useCallback, useState } from 'react';
import { useReschedule, useRespondReschedule } from '../../api/hooks';
import { dateTimeIn, dayLabel, INDIA, ist, now } from '../../lib/format';
import { DateTimeFields } from '../common/DateTimeFields';
import type { Availability } from '../../api/types';
import { rescheduleReason, roundLabel, timeOfDay } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';
import { ErrorBlock, Loading } from '../QueryState';
import { CentrePicker, choiceReady, emptyChoice, type CentreChoice } from './CentrePicker';

/** The candidate asked for another time: approve and send a new one, or keep the original. */
export function RescheduleModal({ requestId, opened, onClose }: { requestId: string | null; opened: boolean; onClose: () => void }) {
  return (
    <Modal opened={opened} onClose={onClose} size="xl" title={<Text fw={600}>Request for a different time</Text>}>
      {opened && requestId && <RescheduleForm requestId={requestId} onClose={onClose} />}
    </Modal>
  );
}

export function RescheduleForm({ requestId, onClose }: { requestId: string; onClose: () => void }) {
  const { data, isLoading, error, refetch } = useReschedule(requestId);
  const respond = useRespondReschedule(requestId);
  const [chosen, setChosen] = useState<string | null>(null);
  const [custom, setCustom] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [seat, setSeat] = useState<CentreChoice>(emptyChoice);
  const onSeat = useCallback((value: CentreChoice) => setSeat(value), []);

  if (isLoading) return <Loading />;
  if (error || !data) return <ErrorBlock error={error} onRetry={refetch} />;
  const interview = data.interview;
  const zone = interview.candidateTz ?? INDIA;
  const newTime = custom ?? chosen;
  const pending = data.status === 'PENDING';
  const partner = interview.roundKind === 'PARTNER';
  const panel = interview.roundKind === 'PANEL';
  const ready = partner ? choiceReady(seat) : !!newTime;
  const freeDates = data.availability.length > 0 ? data.availability.map((a) => a.date) : data.availableDays;

  const send = (action: 'NEW_TIME' | 'KEEP_ORIGINAL') =>
    respond.mutate(
      {
        action,
        newStartAt: action === 'NEW_TIME' && !partner ? newTime ?? undefined : undefined,
        newSlotId: action === 'NEW_TIME' && partner ? seat.slotId ?? undefined : undefined,
        assessorId: action === 'NEW_TIME' && partner ? seat.assessorId ?? undefined : undefined,
        outsideReason: action === 'NEW_TIME' && partner && seat.outsideNearest ? seat.outsideReason.trim() : null,
        message,
      },
      {
        onSuccess: () => {
          notifySuccess(
            action === 'NEW_TIME'
              ? `${interview.candidateName} has been sent the new time.${panel ? ' The panel has been told.' : ''}`
              : 'The candidate knows the original time stays.',
            'Reply sent',
          );
          onClose();
        },
        onError: (e) => notifyError(e),
      },
    );

  return (
    <Stack>
      <Group justify="space-between" align="flex-start">
        <Stack gap={2}>
          <Text fw={600}>
            {interview.candidateName} · {roundLabel(interview.roundName, interview.round)}
          </Text>
          <Text size="sm" c="dimmed">
            {interview.jobTitle} · now {ist(interview.startAt)}
            {interview.centreName ? ` at ${interview.centreName}` : ''}
          </Text>
        </Stack>
        <Stack gap={4} align="flex-end">
          <Badge color="orange">{data.reason ? rescheduleReason[data.reason] : 'Different time'}</Badge>
          {data.requestedByCandidate && (
            <Text size="xs" c="dimmed">
              Sent from their candidate page
            </Text>
          )}
        </Stack>
      </Group>
      {data.note && <Text className="quote">“{data.note}”</Text>}
      <Stack gap={6}>
        <Text size="sm" fw={500}>
          When they're free
        </Text>
        <Group gap={6}>
          {data.availability.length > 0
            ? data.availability.map((a) => (
                <Badge key={`${a.date}-${a.window}-${a.from}`} variant="light" color="ett" size="lg" radius="sm" style={{ textTransform: 'none' }}>
                  {dayLabel(a.date)} · {windowLabel(a)}
                </Badge>
              ))
            : data.availableDays.map((day) => (
                <Badge key={day} variant="light" color="ett" size="lg" radius="sm" style={{ textTransform: 'none' }}>
                  {dayLabel(day)}
                </Badge>
              ))}
          {data.availability.length === 0 && data.timeOfDay && <Text size="sm">· {timeOfDay[data.timeOfDay]}</Text>}
        </Group>
      </Stack>
      <Divider />
      {!pending ? (
        <Alert color={data.status === 'APPROVED' ? 'ett' : 'gray'} icon={<IconCheck size={18} />} title="Answered">
          {data.status === 'APPROVED' ? `New time sent: ${ist(data.newStartAt)}.` : 'The original time was kept.'}{' '}
          {data.resolvedByName && `Answered by ${data.resolvedByName}.`}
        </Alert>
      ) : (
        <Stack>
          {partner ? (
            <>
              <Text size="sm" fw={500}>
                Choose a new slot at a partner assessment centre
              </Text>
              <CentrePicker applicationId={interview.applicationId} value={seat} onChange={onSeat} />
            </>
          ) : (
            <>
              <Text size="sm" fw={500}>
                Free times on the days they gave
                <Text span size="xs" c="dimmed" ml={6}>
                  (their days are highlighted in the calendar)
                </Text>
              </Text>
              {data.suggestions.length === 0 ? (
                <Text size="sm" c="dimmed">
                  No free times on those days. Pick a time below or keep the original.
                </Text>
              ) : (
                <Chip.Group
                  multiple={false}
                  value={custom ? null : chosen}
                  onChange={(value) => {
                    setChosen(value as string);
                    setCustom(null);
                  }}
                >
                  <Group gap={8}>
                    {data.suggestions.map((slot) => (
                      <Chip key={slot} value={slot} size="sm">
                        {dateTimeIn(slot, zone)}
                      </Chip>
                    ))}
                  </Group>
                </Chip.Group>
              )}
              <Text size="sm" fw={500}>
                Or choose another time
              </Text>
              <div style={{ maxWidth: 420 }}>
                <DateTimeFields
                  value={custom}
                  onChange={setCustom}
                  zone={zone}
                  minDate={now()}
                  highlightDates={freeDates}
                  description="Their free days are in bold in the calendar."
                />
              </div>
              {newTime && (
                <Text size="sm">
                  New time: <b>{dateTimeIn(newTime, zone)}</b>
                </Text>
              )}
            </>
          )}
          <Textarea
            label="Message to the candidate (optional)"
            placeholder="No problem. Here's a new time that works for us."
            autosize
            minRows={2}
            maxLength={1000}
            value={message}
            onChange={(e) => setMessage(e.currentTarget.value)}
          />
          <Text size="xs" c="dimmed">
            The candidate gets an email and a notification, and accepts the new time the same way as before.
            {panel ? ' Panel members are told about the new time.' : ''}
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => send('KEEP_ORIGINAL')} loading={respond.isPending}>
              Keep original time
            </Button>
            <Button onClick={() => send('NEW_TIME')} disabled={!ready} loading={respond.isPending}>
              Approve and send new time
            </Button>
          </Group>
        </Stack>
      )}
    </Stack>
  );
}

function windowLabel(a: Availability) {
  if (a.window === 'range' && a.from && a.to) return `${a.from}–${a.to}`;
  if (a.window === 'morning') return 'Morning';
  if (a.window === 'afternoon') return 'Afternoon';
  return 'Any time';
}
