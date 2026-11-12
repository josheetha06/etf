import { Alert, Button, List, PasswordInput, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useChangePassword } from '../../api/hooks';
import { notifyError, notifySuccess } from '../../lib/notify';

/** Choose a new password. After a one-time password the current one isn't asked for again. */
export function PasswordForm({ forced = false, onDone }: { forced?: boolean; onDone?: () => void }) {
  const change = useChangePassword();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [again, setAgain] = useState('');
  const long = next.length >= 12;
  const same = next === again;
  const ready = long && same && (forced || current.length > 0);
  return (
    <Stack>
      {forced && (
        <Alert variant="light" color="ett">
          You signed in with a one-time password from your HR admin. Choose your own password to continue.
        </Alert>
      )}
      {!forced && (
        <PasswordInput label="Current password" value={current} onChange={(e) => setCurrent(e.currentTarget.value)} autoComplete="current-password" />
      )}
      <PasswordInput
        label="New password"
        value={next}
        onChange={(e) => setNext(e.currentTarget.value)}
        autoComplete="new-password"
        error={next && !long ? 'At least 12 characters' : null}
        data-autofocus
      />
      <PasswordInput
        label="New password again"
        value={again}
        onChange={(e) => setAgain(e.currentTarget.value)}
        autoComplete="new-password"
        error={again && !same ? "The two passwords don't match" : null}
      />
      <List size="xs" c="dimmed" spacing={2}>
        <List.Item>At least 12 characters. A short sentence is easy to remember and hard to guess.</List.Item>
        <List.Item>Don't use your email address or a password from another site.</List.Item>
      </List>
      <Button
        disabled={!ready}
        loading={change.isPending}
        onClick={() =>
          change.mutate(
            { currentPassword: forced ? undefined : current, newPassword: next },
            {
              onSuccess: () => {
                notifySuccess('Your new password is set.');
                onDone?.();
              },
              onError: (e) => notifyError(e),
            },
          )
        }
      >
        Save new password
      </Button>
      {!forced && (
        <Text size="xs" c="dimmed">
          Forgotten your current password? Ask an HR admin to give you a one-time password.
        </Text>
      )}
    </Stack>
  );
}
