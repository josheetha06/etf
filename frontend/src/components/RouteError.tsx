import { Button, Center, Code, Group, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconAlertTriangle, IconRefresh } from '@tabler/icons-react';
import { useState } from 'react';
import { isRouteErrorResponse, Link, useRouteError } from 'react-router';

/** A new version was deployed while the page was open, so an old screen file can no longer be loaded. */
function isStaleBundle(error: unknown) {
  const text = error instanceof Error ? `${error.name} ${error.message}` : String(error);
  return /dynamically imported module|Importing a module script failed|Failed to fetch|ChunkLoadError/i.test(text);
}

/** Shown instead of a screen that failed to load or crashed, with a reference to quote to support. */
export function RouteError() {
  const error = useRouteError();
  const [reference] = useState(() => `UI-${Date.now().toString(36).slice(-6).toUpperCase()}`);
  const stale = isStaleBundle(error);
  const notFound = isRouteErrorResponse(error) && error.status === 404;

  if (!notFound && !stale) {
    console.error(`[${reference}]`, error);
  }

  return (
    <Center mih="70vh" p="md">
      <Stack align="center" ta="center" gap="sm" maw={460}>
        <ThemeIcon size={56} radius="xl" variant="light" color={stale ? 'blue' : 'orange'}>
          {stale ? <IconRefresh size={28} /> : <IconAlertTriangle size={28} />}
        </ThemeIcon>
        <Title order={2} fz={24}>
          {notFound ? "This page doesn't exist" : stale ? 'A new version is ready' : 'Something went wrong on this page'}
        </Title>
        <Text c="dimmed" size="sm">
          {notFound
            ? 'Check the address, or go back to your Overview.'
            : stale
              ? 'The portal was updated while you had it open. Reload to carry on; nothing you saved is lost.'
              : "Reloading usually fixes it. If it keeps happening, tell your HR admin and quote the reference below."}
        </Text>
        {!notFound && !stale && (
          <Text size="xs" c="dimmed">
            Reference <Code>{reference}</Code>
          </Text>
        )}
        <Group mt="sm">
          {!notFound && (
            <Button leftSection={<IconRefresh size={16} />} onClick={() => window.location.reload()}>
              Reload
            </Button>
          )}
          <Button variant="default" component={Link} to="/">
            Go to Overview
          </Button>
        </Group>
      </Stack>
    </Center>
  );
}
