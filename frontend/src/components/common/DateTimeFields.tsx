import { Group, Text } from '@mantine/core';
import { DatePickerInput, TimePicker } from '@mantine/dates';
import { IconCalendar, IconClock } from '@tabler/icons-react';
import { dayjs } from '../../lib/format';

const DAY = 'YYYY-MM-DD';

/**
 * A date and a time as two separate fields, so the calendar is closed while the time is chosen. The value is an
 * instant (ISO string); the fields show it in the given time zone.
 */
export function DateTimeFields({ value, onChange, zone, dateLabel = 'Date', timeLabel = 'Time', description, minDate,
  highlightDates, required = false, size = 'sm', defaultTime = '10:00', disabled = false, compact = false }: {
  value: string | null;
  onChange: (value: string | null) => void;
  zone: string;
  dateLabel?: string;
  timeLabel?: string;
  description?: string;
  minDate?: Date | string;
  /** Dates to show in bold (for example the days a candidate said they're free). */
  highlightDates?: string[];
  required?: boolean;
  size?: 'xs' | 'sm' | 'md';
  defaultTime?: string;
  disabled?: boolean;
  /** No labels: for tables. */
  compact?: boolean;
}) {
  const local = value ? dayjs(value).tz(zone) : null;
  const date = local ? local.format(DAY) : null;
  const time = local ? local.format('HH:mm') : '';

  const combine = (d: string | null, t: string) => {
    if (!d) {
      onChange(null);
      return;
    }
    const hhmm = /^\d{1,2}:\d{2}/.test(t) ? t.slice(0, 5) : defaultTime;
    onChange(dayjs.tz(`${d} ${hhmm}`, 'YYYY-MM-DD HH:mm', zone).toISOString());
  };

  return (
    <div>
      <Group gap="xs" wrap="nowrap" align="flex-start">
        <DatePickerInput
          label={compact ? undefined : dateLabel}
          aria-label={dateLabel}
          placeholder="Pick a day"
          valueFormat="ddd D MMM YYYY"
          value={date}
          onChange={(d) => combine(d ? dayjs(d).format(DAY) : null, time)}
          minDate={minDate}
          leftSection={<IconCalendar size={16} />}
          getDayProps={(day) =>
            highlightDates?.includes(String(day).slice(0, 10))
              ? { style: { fontWeight: 700, background: 'var(--mantine-color-ett-0)', color: 'var(--mantine-color-ett-9)' } }
              : {}
          }
          required={required}
          size={size}
          disabled={disabled}
          style={{ flex: '1 1 170px', minWidth: 0 }}
        />
        <TimePicker
          label={compact ? undefined : timeLabel}
          aria-label={timeLabel}
          format="24h"
          withDropdown
          minutesStep={15}
          value={time}
          onChange={(t) => combine(date, t)}
          leftSection={<IconClock size={16} />}
          disabled={disabled || !date}
          required={required}
          size={size}
          popoverProps={{ position: 'bottom-start' }}
          style={{ flex: '0 0 120px' }}
        />
      </Group>
      {description && !compact && (
        <Text size="xs" c="dimmed" mt={4}>
          {description}
        </Text>
      )}
    </div>
  );
}
