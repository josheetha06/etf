import { Button, Group, TextInput } from '@mantine/core';
import { IconMapPin } from '@tabler/icons-react';
import { useState } from 'react';
import { useSetLocation } from '../../api/hooks';
import type { CandidateLocation } from '../../api/types';
import { notifyError, notifySuccess } from '../../lib/notify';

/** HR types the candidate's town, city or PIN code. It must be somewhere the portal can place on the map. */
export function LocationEditor({ applicationId, initial = '', onSaved, onCancel, autoFocus = false }: {
  applicationId: string;
  initial?: string;
  onSaved?: (location: CandidateLocation) => void;
  onCancel?: () => void;
  autoFocus?: boolean;
}) {
  const [text, setText] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const save = useSetLocation(applicationId);
  const submit = () => {
    if (!text.trim() || save.isPending) return;
    save.mutate(text.trim(), {
      onSuccess: (location) => {
        setError(null);
        notifySuccess(`Location saved: ${location.label ?? location.text}.`);
        onSaved?.(location);
      },
      onError: (e) => {
        if (e instanceof Error && 'code' in e && (e as { code: string }).code === 'location_not_found') {
          setError(e.message);
        } else {
          notifyError(e);
        }
      },
    });
  };
  return (
    <Group gap="xs" align="flex-start" wrap="nowrap">
      <TextInput
        aria-label="Candidate's location"
        placeholder="Town or city, or PIN code (e.g. Sivakasi 626123)"
        leftSection={<IconMapPin size={16} />}
        value={text}
        onChange={(e) => {
          setText(e.currentTarget.value);
          setError(null);
        }}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        error={error}
        maxLength={200}
        autoFocus={autoFocus}
        style={{ flex: 1, minWidth: 0 }}
      />
      <Button onClick={submit} loading={save.isPending} disabled={!text.trim()}>
        Save
      </Button>
      {onCancel && (
        <Button variant="default" onClick={onCancel}>
          Cancel
        </Button>
      )}
    </Group>
  );
}
