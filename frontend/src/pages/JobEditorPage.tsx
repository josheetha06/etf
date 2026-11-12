import {
  Alert,
  Badge,
  Button,
  Card,
  Chip,
  FileButton,
  Grid,
  Group,
  List,
  Modal,
  NumberInput,
  Select,
  SimpleGrid,
  Stack,
  Stepper,
  Switch,
  TagsInput,
  Text,
  Textarea,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconAlertTriangle, IconCalculator, IconFileText, IconSparkles, IconUpload, IconX } from '@tabler/icons-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import {
  useCriteriaCheck,
  useDefaultPlan,
  useJob,
  useJobAction,
  useMe,
  usePeople,
  useReadDescription,
  useSaveJob,
  useSuggestSkills,
  useThresholds,
} from '../api/hooks';
import type { ContractType, CriteriaCheckItem, CriterionInput, CriterionKind, JobDetail, JobRequest } from '../api/types';
import { PlanPreview, RoundsBuilder, roundsReady, toDrafts, toInputs, type RoundDraft } from '../components/job/RoundsBuilder';
import { PageHeader } from '../components/PageHeader';
import { ErrorBlock, Loading } from '../components/QueryState';
import { contractType, roleLabel } from '../lib/labels';
import { notifyError, notifySuccess } from '../lib/notify';

interface Values {
  title: string;
  location: string;
  contractType: ContractType | null;
  openings: number | '';
  minExperienceYrs: number | '';
  maxNoticeDays: number | '';
  maxCtcLpa: number | '';
  acceptedLocations: string[];
  mustHave: string[];
  minYears: Record<string, number | ''>;
  niceToHave: string[];
  certifications: string[];
  rounds: RoundDraft[];
  aiShortlisting: boolean;
  description: string;
  descriptionFileName: string | null;
  ownerId: string | null;
  hiringManagerId: string | null;
}

const EMPTY: Values = {
  title: '',
  location: '',
  contractType: 'PERMANENT',
  openings: 1,
  minExperienceYrs: '',
  maxNoticeDays: '',
  maxCtcLpa: '',
  acceptedLocations: [],
  mustHave: [],
  minYears: {},
  niceToHave: [],
  certifications: [],
  rounds: [],
  aiShortlisting: true,
  description: '',
  descriptionFileName: null,
  ownerId: null,
  hiringManagerId: null,
};

function fromJob(job: JobDetail): Values {
  const byKind = (kind: CriterionKind) => job.criteria.filter((c) => c.kind === kind).map((c) => c.value);
  return {
    title: job.title,
    location: job.location ?? '',
    contractType: job.contractType,
    openings: job.openings ?? '',
    minExperienceYrs: job.minExperienceYrs ?? '',
    maxNoticeDays: job.maxNoticeDays ?? '',
    maxCtcLpa: job.maxCtcLpa ?? '',
    acceptedLocations: job.acceptedLocations,
    mustHave: byKind('MUST_HAVE'),
    minYears: Object.fromEntries(job.criteria.filter((c) => c.kind === 'MUST_HAVE' && c.minYears).map((c) => [c.value, c.minYears ?? ''])),
    niceToHave: byKind('NICE_TO_HAVE'),
    certifications: byKind('CERTIFICATION'),
    rounds: toDrafts(job.rounds),
    aiShortlisting: job.aiShortlisting,
    description: job.description ?? '',
    descriptionFileName: job.descriptionFileName,
    ownerId: job.owner?.id ?? null,
    hiringManagerId: job.hiringManager?.id ?? null,
  };
}

function criteriaOf(values: Values): CriterionInput[] {
  return [
    ...values.mustHave.map((value) => ({
      kind: 'MUST_HAVE' as const,
      value,
      minYears: values.minYears[value] === '' || values.minYears[value] === undefined ? null : Number(values.minYears[value]),
    })),
    ...values.niceToHave.map((value) => ({ kind: 'NICE_TO_HAVE' as const, value })),
    ...values.certifications.map((value) => ({ kind: 'CERTIFICATION' as const, value })),
  ];
}

function toRequest(values: Values, rescore = false): JobRequest {
  const num = (value: number | '') => (value === '' ? null : Number(value));
  return {
    title: values.title.trim(),
    location: values.location.trim() || null,
    contractType: values.contractType,
    openings: num(values.openings),
    minExperienceYrs: num(values.minExperienceYrs),
    criteria: criteriaOf(values),
    rounds: toInputs(values.rounds),
    maxNoticeDays: num(values.maxNoticeDays),
    maxCtcLpa: num(values.maxCtcLpa),
    acceptedLocations: values.acceptedLocations,
    rescore,
    aiShortlisting: values.aiShortlisting,
    description: values.description.trim() || null,
    descriptionFileName: values.description.trim() ? values.descriptionFileName : null,
    hiringManagerId: values.hiringManagerId,
    ownerId: values.ownerId,
  };
}

/**
 * A copy of another job: the same skills, rounds, job description, limits, AI switch and hiring manager, owned by
 * whoever copies it. Candidates and interviews stay with the original, and nothing is created until it's saved.
 */
function cloneOf(source: JobDetail): Values {
  return { ...fromJob(source), title: `Copy of ${source.title}`, ownerId: null };
}

/**
 * Create or edit a job in three steps: the job, its skills and shortlisting, and the interview rounds.
 * With ?from=<job id>, the new job starts as a copy of that job.
 */
export function JobEditorPage() {
  const { jobId } = useParams();
  const [params] = useSearchParams();
  const fromId = jobId ? undefined : (params.get('from') ?? undefined);
  const { data: job, isLoading, error, refetch } = useJob(jobId);
  const source = useJob(fromId);
  if (jobId && isLoading) return <Loading />;
  if (jobId && (error || !job)) return <ErrorBlock error={error} onRetry={refetch} />;
  if (fromId && source.isLoading) return <Loading />;
  if (fromId && (source.error || !source.data)) return <ErrorBlock error={source.error} onRetry={source.refetch} />;
  return <JobEditor key={job?.id ?? fromId ?? 'new'} job={job ?? null} copyOf={fromId ? (source.data ?? null) : null} />;
}

function JobEditor({ job, copyOf }: { job: JobDetail | null; copyOf: JobDetail | null }) {
  const navigate = useNavigate();
  const { data: me } = useMe();
  const save = useSaveJob(job?.id);
  const jobAction = useJobAction();
  const defaultPlan = useDefaultPlan();
  const [active, setActive] = useState(0);
  const [values, setValues] = useState<Values>(() => (job ? fromJob(job) : copyOf ? cloneOf(copyOf) : EMPTY));
  const [rescore, setRescore] = useState<string | null>(null);
  const set = <K extends keyof Values>(key: K, value: Values[K]) => setValues((current) => ({ ...current, [key]: value }));

  // A new job starts from the company's default round plan (a copy keeps its own rounds), and is owned by whoever
  // creates it.
  const seeded = useRef(false);
  useEffect(() => {
    if (job || seeded.current) return;
    if (copyOf) {
      if (!me?.id) return;
      seeded.current = true;
      setValues((current) => ({ ...current, ownerId: me.id }));
      return;
    }
    if (!defaultPlan.data) return;
    seeded.current = true;
    setValues((current) => ({ ...current, rounds: toDrafts(defaultPlan.data), ownerId: current.ownerId ?? me?.id ?? null }));
  }, [job, copyOf, defaultPlan.data, me?.id]);

  const [debounced] = useDebouncedValue(criteriaOf(values), 400);
  const check = useCriteriaCheck(debounced);
  const blocking = check.data?.blocking ?? false;

  const stepOk = [values.title.trim().length > 0, values.mustHave.length > 0 && !blocking, roundsReady(values.rounds)];
  const busy = save.isPending || jobAction.isPending;

  const submit = (publish: boolean, withRescore = false) => {
    if (busy) return;
    save.mutate(toRequest(values, withRescore), {
      onSuccess: (saved) => {
        const done = () => {
          notifySuccess(
            withRescore ? 'Saved. Every CV in this job is being re-scored with the new skills.' : publish ? 'The job is open for CVs.' : 'Saved.',
          );
          navigate(`/jobs/${saved.id}`);
        };
        if (publish && saved.status === 'DRAFT') {
          jobAction.mutate({ id: saved.id, action: 'publish' }, { onSuccess: done, onError: (e) => notifyError(e) });
        } else {
          done();
        }
      },
      onError: (e) => {
        if (e instanceof ApiError && e.code === 'rescore_needed') {
          setRescore(e.message);
        } else {
          notifyError(e);
        }
      },
    });
  };

  return (
    <>
      <PageHeader
        eyebrow={job ? `${job.jobRef} · editing` : copyOf ? `Copy of ${copyOf.jobRef}` : 'New job'}
        title={job ? job.title : copyOf ? 'Clone a job' : 'Create a job'}
        subtitle={
          copyOf
            ? `Everything from ${copyOf.title} is filled in: skills, rounds, job description, limits and the hiring manager. Candidates and interviews aren't copied, and nothing is created until you save.`
            : "Three steps: the job, the skills you're looking for, and the interview rounds."
        }
      />
      <Stepper
        active={active}
        onStepClick={(step) => (step <= active || stepOk.slice(0, step).every(Boolean)) && setActive(step)}
        mb="xl"
        size="sm"
      >
        <Stepper.Step label="The job" description="Details and description" />
        <Stepper.Step label="Skills" description="And AI shortlisting" />
        <Stepper.Step label="Interview rounds" description="Up to 8, in any order" />
      </Stepper>

      {active === 0 && <JobStep values={values} set={set} canChooseOwner={!!me?.seesAllJobs} />}
      {active === 1 && (
        <SkillsStep values={values} set={set} check={check.data?.items ?? []} checking={check.isFetching} job={job} />
      )}
      {active === 2 && (
        <RoundsStep values={values} set={set} lockedUpTo={job?.roundsLockedUpTo ?? 0} loading={!job && !copyOf && defaultPlan.isLoading} />
      )}

      <Group justify="space-between" mt="xl">
        <Button variant="default" onClick={() => (active === 0 ? navigate(-1) : setActive(active - 1))}>
          {active === 0 ? 'Cancel' : 'Back'}
        </Button>
        <Group>
          {!stepOk[active] && (
            <Text size="sm" c="dimmed" visibleFrom="xs">
              {active === 0
                ? 'Add a job title to continue'
                : active === 1
                  ? blocking
                    ? 'Fix the skills flagged in red'
                    : 'Add at least one must-have to continue'
                  : 'Add at least one round, and name each one'}
            </Text>
          )}
          {active < 2 ? (
            <Button onClick={() => setActive(active + 1)} disabled={!stepOk[active]}>
              Next
            </Button>
          ) : job ? (
            <Button onClick={() => submit(false)} loading={busy} disabled={!stepOk.every(Boolean)}>
              Save changes
            </Button>
          ) : (
            <>
              <Button variant="default" onClick={() => submit(false)} loading={busy} disabled={!stepOk.every(Boolean)}>
                Save as draft
              </Button>
              <Button onClick={() => submit(true)} loading={busy} disabled={!stepOk.every(Boolean)}>
                Publish job
              </Button>
            </>
          )}
        </Group>
      </Group>

      <Modal opened={!!rescore} onClose={() => setRescore(null)} title="Re-score this job's CVs?">
        <Stack>
          <Text size="sm">{rescore}</Text>
          <Text size="sm" c="dimmed">
            Decisions you've already made stay as they are. Candidates whose suggestion changes are easy to spot on the
            shortlist tab.
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setRescore(null)}>
              Keep the old skills
            </Button>
            <Button
              onClick={() => {
                setRescore(null);
                submit(false, true);
              }}
            >
              Save and re-score
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

type Setter = <K extends keyof Values>(key: K, value: Values[K]) => void;

function JobStep({ values, set, canChooseOwner }: { values: Values; set: Setter; canChooseOwner: boolean }) {
  const people = usePeople();
  const owners = (people.data ?? [])
    .filter((p) => p.role === 'admin' || p.role === 'recruiter')
    .map((p) => ({ value: p.id, label: `${p.fullName} · ${roleLabel[p.role]}` }));
  const managers = (people.data ?? []).map((p) => ({ value: p.id, label: `${p.fullName} · ${roleLabel[p.role]}` }));
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 7 }}>
        <Stack>
          <Card>
            <Stack>
              <TextInput
                label="Job title"
                placeholder="e.g. Cloud Engineer"
                required
                maxLength={150}
                value={values.title}
                onChange={(e) => set('title', e.currentTarget.value)}
                error={values.title.length > 0 && !values.title.trim() ? 'Add a job title' : null}
              />
              <TextInput
                label="Location"
                placeholder="e.g. Bengaluru (hybrid)"
                maxLength={150}
                value={values.location}
                onChange={(e) => set('location', e.currentTarget.value)}
              />
              <SimpleGrid cols={{ base: 1, sm: 3 }}>
                <Select
                  label="Contract"
                  data={Object.entries(contractType).map(([value, label]) => ({ value, label }))}
                  value={values.contractType}
                  onChange={(v) => set('contractType', v as ContractType)}
                />
                <NumberInput label="Openings" min={1} max={500} value={values.openings} onChange={(v) => set('openings', v === '' ? '' : Number(v))} />
                <NumberInput
                  label="Minimum experience"
                  suffix=" yrs"
                  min={0}
                  max={40}
                  value={values.minExperienceYrs}
                  onChange={(v) => set('minExperienceYrs', v === '' ? '' : Number(v))}
                />
              </SimpleGrid>
            </Stack>
          </Card>
          <DescriptionCard values={values} set={set} />
        </Stack>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 5 }}>
        <Stack>
          <Card>
            <Title order={5} className="section-title" mb={4}>
              Who's hiring
            </Title>
            <Text size="xs" c="dimmed" mb="md">
              The owner and the hiring manager see this job and get its notifications.
            </Text>
            <Stack>
              <Select
                label="Job owner"
                description={canChooseOwner ? 'An HR admin or recruiter' : 'Ask an HR admin to hand the job to someone else'}
                data={owners}
                value={values.ownerId}
                onChange={(v) => set('ownerId', v)}
                disabled={!canChooseOwner}
                searchable
                allowDeselect={false}
                placeholder="You"
              />
              <Select
                label="Hiring manager"
                description="Optional. They can review candidates and make round decisions."
                data={managers}
                value={values.hiringManagerId}
                onChange={(v) => set('hiringManagerId', v)}
                searchable
                clearable
                placeholder="Choose from your team"
              />
            </Stack>
          </Card>
          <Card>
            <Title order={5} className="section-title" mb={4}>
              Limits to check (optional)
            </Title>
            <Text size="xs" c="dimmed" mb="md">
              CVs that go past these get a point to check. They never lower the score.
            </Text>
            <Stack>
              <NumberInput label="Longest notice period" suffix=" days" min={0} max={365} placeholder="e.g. 60" value={values.maxNoticeDays} onChange={(v) => set('maxNoticeDays', v === '' ? '' : Number(v))} />
              <NumberInput label="CTC budget up to" suffix=" LPA" min={0} decimalScale={1} placeholder="e.g. 30" value={values.maxCtcLpa} onChange={(v) => set('maxCtcLpa', v === '' ? '' : Number(v))} />
              <TagsInput
                label="Locations that work"
                placeholder="Type a city and press Enter"
                value={values.acceptedLocations}
                onChange={(v) => set('acceptedLocations', v)}
              />
            </Stack>
          </Card>
        </Stack>
      </Grid.Col>
    </Grid>
  );
}

/** Paste the job description or upload it (PDF or Word). Skills are suggested from it on the next step. */
function DescriptionCard({ values, set }: { values: Values; set: Setter }) {
  const read = useReadDescription();
  const upload = (file: File | null) => {
    if (!file) return;
    read.mutate(file, {
      onSuccess: (result) => {
        set('description', result.text ?? '');
        set('descriptionFileName', result.fileName);
        notifySuccess('Job description read. Check the skills it suggests on the next step.');
      },
      onError: (e) => notifyError(e, "We couldn't read that file"),
    });
  };
  return (
    <Card>
      <Group justify="space-between" mb="xs" wrap="wrap" gap="xs">
        <div>
          <Title order={5} className="section-title">
            Job description
          </Title>
          <Text size="xs" c="dimmed">
            Paste it or upload a PDF or Word file. Panel members see it with their review form.
          </Text>
        </div>
        <FileButton onChange={upload} accept=".pdf,.doc,.docx,.txt,.rtf,.odt">
          {(props) => (
            <Button {...props} variant="default" size="xs" leftSection={<IconUpload size={14} />} loading={read.isPending}>
              Upload a file
            </Button>
          )}
        </FileButton>
      </Group>
      {values.descriptionFileName && values.description && (
        <Group gap={6} mb="xs">
          <Badge variant="light" color="gray" leftSection={<IconFileText size={12} />} style={{ textTransform: 'none' }}>
            {values.descriptionFileName}
          </Badge>
          <Tooltip label="Remove the description">
            <Button
              variant="subtle"
              color="gray"
              size="compact-xs"
              onClick={() => {
                set('description', '');
                set('descriptionFileName', null);
              }}
            >
              <IconX size={12} />
            </Button>
          </Tooltip>
        </Group>
      )}
      <Textarea
        aria-label="Job description"
        placeholder="Paste the job description here"
        autosize
        minRows={6}
        maxRows={16}
        maxLength={20000}
        value={values.description}
        onChange={(e) => set('description', e.currentTarget.value)}
      />
      <Text size="xs" c="dimmed" ta="right" mt={4}>
        {values.description.length.toLocaleString()} / 20,000
      </Text>
    </Card>
  );
}

const WEIGHTS = { MUST_HAVE: 50, NICE_TO_HAVE: 20, EXPERIENCE: 20, CERTIFICATION: 10 };

function SkillsStep({ values, set, check, checking, job }: {
  values: Values;
  set: Setter;
  check: CriteriaCheckItem[];
  checking: boolean;
  job: JobDetail | null;
}) {
  const turningOff = !!job && job.aiShortlisting && !values.aiShortlisting && job.status !== 'DRAFT';
  const warnings = check.filter((c) => c.warning);
  const list = (kind: CriterionKind, key: 'mustHave' | 'niceToHave' | 'certifications', label: string, placeholder: string, description: string) => (
    <TagsInput
      label={label}
      description={description}
      placeholder={placeholder}
      value={values[key]}
      onChange={(v) => set(key, v)}
      clearable
      splitChars={[',', ';']}
      maxTags={kind === 'MUST_HAVE' ? 12 : 15}
    />
  );

  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 7 }}>
        <Stack>
          <Card>
            <Switch
              size="md"
              checked={values.aiShortlisting}
              onChange={(e) => set('aiShortlisting', e.currentTarget.checked)}
              label={<Text fw={600}>AI shortlisting</Text>}
              description={
                values.aiShortlisting
                  ? 'CVs are read and scored on the skills below. You confirm who is shortlisted.'
                  : 'Off: every candidate you add goes straight to Round 1. The skills below are still used to score panel reviews.'
              }
            />
            {turningOff && (
              <Alert color="orange" variant="light" mt="md" icon={<IconAlertTriangle size={16} />}>
                <Text size="sm">Candidates still waiting for shortlisting on this job will move straight to Round 1 when you save.</Text>
              </Alert>
            )}
          </Card>
          <SuggestedSkills values={values} set={set} check={check} />
          <Card>
            <Stack>
              {list(
                'MUST_HAVE',
                'mustHave',
                'Must-haves',
                'e.g. a skill or tool, then Enter',
                values.aiShortlisting
                  ? 'To be shortlisted, each must show up in the candidate’s work, not only in a skills list.'
                  : 'Panel members score each must-have in their review.',
              )}
              {values.aiShortlisting && values.mustHave.length > 0 && (
                <Stack gap={6}>
                  <Text size="sm" fw={500}>
                    Minimum years for a must-have (optional)
                  </Text>
                  <Group gap="sm">
                    {values.mustHave.map((skill) => (
                      <NumberInput
                        key={skill}
                        label={skill}
                        size="xs"
                        w={130}
                        min={0}
                        max={40}
                        suffix=" yrs"
                        placeholder="Any"
                        value={values.minYears[skill] ?? ''}
                        onChange={(v) => set('minYears', { ...values.minYears, [skill]: v === '' ? '' : Number(v) })}
                      />
                    ))}
                  </Group>
                </Stack>
              )}
              {list('NICE_TO_HAVE', 'niceToHave', 'Nice-to-haves', 'e.g. another skill, then Enter', 'Add points but are not required.')}
              {list('CERTIFICATION', 'certifications', 'Certifications', 'e.g. the full certification name', 'Matched on the full name, so similar-sounding skills don’t count.')}
            </Stack>
          </Card>
        </Stack>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 5 }}>
        <Stack>
          {values.aiShortlisting && <ScoringCard values={values} />}
          <Card>
            <Group justify="space-between" mb="xs">
              <Title order={5} className="section-title">
                How the skills are matched
              </Title>
              {checking && (
                <Text size="xs" c="dimmed">
                  Checking…
                </Text>
              )}
            </Group>
            {check.length === 0 ? (
              <Text size="sm" c="dimmed">
                Add skills to see how they'll be matched.
              </Text>
            ) : (
              <Stack gap={8}>
                {check.map((item, i) => (
                  <Stack key={`${item.kind}-${item.value}-${i}`} gap={2}>
                    <Group gap={6}>
                      <Text size="sm" fw={500}>
                        {item.display}
                      </Text>
                      {item.display !== item.value && (
                        <Text size="xs" c="dimmed">
                          (from “{item.value}”)
                        </Text>
                      )}
                    </Group>
                    {item.alsoMatches.length > 0 && (
                      <Tooltip label={item.alsoMatches.join(', ')} multiline w={280}>
                        <Text size="xs" c="dimmed" lineClamp={1}>
                          Also matches: {item.alsoMatches.slice(0, 6).join(', ')}
                          {item.alsoMatches.length > 6 ? ` +${item.alsoMatches.length - 6} more` : ''}
                        </Text>
                      </Tooltip>
                    )}
                  </Stack>
                ))}
              </Stack>
            )}
          </Card>
          {warnings.length > 0 && (
            <Alert color={warnings.some((w) => w.blocking) ? 'red' : 'yellow'} icon={<IconAlertTriangle size={18} />} title="Check these">
              <List size="sm" spacing={4}>
                {warnings.map((w, i) => (
                  <List.Item key={i}>
                    <b>{w.display}</b>: {w.warning}
                  </List.Item>
                ))}
              </List>
            </Alert>
          )}
        </Stack>
      </Grid.Col>
    </Grid>
  );
}

/** Skills found in the job description, to add with one click. */
function SuggestedSkills({ values, set, check }: { values: Values; set: Setter; check: CriteriaCheckItem[] }) {
  const suggest = useSuggestSkills();
  const [found, setFound] = useState<{ mustHave: string[]; niceToHave: string[] } | null>(null);
  const text = values.description.trim();
  const lastText = useRef<string | null>(null);
  useEffect(() => {
    if (text.length < 30 || lastText.current === text) return;
    lastText.current = text;
    suggest.mutate(text, { onSuccess: (r) => setFound({ mustHave: r.mustHave, niceToHave: r.niceToHave }) });
    // Only when the description changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);

  if (text.length < 30) return null;
  // Skip skills already added, or already matched through another name (SQL covers MySQL, for example).
  const chosen = new Set(
    [...values.mustHave, ...values.niceToHave, ...values.certifications, ...check.flatMap((c) => [c.display, ...c.alsoMatches])].map((s) =>
      s.toLowerCase(),
    ),
  );
  const open = (list: string[]) => list.filter((s) => !chosen.has(s.toLowerCase()));
  const must = open(found?.mustHave ?? []);
  const nice = open(found?.niceToHave ?? []);

  return (
    <Card>
      <Group gap="xs" mb={4}>
        <IconSparkles size={16} color="var(--mantine-color-violet-6)" />
        <Title order={5} className="section-title">
          Suggested from the job description
        </Title>
      </Group>
      {suggest.isPending && !found ? (
        <Text size="sm" c="dimmed">
          Reading the description…
        </Text>
      ) : must.length + nice.length === 0 ? (
        <Text size="sm" c="dimmed">
          {found && (found.mustHave.length || found.niceToHave.length) ? 'All the suggested skills are added.' : 'No skills we recognise in the description yet.'}
        </Text>
      ) : (
        <Stack gap="xs">
          <Text size="xs" c="dimmed">
            Click a skill to add it. You can move it between lists below.
          </Text>
          {must.length > 0 && (
            <Group gap={6}>
              <Text size="xs" fw={600} w={92}>
                Must-have
              </Text>
              {must.map((skill) => (
                <Chip key={skill} size="xs" checked={false} onChange={() => set('mustHave', [...values.mustHave, skill])}>
                  + {skill}
                </Chip>
              ))}
            </Group>
          )}
          {nice.length > 0 && (
            <Group gap={6}>
              <Text size="xs" fw={600} w={92}>
                Nice-to-have
              </Text>
              {nice.map((skill) => (
                <Chip key={skill} size="xs" checked={false} onChange={() => set('niceToHave', [...values.niceToHave, skill])}>
                  + {skill}
                </Chip>
              ))}
            </Group>
          )}
        </Stack>
      )}
    </Card>
  );
}

function ScoringCard({ values }: { values: Values }) {
  const { data: thresholds } = useThresholds();
  const shares = useMemo(() => {
    const active: [string, number][] = [];
    if (values.mustHave.length) active.push(['Must-haves', WEIGHTS.MUST_HAVE]);
    if (values.niceToHave.length) active.push(['Nice-to-haves', WEIGHTS.NICE_TO_HAVE]);
    active.push(['Relevant experience', WEIGHTS.EXPERIENCE]);
    if (values.certifications.length) active.push(['Certifications', WEIGHTS.CERTIFICATION]);
    const total = active.reduce((sum, [, w]) => sum + w, 0);
    return active.map(([label, w]) => [label, Math.round((w / total) * 1000) / 10] as const);
  }, [values.mustHave.length, values.niceToHave.length, values.certifications.length]);
  const mustShare = shares.find(([l]) => l === 'Must-haves')?.[1] ?? 0;
  return (
    <Card>
      <Group gap="xs" mb="xs">
        <IconCalculator size={18} />
        <Title order={5} className="section-title">
          How a CV is scored
        </Title>
      </Group>
      <Stack gap={6}>
        {shares.map(([label, share]) => (
          <Group key={label} justify="space-between">
            <Text size="sm">{label}</Text>
            <Text size="sm" fw={600} className="tabular">
              {share} pts
            </Text>
          </Group>
        ))}
      </Stack>
      <Text size="xs" c="dimmed" mt="sm">
        Out of 100. Sections you don't use are left out and the rest scaled up, so there are no free points.
        {values.mustHave.length > 0 &&
          ` Each must-have is worth up to ${Math.round((mustShare / values.mustHave.length) * 10) / 10} points: full marks when used in a job in the last 3 years, ¾ in an older job, ½ in a skills list only, ¼ when just mentioned.`}
      </Text>
      <Group gap="xs" mt="sm">
        <Badge color="ett" variant="light">
          Shortlist from {thresholds?.shortlistCutoff ?? 70}
        </Badge>
        <Badge color="yellow" variant="light">
          Reserve from {thresholds?.reserveCutoff ?? 50}
        </Badge>
      </Group>
    </Card>
  );
}

function RoundsStep({ values, set, lockedUpTo, loading }: { values: Values; set: Setter; lockedUpTo: number; loading: boolean }) {
  if (loading) return <Loading label="Loading your default rounds…" />;
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 8 }}>
        <Stack>
          <Text size="sm" c="dimmed">
            Choose up to 8 rounds in any order. A panel round can be online or in person. The target time is how long a
            candidate should take from reaching the round to your decision.
          </Text>
          {lockedUpTo > 0 && (
            <Alert variant="light" color="blue">
              <Text size="sm">
                Candidates have reached Round {lockedUpTo}, so {lockedUpTo === 1 ? 'that round keeps its' : `rounds 1 to ${lockedUpTo} keep their`} type and
                place. Later rounds can change freely.
              </Text>
            </Alert>
          )}
          <RoundsBuilder value={values.rounds} onChange={(rounds) => set('rounds', rounds)} lockedUpTo={lockedUpTo} />
        </Stack>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 4 }}>
        <Card style={{ position: 'sticky', top: 80 }}>
          <Title order={5} className="section-title" mb={2}>
            {values.title || 'Untitled job'}
          </Title>
          <Text size="xs" c="dimmed" mb="md">
            {values.mustHave.length} must-have{values.mustHave.length === 1 ? '' : 's'}
            {values.location ? ` · ${values.location}` : ''}
          </Text>
          <PlanPreview rounds={toInputs(values.rounds)} aiShortlisting={values.aiShortlisting} />
        </Card>
      </Grid.Col>
    </Grid>
  );
}
