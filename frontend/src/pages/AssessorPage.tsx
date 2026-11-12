import {
  Alert,
  Box,
  Button,
  Card,
  Center,
  Group,
  SegmentedControl,
  Stack,
  Text,
  Textarea,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { useMutation, useQuery } from '@tanstack/react-query';
import { IconCircleCheck, IconId } from '@tabler/icons-react';
import { useState } from 'react';
import { useParams } from 'react-router';
import { api } from '../api/client';
import type { AssessorLinkView, Recommendation } from '../api/types';
import { BrandMark } from '../components/BrandMark';
import { Loading } from '../components/QueryState';
import { hasStarted, ist } from '../lib/format';

/** The partner assessor's report form, opened from the one-time link in their email. No sign-in. */
export function AssessorPage() {
  const { token = '' } = useParams();
  const link = useQuery({
    queryKey: ['assessor', token],
    queryFn: () => api.get<AssessorLinkView>(`/public/assessor/${encodeURIComponent(token)}`),
    retry: false,
  });
  const submit = useMutation({
    mutationFn: (body: unknown) => api.post<AssessorLinkView>(`/public/assessor/${encodeURIComponent(token)}`, body),
    onSuccess: () => link.refetch(),
  });
  const [idChecked, setIdChecked] = useState<'yes' | 'no' | null>(null);
  const [idNote, setIdNote] = useState('');
  const [scores, setScores] = useState<Record<string, string>>({});
  const [rec, setRec] = useState<Recommendation | null>(null);
  const [notes, setNotes] = useState('');

  const view = link.data;
  const early = !!view?.startAt && !hasStarted(view.startAt);
  const complete =
    !!view && idChecked !== null && (idChecked === 'yes' || idNote.trim().length > 0) && view.areas.every((a) => scores[a]) && rec !== null;

  return (
    <Box bg="var(--brand-surface)" mih="100vh" py={{ base: 'md', sm: 48 }} px="md">
      <Stack maw={720} mx="auto" gap="lg">
        <Group gap="sm">
          <BrandMark size={32} />
          <Stack gap={0}>
            <Text fw={700} c="navy.9">
              Epic Talent Trust
            </Text>
            <Text size="xs" c="dimmed">
              Partner assessment centre report
            </Text>
          </Stack>
        </Group>
        {link.isLoading && <Loading />}
        {link.error && (
          <Alert color="red" title="This link doesn't work">
            {link.error.message} If you think this is a mistake, contact ETT.
          </Alert>
        )}
        {view && view.submitted && (
          <Card>
            <Center py="xl">
              <Stack align="center" gap="xs">
                <ThemeIcon size={56} radius="xl" color="ett" variant="light">
                  <IconCircleCheck size={32} />
                </ThemeIcon>
                <Title order={3}>Thank you</Title>
                <Text c="dimmed" ta="center">
                  Your report for {view.candidateName} has been sent to {view.companyName}. This link no longer works.
                </Text>
              </Stack>
            </Center>
          </Card>
        )}
        {view && !view.submitted && view.expired && (
          <Alert color="orange" title="This link has expired">
            Please contact ETT to send your report another way.
          </Alert>
        )}
        {view && !view.submitted && !view.expired && (
          <>
            <Card>
              <Title order={2} className="page-title" fz={26}>
                {view.candidateName}
              </Title>
              <Text c="dimmed">
                {view.jobTitle} for {view.companyName}
              </Text>
              <Text size="sm" mt="xs">
                {ist(view.startAt)} · {view.centreName} · assessor {view.assessorName}
              </Text>
            </Card>
            <Card>
              <Stack>
                <Group gap="sm">
                  <IconId size={20} />
                  <Text fw={600}>Did you check the original Aadhaar and PAN cards?</Text>
                </Group>
                <Text size="sm" c="dimmed">
                  The name and photo should match the candidate in front of you. Don't write card numbers here.
                </Text>
                <SegmentedControl
                  value={idChecked ?? ''}
                  onChange={(v) => setIdChecked(v as 'yes' | 'no')}
                  data={[
                    { value: 'yes', label: 'Yes, both match' },
                    { value: 'no', label: 'No, or something was wrong' },
                  ]}
                  maw={420}
                />
                {idChecked === 'no' && (
                  <Textarea label="What happened?" required autosize minRows={2} value={idNote} onChange={(e) => setIdNote(e.currentTarget.value)} />
                )}
              </Stack>
            </Card>
            <Card>
              <Text fw={600} mb="xs">
                Score each area from 1 (weak) to 5 (excellent)
              </Text>
              <Stack gap="sm">
                {view.areas.map((area) => (
                  <Group key={area} justify="space-between" wrap="wrap">
                    <Text size="sm">{area}</Text>
                    <SegmentedControl
                      size="sm"
                      value={scores[area] ?? ''}
                      onChange={(v) => setScores({ ...scores, [area]: v })}
                      data={['1', '2', '3', '4', '5']}
                    />
                  </Group>
                ))}
              </Stack>
            </Card>
            <Card>
              <Stack>
                <Text fw={600}>Your recommendation</Text>
                <SegmentedControl
                  value={rec ?? ''}
                  onChange={(v) => setRec(v as Recommendation)}
                  data={[
                    { value: 'ADVANCE', label: 'Progress' },
                    { value: 'HOLD', label: 'Unsure' },
                    { value: 'REJECT', label: 'Not progressing' },
                  ]}
                  maw={420}
                />
                <Textarea
                  label="Notes for the hiring team"
                  description="How they did on the hands-on task, strengths and concerns."
                  autosize
                  minRows={4}
                  maxLength={4000}
                  value={notes}
                  onChange={(e) => setNotes(e.currentTarget.value)}
                />
                {early && (
                  <Alert color="blue" variant="light">
                    You can send the report once the interview has started ({ist(view.startAt)}). You can fill it in now.
                  </Alert>
                )}
                {submit.error && <Alert color="red">{submit.error.message}</Alert>}
                <Group justify="flex-end">
                  <Button
                    size="md"
                    disabled={!complete || early}
                    loading={submit.isPending}
                    onClick={() =>
                      submit.mutate({
                        idChecked: idChecked === 'yes',
                        idCheckNote: idNote.trim() || null,
                        areaScores: Object.fromEntries(Object.entries(scores).map(([k, v]) => [k, Number(v)])),
                        recommendation: rec,
                        notes: notes.trim() || null,
                      })
                    }
                  >
                    Send report
                  </Button>
                </Group>
              </Stack>
            </Card>
          </>
        )}
      </Stack>
    </Box>
  );
}
