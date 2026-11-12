import { Box, Group, Paper, Text, Title } from '@mantine/core';
import { useBranding, useMe } from '../../api/hooks';
import { ClientLogo } from '../ClientLogo';
import { PasswordForm } from './PasswordForm';

/** Shown after signing in with a one-time password, before anything else in the portal. */
export function ChoosePasswordScreen() {
  const { data: me } = useMe();
  const { data: branding } = useBranding();
  return (
    <Box bg="var(--brand-surface)" mih="100vh" py={{ base: 40, md: 96 }} px="md">
      <Box maw={440} mx="auto">
        {branding && (
          <Group justify="center" mb="xl">
            <ClientLogo branding={branding} height={48} />
          </Group>
        )}
        <Paper withBorder shadow="sm" p={{ base: 'lg', sm: 32 }}>
          <Title order={2} className="page-title" mb={4}>
            Welcome, {me?.fullName.split(' ')[0]}
          </Title>
          <Text size="sm" c="dimmed" mb="md">
            {me?.email}
          </Text>
          <PasswordForm forced />
        </Paper>
      </Box>
    </Box>
  );
}
