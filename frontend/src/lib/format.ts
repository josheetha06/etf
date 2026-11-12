import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(relativeTime);

export const INDIA = 'Asia/Kolkata';
export const UK = 'Europe/London';
const PICKER_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || INDIA;
}

/** The second zone shown next to a candidate's own: GMT/BST for India, IST for the UK. */
export function otherZone(zone: string): string {
  return zone === INDIA ? UK : INDIA;
}

export function zoneAbbreviation(zone: string, instant: string | Date = new Date()): string {
  if (zone === INDIA) return 'IST';
  if (zone === UK) return dayjs(instant).tz(UK).utcOffset() === 0 ? 'GMT' : 'BST';
  const part = new Intl.DateTimeFormat('en-GB', { timeZone: zone, timeZoneName: 'short' })
    .formatToParts(new Date(instant))
    .find((p) => p.type === 'timeZoneName');
  return part?.value ?? zone;
}

export function zoneLabel(zone: string): string {
  if (zone === INDIA) return 'India (IST)';
  if (zone === UK) return 'UK (GMT/BST)';
  return zone.replace('_', ' ');
}

/** "Thu 29 Oct, 11:00 IST" */
export function dateTimeIn(instant: string | null | undefined, zone: string): string {
  if (!instant) return '—';
  return `${dayjs(instant).tz(zone).format('ddd D MMM, HH:mm')} ${zoneAbbreviation(zone, instant)}`;
}

/** "05:30 GMT" */
export function timeIn(instant: string | null | undefined, zone: string): string {
  if (!instant) return '—';
  return `${dayjs(instant).tz(zone).format('HH:mm')} ${zoneAbbreviation(zone, instant)}`;
}

/** Times are shown in India Standard Time throughout the portal. */
export function ist(instant: string | null | undefined): string {
  return dateTimeIn(instant, INDIA);
}

/** "29 Oct 2026, 11:00 IST" for tables where the year matters. */
export function istLong(instant: string | null | undefined): string {
  if (!instant) return '—';
  return `${dayjs(instant).tz(INDIA).format('D MMM YYYY, HH:mm')} IST`;
}

export function istDay(instant: string | null | undefined): string {
  return instant ? dayjs(instant).tz(INDIA).format('ddd D MMM') : '—';
}

export function istTime(instant: string | null | undefined): string {
  return instant ? `${dayjs(instant).tz(INDIA).format('HH:mm')} IST` : '—';
}

/** "₹ 18 LPA" */
export function lpa(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `₹${Number.isInteger(value) ? value : value.toFixed(1)} LPA`;
}

export function years(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  const rounded = Math.round(value * 10) / 10;
  return `${rounded} ${rounded === 1 ? 'yr' : 'yrs'}`;
}

/** True once the given time has passed. */
export function hasStarted(instant: string | null | undefined): boolean {
  return !!instant && new Date(instant).getTime() < Date.now();
}

/** Now, for date pickers that shouldn't offer past days. */
export function now(): Date {
  return new Date();
}

export function dateOnly(value: string | null | undefined): string {
  return value ? dayjs(value).format('D MMM YYYY') : '—';
}

export function dayLabel(isoDate: string): string {
  return dayjs(isoDate).format('ddd D MMM');
}

export function relative(instant: string | null | undefined): string {
  return instant ? dayjs(instant).fromNow() : '';
}

/** Mantine pickers work with wall-clock strings; convert them to and from a given zone. */
export function toPickerValue(instant: string | null | undefined, zone: string): string | null {
  return instant ? dayjs(instant).tz(zone).format(PICKER_FORMAT) : null;
}

export function fromPickerValue(value: string | null, zone: string): string | null {
  return value ? dayjs.tz(value, PICKER_FORMAT, zone).toISOString() : null;
}

export function greeting(zone = browserZone()): string {
  const hour = dayjs().tz(zone).hour();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

export function todayLong(zone = browserZone()): string {
  return dayjs().tz(zone).format('dddd D MMMM');
}

export { dayjs };
