import { Alert, Button, Group, ScrollArea, SegmentedControl, Stack, Table, Text, Tooltip } from '@mantine/core';
import { IconAlertTriangle, IconArrowRight, IconInfoCircle, IconQuestionMark } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useApplications, useShortlistDecision, useUploads } from '../../api/hooks';
import type { AiSuggestion, ApplicationRow, JobDetail, ShortlistDecision } from '../../api/types';
import { LocationCell } from '../../components/candidate/LocationCell';
import { ScorePill, ToneBadge } from '../../components/Badges';
import { Empty, ErrorBlock, Loading } from '../../components/QueryState';
import { years } from '../../lib/format';
import { nextAction, shortlistDecision, suggestion } from '../../lib/labels';
import { notifyError, notifySuccess } from '../../lib/notify';
import { flagsToCheck, MissingInformation, missingDetails } from './MissingInformation';

const AGREES: Record<AiSuggestion, ShortlistDecision> = { SHORTLIST: 'SHORTLISTED', REVIEW: 'HOLD', REJECT: 'REJECTED' };

const VIEWS: Record<string, (row: ApplicationRow) => boolean> = {
  // CVs with red flags wait in Missing information until the flags are checked.
  todo: (r) => r.matchScore !== null && !r.decision && r.status === 'ACTIVE' && r.redFlagsOpen === 0,
  shortlisted: (r) => r.decision === 'SHORTLISTED',
  reserve: (r) => r.decision === 'HOLD',
  rejected: (r) => r.decision === 'REJECTED',
  all: (r) => r.matchScore !== null,
};

/** What an empty decision tab says once every CV has a decision. */
const ALL_DONE: Record<string, string> = {
  shortlisted: 'No one is shortlisted.',
  reserve: 'No one is in reserve.',
  rejected: 'No one is in Not now.',
};

/**
 * Report 1 for the whole job: every scored CV with the AI's suggestion, its reason and HR's decision, plus the CVs
 * with missing information or red flags to check.
 */
export function ShortlistTab({ job, canEdit }: { job: JobDetail; canEdit: boolean }) {
  const { data: rows, isLoading, error, refetch } = useApplications(job.id);
  const { data: uploads } = useUploads(job.id);
  const decide = useShortlistDecision();
  const navigate = useNavigate();
  const [view, setView] = useState('todo');

  if (isLoading) return <Loading />;
  if (error || !rows) return <ErrorBlock error={error} onRetry={refetch} />;
  const scored = rows.filter((r) => r.matchScore !== null);
  const missingCount = missingDetails(uploads).length + flagsToCheck(rows).length;
  if (scored.length === 0 && missingCount === 0) {
    return <Empty title="Nothing to review yet">Add CVs on the CVs tab. Each one is read and scored within a few minutes.</Empty>;
  }
  const todo = rows.filter(VIEWS.todo);
  const visible = view === 'missing' ? [] : rows.filter(VIEWS[view]);
  const stillToDecide = todo.length + flagsToCheck(rows).length;
  const editable = canEdit && (job.status === 'OPEN' || job.status === 'SHORTLIST_CONFIRMED');

  const agree = (row: ApplicationRow) =>
    row.suggestion &&
    decide.mutate(
      { applicationId: row.id, decision: AGREES[row.suggestion] },
      {
        onSuccess: () => notifySuccess(`${row.candidateName}: ${shortlistDecision[AGREES[row.suggestion!]].label.toLowerCase()}.`),
        onError: (e) => notifyError(e, "The decision wasn't saved"),
      },
    );

  return (
    <Stack>
      <Alert icon={<IconInfoCircle size={18} />} variant="light">
        Report 1 · AI shortlisting. The AI recommends; you decide. Each score is backed by lines from the CV, and red flags
        must be checked before shortlisting. Open a candidate to see the full evidence or to go against the suggestion.
      </Alert>
      <Group justify="space-between" wrap="wrap">
        <ScrollArea type="never" maw="100%">
          <SegmentedControl
            value={view}
            onChange={setView}
            data={[
              { value: 'todo', label: `To decide (${todo.length})` },
              { value: 'shortlisted', label: `Shortlisted (${rows.filter(VIEWS.shortlisted).length})` },
              { value: 'reserve', label: `Reserve (${rows.filter(VIEWS.reserve).length})` },
              { value: 'rejected', label: `Not now (${rows.filter(VIEWS.rejected).length})` },
              { value: 'missing', label: `Missing information (${missingCount})` },
              { value: 'all', label: 'All' },
            ]}
          />
        </ScrollArea>
        {todo.length > 0 && view !== 'missing' && (
          <Button variant="light" rightSection={<IconArrowRight size={16} />} onClick={() => navigate(`/applications/${todo[0].id}#decide`)}>
            Review one by one
          </Button>
        )}
      </Group>
      {view === 'missing' ? (
        <MissingInformation job={job} canEdit={canEdit} uploads={uploads} rows={rows} />
      ) : visible.length === 0 ? (
        stillToDecide === 0 ? (
          <Empty title="All CVs reviewed">
            {view === 'todo'
              ? missingCount > 0
                ? `Every scored CV has a decision. ${missingCount} ${missingCount === 1 ? 'CV is' : 'CVs are'} in Missing information.`
                : 'Every CV has a decision. Shortlisted candidates are ready to book for Round 1.'
              : ALL_DONE[view]}
          </Empty>
        ) : (
          <Empty
            title="No one here yet"
            action={
              <Button variant="subtle" size="compact-sm" onClick={() => setView(todo.length > 0 ? 'todo' : 'missing')}>
                {stillToDecide} {stillToDecide === 1 ? 'CV' : 'CVs'} still to decide
              </Button>
            }
          />
        )
      ) : (
        <Table.ScrollContainer minWidth={1000}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Candidate</Table.Th>
                <Table.Th>Location</Table.Th>
                <Table.Th ta="center">Score</Table.Th>
                <Table.Th>AI suggestion and why</Table.Th>
                <Table.Th ta="center">Must-haves in work</Table.Th>
                <Table.Th>Relevant exp.</Table.Th>
                <Table.Th>Decision</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {visible.map((row) => (
                <Table.Tr key={row.id} className="clickable-row" onClick={() => navigate(`/applications/${row.id}#decide`)}>
                  <Table.Td>
                    <Group gap={6} wrap="nowrap">
                      <Text fw={600} size="sm">
                        {row.candidateName}
                      </Text>
                      {row.redFlags > 0 && (
                        <Tooltip label={`${row.redFlags} red flag${row.redFlags === 1 ? '' : 's'}${row.redFlagsOpen ? `, ${row.redFlagsOpen} to check` : ' (checked)'}`}>
                          <IconAlertTriangle size={15} color={row.redFlagsOpen ? 'var(--mantine-color-red-6)' : 'var(--mantine-color-gray-5)'} />
                        </Tooltip>
                      )}
                      {row.checkFlags > 0 && (
                        <Tooltip label={`${row.checkFlags} thing${row.checkFlags === 1 ? '' : 's'} to check (notice period, gaps, budget…)`}>
                          <IconQuestionMark size={15} color="var(--mantine-color-yellow-7)" />
                        </Tooltip>
                      )}
                    </Group>
                  </Table.Td>
                  <Table.Td maw={180}>
                    <LocationCell location={row.location} />
                  </Table.Td>
                  <Table.Td ta="center">
                    <ScorePill score={row.matchScore} />
                  </Table.Td>
                  <Table.Td maw={380}>
                    {row.suggestion && <ToneBadge tone={suggestion[row.suggestion]} size="sm" />}
                    {row.reason && (
                      <Text size="xs" c="dimmed" mt={4} lineClamp={2}>
                        {row.reason}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td ta="center" className="tabular">
                    <Text size="sm" fw={500} c={row.mustHaveBacked === row.mustHaveTotal ? 'ett.7' : undefined}>
                      {row.mustHaveBacked}/{row.mustHaveTotal}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{years(row.experienceYears)}</Text>
                  </Table.Td>
                  <Table.Td onClick={(e) => e.stopPropagation()}>
                    {row.decision ? (
                      <Stack gap={2}>
                        <ToneBadge tone={shortlistDecision[row.decision]} size="sm" />
                        {row.decision === 'SHORTLISTED' && row.nextAction !== 'none' && (
                          <Text size="xs" c="dimmed">
                            {nextAction[row.nextAction].label}
                          </Text>
                        )}
                      </Stack>
                    ) : editable && row.suggestion ? (
                      <Group gap={6} wrap="nowrap">
                        <Tooltip
                          label={
                            row.redFlagsOpen > 0 && row.suggestion === 'SHORTLIST'
                              ? 'Check the red flags first'
                              : `Agree: ${shortlistDecision[AGREES[row.suggestion]].label.toLowerCase()}`
                          }
                        >
                          <Button
                            size="compact-sm"
                            variant="light"
                            disabled={row.redFlagsOpen > 0 && row.suggestion === 'SHORTLIST'}
                            onClick={() => agree(row)}
                          >
                            Agree
                          </Button>
                        </Tooltip>
                        <Button size="compact-sm" variant="subtle" color="gray" onClick={() => navigate(`/applications/${row.id}#decide`)}>
                          Decide
                        </Button>
                      </Group>
                    ) : (
                      <Text size="sm" c="dimmed">
                        —
                      </Text>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Stack>
  );
}
