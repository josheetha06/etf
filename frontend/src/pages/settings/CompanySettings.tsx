import { Button, Card, Group, Select, Stack, Text, Textarea, TextInput, Title } from '@mantine/core';
import { useState } from 'react';
import { useCompanyDetails, useSaveCompanyDetails } from '../../api/hooks';
import type { CompanyDetails } from '../../api/types';
import { Loading } from '../../components/QueryState';
import { notifyError, notifySuccess } from '../../lib/notify';

export const TIME_ZONES = [
  { value: 'Asia/Kolkata', label: 'India (IST)' },
  { value: 'Europe/London', label: 'UK (GMT/BST)' },
  { value: 'Asia/Dubai', label: 'UAE (GST)' },
  { value: 'Asia/Singapore', label: 'Singapore (SGT)' },
  { value: 'Europe/Berlin', label: 'Central Europe (CET)' },
  { value: 'America/New_York', label: 'US Eastern' },
  { value: 'America/Los_Angeles', label: 'US Pacific' },
  { value: 'Australia/Sydney', label: 'Australia Eastern' },
];

/** The company's name, address and time zone. Used in emails and for every date the portal shows. */
export function CompanyDetailsCard({ admin }: { admin: boolean }) {
  const { data } = useCompanyDetails();
  if (!data) return <Loading />;
  return <DetailsForm admin={admin} initial={data} />;
}

function DetailsForm({ admin, initial }: { admin: boolean; initial: CompanyDetails }) {
  const save = useSaveCompanyDetails();
  const [name, setName] = useState(initial.name);
  const [address, setAddress] = useState(initial.address ?? '');
  const [timeZone, setTimeZone] = useState(initial.timeZone);
  const changed = name !== initial.name || address !== (initial.address ?? '') || timeZone !== initial.timeZone;
  const zones = TIME_ZONES.some((z) => z.value === timeZone) ? TIME_ZONES : [...TIME_ZONES, { value: timeZone, label: timeZone }];
  const submit = () => {
    if (!name.trim() || save.isPending) return;
    save.mutate(
      { name: name.trim(), address: address.trim() || null, timeZone },
      { onSuccess: () => notifySuccess('Company details saved.'), onError: (e) => notifyError(e) },
    );
  };
  return (
    <Card>
      <Title order={4} className="section-title" mb={4}>
        Company details
      </Title>
      <Text size="sm" c="dimmed" mb="md">
        Used in emails to candidates and panel members, and for dates and deadlines across the portal.
      </Text>
      <Stack>
        <TextInput label="Company name" value={name} onChange={(e) => setName(e.currentTarget.value)} maxLength={120} disabled={!admin} required error={!name.trim() ? 'Add the company name' : null} />
        <Textarea
          label="Office address"
          description="Shown to candidates for in-person panel interviews when no other venue is given."
          autosize
          minRows={2}
          maxLength={500}
          value={address}
          onChange={(e) => setAddress(e.currentTarget.value)}
          disabled={!admin}
        />
        <Select
          label="Time zone"
          description="Target times, reminders and the Overview follow this zone."
          data={zones}
          value={timeZone}
          onChange={(v) => v && setTimeZone(v)}
          allowDeselect={false}
          disabled={!admin}
          maw={320}
        />
        {admin && (
          <Group justify="flex-end">
            <Button onClick={submit} disabled={!changed || !name.trim()} loading={save.isPending}>
              Save details
            </Button>
          </Group>
        )}
      </Stack>
    </Card>
  );
}
