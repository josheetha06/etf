import { ActionIcon, Badge, Button, Card, Group, Modal, NumberInput, Select, Stack, Switch, TagsInput, Text, TextInput, Title, Tooltip } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { useDisclosure } from '@mantine/hooks';
import { IconPencil } from '@tabler/icons-react';
import { useState } from 'react';
import { useKeyFacts, useMe, useSaveKeyFacts } from '../../api/hooks';
import type { KeyFacts } from '../../api/types';
import { dateOnly, lpa, years } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';

/** The facts Indian recruiters ask first. Read from the CV, and HR can correct them. */
export function KeyFactsCard({ applicationId }: { applicationId: string }) {
  const { data: me } = useMe();
  const { data } = useKeyFacts(applicationId);
  const [opened, modal] = useDisclosure(false);
  // The AI interviewer asks for what the CV left out; those answers are labelled.
  const rows: [string, string, string?][] = data
    ? [
        [
          'Notice period',
          data.servingNotice ? `Serving notice${data.lastWorkingDay ? ` · last day ${dateOnly(data.lastWorkingDay)}` : ''}` : data.noticePeriodDays !== null ? `${data.noticePeriodDays} days` : '—',
          data.noticeSource === 'ai_interview' && data.noticePeriodDays !== null ? 'From AI interview' : undefined,
        ],
        ['Current CTC', lpa(data.currentCtcLpa)],
        ['Expected CTC', lpa(data.expectedCtcLpa), data.ctcSource === 'ai_interview' && data.expectedCtcLpa !== null ? 'From AI interview' : undefined],
        ['Current location', data.currentLocation ?? '—'],
        ['Preferred locations', data.preferredLocations.length ? data.preferredLocations.join(', ') : '—'],
        ['Open to relocate', data.willingToRelocate === null ? '—' : data.willingToRelocate ? 'Yes' : 'No'],
        ['Work mode', data.workMode ? data.workMode[0].toUpperCase() + data.workMode.slice(1) : '—'],
        ['Experience', `${years(data.totalExperienceYears)} total · ${years(data.relevantExperienceYears)} relevant`],
      ]
    : [];
  return (
    <Card>
      <Group justify="space-between" mb="xs">
        <Title order={5} className="section-title">
          Key facts
        </Title>
        {me?.canManageHiring && (
          <Tooltip label="Correct the key facts">
            <ActionIcon variant="subtle" color="gray" onClick={modal.open} aria-label="Edit key facts">
              <IconPencil size={16} />
            </ActionIcon>
          </Tooltip>
        )}
      </Group>
      <Stack gap={8}>
        {rows.map(([label, value, from]) => (
          <Group key={label} justify="space-between" wrap="nowrap" gap="sm" align="flex-start">
            <Text size="sm" c="dimmed">
              {label}
            </Text>
            <Stack gap={2} align="flex-end">
              <Text size="sm" ta="right" fw={value === '—' ? 400 : 500} c={value === '—' ? 'dimmed' : undefined}>
                {value}
              </Text>
              {from && (
                <Badge size="xs" variant="light" color="violet">
                  {from}
                </Badge>
              )}
            </Stack>
          </Group>
        ))}
      </Stack>
      <Text size="xs" c="dimmed" mt="sm">
        Read from the CV. If the CV leaves out the notice period or expected CTC, the AI interviewer asks.
      </Text>
      {data && opened && <EditKeyFacts onClose={modal.close} applicationId={applicationId} facts={data} />}
    </Card>
  );
}

function EditKeyFacts({ onClose, applicationId, facts }: { onClose: () => void; applicationId: string; facts: KeyFacts }) {
  const save = useSaveKeyFacts(applicationId);
  const [value, setValue] = useState<KeyFacts>(facts);
  const set = <K extends keyof KeyFacts>(key: K, v: KeyFacts[K]) => setValue((current) => ({ ...current, [key]: v }));
  return (
    <Modal opened onClose={onClose} title="Key facts" size="lg">
      <Stack>
        <Group grow>
          <NumberInput label="Notice period (days)" min={0} max={365} value={value.noticePeriodDays ?? ''} onChange={(v) => set('noticePeriodDays', v === '' ? null : Number(v))} />
          <Switch label="Serving notice" mt={28} checked={!!value.servingNotice} onChange={(e) => set('servingNotice', e.currentTarget.checked)} />
        </Group>
        {value.servingNotice && (
          <DatePickerInput label="Last working day" value={value.lastWorkingDay} onChange={(v) => set('lastWorkingDay', v)} clearable maw={240} />
        )}
        <Group grow>
          <NumberInput label="Current CTC (LPA)" min={0} decimalScale={1} value={value.currentCtcLpa ?? ''} onChange={(v) => set('currentCtcLpa', v === '' ? null : Number(v))} />
          <NumberInput label="Expected CTC (LPA)" min={0} decimalScale={1} value={value.expectedCtcLpa ?? ''} onChange={(v) => set('expectedCtcLpa', v === '' ? null : Number(v))} />
        </Group>
        <TextInput label="Current location" value={value.currentLocation ?? ''} onChange={(e) => set('currentLocation', e.currentTarget.value || null)} />
        <TagsInput label="Preferred locations" value={value.preferredLocations} onChange={(v) => set('preferredLocations', v)} />
        <Group grow>
          <Select
            label="Open to relocate"
            data={[{ value: 'yes', label: 'Yes' }, { value: 'no', label: 'No' }]}
            value={value.willingToRelocate === null ? null : value.willingToRelocate ? 'yes' : 'no'}
            onChange={(v) => set('willingToRelocate', v === null ? null : v === 'yes')}
            clearable
          />
          <Select
            label="Work mode"
            data={[
              { value: 'office', label: 'Office' },
              { value: 'hybrid', label: 'Hybrid' },
              { value: 'remote', label: 'Remote' },
              { value: 'any', label: 'Any' },
            ]}
            value={value.workMode}
            onChange={(v) => set('workMode', v)}
            clearable
          />
        </Group>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={save.isPending}
            onClick={() =>
              save.mutate(value, {
                onSuccess: () => {
                  notifySuccess('Key facts saved.');
                  onClose();
                },
                onError: (e) => notifyError(e),
              })
            }
          >
            Save
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
