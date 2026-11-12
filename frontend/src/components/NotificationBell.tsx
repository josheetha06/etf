import { ActionIcon, Badge, Button, Divider, Group, Indicator, Popover, ScrollArea, Stack, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconBell,
  IconCalendarCheck,
  IconCalendarQuestion,
  IconClockExclamation,
  IconFileCheck,
  IconFiles,
  IconUsersGroup,
} from '@tabler/icons-react';
import { useNavigate } from 'react-router';
import { useNotificationCount, useNotificationRead, useNotifications } from '../api/hooks';
import type { NotificationView } from '../api/types';
import { relative } from '../lib/format';

const ICONS: Record<string, typeof IconBell> = {
  cv_batch_done: IconFiles,
  cv_overdue: IconClockExclamation,
  report_ready: IconFileCheck,
  reschedule_requested: IconCalendarQuestion,
  interview_accepted: IconCalendarCheck,
  no_reply: IconClockExclamation,
  panel_declined: IconUsersGroup,
  panel_invited: IconUsersGroup,
  feedback_due: IconClockExclamation,
};

const COLORS: Record<string, string> = {
  report_ready: 'ett',
  reschedule_requested: 'orange',
  no_reply: 'red',
  cv_overdue: 'red',
  panel_declined: 'red',
  feedback_due: 'orange',
};

export function NotificationBell() {
  const [opened, { toggle, close }] = useDisclosure(false);
  const { data: count } = useNotificationCount();
  const { data: items } = useNotifications(opened);
  const read = useNotificationRead();
  const navigate = useNavigate();
  const unread = count?.unread ?? 0;

  const open = (item: NotificationView) => {
    if (!item.readAt) read.mutate(item.id);
    close();
    if (item.link) navigate(item.link);
  };

  return (
    <Popover opened={opened} onChange={(value) => (value ? undefined : close())} position="bottom-end" width={380} shadow="lg" withinPortal>
      <Popover.Target>
        <Indicator label={unread > 99 ? '99+' : unread} size={18} color="red" disabled={unread === 0} offset={6}>
          <ActionIcon variant="subtle" color="gray" size="lg" radius="xl" onClick={toggle} aria-label={`Notifications, ${unread} unread`}>
            <IconBell size={21} stroke={1.7} />
          </ActionIcon>
        </Indicator>
      </Popover.Target>
      <Popover.Dropdown p={0}>
        <Group justify="space-between" px="md" py="sm">
          <Text fw={600}>Notifications</Text>
          <Button variant="subtle" size="compact-xs" disabled={unread === 0} onClick={() => read.mutate('all')}>
            Mark all as read
          </Button>
        </Group>
        <Divider />
        <ScrollArea.Autosize mah={440}>
          {!items?.length && (
            <Text size="sm" c="dimmed" ta="center" py="xl">
              You're all caught up.
            </Text>
          )}
          <Stack gap={0}>
            {items?.map((item) => {
              const Icon = ICONS[item.kind] ?? IconBell;
              return (
                <UnstyledButton
                  key={item.id}
                  onClick={() => open(item)}
                  px="md"
                  py="sm"
                  className="notification-item"
                  data-unread={!item.readAt || undefined}
                >
                  <Group align="flex-start" wrap="nowrap" gap="sm">
                    <Badge circle size="lg" variant="light" color={COLORS[item.kind] ?? 'gray'} p={4} mt={2}>
                      <Icon size={14} />
                    </Badge>
                    <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                      <Text size="sm" fw={item.readAt ? 400 : 600} lh={1.35}>
                        {item.title}
                      </Text>
                      {item.body && (
                        <Text size="xs" c="dimmed" lineClamp={2}>
                          {item.body}
                        </Text>
                      )}
                      <Text size="xs" c="dimmed">
                        {relative(item.createdAt)}
                      </Text>
                    </Stack>
                  </Group>
                </UnstyledButton>
              );
            })}
          </Stack>
        </ScrollArea.Autosize>
      </Popover.Dropdown>
    </Popover>
  );
}
