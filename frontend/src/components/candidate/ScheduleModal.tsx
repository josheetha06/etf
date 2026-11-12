import { Alert, Button, Group, Modal, SegmentedControl, Select, Stack, Text, TextInput } from '@mantine/core';
import { IconId, IconRobot, IconUsersGroup } from '@tabler/icons-react';
import { useCallback, useState } from 'react';
import { useCentres, useCurrentRound, useScheduleAi, useSchedulePanel, useSchedulePartner } from '../../api/hooks';
import type { InterviewMode, MemberInput, RoundKind } from '../../api/types';
import { dateTimeIn, INDIA, ist, now, UK, zoneLabel } from '../../lib/format';
import { roundKind, roundLabel } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';
import { DateTimeFields } from '../common/DateTimeFields';
import { DurationInput } from '../common/DurationInput';
import { CentrePicker, choiceReady, emptyChoice, type CentreChoice } from './CentrePicker';
import { emptyMember, PanelMembersEditor, panelReady } from './PanelMembersEditor';

export interface RoundToBook {
  round: number;
  name: string;
  kind: RoundKind;
  mode: InterviewMode | null;
  durationMin: number | null;
}

interface Props {
  opened: boolean;
  onClose: () => void;
  applicationId: string;
  candidateName: string;
  round: RoundToBook;
  onScheduled?: () => void;
}

/** Books the candidate's current round, whatever its type. Opened from the candidate page and the Interviews page. */
export function ScheduleModal(props: Props) {
  const { round } = props;
  return (
    <Modal
      opened={props.opened}
      onClose={props.onClose}
      size={round.kind === 'PARTNER' ? 'xl' : 'lg'}
      title={
        <Stack gap={0}>
          <Text fw={600}>Book {roundLabel(round.name, round.round)}</Text>
          <Text size="sm" c="dimmed">
            {props.candidateName} · {roundKind[round.kind].label}
          </Text>
        </Stack>
      }
    >
      {props.opened && round.kind === 'AI_INTERVIEW' && <AiForm {...props} />}
      {props.opened && round.kind === 'PARTNER' && <PartnerForm {...props} />}
      {props.opened && round.kind === 'PANEL' && <PanelForm {...props} />}
    </Modal>
  );
}

/** Opens the booking dialog for a candidate's current round, looking the round up first. */
export function ScheduleCurrentRound({ applicationId, candidateName, onClose }: {
  applicationId: string;
  candidateName: string;
  onClose: () => void;
}) {
  const current = useCurrentRound(applicationId);
  if (!current.data) return null;
  const r = current.data;
  return (
    <ScheduleModal
      opened
      onClose={onClose}
      applicationId={applicationId}
      candidateName={candidateName}
      round={{ round: r.number, name: r.name, kind: r.kind, mode: r.mode, durationMin: r.durationMin }}
    />
  );
}

const ZONES = [
  { value: INDIA, label: zoneLabel(INDIA) },
  { value: UK, label: zoneLabel(UK) },
  { value: 'Asia/Dubai', label: 'UAE (GST)' },
  { value: 'Asia/Singapore', label: 'Singapore (SGT)' },
  { value: 'America/New_York', label: 'US Eastern' },
];

export function TimeFields({ start, setStart, zone, setZone, duration, setDuration, highlightDates }: {
  start: string | null;
  setStart: (value: string | null) => void;
  zone: string;
  setZone: (value: string) => void;
  duration: number;
  setDuration: (value: number) => void;
  /** Dates the candidate said they're free, shown in bold in the calendar. */
  highlightDates?: string[];
}) {
  return (
    <Stack gap="sm">
      <DateTimeFields
        value={start}
        onChange={setStart}
        zone={zone}
        description="In the candidate's time zone"
        minDate={now()}
        highlightDates={highlightDates}
        required
      />
      <Group align="flex-start" gap="md" wrap="wrap">
        <Select
          label="Candidate's time zone"
          description="IST for candidates in India"
          data={ZONES}
          value={zone}
          onChange={(value) => setZone(value ?? INDIA)}
          allowDeselect={false}
          w={220}
        />
        <DurationInput value={duration} onChange={setDuration} />
      </Group>
      {start && zone !== INDIA && (
        <Text size="sm" c="dimmed">
          That's {ist(start)} for your team.
        </Text>
      )}
    </Stack>
  );
}

function AiForm({ applicationId, candidateName, round, onClose, onScheduled }: Props) {
  const [start, setStart] = useState<string | null>(null);
  const [zone, setZone] = useState(INDIA);
  const [duration, setDuration] = useState(round.durationMin ?? 45);
  const schedule = useScheduleAi(applicationId);
  const submit = () =>
    start &&
    schedule.mutate(
      { startAt: start, candidateZone: zone, durationMin: duration },
      {
        onSuccess: () => {
          notifySuccess(`${candidateName} has the invitation for ${dateTimeIn(start, zone)}.`, 'Invitation sent');
          onScheduled?.();
          onClose();
        },
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Stack>
      <Alert variant="light" color="violet" icon={<IconRobot size={18} />}>
        <Text size="sm">{roundKind.AI_INTERVIEW.help}</Text>
      </Alert>
      <TimeFields start={start} setStart={setStart} zone={zone} setZone={setZone} duration={duration} setDuration={setDuration} />
      <Text size="xs" c="dimmed">
        The candidate gets an email with the time and a link to their own page, where they can accept or ask for another time.
      </Text>
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!start} loading={schedule.isPending}>
          Send invitation
        </Button>
      </Group>
    </Stack>
  );
}

function PartnerForm({ applicationId, candidateName, onClose, onScheduled }: Props) {
  const centres = useCentres(applicationId);
  const [choice, setChoice] = useState<CentreChoice>(emptyChoice);
  const onChange = useCallback((value: CentreChoice) => setChoice(value), []);
  const schedule = useSchedulePartner(applicationId);
  const centre = centres.data?.centres.find((c) => c.id === choice.centreId);
  const slot = centre?.slots.find((s) => s.id === choice.slotId);
  const submit = () =>
    choice.slotId &&
    schedule.mutate(
      { slotId: choice.slotId, assessorId: choice.assessorId ?? undefined, outsideReason: choice.outsideNearest ? choice.outsideReason.trim() : null },
      {
        onSuccess: () => {
          notifySuccess(`${candidateName} has the invitation. The assessor has their report link.`, 'Interview booked');
          onScheduled?.();
          onClose();
        },
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Stack>
      <Alert variant="light" color="teal" icon={<IconId size={18} />}>
        <Text size="sm">
          {roundKind.PARTNER.help} The assessor checks the candidate's original Aadhaar and PAN, then sends the report.
        </Text>
      </Alert>
      <CentrePicker applicationId={applicationId} value={choice} onChange={onChange} />
      {slot && centre && (
        <Text size="sm">
          Booking <b>{centre.name}</b>, <b>{dateTimeIn(slot.startAt, centre.timeZone)}</b>. The candidate is asked to arrive
          15 minutes early with their original ID.
        </Text>
      )}
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!choiceReady(choice)} loading={schedule.isPending}>
          Book and send invitation
        </Button>
      </Group>
    </Stack>
  );
}

function PanelForm({ applicationId, candidateName, round, onClose, onScheduled }: Props) {
  const [start, setStart] = useState<string | null>(null);
  const [zone, setZone] = useState(INDIA);
  const [duration, setDuration] = useState(round.durationMin ?? 60);
  const [mode, setMode] = useState<InterviewMode>(round.mode === 'IN_PERSON' ? 'IN_PERSON' : 'ONLINE');
  const [link, setLink] = useState('');
  const [venue, setVenue] = useState('');
  const [panel, setPanel] = useState<MemberInput[]>([emptyMember()]);
  const schedule = useSchedulePanel(applicationId);
  const linkOk = /^https:\/\/\S+$/.test(link.trim());
  const placeOk = mode === 'ONLINE' ? linkOk : venue.trim().length >= 3;
  const submit = () =>
    start &&
    schedule.mutate(
      {
        startAt: start,
        candidateZone: zone,
        durationMin: duration,
        mode,
        meetingLink: mode === 'ONLINE' ? link.trim() : null,
        venue: mode === 'IN_PERSON' ? venue.trim() : null,
        panel: panel.map((m) => ({ employeeId: m.employeeId.trim(), fullName: m.fullName.trim(), email: m.email.trim() })),
      },
      {
        onSuccess: () => {
          notifySuccess(`${candidateName} and ${panel.length} panel member${panel.length === 1 ? '' : 's'} have been emailed.`,
            'Interview booked');
          onScheduled?.();
          onClose();
        },
        onError: (error) => notifyError(error),
      },
    );
  return (
    <Stack>
      <Alert variant="light" color="blue" icon={<IconUsersGroup size={18} />}>
        <Text size="sm">
          Each panel member gets an email with an interview code. After the interview they sign in with the panel login,
          enter their employee ID and the code, and fill in a detailed review.
        </Text>
      </Alert>
      <TimeFields start={start} setStart={setStart} zone={zone} setZone={setZone} duration={duration} setDuration={setDuration} />
      <SegmentedControl
        value={mode}
        onChange={(v) => setMode(v as InterviewMode)}
        data={[
          { value: 'ONLINE', label: 'Online' },
          { value: 'IN_PERSON', label: 'In person' },
        ]}
        w={240}
      />
      {mode === 'ONLINE' ? (
        <TextInput
          label="Meeting link"
          placeholder="https://meet.google.com/… or https://teams.microsoft.com/…"
          value={link}
          onChange={(event) => setLink(event.currentTarget.value)}
          error={link && !linkOk ? 'Paste the full link, starting with https://' : null}
          required
        />
      ) : (
        <TextInput
          label="Where"
          placeholder="Office address and meeting room"
          value={venue}
          onChange={(event) => setVenue(event.currentTarget.value)}
          required
        />
      )}
      <PanelMembersEditor value={panel} onChange={setPanel} />
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!start || !placeOk || !panelReady(panel)} loading={schedule.isPending}>
          Book and send invitations
        </Button>
      </Group>
    </Stack>
  );
}
