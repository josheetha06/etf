import {
  Alert,
  Badge,
  Button,
  Divider,
  Group,
  Paper,
  Popover,
  Radio,
  ScrollArea,
  SegmentedControl,
  Select,
  Stack,
  Text,
  Textarea,
  UnstyledButton,
} from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconBuildingCommunity, IconInfoCircle, IconMapPin, IconPencil, IconRotateClockwise2, IconRoute, IconWheelchair } from '@tabler/icons-react';
import { useEffect, useMemo, useState } from 'react';
import { useCentres } from '../../api/hooks';
import type { CentreOptions, CentreView, SlotView } from '../../api/types';
import { dayjs, now } from '../../lib/format';
import { journey, locationSource } from '../../lib/labels';
import { Loading } from '../QueryState';
import { LocationEditor } from './LocationEditor';

export interface CentreChoice {
  centreId: string | null;
  slotId: string | null;
  assessorId: string | null;
  /** The centre isn't in the candidate's nearest centre city, so HR gives a reason. */
  outsideNearest: boolean;
  outsideReason: string;
}

export const emptyChoice = (): CentreChoice => ({ centreId: null, slotId: null, assessorId: null, outsideNearest: false, outsideReason: '' });

/** Ready to book: a slot and an assessor, and a reason when the centre is outside the nearest city. */
export function choiceReady(choice: CentreChoice) {
  return !!choice.slotId && !!choice.assessorId && (!choice.outsideNearest || choice.outsideReason.trim().length >= 5);
}

const OTHER_CITIES_SHOWN = 3;

/**
 * The partner assessment centres for a candidate. Only the centres in their nearest centre city are offered, whatever
 * dates they have free, and they take turns: the suggestion is the one that has gone longest without a candidate.
 * "Show nearest cities" lists other cities for exceptions; booking one of those needs a short reason.
 */
export function CentrePicker({ applicationId, value, onChange }: {
  applicationId: string;
  value: CentreChoice;
  onChange: (value: CentreChoice) => void;
}) {
  const [day, setDay] = useState<string | null>(null);
  const [timeOfDay, setTimeOfDay] = useState('ANY');
  const [otherCities, setOtherCities] = useState(false);
  const [allCities, setAllCities] = useState(false);
  const [editingLocation, setEditingLocation] = useState(false);
  const { data, isLoading, isFetching } = useCentres(applicationId, true, day, timeOfDay, otherCities);
  const centres = useMemo(() => data?.centres ?? [], [data]);
  const centre = centres.find((c) => c.id === value.centreId) ?? null;

  const pick = (c: CentreView, slotId: string | null): CentreChoice => {
    const slot = c.slots.find((s) => s.id === slotId) ?? null;
    return {
      centreId: c.id,
      slotId: slot?.id ?? null,
      assessorId: slot?.freeAssessorIds[0] ?? null,
      outsideNearest: !c.inNearestCity,
      outsideReason: c.inNearestCity ? '' : value.outsideReason,
    };
  };

  // Start from the centre whose turn it is, and move to the new best match when HR changes the day or time.
  useEffect(() => {
    if (!data) return;
    const current = centres.find((c) => c.id === value.centreId);
    if (!current || (value.slotId && !current.slots.some((s) => s.id === value.slotId && fits(s, current, day, timeOfDay)))) {
      const best = centres.find((c) => c.recommended) ?? centres.find((c) => c.inNearestCity && c.firstSuitableSlotId) ?? null;
      onChange(best ? pick(best, best.firstSuitableSlotId) : emptyChoice());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  if (isLoading || !data) return <Loading label="Finding the candidate's nearest partner assessment centres…" />;

  const nearest = centres.filter((c) => c.inNearestCity);
  const others = centres.filter((c) => !c.inNearestCity);
  const otherCityNames = [...new Set(others.map((c) => c.city))];
  const shownCityNames = allCities ? otherCityNames : otherCityNames.slice(0, OTHER_CITIES_SHOWN);
  const next = nearest.find((c) => c.turn === 1) ?? null;
  const visibleSlots = centre ? centre.slots.filter((s) => fits(s, centre, day, timeOfDay)) : [];
  const slot = centre?.slots.find((s) => s.id === value.slotId) ?? null;
  const fromName = data.candidate.label?.split(',')[0] ?? data.candidate.text;

  return (
    <Stack gap="md" style={{ opacity: isFetching ? 0.7 : 1 }}>
      <Paper withBorder radius="md" p="sm" bg="gray.0">
        <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
          <Group gap="sm" align="flex-start" wrap="nowrap" style={{ minWidth: 0 }}>
            <IconMapPin size={18} style={{ marginTop: 2, flexShrink: 0 }} />
            <Stack gap={2} style={{ minWidth: 0 }}>
              {data.candidate.found ? (
                <Text size="sm">
                  Candidate is in <b>{data.candidate.label}</b>
                  {data.candidate.source && (
                    <Text span size="xs" c="dimmed">
                      {' '}
                      · {locationSource[data.candidate.source]}
                    </Text>
                  )}
                </Text>
              ) : (
                <Text size="sm">
                  {data.candidate.text
                    ? `We couldn't place “${data.candidate.text}” on the map.`
                    : "The candidate's location isn't known yet."}{' '}
                  Add their town or PIN code to see their nearest partner assessment centres.
                </Text>
              )}
              {data.nearestCity && (
                <Text size="xs" c="dimmed">
                  Nearest centre city: <b>{data.nearestCity}</b>
                  {data.nearestCityMinutes !== null ? `, about ${journey(data.nearestCityMinutes)} away` : ''}
                </Text>
              )}
            </Stack>
          </Group>
          <Group gap="xs" wrap="nowrap">
            {data.candidate.found && !editingLocation && (
              <Button variant="subtle" size="compact-xs" leftSection={<IconPencil size={12} />} onClick={() => setEditingLocation(true)}>
                Change
              </Button>
            )}
            <HowItWorks options={data} />
          </Group>
        </Group>
        {(!data.candidate.found || editingLocation) && (
          <Stack mt="sm" gap={4}>
            <LocationEditor
              applicationId={applicationId}
              initial={data.candidate.found ? '' : (data.candidate.text ?? '')}
              onSaved={() => setEditingLocation(false)}
              onCancel={editingLocation ? () => setEditingLocation(false) : undefined}
              autoFocus={editingLocation}
            />
            <Text size="xs" c="dimmed">
              Saved on the candidate as "Set by HR" and recorded in the audit trail.
            </Text>
          </Stack>
        )}
      </Paper>

      <Group gap="sm" align="flex-end">
        <DatePickerInput
          label="Preferred day (optional)"
          placeholder="Any day"
          value={day}
          onChange={setDay}
          minDate={now()}
          clearable
          valueFormat="ddd D MMM"
          w={190}
        />
        <SegmentedControl
          value={timeOfDay}
          onChange={setTimeOfDay}
          data={[
            { value: 'ANY', label: 'Any time' },
            { value: 'MORNING', label: 'Morning' },
            { value: 'AFTERNOON', label: 'Afternoon' },
          ]}
        />
      </Group>

      <Radio.Group
        value={value.centreId}
        onChange={(id) => {
          const chosen = centres.find((c) => c.id === id);
          if (chosen) onChange(pick(chosen, chosen.firstSuitableSlotId));
        }}
      >
        <Stack gap="xs">
          {data.nearestCity && (
            <Group gap={6} wrap="nowrap">
              <IconBuildingCommunity size={14} color="var(--mantine-color-ett-7)" />
              <Text className="stat-label">
                {data.nearestCity} partner assessment centres
                {nearest.length > 1 ? ' · they take turns' : ''}
              </Text>
            </Group>
          )}
          {nearest.map((c) => (
            <CentreCard key={c.id} centre={c} />
          ))}
          {next && centre && centre.inNearestCity && centre.id !== next.id && (
            <Alert variant="light" color="gray" p="xs" icon={<IconRotateClockwise2 size={16} />}>
              <Text size="xs">
                {next.name} is next in turn. You can still book {centre.name}; {next.name} then stays first in line
                for the next candidate.
              </Text>
            </Alert>
          )}

          {data.otherCities > 0 && !otherCities && (
            <Group>
              <Button variant="subtle" size="xs" leftSection={<IconRoute size={14} />} onClick={() => setOtherCities(true)}>
                Show nearest cities
              </Button>
              <Text size="xs" c="dimmed">
                For exceptions, for example if the candidate can't travel to {data.nearestCity ?? 'their nearest city'}.
              </Text>
            </Group>
          )}
          {otherCities &&
            shownCityNames.map((city) => {
              const list = others.filter((c) => c.city === city);
              const minutes = list.map((c) => c.travelMinutes).filter((m): m is number => m !== null);
              return (
                <Stack key={city} gap="xs">
                  <Divider
                    my={4}
                    labelPosition="left"
                    label={
                      <Text size="xs" c="dimmed">
                        {city}
                        {minutes.length > 0 ? ` · about ${journey(Math.min(...minutes))}${fromName ? ` from ${fromName}` : ''}` : ''}
                      </Text>
                    }
                  />
                  {list.map((c) => (
                    <CentreCard key={c.id} centre={c} />
                  ))}
                </Stack>
              );
            })}
          {otherCities && (
            <Group gap="xs">
              {otherCityNames.length > OTHER_CITIES_SHOWN && (
                <Button variant="subtle" size="xs" onClick={() => setAllCities(!allCities)}>
                  {allCities ? 'Show fewer cities' : `Show all ${otherCityNames.length} cities`}
                </Button>
              )}
              <Button variant="subtle" color="gray" size="xs" onClick={() => {
                setOtherCities(false);
                setAllCities(false);
                if (centre && !centre.inNearestCity) onChange(emptyChoice());
              }}>
                Hide other cities
              </Button>
            </Group>
          )}
        </Stack>
      </Radio.Group>

      {centre && value.outsideNearest && (
        <Textarea
          label={`Why ${centre.city}?`}
          description={
            data.nearestCity
              ? `${centre.city} isn't the candidate's nearest centre city (${data.nearestCity}). The reason is kept with the booking and in the audit trail.`
              : 'The candidate\'s location isn\'t known. The reason is kept with the booking and in the audit trail.'
          }
          placeholder="e.g. The candidate is travelling for a family event that week"
          value={value.outsideReason}
          onChange={(e) => onChange({ ...value, outsideReason: e.currentTarget.value })}
          error={value.outsideReason.length > 0 && value.outsideReason.trim().length < 5 ? 'At least 5 characters' : null}
          maxLength={500}
          autosize
          minRows={2}
          required
        />
      )}

      {centre && (
        <Stack gap={6}>
          <Text size="sm" fw={500}>
            Time slot at {centre.name}{' '}
            <Text span size="xs" c="dimmed">
              (centre's local time, {zoneShort(centre.timeZone)})
            </Text>
          </Text>
          {visibleSlots.length === 0 ? (
            <Text size="sm" c="dimmed">
              No free slot with an assessor for the day or time you chose. Clear the preferred day or pick another centre.
            </Text>
          ) : (
            <SlotGrid centre={centre} slots={visibleSlots} selected={value.slotId} onSelect={(s) => onChange(pick(centre, s.id))} />
          )}
          {slot && (
            <Select
              label="Assessor"
              description="Only assessors who are free at this time"
              data={centre.assessors
                .filter((a) => slot.freeAssessorIds.includes(a.id))
                .map((a) => ({ value: a.id, label: a.fullName }))}
              value={value.assessorId}
              onChange={(id) => onChange({ ...value, assessorId: id })}
              allowDeselect={false}
              maw={320}
            />
          )}
        </Stack>
      )}
    </Stack>
  );
}

function fits(slot: SlotView, centre: CentreView, day: string | null, timeOfDay: string) {
  if (slot.seatsLeft <= 0 || slot.freeAssessorIds.length === 0) return false;
  const local = dayjs(slot.startAt).tz(centre.timeZone);
  if (day && local.format('YYYY-MM-DD') !== dayjs(day).format('YYYY-MM-DD')) return false;
  if (timeOfDay === 'MORNING') return local.hour() < 12;
  if (timeOfDay === 'AFTERNOON') return local.hour() >= 12;
  return true;
}

function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`;
}

function zoneShort(zone: string) {
  return zone === 'Asia/Kolkata' ? 'IST' : zone === 'Europe/London' ? 'UK time' : zone;
}

function travelText(c: CentreView) {
  if (c.travelMinutes === null || c.roadKm === null) return null;
  const km = c.roadKm < 10 ? c.roadKm.toFixed(1) : Math.round(c.roadKm).toString();
  return `${km} km · ${journey(c.travelMinutes)}`;
}

function CentreCard({ centre }: { centre: CentreView }) {
  const travel = travelText(centre);
  return (
    <Radio.Card value={centre.id} p="sm" radius="md" disabled={!centre.firstSuitableSlotId}>
      <Group align="flex-start" wrap="nowrap" gap="sm">
        <Radio.Indicator mt={2} />
        <Stack gap={3} style={{ flex: 1, minWidth: 0 }}>
          <Group gap={6} justify="space-between" wrap="nowrap">
            <Group gap={6}>
              <Text size="sm" fw={600}>
                {centre.name}
              </Text>
              {centre.recommended && centre.inRotation && (
                <Badge size="xs" color="ett" variant="filled" leftSection={<IconRotateClockwise2 size={9} />}>
                  Next in turn
                </Badge>
              )}
              {centre.recommended && !centre.inRotation && (
                <Badge size="xs" color="ett" variant="filled">
                  Suggested
                </Badge>
              )}
              {!centre.recommended && centre.turn !== null && (
                <Badge size="xs" color="ett" variant="light">
                  {ordinal(centre.turn)} in turn
                </Badge>
              )}
              {centre.inRotation && centre.turn === null && (
                <Badge size="xs" color="gray" variant="light">
                  Sits out this turn
                </Badge>
              )}
            </Group>
            {travel && (
              <Badge size="sm" variant="light" color={centre.inNearestCity ? 'ett' : 'gray'} leftSection={<IconRoute size={11} />}>
                {travel}
              </Badge>
            )}
          </Group>
          <Text size="xs" c="dimmed" lineClamp={1}>
            {[centre.address, centre.city, centre.postcode].filter(Boolean).join(', ')}
            {centre.stepFreeAccess && (
              <>
                {' · '}
                <IconWheelchair size={11} style={{ verticalAlign: -1 }} /> step-free
              </>
            )}
          </Text>
          {centre.reasons
            .filter((reason) => !reason.startsWith('About '))
            .map((reason) => (
              <Text key={reason} size="xs" c={reason.startsWith('No free') || reason.startsWith('Sits out') ? 'orange.8' : 'dimmed'}>
                {reason}
              </Text>
            ))}
        </Stack>
      </Group>
    </Radio.Card>
  );
}

function SlotGrid({ centre, slots, selected, onSelect }: {
  centre: CentreView;
  slots: SlotView[];
  selected: string | null;
  onSelect: (slot: SlotView) => void;
}) {
  const days = useMemo(() => {
    const groups = new Map<string, SlotView[]>();
    slots.forEach((slot) => {
      const key = dayjs(slot.startAt).tz(centre.timeZone).format('YYYY-MM-DD');
      groups.set(key, [...(groups.get(key) ?? []), slot]);
    });
    return [...groups.entries()].slice(0, 14);
  }, [slots, centre.timeZone]);
  return (
    <ScrollArea.Autosize mah={220}>
      <Stack gap="xs">
        {days.map(([day, list]) => (
          <Group key={day} gap="xs" wrap="nowrap" align="flex-start">
            <Text size="xs" c="dimmed" w={78} pt={6} style={{ flexShrink: 0 }}>
              {dayjs(list[0].startAt).tz(centre.timeZone).format('ddd D MMM')}
            </Text>
            <Group gap={6}>
              {list.map((slot) => (
                <UnstyledButton key={slot.id} onClick={() => onSelect(slot)} aria-pressed={selected === slot.id}>
                  <Paper withBorder px="sm" py={4} radius="sm" className="slot-button" data-selected={selected === slot.id || undefined}>
                    <Text size="sm" fw={500} className="tabular">
                      {dayjs(slot.startAt).tz(centre.timeZone).format('HH:mm')}
                    </Text>
                    <Text size="10px" c="dimmed">
                      {slot.seatsLeft} {slot.seatsLeft === 1 ? 'seat' : 'seats'} · {slot.freeAssessorIds.length} free
                    </Text>
                  </Paper>
                </UnstyledButton>
              ))}
            </Group>
          </Group>
        ))}
      </Stack>
    </ScrollArea.Autosize>
  );
}

function HowItWorks({ options }: { options: CentreOptions }) {
  return (
    <Popover width={360} position="bottom-end" withArrow shadow="md">
      <Popover.Target>
        <UnstyledButton aria-label="How centres are chosen">
          <Group gap={4} wrap="nowrap">
            <IconInfoCircle size={16} color="var(--mantine-color-ett-7)" />
            <Text size="xs" c="ett.7" fw={500} style={{ whiteSpace: 'nowrap' }}>
              How it works
            </Text>
          </Group>
        </UnstyledButton>
      </Popover.Target>
      <Popover.Dropdown>
        <Stack gap={6}>
          <Text size="sm" fw={600}>
            How centres are chosen
          </Text>
          <Text size="xs">{options.method}</Text>
          <Alert variant="light" color="gray" p="xs">
            <Stack gap={6}>
              <Text size="xs">
                <b>Nearest city only:</b> a candidate in Sivakasi or Virudhunagar is offered the Madurai centres, with
                whichever dates they have free.
              </Text>
              <Text size="xs">
                <b>Taking turns:</b> the centre that has gone longest without a candidate is suggested. A centre with no
                free slot sits out that turn and keeps its place in the queue.
              </Text>
              <Text size="xs">
                <b>Exceptions:</b> "Show nearest cities" lists other cities. Booking one needs a short reason.
              </Text>
            </Stack>
          </Alert>
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
