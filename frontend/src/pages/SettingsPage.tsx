import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  FileButton,
  Grid,
  Group,
  Modal,
  NumberInput,
  Paper,
  Progress,
  ScrollArea,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Tabs,
  TagsInput,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import { useDebouncedValue, useMediaQuery } from '@mantine/hooks';
import { IconInfoCircle, IconPencil, IconPlus, IconSearch, IconTrash, IconUpload } from '@tabler/icons-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import {
  useAccuracy,
  useBranding,
  useDeleteSkill,
  useJobs,
  useLogo,
  useMe,
  usePreferences,
  useSaveThresholds,
  useSavePreferences,
  useSaveSkill,
  useSkills,
  useThresholds,
} from '../api/hooks';
import type { SkillView, Thresholds } from '../api/types';
import { ClientLogo } from '../components/ClientLogo';
import { LeaveCoverSettings } from '../components/cover/LeaveCover';
import { TeamSettings } from '../components/team/TeamSettings';
import { PageHeader } from '../components/PageHeader';
import { Loading } from '../components/QueryState';
import { notifyError, notifySuccess } from '../lib/notify';
import { CentreSettings } from './settings/CentreSettings';
import { CompanyDetailsCard } from './settings/CompanySettings';
import { DecisionReasonSettings } from './settings/DecisionReasonSettings';
import { EmailTemplateSettings } from './settings/EmailTemplateSettings';
import { RoundSettings } from './settings/RoundSettings';

const SECTIONS: { group: string; tabs: { value: string; label: string; adminOnly?: boolean }[] }[] = [
  {
    group: 'Company',
    tabs: [
      { value: 'company', label: 'Company details' },
      { value: 'team', label: 'Team and logins' },
      { value: 'cover', label: 'Leave cover' },
    ],
  },
  {
    group: 'Hiring',
    tabs: [
      { value: 'rounds', label: 'Interview rounds' },
      { value: 'centres', label: 'Partner assessment centres' },
      { value: 'emails', label: 'Email wording' },
      { value: 'reasons', label: 'Decision reasons' },
      { value: 'shortlisting', label: 'Shortlisting' },
      { value: 'skills', label: 'Skills and other names' },
      { value: 'accuracy', label: 'AI accuracy' },
    ],
  },
  { group: 'You', tabs: [{ value: 'alerts', label: 'My email alerts' }] },
];

const ALL_TABS = SECTIONS.flatMap((s) => s.tabs.map((t) => t.value));

export function SettingsPage() {
  const [params, setParams] = useSearchParams();
  const { data: me } = useMe();
  const admin = me?.role === 'admin';
  const wide = useMediaQuery('(min-width: 62em)');
  const requested = params.get('tab') === 'branding' ? 'company' : params.get('tab');
  const tab = requested && ALL_TABS.includes(requested) ? requested : 'company';
  return (
    <>
      <PageHeader
        title="Settings"
        subtitle={admin ? 'Set up your company, team and hiring process. Only HR admins can change these.' : 'Only HR admins can change most of these settings.'}
      />
      <Tabs
        value={tab}
        onChange={(v) => setParams({ tab: v ?? 'company' }, { replace: true })}
        keepMounted={false}
        orientation={wide ? 'vertical' : 'horizontal'}
        variant={wide ? 'pills' : 'default'}
        styles={wide ? { tab: { justifyContent: 'flex-start' }, tabLabel: { textAlign: 'left' } } : undefined}
      >
        {wide ? (
          <Tabs.List w={220} mr="xl" className="settings-nav">
            {SECTIONS.map((section) => (
              <Stack key={section.group} gap={2} mb="md">
                <Text size="xs" c="dimmed" fw={600} tt="uppercase" px="sm" mb={2} style={{ letterSpacing: 0.4 }}>
                  {section.group}
                </Text>
                {section.tabs.map((t) => (
                  <Tabs.Tab key={t.value} value={t.value}>
                    {t.label}
                  </Tabs.Tab>
                ))}
              </Stack>
            ))}
          </Tabs.List>
        ) : (
          <ScrollArea type="never" mb="lg">
            <Tabs.List style={{ flexWrap: 'nowrap' }}>
              {SECTIONS.flatMap((s) => s.tabs).map((t) => (
                <Tabs.Tab key={t.value} value={t.value} style={{ whiteSpace: 'nowrap' }}>
                  {t.label}
                </Tabs.Tab>
              ))}
            </Tabs.List>
          </ScrollArea>
        )}
        <Tabs.Panel value="company">
          <Stack gap="lg">
            <CompanyDetailsCard admin={admin} />
            <BrandingSettings admin={admin} />
          </Stack>
        </Tabs.Panel>
        <Tabs.Panel value="team">
          <TeamSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="cover">
          <LeaveCoverSettings />
        </Tabs.Panel>
        <Tabs.Panel value="rounds">
          <RoundSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="centres">
          <CentreSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="emails">
          <EmailTemplateSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="reasons">
          <DecisionReasonSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="shortlisting">
          <ShortlistingSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="skills">
          <SkillsSettings admin={admin} />
        </Tabs.Panel>
        <Tabs.Panel value="accuracy">
          <AccuracySettings />
        </Tabs.Panel>
        <Tabs.Panel value="alerts">
          <AlertSettings />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}

// ---------------------------------------------------------------- logo

function BrandingSettings({ admin }: { admin: boolean }) {
  const { data: branding } = useBranding();
  const { upload, remove } = useLogo();
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 7 }}>
        <Card>
          <Title order={4} className="section-title" mb={4}>
            Logo
          </Title>
          <Text size="sm" c="dimmed" mb="md">
            Shown at the top of every page for your team, and on the sign-in page.
          </Text>
          <Paper withBorder p="xl" radius="md" bg="gray.0" mb="md">
            <Group justify="center" mih={72}>
              {branding?.hasLogo ? (
                <ClientLogo branding={branding} height={64} />
              ) : (
                <Text c="dimmed" size="sm">
                  No logo yet. Your company name is shown instead.
                </Text>
              )}
            </Group>
          </Paper>
          {admin && (
            <Group>
              <FileButton
                accept="image/png,image/jpeg,image/webp"
                onChange={(file) =>
                  file &&
                  upload.mutate(file, {
                    onSuccess: () => notifySuccess('Logo updated.'),
                    onError: (e) => notifyError(e, "The logo wasn't saved"),
                  })
                }
              >
                {(props) => (
                  <Button {...props} leftSection={<IconUpload size={16} />} loading={upload.isPending}>
                    {branding?.hasLogo ? 'Replace logo' : 'Upload logo'}
                  </Button>
                )}
              </FileButton>
              {branding?.hasLogo && (
                <Button variant="subtle" color="red" loading={remove.isPending} onClick={() => remove.mutate(undefined, { onError: (e) => notifyError(e) })}>
                  Remove
                </Button>
              )}
            </Group>
          )}
        </Card>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 5 }}>
        <Alert icon={<IconInfoCircle size={18} />} variant="light" title="What works best">
          A PNG with a transparent background, wider than it is tall, at least 300 pixels wide. PNG, JPG or WebP up to 1 MB.
          SVG files aren’t accepted for security reasons.
        </Alert>
      </Grid.Col>
    </Grid>
  );
}

// ---------------------------------------------------------------- shortlisting rules

function ShortlistingSettings({ admin }: { admin: boolean }) {
  const { data } = useThresholds();
  if (!data) return <Loading />;
  return <ThresholdsForm admin={admin} initial={data} />;
}

function ThresholdsForm({ admin, initial }: { admin: boolean; initial: Thresholds }) {
  const save = useSaveThresholds();
  const [value, setValue] = useState<Thresholds>(initial);
  const valid = value.reserveCutoff < value.shortlistCutoff;
  const field = (key: keyof Thresholds, label: string, description: string, suffix: string, min: number, max: number) => (
    <NumberInput
      label={label}
      description={description}
      suffix={suffix}
      min={min}
      max={max}
      value={value[key]}
      onChange={(v) => setValue({ ...value, [key]: Number(v) || min })}
      disabled={!admin}
    />
  );
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 7 }}>
        <Card>
          <Stack>
            <Title order={4} className="section-title">
              Cut-offs
            </Title>
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              {field('shortlistCutoff', 'Shortlist from', 'And every must-have shows up in work', ' pts', 1, 100)}
              {field('reserveCutoff', 'Reserve from', 'Below this: not recommended', ' pts', 0, 99)}
            </SimpleGrid>
            <Progress.Root size={22} radius="md">
              <Progress.Section value={value.reserveCutoff} color="gray.4">
                <Progress.Label>Not recommended</Progress.Label>
              </Progress.Section>
              <Progress.Section value={Math.max(0, value.shortlistCutoff - value.reserveCutoff)} color="yellow.5">
                <Progress.Label>Reserve</Progress.Label>
              </Progress.Section>
              <Progress.Section value={Math.max(0, 100 - value.shortlistCutoff)} color="ett.6">
                <Progress.Label>Shortlist</Progress.Label>
              </Progress.Section>
            </Progress.Root>
            {!valid && (
              <Text size="sm" c="red">
                The reserve cut-off must be lower than the shortlist cut-off.
              </Text>
            )}
            <Title order={4} className="section-title" mt="md">
              Points to check on a CV
            </Title>
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              {field('gapFlagMonths', 'Gap between jobs longer than', 'Raised as a point to check', ' months', 1, 60)}
              {field('shortJobMonths', 'Several jobs shorter than', 'Raised when three or more are this short', ' months', 1, 60)}
            </SimpleGrid>
            {admin && (
              <Group justify="flex-end">
                <Button
                  disabled={!valid}
                  loading={save.isPending}
                  onClick={() => save.mutate(value, { onSuccess: () => notifySuccess('Saved. New CVs use these settings.'), onError: (e) => notifyError(e) })}
                >
                  Save
                </Button>
              </Group>
            )}
          </Stack>
        </Card>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 5 }}>
        <Alert icon={<IconInfoCircle size={18} />} variant="light" title="When changes apply">
          CVs scored from now on use the new cut-offs. Earlier results keep the cut-offs they were scored with, so a report
          never changes under you. Check the AI accuracy tab to see whether the cut-offs suit your team.
        </Alert>
      </Grid.Col>
    </Grid>
  );
}

// ---------------------------------------------------------------- skills

function SkillsSettings({ admin }: { admin: boolean }) {
  const [text, setText] = useState('');
  const [q] = useDebouncedValue(text.trim(), 250);
  const { data, isLoading } = useSkills(q);
  const remove = useDeleteSkill();
  const [editing, setEditing] = useState<SkillView | 'new' | null>(null);
  const custom = (data ?? []).filter((s) => s.custom);
  const builtIn = (data ?? []).filter((s) => !s.custom);
  return (
    <Stack>
      <Alert icon={<IconInfoCircle size={18} />} variant="light">
        The AI already knows hundreds of skills and their other names (for example “K8s” for Kubernetes). Add the names your
        industry or region uses, and CVs that use them will match.
      </Alert>
      <Group justify="space-between">
        <TextInput placeholder="Search skills" leftSection={<IconSearch size={16} />} value={text} onChange={(e) => setText(e.currentTarget.value)} w={300} />
        {admin && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing('new')}>
            Add a skill
          </Button>
        )}
      </Group>
      {isLoading && <Loading />}
      <Card>
        <Title order={5} className="section-title" mb="sm">
          Added by your company ({custom.length})
        </Title>
        {custom.length === 0 ? (
          <Text size="sm" c="dimmed">
            None yet.
          </Text>
        ) : (
          <Table>
            <Table.Tbody>
              {custom.map((s) => (
                <Table.Tr key={s.id}>
                  <Table.Td w={220}>
                    <Text fw={600} size="sm">
                      {s.skill}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Group gap={4}>
                      {s.otherNames.map((n) => (
                        <Badge key={n} variant="outline" color="gray" tt="none">
                          {n}
                        </Badge>
                      ))}
                    </Group>
                  </Table.Td>
                  <Table.Td w={90} ta="right">
                    {admin && (
                      <Group gap={4} justify="flex-end" wrap="nowrap">
                        <ActionIcon variant="subtle" color="gray" onClick={() => setEditing(s)} aria-label="Edit">
                          <IconPencil size={16} />
                        </ActionIcon>
                        <ActionIcon variant="subtle" color="red" onClick={() => s.id && remove.mutate(s.id, { onError: (e) => notifyError(e) })} aria-label="Delete">
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Group>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
      <Card>
        <Title order={5} className="section-title" mb="sm">
          Built in {q ? `matching “${q}”` : ''}
        </Title>
        <Table>
          <Table.Tbody>
            {builtIn.map((s) => (
              <Table.Tr key={s.skill}>
                <Table.Td w={220}>
                  <Text size="sm" fw={500}>
                    {s.skill}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {s.otherNames.join(', ') || '—'}
                  </Text>
                  {s.alsoCounts.length > 0 && (
                    <Tooltip label="Experience with these also counts as evidence for the skill" multiline w={260}>
                      <Text size="xs" c="dimmed">
                        Also counts: {s.alsoCounts.join(', ')}
                      </Text>
                    </Tooltip>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>
      {editing && <SkillModal skill={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function SkillModal({ skill, onClose }: { skill: SkillView | null; onClose: () => void }) {
  const save = useSaveSkill();
  const [name, setName] = useState(skill?.skill ?? '');
  const [others, setOthers] = useState<string[]>(skill?.otherNames ?? []);
  return (
    <Modal opened onClose={onClose} title={skill ? `Edit ${skill.skill}` : 'Add a skill'}>
      <Stack>
        <TextInput label="Skill" placeholder="e.g. a tool or technology" value={name} onChange={(e) => setName(e.currentTarget.value)} />
        <TagsInput
          label="Other names"
          description="Spellings, short forms and product names that mean the same thing. Press Enter after each."
          value={others}
          onChange={setOthers}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={!name.trim() || others.length === 0}
            loading={save.isPending}
            onClick={() =>
              save.mutate(
                { id: skill?.id, skill: name.trim(), otherNames: others },
                {
                  onSuccess: () => {
                    notifySuccess('Saved. New CVs will match these names.');
                    onClose();
                  },
                  onError: (e) => notifyError(e),
                },
              )
            }
          >
            Save
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

// ---------------------------------------------------------------- accuracy

const SUGGESTION_LABEL: Record<string, string> = { shortlist: 'Shortlist', review: 'Reserve', reject: 'Not recommended' };

function AccuracySettings() {
  const [jobId, setJobId] = useState<string | null>(null);
  const { data: jobs } = useJobs();
  const { data, isLoading } = useAccuracy(jobId ?? undefined);
  if (isLoading || !data) return <Loading />;
  const pct = (n: number, d: number) => (d ? `${Math.round((100 * n) / d)}%` : '—');
  return (
    <Stack>
      <Group justify="space-between">
        <Text size="sm" c="dimmed" maw={640}>
          How often your team agreed with the AI’s shortlisting suggestion, and how the candidates did in Round 1. Use it to
          tune the cut-offs.
        </Text>
        <Select
          placeholder="All jobs"
          data={(jobs ?? []).map((j) => ({ value: j.id, label: `${j.title} · ${j.jobRef}` }))}
          value={jobId}
          onChange={setJobId}
          clearable
          w={260}
        />
      </Group>
      <SimpleGrid cols={{ base: 2, md: 4 }}>
        <Stat label="CVs scored" value={String(data.screened)} />
        <Stat label="Decided by HR" value={String(data.decided)} />
        <Stat label="Agreed with the AI" value={data.agreementPct === null ? '—' : `${data.agreementPct}%`} />
        <Stat label="Current cut-offs" value={`${data.shortlistCutoff} / ${data.reserveCutoff}`} />
      </SimpleGrid>
      <Grid gap="lg">
        <Grid.Col span={{ base: 12, md: 6 }}>
          <Card>
            <Title order={5} className="section-title" mb="sm">
              By AI suggestion
            </Title>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>AI said</Table.Th>
                  <Table.Th ta="right">CVs</Table.Th>
                  <Table.Th ta="right">HR shortlisted</Table.Th>
                  <Table.Th ta="right">HR said not now</Table.Th>
                  <Table.Th ta="right">Passed Round 1</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {[...data.bySuggestion]
                  .sort((a, b) => ['shortlist', 'review', 'reject'].indexOf(a.suggestion) - ['shortlist', 'review', 'reject'].indexOf(b.suggestion))
                  .map((row) => (
                  <Table.Tr key={row.suggestion}>
                    <Table.Td>{SUGGESTION_LABEL[row.suggestion] ?? row.suggestion}</Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.screened}
                    </Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.hrShortlisted} <Text span c="dimmed" size="xs">({pct(row.hrShortlisted, row.screened)})</Text>
                    </Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.hrRejected}
                    </Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.passedRound1}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Card>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 6 }}>
          <Card>
            <Title order={5} className="section-title" mb="sm">
              By score
            </Title>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Score</Table.Th>
                  <Table.Th ta="right">CVs</Table.Th>
                  <Table.Th ta="right">HR shortlisted</Table.Th>
                  <Table.Th ta="right">Passed Round 1</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {data.byScore.map((row) => (
                  <Table.Tr key={row.band} bg={row.from >= data.shortlistCutoff - 9 && row.to >= data.shortlistCutoff && row.from <= data.shortlistCutoff ? 'ett.0' : undefined}>
                    <Table.Td>{row.band}</Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.screened}
                    </Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.hrShortlisted} <Text span c="dimmed" size="xs">({pct(row.hrShortlisted, row.screened)})</Text>
                    </Table.Td>
                    <Table.Td ta="right" className="tabular">
                      {row.passedRound1}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Card>
        </Grid.Col>
      </Grid>
    </Stack>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Card padding="md">
      <Text className="stat-label">{label}</Text>
      <Text fz={26} fw={700} c="navy.9" className="tabular">
        {value}
      </Text>
    </Card>
  );
}

// ---------------------------------------------------------------- my alerts

function AlertSettings() {
  const { data } = usePreferences();
  const save = useSavePreferences();
  return (
    <Card maw={620}>
      <Title order={4} className="section-title" mb={4}>
        Email me about notifications
      </Title>
      <Text size="sm" c="dimmed" mb="md">
        Reports ready to review, reschedule requests, missed replies and CV uploads. Everything also appears under the bell
        at the top of the page.
      </Text>
      <SegmentedControl
        value={data?.emailAlerts ?? 'instant'}
        onChange={(v) => save.mutate(v, { onSuccess: () => notifySuccess('Saved.'), onError: (e) => notifyError(e) })}
        data={[
          { value: 'instant', label: 'Straight away' },
          { value: 'daily', label: 'Once a day (8 am IST)' },
          { value: 'off', label: 'Off' },
        ]}
      />
    </Card>
  );
}
