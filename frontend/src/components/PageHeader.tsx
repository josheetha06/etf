import { Group, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  eyebrow?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, eyebrow, actions }: PageHeaderProps) {
  return (
    <Group justify="space-between" align="flex-end" mb="lg" wrap="wrap" gap="md">
      <Stack gap={4}>
        {eyebrow && (
          <Text size="sm" c="dimmed" component="div">
            {eyebrow}
          </Text>
        )}
        <Title order={1} className="page-title" fz={{ base: 26, sm: 32 }}>
          {title}
        </Title>
        {subtitle && (
          <Text c="dimmed" component="div">
            {subtitle}
          </Text>
        )}
      </Stack>
      {actions && <Group gap="sm">{actions}</Group>}
    </Group>
  );
}
