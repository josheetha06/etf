import {
  ActionIcon,
  Badge,
  Button,
  Card,
  Chip,
  Group,
  Modal,
  NumberInput,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Table,
  TagsInput,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconCalendarPlus, IconMapPin, IconPencil, IconPlus, IconSearch, IconTrash, IconUserPlus } from '@tabler/icons-react';
import { useState } from 'react';
import {
  useAddSlots,
  useCentreAdmin,
  useCentreSlots,
  useRemoveSlot,
  useSaveAssessor,
  useSaveCentre,
  type CentreInput,
} from '../../api/hooks';
import type { CentreAdminView } from '../../api/types';
import { DurationInput } from '../../components/common/DurationInput';
import { Empty, Loading } from '../../components/QueryState';
import { dateTimeIn, dayjs } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { TIME_ZONES } from './CompanySettings';

/** Partner assessment centres: where they are, who assesses there, and when they have slots. */
export function CentreSettings({ admin }: { admin: boolean }) {
  const { data, isLoading } = useCentreAdmin();
  const [editing, setEditing] = useState<CentreAdminView | 'new' | null>(null);
  const [slotsFor, setSlotsFor] = useState<CentreAdminView | null>(null);
  const [q, setQ] = useState('');
  if (isLoading || !data) return <Loading />;
  const term = q.trim().toLowerCase();
  const shown = term
    ? data.filter((c) => [c.name, c.city, c.partnerOrg, c.postcode].some((v) => v?.toLowerCase().includes(term)))
    : data;
  return (
    <Stack>
      <Group justify="space-between" align="flex-start" wrap="wrap">
        <div>
          <Title order={4} className="section-title">
            Partner assessment centres
          </Title>
          <Text size="sm" c="dimmed" maw={640}>
            Candidates are offered the centres in their nearest centre city, which take turns (round robin), so keep
            each centre's slots up to date.
          </Text>
        </div>
        {admin && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
            Add centre
          </Button>
        )}
      </Group>
      {data.length > 4 && (
        <TextInput
          placeholder={`Search ${data.length} centres by name, city or partner`}
          leftSection={<IconSearch size={16} />}
          value={q}
          onChange={(e) => setQ(e.currentTarget.value)}
          maw={420}
          aria-label="Search centres"
        />
      )}
      {shown.length === 0 ? (
        <Card>
          <Empty title={data.length === 0 ? 'No centres yet' : 'No centres match'}>
            {data.length === 0 ? 'Add a centre to book partner assessment centre rounds.' : 'Try another city or name.'}
          </Empty>
        </Card>
      ) : (
        <SimpleGrid cols={{ base: 1, lg: 2 }}>
          {shown.map((c) => (
            <CentreCard key={c.id} centre={c} admin={admin && c.editable} onEdit={() => setEditing(c)} onSlots={() => setSlotsFor(c)} />
          ))}
        </SimpleGrid>
      )}
      {editing && <CentreModal centre={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {slotsFor && <SlotsModal centre={slotsFor} admin={admin && slotsFor.editable} onClose={() => setSlotsFor(null)} />}
    </Stack>
  );
}

function CentreCard({ centre, admin, onEdit, onSlots }: { centre: CentreAdminView; admin: boolean; onEdit: () => void; onSlots: () => void }) {
  const [adding, setAdding] = useState(false);
  const save = useSaveAssessor();
  const free = centre.seatsNext4Weeks - centre.bookedNext4Weeks;
  return (
    <Card withBorder padding="md" style={{ opacity: centre.active ? 1 : 0.7 }}>
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={2} style={{ minWidth: 0 }}>
          <Group gap={6}>
            <Text fw={600}>{centre.name}</Text>
            {!centre.active && (
              <Badge size="xs" color="gray">
                Not in use
              </Badge>
            )}
            {centre.stepFreeAccess && (
              <Badge size="xs" variant="light" color="teal">
                Step-free
              </Badge>
            )}
          </Group>
          <Text size="sm" c="dimmed">
            <IconMapPin size={13} style={{ verticalAlign: -2 }} /> {[centre.address, centre.city, centre.postcode].filter(Boolean).join(', ')}
          </Text>
          {centre.partnerOrg && (
            <Text size="xs" c="dimmed">
              Run by {centre.partnerOrg}
            </Text>
          )}
        </Stack>
        {admin && (
          <Tooltip label="Edit centre">
            <ActionIcon variant="subtle" color="gray" onClick={onEdit} aria-label="Edit centre">
              <IconPencil size={16} />
            </ActionIcon>
          </Tooltip>
        )}
      </Group>

      <SimpleGrid cols={3} mt="md" spacing="xs">
        <MiniStat label="Slots, next 4 weeks" value={centre.slotsNext4Weeks} />
        <MiniStat label="Seats free" value={free} tone={free === 0 && centre.active ? 'red' : undefined} />
        <MiniStat label="Booked" value={centre.bookedNext4Weeks} />
      </SimpleGrid>
      <Text size="xs" c="dimmed" mt={6}>
        {centre.nextSlotAt ? `Next slot ${dateTimeIn(centre.nextSlotAt, centre.timeZone)}` : 'No upcoming slots'}
      </Text>

      <Text size="sm" fw={600} mt="md" mb={4}>
        Assessors
      </Text>
      <Stack gap={4}>
        {centre.assessors.length === 0 && (
          <Text size="sm" c="dimmed">
            No assessors yet.
          </Text>
        )}
        {centre.assessors.map((a) => (
          <Group key={a.id} justify="space-between" wrap="nowrap">
            <Text size="sm" c={a.active ? undefined : 'dimmed'} truncate>
              {a.fullName}
              <Text span size="xs" c="dimmed">
                {' '}
                · {a.email ?? 'no email'}
              </Text>
            </Text>
            {admin && (
              <Switch
                size="xs"
                checked={a.active}
                aria-label={`${a.fullName} available`}
                onChange={(e) =>
                  save.mutate(
                    { centreId: centre.id, assessorId: a.id, fullName: a.fullName, email: a.email ?? '', active: e.currentTarget.checked },
                    { onError: (err) => notifyError(err) },
                  )
                }
              />
            )}
          </Group>
        ))}
      </Stack>
      {adding && <AssessorForm centreId={centre.id} onDone={() => setAdding(false)} />}
      <Group mt="md" gap="xs">
        <Button size="xs" variant="light" leftSection={<IconCalendarPlus size={14} />} onClick={onSlots}>
          {admin ? 'Slots' : 'View slots'}
        </Button>
        {admin && !adding && (
          <Button size="xs" variant="subtle" leftSection={<IconUserPlus size={14} />} onClick={() => setAdding(true)}>
            Add assessor
          </Button>
        )}
      </Group>
    </Card>
  );
}

function MiniStat({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div>
      <Text fw={700} size="lg" c={tone ? `${tone}.7` : 'navy.9'} className="tabular">
        {value}
      </Text>
      <Text size="xs" c="dimmed" lh={1.2}>
        {label}
      </Text>
    </div>
  );
}

function AssessorForm({ centreId, onDone }: { centreId: string; onDone: () => void }) {
  const save = useSaveAssessor();
  const [fullName, setName] = useState('');
  const [email, setEmail] = useState('');
  const ok = fullName.trim().length > 1 && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim());
  return (
    <Group mt="sm" gap="xs" align="flex-end" wrap="wrap">
      <TextInput size="xs" label="Name" value={fullName} onChange={(e) => setName(e.currentTarget.value)} style={{ flex: '1 1 140px' }} />
      <TextInput size="xs" label="Email" value={email} onChange={(e) => setEmail(e.currentTarget.value)} style={{ flex: '1 1 180px' }} />
      <Button
        size="xs"
        disabled={!ok}
        loading={save.isPending}
        onClick={() =>
          save.mutate(
            { centreId, fullName: fullName.trim(), email: email.trim(), active: true },
            {
              onSuccess: () => {
                notifySuccess(`${fullName.trim()} added.`);
                onDone();
              },
              onError: (e) => notifyError(e),
            },
          )
        }
      >
        Add
      </Button>
      <Button size="xs" variant="default" onClick={onDone}>
        Cancel
      </Button>
    </Group>
  );
}

function CentreModal({ centre, onClose }: { centre: CentreAdminView | null; onClose: () => void }) {
  const save = useSaveCentre();
  const [v, setV] = useState<CentreInput>(() => ({
    name: centre?.name ?? '',
    address: centre?.address ?? '',
    city: centre?.city ?? '',
    postcode: centre?.postcode ?? '',
    country: centre?.country ?? 'India',
    latitude: centre?.latitude ?? null,
    longitude: centre?.longitude ?? null,
    timeZone: centre?.timeZone ?? 'Asia/Kolkata',
    stepFreeAccess: centre?.stepFreeAccess ?? false,
    partnerOrg: centre?.partnerOrg ?? '',
    active: centre?.active ?? true,
  }));
  const set = <K extends keyof CentreInput>(k: K, value: CentreInput[K]) => setV((c) => ({ ...c, [k]: value }));
  const ok = v.name.trim().length > 1 && v.city.trim().length > 1;
  const submit = () =>
    ok &&
    !save.isPending &&
    save.mutate(
      {
        ...v,
        id: centre?.id,
        name: v.name.trim(),
        city: v.city.trim(),
        address: v.address?.trim() || null,
        postcode: v.postcode?.trim() || null,
        country: v.country?.trim() || null,
        partnerOrg: v.partnerOrg?.trim() || null,
      },
      {
        onSuccess: () => {
          notifySuccess(centre ? 'Centre saved.' : 'Centre added. Add its assessors and slots next.');
          onClose();
        },
        onError: (e) => notifyError(e),
      },
    );
  return (
    <Modal opened onClose={onClose} size="lg" title={<Text fw={600}>{centre ? `Edit ${centre.name}` : 'Add a partner assessment centre'}</Text>}>
      <Stack>
        <TextInput label="Centre name" value={v.name} onChange={(e) => set('name', e.currentTarget.value)} maxLength={120} required />
        <TextInput label="Address" value={v.address ?? ''} onChange={(e) => set('address', e.currentTarget.value)} maxLength={300} />
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <TextInput label="City" value={v.city} onChange={(e) => set('city', e.currentTarget.value)} maxLength={80} required />
          <TextInput label="Postcode / PIN" value={v.postcode ?? ''} onChange={(e) => set('postcode', e.currentTarget.value)} maxLength={20} />
          <TextInput label="Country" value={v.country ?? ''} onChange={(e) => set('country', e.currentTarget.value)} maxLength={60} />
        </SimpleGrid>
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <NumberInput
            label="Latitude (optional)"
            description="Leave blank to place the city on the map"
            decimalScale={6}
            min={-90}
            max={90}
            value={v.latitude ?? ''}
            onChange={(x) => set('latitude', x === '' ? null : Number(x))}
          />
          <NumberInput
            label="Longitude (optional)"
            description="From any online map"
            decimalScale={6}
            min={-180}
            max={180}
            value={v.longitude ?? ''}
            onChange={(x) => set('longitude', x === '' ? null : Number(x))}
          />
        </SimpleGrid>
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <Select label="Time zone" data={TIME_ZONES} value={v.timeZone} onChange={(x) => x && set('timeZone', x)} allowDeselect={false} />
          <TextInput label="Partner organisation" value={v.partnerOrg ?? ''} onChange={(e) => set('partnerOrg', e.currentTarget.value)} maxLength={120} />
        </SimpleGrid>
        <Group>
          <Switch label="Step-free access" checked={!!v.stepFreeAccess} onChange={(e) => set('stepFreeAccess', e.currentTarget.checked)} />
          <Switch label="In use for bookings" checked={v.active} onChange={(e) => set('active', e.currentTarget.checked)} />
        </Group>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!ok} loading={save.isPending}>
            {centre ? 'Save centre' : 'Add centre'}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

const WEEKDAYS = [
  { value: '1', label: 'Mon' },
  { value: '2', label: 'Tue' },
  { value: '3', label: 'Wed' },
  { value: '4', label: 'Thu' },
  { value: '5', label: 'Fri' },
  { value: '6', label: 'Sat' },
  { value: '7', label: 'Sun' },
];

function SlotsModal({ centre, admin, onClose }: { centre: CentreAdminView; admin: boolean; onClose: () => void }) {
  const { data: slots, isLoading } = useCentreSlots(centre.id);
  const add = useAddSlots();
  const remove = useRemoveSlot();
  const [range, setRange] = useState<[string | null, string | null]>([null, null]);
  const [days, setDays] = useState<string[]>(['1', '2', '3', '4', '5']);
  const [times, setTimes] = useState<string[]>(['10:00', '14:00']);
  const [duration, setDuration] = useState(60);
  const [seats, setSeats] = useState<number | string>(2);
  const badTime = times.find((t) => !/^([01]\d|2[0-3]):[0-5]\d$/.test(t));
  const ok = !!range[0] && !!range[1] && days.length > 0 && times.length > 0 && !badTime && Number(seats) >= 1;

  const submit = () =>
    ok &&
    !add.isPending &&
    add.mutate(
      {
        centreId: centre.id,
        fromDate: dayjs(range[0]).format('YYYY-MM-DD'),
        toDate: dayjs(range[1]).format('YYYY-MM-DD'),
        weekdays: days.map(Number),
        times,
        durationMin: duration,
        seats: Number(seats),
      },
      {
        onSuccess: (r) => notifySuccess(`${r.added} slot${r.added === 1 ? '' : 's'} added${r.skipped ? `, ${r.skipped} already there` : ''}.`),
        onError: (e) => notifyError(e),
      },
    );

  return (
    <Modal opened onClose={onClose} size="xl" title={<Text fw={600}>Slots at {centre.name}</Text>}>
      <Stack>
        {admin && (
          <Card withBorder padding="md">
            <Text fw={600} size="sm" mb="sm">
              Add slots
            </Text>
            <Stack gap="sm">
              <Group gap="sm" align="flex-end" wrap="wrap">
                <DatePickerInput
                  type="range"
                  label="Between"
                  placeholder="Choose dates"
                  value={range}
                  onChange={(x) => setRange(x as [string | null, string | null])}
                  minDate={dayjs().format('YYYY-MM-DD')}
                  maxDate={dayjs().add(3, 'month').format('YYYY-MM-DD')}
                  allowSingleDateInRange
                  w={250}
                />
                <TagsInput
                  label="Start times (24-hour)"
                  placeholder="e.g. 10:00, Enter"
                  value={times}
                  onChange={setTimes}
                  splitChars={[',', ' ']}
                  error={badTime ? `${badTime} isn't a 24-hour time` : null}
                  w={240}
                />
                <NumberInput label="Seats" min={1} max={20} value={seats} onChange={setSeats} w={90} />
              </Group>
              <div>
                <Text size="sm" fw={500} mb={4}>
                  On
                </Text>
                <Chip.Group multiple value={days} onChange={setDays}>
                  <Group gap={6}>
                    {WEEKDAYS.map((d) => (
                      <Chip key={d.value} value={d.value} size="xs">
                        {d.label}
                      </Chip>
                    ))}
                  </Group>
                </Chip.Group>
              </div>
              <Group justify="space-between" align="flex-end">
                <DurationInput value={duration} onChange={setDuration} />
                <Button onClick={submit} disabled={!ok} loading={add.isPending} leftSection={<IconCalendarPlus size={16} />}>
                  Add slots
                </Button>
              </Group>
            </Stack>
          </Card>
        )}
        {isLoading ? (
          <Loading />
        ) : !slots || slots.length === 0 ? (
          <Text size="sm" c="dimmed">
            No upcoming slots.
          </Text>
        ) : (
          <Table.ScrollContainer minWidth={480} mah={360}>
            <Table stickyHeader verticalSpacing={6}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>When</Table.Th>
                  <Table.Th ta="right">Seats</Table.Th>
                  <Table.Th ta="right">Booked</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {slots.map((s) => (
                  <Table.Tr key={s.id}>
                    <Table.Td>
                      <Text size="sm">
                        {dateTimeIn(s.startAt, centre.timeZone)} – {dayjs(s.endAt).tz(centre.timeZone).format('HH:mm')}
                      </Text>
                    </Table.Td>
                    <Table.Td ta="right">{s.seats}</Table.Td>
                    <Table.Td ta="right">{s.booked}</Table.Td>
                    <Table.Td ta="right" w={50}>
                      {admin && (
                        <Tooltip label={s.booked > 0 ? 'Someone is booked in this slot' : 'Remove slot'}>
                          <ActionIcon
                            variant="subtle"
                            color="red"
                            disabled={s.booked > 0}
                            aria-label="Remove slot"
                            onClick={() => remove.mutate({ centreId: centre.id, slotId: s.id }, { onError: (e) => notifyError(e) })}
                          >
                            <IconTrash size={15} />
                          </ActionIcon>
                        </Tooltip>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Stack>
    </Modal>
  );
}
