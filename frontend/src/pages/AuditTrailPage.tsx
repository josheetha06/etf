import { Anchor, Badge, Button, Card, Group, SegmentedControl, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { useDebouncedValue } from '@mantine/hooks';
import { IconBriefcase, IconCalendarEvent, IconDownload, IconFilterOff, IconSearch, IconUser } from '@tabler/icons-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { query } from '../api/client';
import { useAudit, useJobs, usePeople } from '../api/hooks';
import type { AuditEntry } from '../api/types';
import { EntryChanges } from '../components/common/HistoryList';
import { PageHeader } from '../components/PageHeader';
import { Empty, ErrorBlock, Loading } from '../components/QueryState';
import { dayjs, INDIA } from '../lib/format';

const TODAY = dayjs().format('YYYY-MM-DD');

const TYPE_META = {
  job: { label: 'Job', icon: IconBriefcase, color: 'blue' },
  candidate: { label: 'Candidate', icon: IconUser, color: 'teal' },
  interview: { label: 'Interview', icon: IconCalendarEvent, color: 'violet' },
};

/** Every change to jobs, candidates and interviews: who made it, when, and exactly what changed. */
export function AuditTrailPage() {
  const [type, setType] = useState('all');
  const [person, setPerson] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [range, setRange] = useState<[string | null, string | null]>([null, null]);
  const [text, setText] = useState('');
  const [q] = useDebouncedValue(text.trim(), 300);
  const { data: jobs } = useJobs();
  const { data: people } = usePeople();

  const filter = {
    type: type === 'all' ? undefined : type,
    person: person ?? undefined,
    jobId: jobId ?? undefined,
    from: range[0] ? dayjs(range[0]).format('YYYY-MM-DD') : undefined,
    to: range[1] ? dayjs(range[1]).format('YYYY-MM-DD') : range[0] ? dayjs(range[0]).format('YYYY-MM-DD') : undefined,
    q: q || undefined,
    size: 50,
  };
  const { data, isLoading, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage, isFetching } = useAudit(filter);
  const items = data?.pages.flatMap((p) => p.items) ?? [];
  const filtered = type !== 'all' || person || jobId || range[0] || q;
  const csvUrl = `/api/audit.csv${query({ type: filter.type, person: filter.person, jobId: filter.jobId, from: filter.from, to: filter.to, q: filter.q })}`;

  return (
    <>
      <PageHeader
        title="Audit trail"
        subtitle="Every change to jobs, candidates and interviews: who made it, when, and what changed."
        actions={
          <Button component="a" href={csvUrl} variant="default" leftSection={<IconDownload size={16} />}>
            Download CSV
          </Button>
        }
      />
      <Card mb="lg" padding="md">
        <Stack gap="sm">
          <Group gap="sm" align="flex-end" wrap="wrap">
            <SegmentedControl
              value={type}
              onChange={setType}
              data={[
                { value: 'all', label: 'Everything' },
                { value: 'job', label: 'Jobs' },
                { value: 'candidate', label: 'Candidates' },
                { value: 'interview', label: 'Interviews' },
              ]}
            />
            <TextInput
              placeholder="Search job, candidate or person"
              leftSection={<IconSearch size={16} />}
              value={text}
              onChange={(e) => setText(e.currentTarget.value)}
              aria-label="Search the audit trail"
              style={{ flex: '1 1 220px' }}
            />
          </Group>
          <Group gap="sm" align="flex-end" wrap="wrap">
            <Select
              label="Changed by"
              placeholder="Anyone"
              data={(people ?? []).map((p) => p.fullName)}
              value={person}
              onChange={setPerson}
              clearable
              searchable
              w={200}
            />
            <Select
              label="Job"
              placeholder="All jobs"
              data={(jobs ?? []).map((j) => ({ value: j.id, label: `${j.title} · ${j.jobRef}` }))}
              value={jobId}
              onChange={setJobId}
              clearable
              searchable
              w={240}
            />
            <DatePickerInput
              type="range"
              label="Dates"
              placeholder="Any date"
              value={range}
              onChange={(v) => setRange(v as [string | null, string | null])}
              clearable
              allowSingleDateInRange
              maxDate={TODAY}
              w={240}
            />
            {filtered && (
              <Button
                variant="subtle"
                color="gray"
                size="sm"
                leftSection={<IconFilterOff size={14} />}
                onClick={() => {
                  setType('all');
                  setPerson(null);
                  setJobId(null);
                  setRange([null, null]);
                  setText('');
                }}
              >
                Clear filters
              </Button>
            )}
          </Group>
        </Stack>
      </Card>

      {isLoading && <Loading />}
      {error && <ErrorBlock error={error} onRetry={refetch} />}
      {data && items.length === 0 && (
        <Card>
          <Empty title={filtered ? 'No changes match' : 'No changes recorded yet'}>
            {filtered ? 'Try a wider date range or clear the filters.' : undefined}
          </Empty>
        </Card>
      )}
      {items.length > 0 && (
        <Card padding={0} style={{ opacity: isFetching && !isFetchingNextPage ? 0.75 : 1 }}>
          <Table.ScrollContainer minWidth={900}>
            <Table verticalSpacing="sm">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={120}>When</Table.Th>
                  <Table.Th w={170}>Who</Table.Th>
                  <Table.Th w={320}>What</Table.Th>
                  <Table.Th>Changes</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {items.map((entry) => (
                  <Row key={entry.id} entry={entry} />
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          <Group justify="space-between" p="md">
            <Text size="sm" c="dimmed">
              Showing {items.length} change{items.length === 1 ? '' : 's'}, newest first
            </Text>
            {hasNextPage && (
              <Button variant="default" size="xs" onClick={() => fetchNextPage()} loading={isFetchingNextPage}>
                Show older changes
              </Button>
            )}
          </Group>
        </Card>
      )}
    </>
  );
}

function Row({ entry }: { entry: AuditEntry }) {
  const meta = TYPE_META[entry.type];
  const Icon = meta.icon;
  return (
    <Table.Tr>
      <Table.Td>
        <Text size="sm" style={{ whiteSpace: 'nowrap' }}>
          {dayjs(entry.at).tz(INDIA).format('D MMM YYYY')}
        </Text>
        <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
          {dayjs(entry.at).tz(INDIA).format('HH:mm')} IST
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" fw={500}>
          {entry.who}
        </Text>
        {entry.actorKind && entry.actorKind !== 'hr_user' && (
          <Text size="xs" c="dimmed">
            {entry.actorKind === 'system' ? 'Automatic' : entry.actorKind === 'candidate' ? 'Candidate' : entry.actorKind === 'panel' ? 'Panel member' : 'ETT'}
          </Text>
        )}
      </Table.Td>
      <Table.Td>
        <Group gap={6} wrap="nowrap" align="flex-start">
          <Badge size="sm" variant="light" color={meta.color} leftSection={<Icon size={11} />} style={{ flexShrink: 0 }}>
            {meta.label}
          </Badge>
          <Stack gap={0} style={{ minWidth: 0 }}>
            <Text size="sm">{entry.action}</Text>
            {entry.recordName &&
              (entry.link ? (
                <Anchor component={Link} to={entry.link} size="xs">
                  {entry.recordName}
                </Anchor>
              ) : (
                <Text size="xs" c="dimmed">
                  {entry.recordName}
                </Text>
              ))}
          </Stack>
        </Group>
      </Table.Td>
      <Table.Td>
        <EntryChanges entry={entry} />
      </Table.Td>
    </Table.Tr>
  );
}
