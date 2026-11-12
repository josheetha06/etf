import { ActionIcon, Button, Group, Stack, Text, TextInput, Tooltip } from '@mantine/core';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import type { MemberInput } from '../../api/types';

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** A panel member row is complete when it has an employee ID, a name and a valid email. */
export function memberProblem(member: MemberInput, all: MemberInput[]): string | null {
  if (!member.employeeId.trim() || !member.fullName.trim() || !member.email.trim()) return 'Add the employee ID, name and email.';
  if (!EMAIL.test(member.email.trim())) return 'Check the email address.';
  const same = all.filter((m) => m.employeeId.trim().toUpperCase() === member.employeeId.trim().toUpperCase());
  if (same.length > 1) return 'This employee ID is on the panel twice.';
  return null;
}

export function panelReady(members: MemberInput[]) {
  return members.length > 0 && members.every((m) => !memberProblem(m, members));
}

export const emptyMember = (): MemberInput => ({ employeeId: '', fullName: '', email: '' });

/**
 * The panel for one interview, typed in by HR: employee ID, name and email (all required). Each member is emailed
 * the interview code; they sign in with the panel login, their employee ID and the code to send their review.
 */
export function PanelMembersEditor({ value, onChange, locked = [] }: {
  value: MemberInput[];
  onChange: (value: MemberInput[]) => void;
  /** Employee IDs that can't be removed (their review is already in). */
  locked?: string[];
}) {
  const update = (index: number, patch: Partial<MemberInput>) =>
    onChange(value.map((m, i) => (i === index ? { ...m, ...patch } : m)));
  return (
    <Stack gap="xs">
      <Group justify="space-between" align="flex-end">
        <div>
          <Text size="sm" fw={500}>
            Panel
          </Text>
          <Text size="xs" c="dimmed">
            Each member gets the interview code by email and uses it with their employee ID to send their review.
          </Text>
        </div>
      </Group>
      {value.map((member, index) => {
        const touched = member.employeeId || member.fullName || member.email;
        const problem = touched ? memberProblem(member, value) : null;
        const isLocked = locked.some((id) => id.toUpperCase() === member.employeeId.trim().toUpperCase());
        return (
          <Stack key={index} gap={2}>
            <Group gap="xs" wrap="nowrap" align="flex-start">
              <TextInput
                aria-label="Employee ID"
                placeholder="Employee ID"
                value={member.employeeId}
                onChange={(e) => update(index, { employeeId: e.currentTarget.value })}
                w={130}
                disabled={isLocked}
              />
              <TextInput
                aria-label="Name"
                placeholder="Full name"
                value={member.fullName}
                onChange={(e) => update(index, { fullName: e.currentTarget.value })}
                style={{ flex: 1, minWidth: 0 }}
              />
              <TextInput
                aria-label="Email"
                placeholder="name@company.com"
                type="email"
                value={member.email}
                onChange={(e) => update(index, { email: e.currentTarget.value })}
                style={{ flex: 1.3, minWidth: 0 }}
              />
              <Tooltip label={isLocked ? 'Their review is already in' : 'Remove'}>
                <ActionIcon
                  variant="subtle"
                  color="gray"
                  size="lg"
                  aria-label="Remove panel member"
                  disabled={isLocked || value.length === 1}
                  onClick={() => onChange(value.filter((_, i) => i !== index))}
                >
                  <IconTrash size={16} />
                </ActionIcon>
              </Tooltip>
            </Group>
            {problem && (
              <Text size="xs" c="red">
                {problem}
              </Text>
            )}
          </Stack>
        );
      })}
      <Group>
        <Button
          variant="subtle"
          size="xs"
          leftSection={<IconPlus size={14} />}
          onClick={() => onChange([...value, emptyMember()])}
          disabled={value.length >= 12}
        >
          Add panel member
        </Button>
      </Group>
    </Stack>
  );
}
