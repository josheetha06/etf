import { Alert, Button, Center, Loader, Stack, Text, ThemeIcon } from '@mantine/core';
import { IconAlertTriangle, IconInbox } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { ApiError, errorReference } from '../api/client';

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <Center py={60}>
      <Stack align="center" gap="xs">
        <Loader />
        <Text size="sm" c="dimmed">
          {label}
        </Text>
      </Stack>
    </Center>
  );
}

export function ErrorBlock({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong.';
  const reference = errorReference(error);
  const notFound = error instanceof ApiError && error.status === 404;
  return (
    <Alert color={notFound ? 'gray' : 'red'} icon={<IconAlertTriangle size={18} />} title={notFound ? 'Not found' : "We couldn't load this"} my="md">
      <Text size="sm">{notFound ? "This doesn't exist, or you don't have access to it." : message}</Text>
      {reference && (
        <Text size="xs" c="dimmed" mt={4}>
          Reference {reference}
        </Text>
      )}
      {onRetry && !notFound && (
        <Button variant="light" color="red" size="xs" mt="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </Alert>
  );
}

export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <Center py={48}>
      <Stack align="center" gap="xs" maw={420} ta="center">
        <ThemeIcon size={48} radius="xl" variant="light" color="gray">
          <IconInbox size={24} />
        </ThemeIcon>
        <Text fw={600}>{title}</Text>
        {children && (
          <Text size="sm" c="dimmed">
            {children}
          </Text>
        )}
        {action}
      </Stack>
    </Center>
  );
}
