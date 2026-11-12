import {
  AppShell,
  Avatar,
  Badge,
  Burger,
  Divider,
  Group,
  Menu,
  Modal,
  NavLink,
  ScrollArea,
  SegmentedControl,
  Stack,
  Text,
  UnstyledButton,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconBriefcase,
  IconCalendarEvent,
  IconChevronDown,
  IconBeach,
  IconFileAnalytics,
  IconHistory,
  IconLayoutDashboard,
  IconKey,
  IconLogout,
  IconMail,
  IconSettings,
  IconUsers,
} from '@tabler/icons-react';
import { NavLink as RouterNavLink, Outlet, useLocation, useNavigate } from 'react-router';
import {
  useBranding,
  useLogout,
  useMe,
  usePreferences,
  useReportCounts,
  useSavePreferences,
  useUnreadCount,
} from '../api/hooks';
import { roleLabel } from '../lib/labels';
import { notifyError, notifySuccess } from '../lib/notify';
import { BrandMark } from './BrandMark';
import { GoingOnLeaveModal } from './cover/LeaveCover';
import { ClientLogo } from './ClientLogo';
import { GlobalSearch } from './GlobalSearch';
import { NotificationBell } from './NotificationBell';
import { PasswordForm } from './team/PasswordForm';

const NAV = [
  { to: '/', label: 'Overview', icon: IconLayoutDashboard, exact: true },
  { to: '/jobs', label: 'Jobs', icon: IconBriefcase },
  { to: '/candidates', label: 'Candidates', icon: IconUsers },
  { to: '/interviews', label: 'Interviews', icon: IconCalendarEvent },
  { to: '/reports', label: 'Reports', icon: IconFileAnalytics },
  { to: '/messages', label: 'Messages', icon: IconMail },
  { to: '/audit', label: 'Audit trail', icon: IconHistory },
  { to: '/settings', label: 'Settings', icon: IconSettings },
];

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const [passwordOpen, password] = useDisclosure(false);
  const [leaveOpen, leave] = useDisclosure(false);
  const { data: me } = useMe();
  const { data: branding } = useBranding();
  const { data: unread } = useUnreadCount();
  const { data: reportCounts } = useReportCounts();
  const { data: preferences } = usePreferences();
  const savePreferences = useSavePreferences();
  const logout = useLogout();
  const location = useLocation();
  const navigate = useNavigate();

  const signOut = () =>
    logout.mutate(undefined, {
      onSuccess: () => navigate('/login', { replace: true }),
      onError: (error) => notifyError(error),
    });

  const badgeFor = (to: string) => {
    if (to === '/messages' && (unread?.unread ?? 0) > 0) {
      return (
        <Badge size="sm" color="orange" variant="filled" circle>
          {unread?.unread}
        </Badge>
      );
    }
    if (to === '/reports' && (reportCounts?.total ?? 0) > 0) {
      return (
        <Badge size="sm" color="ett" variant="filled" radius="sm">
          {reportCounts?.total}
        </Badge>
      );
    }
    return undefined;
  };

  return (
    <AppShell
      header={{ height: 68 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding={{ base: 'md', md: 'xl' }}
    >
      <AppShell.Header className="app-header">
        <Group h="100%" px="md" justify="space-between" wrap="nowrap" gap="md">
          <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Open navigation" />
            <UnstyledButton component={RouterNavLink} to="/" aria-label="Overview">
              {branding ? (
                <ClientLogo branding={branding} height={36} />
              ) : (
                <Text fw={700} c="navy.9">
                  {me?.company.name}
                </Text>
              )}
            </UnstyledButton>
            <Divider orientation="vertical" visibleFrom="md" my={14} />
            <Text size="sm" c="dimmed" fw={500} visibleFrom="md" style={{ whiteSpace: 'nowrap' }}>
              Hiring portal
            </Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            <GlobalSearch />
            <NotificationBell />
            <Menu position="bottom-end" width={260} shadow="md">
              <Menu.Target>
                <UnstyledButton aria-label="Account menu">
                  <Group gap={8} wrap="nowrap">
                    <Avatar color="ett" radius="xl" size={34}>
                      {me?.fullName
                        .split(' ')
                        .map((part) => part[0])
                        .slice(0, 2)
                        .join('')}
                    </Avatar>
                    <Stack gap={0} visibleFrom="lg">
                      <Text size="sm" fw={600} lh={1.2}>
                        {me?.fullName}
                      </Text>
                      <Text size="xs" c="dimmed" lh={1.2}>
                        {me ? roleLabel[me.role] : ''}
                      </Text>
                    </Stack>
                    <IconChevronDown size={14} />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{me?.email}</Menu.Label>
                <Stack gap={6} px="sm" py={6}>
                  <Text size="xs" fw={500}>
                    Email me about notifications
                  </Text>
                  <SegmentedControl
                    size="xs"
                    fullWidth
                    value={preferences?.emailAlerts ?? 'instant'}
                    onChange={(value) =>
                      savePreferences.mutate(value, {
                        onSuccess: () => notifySuccess('Email alert setting saved.'),
                        onError: (error) => notifyError(error),
                      })
                    }
                    data={[
                      { value: 'instant', label: 'Straight away' },
                      { value: 'daily', label: 'Daily' },
                      { value: 'off', label: 'Off' },
                    ]}
                  />
                </Stack>
                <Menu.Divider />
                <Menu.Item leftSection={<IconBeach size={16} />} onClick={leave.open}>
                  Going on leave
                </Menu.Item>
                <Menu.Item leftSection={<IconKey size={16} />} onClick={password.open}>
                  Change password
                </Menu.Item>
                <Menu.Item leftSection={<IconLogout size={16} />} onClick={signOut}>
                  Sign out
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        <AppShell.Section grow component={ScrollArea}>
          <Stack gap={2}>
            {NAV.map((item) => {
              const active = item.exact ? location.pathname === item.to : location.pathname.startsWith(item.to);
              return (
                <NavLink
                  key={item.to}
                  component={RouterNavLink}
                  to={item.to}
                  label={item.label}
                  active={active}
                  onClick={close}
                  leftSection={<item.icon size={20} stroke={1.6} />}
                  rightSection={badgeFor(item.to)}
                  styles={{ root: { borderRadius: 8 }, label: { fontWeight: 500 } }}
                />
              );
            })}
          </Stack>
        </AppShell.Section>
        <AppShell.Section>
          <Divider mb="sm" />
          <Group gap={8} px="sm" pb="xs" wrap="nowrap">
            <BrandMark size={22} />
            <Text size="xs" c="dimmed" lh={1.3}>
              Powered by Epic Talent Trust. The AI recommends; your team decides.
            </Text>
          </Group>
        </AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
      {leaveOpen && <GoingOnLeaveModal opened={leaveOpen} onClose={leave.close} />}
      <Modal opened={passwordOpen} onClose={password.close} title="Change your password">
        {passwordOpen && <PasswordForm onDone={password.close} />}
      </Modal>
    </AppShell>
  );
}
