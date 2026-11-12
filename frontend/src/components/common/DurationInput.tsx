import { Group, Input, Select } from '@mantine/core';

const HOURS = Array.from({ length: 9 }, (_, h) => ({ value: String(h), label: h === 1 ? '1 hour' : `${h} hours` }));
const MINUTES = [0, 15, 30, 45].map((m) => ({ value: String(m), label: `${m} min` }));

/** A length in hours and minutes (15 minutes to 8 hours), stored as minutes. */
export function DurationInput({ label = 'Length', value, onChange, description, error }: {
  label?: string;
  value: number;
  onChange: (minutes: number) => void;
  description?: string;
  error?: string | null;
}) {
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  const set = (h: number, m: number) => onChange(Math.min(480, Math.max(15, h * 60 + m)));
  return (
    <Input.Wrapper label={label} description={description} error={error}>
      <Group gap="xs" wrap="nowrap" mt={description ? 4 : 0}>
        <Select
          aria-label={`${label}: hours`}
          data={HOURS}
          value={String(hours)}
          onChange={(v) => set(Number(v ?? 0), minutes)}
          allowDeselect={false}
          w={110}
        />
        <Select
          aria-label={`${label}: minutes`}
          data={MINUTES}
          value={String(minutes - (minutes % 15))}
          onChange={(v) => set(hours, Number(v ?? 0))}
          allowDeselect={false}
          w={100}
        />
      </Group>
    </Input.Wrapper>
  );
}

/** "1 h 30 min", "45 min". */
export function formatDuration(minutes: number | null | undefined) {
  if (!minutes) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}
