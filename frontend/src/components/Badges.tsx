import { Badge, Group, RingProgress, Text, Tooltip } from '@mantine/core';
import { IconRobot, IconUser } from '@tabler/icons-react';

export function ToneBadge({ tone, size = 'md' }: { tone: { label: string; color: string }; size?: 'xs' | 'sm' | 'md' | 'lg' }) {
  return (
    <Badge color={tone.color} size={size}>
      {tone.label}
    </Badge>
  );
}

/** Every message and action is labelled as coming from the AI agent or a person. */
export function ActorBadge({ ai }: { ai: boolean }) {
  return ai ? (
    <Tooltip label="Done by the AI agent">
      <Badge color="violet" variant="light" size="sm" leftSection={<IconRobot size={12} />}>
        AI
      </Badge>
    </Tooltip>
  ) : (
    <Badge color="gray" variant="light" size="sm" leftSection={<IconUser size={12} />}>
      Person
    </Badge>
  );
}

export function ScoreRing({ score, size = 46 }: { score: number | null; size?: number }) {
  if (score === null) {
    return (
      <Text c="dimmed" size="sm">
        —
      </Text>
    );
  }
  const color = score >= 70 ? 'ett' : score >= 50 ? 'yellow' : 'gray';
  return (
    <Group gap={6} wrap="nowrap">
      <RingProgress
        size={size}
        thickness={5}
        roundCaps
        sections={[{ value: score, color }]}
        label={
          <Text ta="center" fw={600} size="xs">
            {score}
          </Text>
        }
      />
    </Group>
  );
}

/** Marks a report that was generated as a sample, not by a real interviewer. */
export function SampleBadge({ show = true }: { show?: boolean }) {
  if (!show) return null;
  return (
    <Tooltip label="Sample report for trying out the flow. Not from a real interview.">
      <Badge color="orange" variant="filled" size="sm" radius="sm">
        SAMPLE
      </Badge>
    </Tooltip>
  );
}

/** A compact score, coloured against the shortlist and reserve cut-offs. */
export function ScorePill({ score, shortlist = 70, reserve = 50 }: { score: number | null; shortlist?: number; reserve?: number }) {
  if (score === null) {
    return (
      <Text c="dimmed" size="sm">
        —
      </Text>
    );
  }
  const color = score >= shortlist ? 'ett' : score >= reserve ? 'yellow' : 'gray';
  return (
    <Badge color={color} variant="light" size="lg" radius="sm" style={{ fontVariantNumeric: 'tabular-nums', minWidth: 44 }}>
      {score}
    </Badge>
  );
}
