import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  CopyButton,
  Group,
  Menu,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import {
  IconCheck,
  IconCopy,
  IconDots,
  IconDownload,
  IconKey,
  IconPlus,
  IconShieldOff,
  IconTrash,
  IconUserCheck,
  IconUserOff,
  IconUserPlus,
} from '@tabler/icons-react';
import { useState } from 'react';
import {
  useAddTeam,
  useBranding,
  useChangeMember,
  useResetMemberPassword,
  useResetMemberTwoStep,
  useTeam,
} from '../../api/hooks';
import type { CreatedLogin, TeamMember } from '../../api/types';
import { istLong, relative } from '../../lib/format';
import { notifyError, notifySuccess } from '../../lib/notify';
import { Loading } from '../QueryState';

const ROLES: { value: TeamMember['role']; label: string; detail: string }[] = [
  { value: 'ADMIN', label: 'HR admin', detail: 'Every job, plus Settings, the team and leave cover for anyone' },
  { value: 'RECRUITER', label: 'Recruiter', detail: 'Their own jobs: CVs, decisions and booking interviews' },
  { value: 'HIRING_MANAGER', label: 'Hiring manager', detail: 'Jobs they manage: reads reports and makes the decisions' },
  { value: 'PANEL', label: 'Panel login', detail: 'One shared login for interview panels. Opens a single review with an employee ID and interview code' },
];
const ROLE_LABEL = {
  ...Object.fromEntries(ROLES.map((r) => [r.value, r.label])),
  INTERVIEWER: 'Interviewer (no longer used)',
} as Record<TeamMember['role'], string>;

/** Settings → Team: who can sign in to the portal, with which role. */
export function TeamSettings({ admin }: { admin: boolean }) {
  const { data, isLoading } = useTeam();
  const change = useChangeMember();
  const resetPassword = useResetMemberPassword();
  const resetTwoStep = useResetMemberTwoStep();
  const [adding, setAdding] = useState(false);
  const [created, setCreated] = useState<CreatedLogin[] | null>(null);
  if (isLoading || !data) return <Loading />;

  const run = (promise: Promise<unknown>, message: string) =>
    promise.then(() => notifySuccess(message)).catch((e) => notifyError(e));

  return (
    <Stack>
      <Group justify="space-between" align="flex-end">
        <Text size="sm" c="dimmed" maw={640}>
          Everyone who can sign in to your company's portal. New people get a one-time password and set up their own
          password and two-step sign-in the first time.
        </Text>
        {admin && (
          <Button leftSection={<IconUserPlus size={16} />} onClick={() => setAdding(true)}>
            Add people
          </Button>
        )}
      </Group>
      <Card padding={0}>
        <Table.ScrollContainer minWidth={820}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Name</Table.Th>
                <Table.Th>Role</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th>Last sign-in</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data.map((m) => (
                <Table.Tr key={m.id} opacity={m.status === 'DISABLED' ? 0.6 : 1}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {m.fullName}
                      {m.me && (
                        <Text span size="xs" c="dimmed" fw={400}>
                          {' '}
                          (you)
                        </Text>
                      )}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {m.email}
                    </Text>
                  </Table.Td>
                  <Table.Td w={200}>
                    {admin && !m.me ? (
                      <Select
                        size="xs"
                        data={ROLES.map((r) => ({ value: r.value, label: r.label }))}
                        value={m.role}
                        onChange={(role) => role && run(change.mutateAsync({ id: m.id, role: role as TeamMember['role'] }), `${m.fullName} is now ${ROLE_LABEL[role as TeamMember['role']].toLowerCase()}.`)}
                        allowDeselect={false}
                      />
                    ) : (
                      <Text size="sm">{ROLE_LABEL[m.role]}</Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Status member={m} />
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="dimmed" title={m.lastSignInAt ? istLong(m.lastSignInAt) : undefined}>
                      {m.lastSignInAt ? relative(m.lastSignInAt) : 'Not yet'}
                    </Text>
                  </Table.Td>
                  <Table.Td ta="right" w={60}>
                    {admin && !m.me && (
                      <Menu position="bottom-end" withinPortal shadow="md" width={240}>
                        <Menu.Target>
                          <ActionIcon variant="subtle" color="gray" aria-label={`Actions for ${m.fullName}`}>
                            <IconDots size={18} />
                          </ActionIcon>
                        </Menu.Target>
                        <Menu.Dropdown>
                          <Menu.Item
                            leftSection={<IconKey size={16} />}
                            onClick={() =>
                              resetPassword
                                .mutateAsync(m.id)
                                .then((login) => setCreated([login]))
                                .catch((e) => notifyError(e))
                            }
                          >
                            New one-time password
                          </Menu.Item>
                          {m.twoStepSetUp && (
                            <Menu.Item
                              leftSection={<IconShieldOff size={16} />}
                              onClick={() => run(resetTwoStep.mutateAsync(m.id), `${m.fullName} will scan a new QR code next time they sign in.`)}
                            >
                              Reset two-step sign-in (lost phone)
                            </Menu.Item>
                          )}
                          <Menu.Divider />
                          {m.status === 'DISABLED' ? (
                            <Menu.Item
                              leftSection={<IconUserCheck size={16} />}
                              onClick={() => run(change.mutateAsync({ id: m.id, active: true }), `${m.fullName} can sign in again.`)}
                            >
                              Turn access back on
                            </Menu.Item>
                          ) : (
                            <Menu.Item
                              color="red"
                              leftSection={<IconUserOff size={16} />}
                              onClick={() => run(change.mutateAsync({ id: m.id, active: false }), `${m.fullName} has been signed out and can't sign in.`)}
                            >
                              Turn access off
                            </Menu.Item>
                          )}
                        </Menu.Dropdown>
                      </Menu>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      </Card>
      <Text size="xs" c="dimmed">
        Turning access off signs the person out straight away. Their decisions and notes stay in the history.
      </Text>
      {adding && (
        <AddPeopleModal
          onClose={() => setAdding(false)}
          onCreated={(logins) => {
            setAdding(false);
            setCreated(logins);
          }}
        />
      )}
      {created && <CreatedLoginsModal logins={created} onClose={() => setCreated(null)} />}
    </Stack>
  );
}

function Status({ member }: { member: TeamMember }) {
  if (member.status === 'DISABLED') return <Badge color="gray">Access off</Badge>;
  if (member.status === 'INVITED' || member.mustChangePassword) {
    return (
      <Tooltip label="Hasn't signed in with their own password yet">
        <Badge color="orange" variant="light">
          Invited
        </Badge>
      </Tooltip>
    );
  }
  return (
    <Group gap={6}>
      <Badge color="ett" variant="light">
        Active
      </Badge>
      {!member.twoStepSetUp && member.role !== 'PANEL' && (
        <Text size="xs" c="dimmed">
          two-step not set up
        </Text>
      )}
    </Group>
  );
}

interface Row {
  key: number;
  fullName: string;
  email: string;
  role: TeamMember['role'];
}

function AddPeopleModal({ onClose, onCreated }: { onClose: () => void; onCreated: (logins: CreatedLogin[]) => void }) {
  const add = useAddTeam();
  const [rows, setRows] = useState<Row[]>([{ key: 1, fullName: '', email: '', role: 'RECRUITER' }]);
  const update = (key: number, patch: Partial<Row>) => setRows((current) => current.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const filled = rows.filter((r) => r.fullName.trim() || r.email.trim());
  const valid = filled.length > 0 && filled.every((r) => r.fullName.trim() && /^\S+@\S+\.\S+$/.test(r.email.trim()));
  return (
    <Modal opened onClose={onClose} size="xl" title={<Text fw={600}>Add people</Text>}>
      <Stack>
        {rows.map((row, index) => (
          <Group key={row.key} align="flex-end" gap="sm" wrap="nowrap">
            <TextInput
              label={index === 0 ? 'Full name' : undefined}
              placeholder="e.g. Jane Smith"
              value={row.fullName}
              onChange={(e) => update(row.key, { fullName: e.currentTarget.value })}
              style={{ flex: 1 }}
            />
            <TextInput
              label={index === 0 ? 'Work email' : undefined}
              placeholder="name@company.com"
              value={row.email}
              onChange={(e) => update(row.key, { email: e.currentTarget.value })}
              style={{ flex: 1.3 }}
            />
            <Select
              label={index === 0 ? 'Role' : undefined}
              data={ROLES.map((r) => ({ value: r.value, label: r.label }))}
              value={row.role}
              onChange={(role) => update(row.key, { role: (role as TeamMember['role']) ?? 'RECRUITER' })}
              allowDeselect={false}
              w={170}
            />
            <ActionIcon
              variant="subtle"
              color="gray"
              mb={4}
              disabled={rows.length === 1}
              onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}
              aria-label="Remove row"
            >
              <IconTrash size={16} />
            </ActionIcon>
          </Group>
        ))}
        <Group>
          <Button
            variant="subtle"
            leftSection={<IconPlus size={16} />}
            disabled={rows.length >= 25}
            onClick={() => setRows((current) => [...current, { key: Date.now(), fullName: '', email: '', role: current[current.length - 1]?.role ?? 'RECRUITER' }])}
          >
            Add another person
          </Button>
        </Group>
        <Stack gap={2}>
          {ROLES.map((r) => (
            <Text key={r.value} size="xs" c="dimmed">
              <b>{r.label}:</b> {r.detail}
            </Text>
          ))}
        </Stack>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={!valid}
            loading={add.isPending}
            onClick={() =>
              add.mutate(
                filled.map((r) => ({ fullName: r.fullName.trim(), email: r.email.trim(), role: r.role })),
                { onSuccess: onCreated, onError: (e) => notifyError(e) },
              )
            }
          >
            Create {filled.length > 1 ? `${filled.length} logins` : 'login'}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/** One-time passwords are shown here once. The admin passes them on in person or by phone. */
function CreatedLoginsModal({ logins, onClose }: { logins: CreatedLogin[]; onClose: () => void }) {
  const { data: branding } = useBranding();
  const signInAt = `${window.location.origin}/login`;
  const text = logins.map((l) => `${l.fullName}\t${l.email}\t${l.oneTimePassword}`).join('\n');
  const download = () => {
    const csv = ['Name,Email,Role,One-time password,Sign in at', ...logins.map((l) => [l.fullName, l.email, ROLE_LABEL[l.role], l.oneTimePassword, signInAt].map((v) => `"${v.replace(/"/g, '""')}"`).join(','))].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `${(branding?.companyName ?? 'portal').replace(/\W+/g, '-').toLowerCase()}-logins.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };
  return (
    <Modal opened onClose={onClose} size="xl" closeOnClickOutside={false} title={<Text fw={600}>{logins.length === 1 ? 'Login ready' : `${logins.length} logins ready`}</Text>}>
      <Stack>
        <Alert color="orange" variant="light">
          Copy or download these now: one-time passwords are shown only once. Give each person their password in
          person or by phone, not in the same email as the sign-in link. They choose their own password when they first
          sign in.
        </Alert>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Email</Table.Th>
              <Table.Th>One-time password</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {logins.map((l) => (
              <Table.Tr key={l.id}>
                <Table.Td>
                  <Text size="sm" fw={500}>
                    {l.fullName}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {ROLE_LABEL[l.role]}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{l.email}</Text>
                </Table.Td>
                <Table.Td>
                  <Group gap={6} wrap="nowrap">
                    <Text ff="monospace" fw={600}>
                      {l.oneTimePassword}
                    </Text>
                    <CopyButton value={l.oneTimePassword}>
                      {({ copied, copy }) => (
                        <ActionIcon variant="subtle" color={copied ? 'ett' : 'gray'} onClick={copy} aria-label="Copy password">
                          {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                        </ActionIcon>
                      )}
                    </CopyButton>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
        <Text size="sm" c="dimmed">
          Sign in at {signInAt}. Each person also gets an email with the link (without the password).
        </Text>
        <Group justify="space-between">
          <Group gap="xs">
            <CopyButton value={text}>
              {({ copied, copy }) => (
                <Button variant="default" leftSection={copied ? <IconCheck size={16} /> : <IconCopy size={16} />} onClick={copy}>
                  {copied ? 'Copied' : 'Copy all'}
                </Button>
              )}
            </CopyButton>
            <Button variant="default" leftSection={<IconDownload size={16} />} onClick={download}>
              Download CSV
            </Button>
          </Group>
          <Button onClick={onClose}>Done</Button>
        </Group>
      </Stack>
    </Modal>
  );
}
