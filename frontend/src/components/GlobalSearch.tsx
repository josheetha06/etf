import { Group, Kbd, Popover, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { useDebouncedValue, useHotkeys } from '@mantine/hooks';
import { IconBriefcase, IconSearch, IconUser } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useCandidates, useJobs } from '../api/hooks';
import { stage } from '../lib/labels';

/** Search candidates (name, email, phone, ID) and jobs (title, job ID, location) from anywhere. */
export function GlobalSearch() {
  const [value, setValue] = useState('');
  const [focused, setFocused] = useState(false);
  const [q] = useDebouncedValue(value.trim(), 250);
  const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const active = q.length >= 2;
  const candidates = useCandidates(active ? { q, size: 5 } : { size: 0 });
  const jobs = useJobs(active ? q : '__none__');
  useHotkeys([['mod+K', () => input.current?.focus()]]);

  const go = (path: string) => {
    setValue('');
    input.current?.blur();
    navigate(path);
  };
  const people = active ? candidates.data?.items ?? [] : [];
  const roles = active ? (jobs.data ?? []).slice(0, 4) : [];

  return (
    <Popover opened={focused && active} position="bottom-end" width={420} shadow="lg" trapFocus={false} withinPortal>
      <Popover.Target>
        <TextInput
          ref={input}
          value={value}
          onChange={(event) => setValue(event.currentTarget.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setTimeout(() => setFocused(false), 150)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && value.trim()) go(`/candidates?q=${encodeURIComponent(value.trim())}`);
            if (event.key === 'Escape') input.current?.blur();
          }}
          placeholder="Search candidates or jobs"
          leftSection={<IconSearch size={16} />}
          rightSection={<Kbd size="xs">⌘K</Kbd>}
          rightSectionWidth={44}
          w={{ base: 160, sm: 240, lg: 300 }}
          visibleFrom="xs"
          aria-label="Search candidates or jobs"
        />
      </Popover.Target>
      <Popover.Dropdown p={6}>
        <Stack gap={2}>
          {people.length === 0 && roles.length === 0 && (
            <Text size="sm" c="dimmed" p="sm">
              No matches for “{q}”.
            </Text>
          )}
          {people.length > 0 && (
            <Text size="xs" c="dimmed" fw={600} px="sm" pt={4} tt="uppercase">
              Candidates
            </Text>
          )}
          {people.map((person) => (
            <UnstyledButton key={person.applicationId} className="search-item" onMouseDown={() => go(`/applications/${person.applicationId}`)}>
              <Group gap="sm" wrap="nowrap">
                <IconUser size={16} color="var(--mantine-color-dimmed)" />
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={500} truncate>
                    {person.candidateName} <Text span c="dimmed" size="xs">{person.candidateRef}</Text>
                  </Text>
                  <Text size="xs" c="dimmed" truncate>
                    {person.jobTitle} · {stage[person.stage]}
                  </Text>
                </div>
              </Group>
            </UnstyledButton>
          ))}
          {roles.length > 0 && (
            <Text size="xs" c="dimmed" fw={600} px="sm" pt={6} tt="uppercase">
              Jobs
            </Text>
          )}
          {roles.map((job) => (
            <UnstyledButton key={job.id} className="search-item" onMouseDown={() => go(`/jobs/${job.id}`)}>
              <Group gap="sm" wrap="nowrap">
                <IconBriefcase size={16} color="var(--mantine-color-dimmed)" />
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={500} truncate>
                    {job.title}
                  </Text>
                  <Text size="xs" c="dimmed" truncate>
                    {job.jobRef} · {job.location ?? 'No location'}
                  </Text>
                </div>
              </Group>
            </UnstyledButton>
          ))}
          {(candidates.data?.total ?? 0) > people.length && (
            <UnstyledButton className="search-item" onMouseDown={() => go(`/candidates?q=${encodeURIComponent(q)}`)}>
              <Text size="sm" c="ett.7" fw={500}>
                See all {candidates.data?.total} candidates
              </Text>
            </UnstyledButton>
          )}
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
