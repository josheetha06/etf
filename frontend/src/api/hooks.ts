import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, query } from './client';
import type {
  Accuracy,
  ApplicationDetail,
  ApplicationRow,
  Branding,
  CentreOptions,
  CriteriaCheck,
  CriterionInput,
  DirectoryPage,
  InterviewRow,
  JobDetail,
  JobRequest,
  JobSummary,
  KeyFacts,
  Me,
  MessageTemplate,
  MessageView,
  Note,
  NotificationView,
  Dashboard,
  PasswordStepResult,
  ReadyRow,
  ReportCounts,
  ReportRow,
  RescheduleDetail,
  RescheduleReason,
  Round1Plan,
  ShortlistDecision,
  SkillView,
  CreatedLogin,
  TeamMember,
  StageDecisionType,
  StageDecisionView,
  Thresholds,
  TimeOfDay,
  TimelineEvent,
  UploadRow,
  AuditPage,
  CandidatePage,
  CentreAdminView,
  CentreSlot,
  CompanyDetails,
  DescriptionRead,
  EmailTemplate,
  JobStatus,
  LeaveCover,
  MemberInput,
  PanelReviewPage,
  PersonOption,
  ReviewInput,
  Round,
  RoundInput,
  RoundKind,
  InterviewMode,
  Availability,
  CandidateLocation,
  CloseImpact,
} from './types';
import { INDIA } from '../lib/format';

export const keys = {
  me: ['me'] as const,
  branding: ['branding'] as const,
  publicBranding: ['branding', 'public'] as const,
  dashboard: ['dashboard'] as const,
  jobs: (q = '', status = '') => ['jobs', 'list', q, status] as const,
  job: (id: string) => ['jobs', id] as const,
  applications: (jobId: string) => ['jobs', jobId, 'applications'] as const,
  uploads: (jobId: string, batch?: string, open?: boolean) => ['jobs', jobId, 'uploads', batch ?? '', !!open] as const,
  application: (id: string) => ['applications', id] as const,
  plan: (jobId: string, round?: number) => ['jobs', jobId, 'ai-plan', round ?? 0] as const,
  interviews: (view: string, jobId?: string) => ['interviews', view, jobId ?? 'all'] as const,
  ready: (jobId?: string) => ['interviews', 'ready', jobId ?? 'all'] as const,
  reschedule: (id: string) => ['reschedule', id] as const,
  candidates: (filter: object) => ['candidates', filter] as const,
  messages: (folder: string) => ['messages', folder] as const,
  unread: ['messages', 'unread'] as const,
  reports: (filter: object) => ['reports', filter] as const,
  reportCounts: ['reports', 'counts'] as const,
  notifications: ['notifications'] as const,
  notificationCount: ['notifications', 'count'] as const,
};

// ---------------------------------------------------------------- session

export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async () => {
      try {
        return await api.get<Me>('/auth/me');
      } catch (error) {
        if ((error as { status?: number }).status === 401) {
          return null;
        }
        throw error;
      }
    },
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useLogin() {
  return useMutation({
    mutationFn: (body: { email: string; password: string }) => api.post<PasswordStepResult>('/auth/login', body),
  });
}

export function useVerifyCode() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => api.post<Me>('/auth/totp', { code }),
    onSuccess: (me) => client.setQueryData(keys.me, me),
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>('/auth/logout'),
    onSettled: () => {
      client.clear();
      client.setQueryData(keys.me, null);
    },
  });
}

export const useBranding = () =>
  useQuery({ queryKey: keys.branding, queryFn: () => api.get<Branding>('/branding'), staleTime: 10 * 60_000 });

export const usePublicBranding = () =>
  useQuery({
    queryKey: keys.publicBranding,
    queryFn: () => api.get<Branding>('/public/branding'),
    staleTime: 10 * 60_000,
    retry: false,
  });

/** Refreshes everything that hiring changes can touch (not the session or branding). */
export function useRefreshAll() {
  const client = useQueryClient();
  return () =>
    client.invalidateQueries({
      predicate: (q) => q.queryKey[0] !== 'me' && q.queryKey[0] !== 'branding',
    });
}

function useAction<TInput, TResult = unknown>(fn: (input: TInput) => Promise<TResult>) {
  const refresh = useRefreshAll();
  return useMutation({ mutationFn: fn, onSuccess: () => refresh() });
}

// ---------------------------------------------------------------- overview and jobs

export const useDashboard = () =>
  useQuery({ queryKey: keys.dashboard, queryFn: () => api.get<Dashboard>('/dashboard'), refetchInterval: 60_000 });

export const useJobs = (q = '', status = '') =>
  useQuery({
    queryKey: keys.jobs(q, status),
    queryFn: () => api.get<JobSummary[]>(`/jobs${query({ q, status })}`),
    placeholderData: (previous) => previous,
  });

export const useJob = (id: string | undefined) =>
  useQuery({ queryKey: keys.job(id ?? ''), queryFn: () => api.get<JobDetail>(`/jobs/${id}`), enabled: !!id });

export const useCriteriaCheck = (criteria: CriterionInput[]) =>
  useQuery({
    queryKey: ['criteria-check', criteria],
    queryFn: () => api.post<CriteriaCheck>('/jobs/criteria-check', { criteria }),
    enabled: criteria.length > 0,
    placeholderData: (previous) => previous,
    staleTime: 60_000,
  });

export function useSaveJob(id?: string) {
  return useAction((body: JobRequest) =>
    id ? api.put<JobDetail>(`/jobs/${id}`, body) : api.post<JobDetail>('/jobs', body),
  );
}

export function useJobStatus() {
  return useAction(({ id, status, reason }: { id: string; status: JobStatus; reason?: string }) =>
    api.post<JobDetail>(`/jobs/${id}/status`, { status, reason }),
  );
}

export const useDefaultPlan = () =>
  useQuery({ queryKey: ['jobs', 'default-plan'], queryFn: () => api.get<Round[]>('/jobs/default-plan') });

export const usePeople = () =>
  useQuery({ queryKey: ['jobs', 'people'], queryFn: () => api.get<PersonOption[]>('/jobs/people'), staleTime: 60_000 });

export function useReadDescription() {
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.post<DescriptionRead>('/jobs/description/read', form);
    },
  });
}

export function useSuggestSkills() {
  return useMutation({ mutationFn: (text: string) => api.post<DescriptionRead>('/jobs/description/suggest', { text }) });
}

/** Stars are personal: only the signed-in person sees them. */
export function useStarJob() {
  return useAction(({ id, starred }: { id: string; starred: boolean }) =>
    starred ? api.put<{ starred: boolean }>(`/jobs/${id}/star`) : api.delete<{ starred: boolean }>(`/jobs/${id}/star`),
  );
}

/** What closing a job would do: interviews cancelled and candidates told. */
export const useCloseImpact = (id: string, enabled: boolean) =>
  useQuery({
    queryKey: ['jobs', id, 'close-impact'],
    queryFn: () => api.get<CloseImpact>(`/jobs/${id}/close-impact`),
    enabled,
    staleTime: 0,
  });

export function useJobAction() {
  return useAction(({ id, action }: { id: string; action: 'publish' | 'close' }) =>
    api.post<JobDetail>(`/jobs/${id}/${action}`),
  );
}

// ---------------------------------------------------------------- CVs

export const useApplications = (jobId: string) =>
  useQuery({
    queryKey: keys.applications(jobId),
    queryFn: () => api.get<ApplicationRow[]>(`/jobs/${jobId}/applications`),
  });

/** CV uploads for a job. Polls while any are still being read. */
export const useUploads = (jobId: string, batch?: string, open?: boolean) =>
  useQuery({
    queryKey: keys.uploads(jobId, batch, open),
    queryFn: () => api.get<UploadRow[]>(`/jobs/${jobId}/cv-uploads${query({ batch, open })}`),
    refetchInterval: (q) =>
      q.state.data?.some((u) => u.status === 'QUEUED' || u.status === 'READING') ? 2_000 : false,
  });

export function useUploadCvs(jobId: string) {
  const refresh = useRefreshAll();
  return useMutation({
    mutationFn: ({ files, batchId }: { files: File[]; batchId?: string }) => {
      const form = new FormData();
      files.forEach((file) => form.append('files', file));
      if (batchId) form.append('batchId', batchId);
      return api.post<UploadRow[]>(`/jobs/${jobId}/cvs`, form);
    },
    onSuccess: () => refresh(),
  });
}

/** "Add candidate": reads the CV straight away so HR can check the details before saving. */
export function useCandidateDraft(jobId: string) {
  return useMutation({
    mutationFn: ({ file }: { file: File }) => {
      const form = new FormData();
      form.append('file', file);
      return api.post<UploadRow>(`/jobs/${jobId}/candidate-drafts`, form);
    },
  });
}

export function useCompleteUpload() {
  return useAction(
    ({ uploadId, ...body }: {
      uploadId: string;
      fullName: string;
      email: string;
      mobile?: string;
      city?: string;
      country?: string;
    }) => api.post<UploadRow>(`/cv-uploads/${uploadId}/complete`, body),
  );
}

export function useDismissUpload() {
  return useAction((uploadId: string) => api.post<UploadRow>(`/cv-uploads/${uploadId}/dismiss`));
}

// ---------------------------------------------------------------- candidate page

export const useApplication = (id: string) =>
  useQuery({ queryKey: keys.application(id), queryFn: () => api.get<ApplicationDetail>(`/applications/${id}`) });

export function useShortlistDecision() {
  return useAction(({ applicationId, decision, reason }: { applicationId: string; decision: ShortlistDecision; reason?: string }) =>
    api.post<void>(`/applications/${applicationId}/shortlist-decision`, { decision, reason }),
  );
}

export function useReviewFlag() {
  return useAction(({ flagId, outcome, note }: { flagId: string; outcome: 'fine' | 'concern'; note?: string }) =>
    api.post<void>(`/screening-flags/${flagId}/review`, { outcome, note }),
  );
}

export function useFinishReview(jobId: string) {
  return useAction(() => api.post<void>(`/jobs/${jobId}/shortlist/finish-review`));
}

export function useFlagEvidence() {
  return useAction(({ evidenceId, note }: { evidenceId: string; note: string }) =>
    api.post<void>(`/match-evidence/${evidenceId}/flag`, { note }),
  );
}

export function useStageDecision(applicationId: string) {
  return useAction((body: { stage: ApplicationDetail['stage']; decision: StageDecisionType; reason?: string }) =>
    api.post<StageDecisionView[]>(`/applications/${applicationId}/stage-decisions`, body),
  );
}

/** Undo a round decision in its first 5 minutes; the candidate's email about it is cancelled. */
export function useUndoDecision() {
  return useAction((decisionId: string) => api.post<StageDecisionView[]>(`/stage-decisions/${decisionId}/undo`));
}

export function useRecheck(applicationId: string) {
  return useAction((note: string) => api.post<void>(`/applications/${applicationId}/ai-recheck`, { note }));
}

export function useRecordChallenge() {
  return useAction((decisionId: string) => api.post<StageDecisionView[]>(`/stage-decisions/${decisionId}/challenge`));
}

export const useTimeline = (applicationId: string) =>
  useQuery({
    queryKey: ['applications', applicationId, 'timeline'],
    queryFn: () => api.get<TimelineEvent[]>(`/applications/${applicationId}/timeline`),
  });

export const useNotes = (applicationId: string) =>
  useQuery({
    queryKey: ['applications', applicationId, 'notes'],
    queryFn: () => api.get<Note[]>(`/applications/${applicationId}/notes`),
  });

export function useAddNote(applicationId: string) {
  return useAction((body: string) => api.post<Note[]>(`/applications/${applicationId}/notes`, { body }));
}

export function useDeleteNote(applicationId: string) {
  return useAction((noteId: string) => api.delete<Note[]>(`/applications/${applicationId}/notes/${noteId}`));
}

export const useKeyFacts = (applicationId: string) =>
  useQuery({
    queryKey: ['applications', applicationId, 'key-facts'],
    queryFn: () => api.get<KeyFacts>(`/applications/${applicationId}/key-facts`),
  });

export function useSaveKeyFacts(applicationId: string) {
  return useAction((body: KeyFacts) => api.put<KeyFacts>(`/applications/${applicationId}/key-facts`, body));
}

export function useUpdateDetails(applicationId: string) {
  return useAction((body: { fullName: string; email: string; mobile: string; city: string; country: string }) =>
    api.put<void>(`/applications/${applicationId}/candidate`, body),
  );
}

export function useWithdraw(applicationId: string) {
  return useAction((reason: string) => api.post<void>(`/applications/${applicationId}/withdraw`, { reason }));
}

export function useOfferOutcome(applicationId: string) {
  return useAction((body: { accepted: boolean; note?: string }) =>
    api.post<void>(`/applications/${applicationId}/offer-outcome`, body),
  );
}

export const useTemplates = (applicationId: string, enabled = true) =>
  useQuery({
    queryKey: ['applications', applicationId, 'templates'],
    queryFn: () => api.get<MessageTemplate[]>(`/applications/${applicationId}/message-templates`),
    enabled,
  });

export function useSendMessage(applicationId: string) {
  return useAction((body: { subject: string; body: string }) =>
    api.post<void>(`/applications/${applicationId}/messages`, body),
  );
}

export function useAddToJob(applicationId: string) {
  return useAction((jobId: string) =>
    api.post<{ applicationId: string }>(`/applications/${applicationId}/add-to-job`, { jobId }),
  );
}

// ---------------------------------------------------------------- scheduling

export const useRound1Plan = (jobId: string, round?: number) =>
  useQuery({
    queryKey: keys.plan(jobId, round),
    queryFn: () => api.get<Round1Plan>(`/jobs/${jobId}/ai-interview-plan${query({ round, zone: INDIA })}`),
  });

export function useSaveRound1(jobId: string, round?: number) {
  const refresh = useRefreshAll();
  return useMutation({
    mutationFn: ({ send, ...body }: { send: boolean; replyBy: string; items: { applicationId: string; startAt: string; candidateZone: string }[] }) =>
      send
        ? api.post<{ saved: number; sent: number }>(`/jobs/${jobId}/ai-interviews/send${query({ round })}`, body)
        : api.put<{ saved: number; sent: number }>(`/jobs/${jobId}/ai-interviews${query({ round })}`, body),
    onSuccess: () => refresh(),
  });
}

export interface CurrentRound {
  number: number;
  name: string;
  kind: RoundKind;
  mode: InterviewMode | null;
  durationMin: number;
  targetDays: number | null;
}

export const useCurrentRound = (applicationId: string, enabled = true) =>
  useQuery({
    queryKey: ['applications', applicationId, 'current-round'],
    queryFn: () => api.get<CurrentRound>(`/applications/${applicationId}/current-round`),
    enabled,
  });

export function useScheduleAi(applicationId: string) {
  return useAction((body: { startAt: string; candidateZone: string; durationMin?: number; replyBy?: string }) =>
    api.post<InterviewRow>(`/applications/${applicationId}/schedule/ai-interview`, body),
  );
}

export const useCentres = (applicationId: string, enabled = true, day?: string | null, timeOfDay?: string, otherCities = false) =>
  useQuery({
    queryKey: ['applications', applicationId, 'centres', day ?? '', timeOfDay ?? 'ANY', otherCities],
    queryFn: () =>
      api.get<CentreOptions>(
        `/applications/${applicationId}/partner-centres${query({ day, timeOfDay: timeOfDay === 'ANY' ? undefined : timeOfDay, otherCities })}`,
      ),
    enabled,
    placeholderData: (previous) => previous,
  });

/** HR sets or corrects where the candidate is (town, city or PIN code). */
export function useSetLocation(applicationId: string) {
  return useAction((text: string) => api.put<CandidateLocation>(`/applications/${applicationId}/location`, { text }));
}

export function useSchedulePartner(applicationId: string) {
  return useAction((body: { slotId: string; assessorId?: string | null; replyBy?: string; outsideReason?: string | null }) =>
    api.post<InterviewRow>(`/applications/${applicationId}/schedule/partner`, body),
  );
}

export function useSchedulePanel(applicationId: string) {
  return useAction((body: {
    startAt: string;
    candidateZone: string;
    durationMin: number;
    mode: InterviewMode;
    meetingLink?: string | null;
    venue?: string | null;
    panel: MemberInput[];
    replyBy?: string;
  }) => api.post<InterviewRow>(`/applications/${applicationId}/schedule/panel`, body));
}

export function useUpdatePanel() {
  return useAction(({ interviewId, panel }: { interviewId: string; panel: MemberInput[] }) =>
    api.put<InterviewRow>(`/interviews/${interviewId}/panel`, { panel }),
  );
}

export function useSampleReport() {
  return useAction((interviewId: string) => api.post<void>(`/interviews/${interviewId}/sample-report`));
}

export function useNoShow() {
  return useAction((interviewId: string) => api.post<InterviewRow>(`/interviews/${interviewId}/no-show`));
}

export const useInterviews = (view: string, jobId?: string) =>
  useQuery({
    queryKey: keys.interviews(view, jobId),
    queryFn: () => api.get<InterviewRow[]>(`/interviews${query({ view, jobId })}`),
  });

export const useReadyToSchedule = (jobId?: string) =>
  useQuery({
    queryKey: keys.ready(jobId),
    queryFn: () => api.get<ReadyRow[]>(`/interviews/ready-to-schedule${query({ jobId })}`),
  });

export function useInterviewAction() {
  return useAction(({ id, action, reason }: { id: string; action: 'accept' | 'cancel'; reason?: string }) =>
    api.post<InterviewRow>(`/interviews/${id}/${action}`, action === 'cancel' ? { reason } : undefined),
  );
}

export function useLogReschedule() {
  return useAction(({ interviewId, ...body }: {
    interviewId: string;
    reason: RescheduleReason;
    note: string;
    availableDays: string[];
    timeOfDay: TimeOfDay;
  }) => api.post<RescheduleDetail>(`/interviews/${interviewId}/reschedule-requests`, body));
}

export const useReschedule = (id: string | undefined) =>
  useQuery({
    queryKey: keys.reschedule(id ?? ''),
    queryFn: () => api.get<RescheduleDetail>(`/reschedule-requests/${id}?zone=${INDIA}`),
    enabled: !!id,
  });

export function useRespondReschedule(requestId: string) {
  return useAction((body: {
    action: 'NEW_TIME' | 'KEEP_ORIGINAL';
    newStartAt?: string;
    newSlotId?: string;
    assessorId?: string;
    message?: string;
    outsideReason?: string | null;
  }) => api.post<RescheduleDetail>(`/reschedule-requests/${requestId}/respond`, body));
}

// ---------------------------------------------------------------- candidates, reports, messages

export interface CandidateFilter {
  q?: string;
  jobId?: string;
  stage?: string;
  status?: string;
  suggestion?: string;
  decision?: string;
  source?: string;
  needsDecision?: boolean;
  action?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export const useCandidates = (filter: CandidateFilter) =>
  useQuery({
    queryKey: keys.candidates(filter),
    queryFn: () => api.get<DirectoryPage>(`/candidates${query({ ...filter })}`),
    placeholderData: (previous) => previous,
  });

export interface ReportFilter {
  view: 'to_review' | 'reviewed' | 'all';
  type?: string;
  jobId?: string;
  flagged?: boolean;
}

export const useReports = (filter: ReportFilter) =>
  useQuery({
    queryKey: keys.reports(filter),
    queryFn: () => api.get<ReportRow[]>(`/reports${query({ ...filter })}`),
    placeholderData: (previous) => previous,
  });

export const useReportCounts = () =>
  useQuery({
    queryKey: keys.reportCounts,
    queryFn: () => api.get<ReportCounts>('/reports/counts'),
    refetchInterval: 60_000,
  });

export const useMessages = (folder: 'inbox' | 'sent') =>
  useQuery({ queryKey: keys.messages(folder), queryFn: () => api.get<MessageView[]>(`/messages?folder=${folder}`) });

export const useUnreadCount = () =>
  useQuery({
    queryKey: keys.unread,
    queryFn: () => api.get<{ unread: number }>('/messages/unread-count'),
    refetchInterval: 60_000,
  });

export function useMarkRead() {
  return useAction((messageId: string) => api.post<void>(`/messages/${messageId}/read`));
}

// ---------------------------------------------------------------- notifications and preferences

export const useNotifications = (enabled: boolean) =>
  useQuery({
    queryKey: keys.notifications,
    queryFn: () => api.get<NotificationView[]>('/notifications?limit=30'),
    enabled,
  });

export const useNotificationCount = () =>
  useQuery({
    queryKey: keys.notificationCount,
    queryFn: () => api.get<{ unread: number }>('/notifications/count'),
    refetchInterval: 30_000,
  });

export function useNotificationRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string | 'all') =>
      id === 'all' ? api.post<void>('/notifications/read-all') : api.post<void>(`/notifications/${id}/read`),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

export const usePreferences = () =>
  useQuery({ queryKey: ['preferences'], queryFn: () => api.get<{ emailAlerts: string }>('/me/preferences') });

export function useSavePreferences() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (emailAlerts: string) => api.put<{ emailAlerts: string }>('/me/preferences', { emailAlerts }),
    onSuccess: (data) => client.setQueryData(['preferences'], data),
  });
}

// ---------------------------------------------------------------- settings

export const useThresholds = () =>
  useQuery({ queryKey: ['settings', 'screening'], queryFn: () => api.get<Thresholds>('/settings/screening') });

export function useSaveThresholds() {
  return useAction((body: Thresholds) => api.put<Thresholds>('/settings/screening', body));
}

export const useSkills = (q: string) =>
  useQuery({
    queryKey: ['settings', 'skills', q],
    queryFn: () => api.get<SkillView[]>(`/settings/skills${query({ q })}`),
    placeholderData: (previous) => previous,
  });

export function useSaveSkill() {
  return useAction(({ id, skill, otherNames }: { id?: string | null; skill: string; otherNames: string[] }) =>
    id
      ? api.put<SkillView>(`/settings/skills/${id}`, { skill, otherNames })
      : api.post<SkillView>('/settings/skills', { skill, otherNames }),
  );
}

export function useDeleteSkill() {
  return useAction((id: string) => api.delete<void>(`/settings/skills/${id}`));
}

export const useAccuracy = (jobId?: string) =>
  useQuery({
    queryKey: ['settings', 'accuracy', jobId ?? ''],
    queryFn: () => api.get<Accuracy>(`/settings/accuracy${query({ jobId })}`),
  });

export function useLogo() {
  const client = useQueryClient();
  const refresh = () => client.invalidateQueries({ queryKey: ['branding'] });
  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.put<Branding>('/settings/logo', form);
    },
    onSuccess: refresh,
  });
  const remove = useMutation({ mutationFn: () => api.delete<Branding>('/settings/logo'), onSuccess: refresh });
  return { upload, remove };
}

// ---------------------------------------------------------------- team and passwords

export const useTeam = () => useQuery({ queryKey: ['team'], queryFn: () => api.get<TeamMember[]>('/team') });

function useTeamAction<TInput, TResult>(fn: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => client.invalidateQueries({ queryKey: ['team'] }) });
}

export function useAddTeam() {
  return useTeamAction((people: { fullName: string; email: string; role: TeamMember['role'] }[]) =>
    api.post<CreatedLogin[]>('/team', { people }),
  );
}

// ---------------------------------------------------------------- leave cover

export const useLeaveCover = () => useQuery({ queryKey: ['leave-cover'], queryFn: () => api.get<LeaveCover[]>('/leave-cover') });

export const useColleagues = (enabled = true) =>
  useQuery({
    queryKey: ['leave-cover', 'colleagues'],
    queryFn: () => api.get<PersonOption[]>('/leave-cover/colleagues'),
    enabled,
  });

export function useCreateCover() {
  return useAction((body: { userId?: string | null; coverUserId: string; startsOn: string; endsOn: string; note?: string }) =>
    api.post<LeaveCover>('/leave-cover', body),
  );
}

export function useChangeCover() {
  return useAction(({ id, ...body }: { id: string; startsOn: string; endsOn: string }) =>
    api.put<LeaveCover>(`/leave-cover/${id}`, body),
  );
}

export function useEndCover() {
  return useAction((id: string) => api.post<LeaveCover>(`/leave-cover/${id}/end`));
}

// ---------------------------------------------------------------- audit trail

export interface AuditFilter {
  type?: string;
  person?: string;
  from?: string;
  to?: string;
  jobId?: string;
  applicationId?: string;
  interviewId?: string;
  q?: string;
  size?: number;
}

export const useAudit = (filter: AuditFilter, enabled = true) =>
  useInfiniteQuery({
    queryKey: ['audit', filter],
    initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam }) => api.get<AuditPage>(`/audit${query({ ...filter, before: pageParam })}`),
    getNextPageParam: (last) => (last.more && last.items.length > 0 ? last.items[last.items.length - 1].id : undefined),
    enabled,
  });

// ---------------------------------------------------------------- settings for HR admins

export const useCompanyDetails = () =>
  useQuery({ queryKey: ['settings', 'company'], queryFn: () => api.get<CompanyDetails>('/settings/company') });

export function useSaveCompanyDetails() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; address: string | null; timeZone: string }) =>
      api.put<CompanyDetails>('/settings/company', body),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['settings', 'company'] });
      client.invalidateQueries({ queryKey: keys.me });
      client.invalidateQueries({ queryKey: keys.branding });
    },
  });
}

export const useRoundPlan = () => useQuery({ queryKey: ['settings', 'round-plan'], queryFn: () => api.get<Round[]>('/settings/round-plan') });

export function useSaveRoundPlan() {
  return useAction((rounds: RoundInput[]) => api.put<Round[]>('/settings/round-plan', { rounds }));
}

export const useMeasures = () =>
  useQuery({ queryKey: ['settings', 'measures'], queryFn: () => api.get<string[]>('/settings/review-measures') });

export function useSaveMeasures() {
  return useAction((measures: string[]) => api.put<string[]>('/settings/review-measures', { measures }));
}

export const useDecisionReasons = () =>
  useQuery({ queryKey: ['settings', 'decision-reasons'], queryFn: () => api.get<string[]>('/settings/decision-reasons') });

export function useSaveDecisionReasons() {
  return useAction((reasons: string[]) => api.put<string[]>('/settings/decision-reasons', { reasons }));
}

export const useEmailTemplates = () =>
  useQuery({ queryKey: ['settings', 'email-templates'], queryFn: () => api.get<EmailTemplate[]>('/settings/email-templates') });

export function useSaveEmailTemplate() {
  return useAction(({ key, subject, body }: { key: string; subject: string; body: string }) =>
    api.put<EmailTemplate>(`/settings/email-templates/${key}`, { subject, body }),
  );
}

export function useResetEmailTemplate() {
  return useAction((key: string) => api.delete<EmailTemplate>(`/settings/email-templates/${key}`));
}

export function usePreviewEmail() {
  return useMutation({
    mutationFn: ({ key, subject, body }: { key: string; subject: string; body: string }) =>
      api.post<{ subject: string; body: string }>(`/settings/email-templates/${key}/preview`, { subject, body }),
  });
}

export const useCentreAdmin = () =>
  useQuery({ queryKey: ['settings', 'centres'], queryFn: () => api.get<CentreAdminView[]>('/settings/centres') });

export interface CentreInput {
  name: string;
  address: string | null;
  city: string;
  postcode: string | null;
  country: string | null;
  latitude: number | null;
  longitude: number | null;
  timeZone: string;
  stepFreeAccess: boolean;
  partnerOrg: string | null;
  active: boolean;
}

export function useSaveCentre() {
  return useAction(({ id, ...body }: CentreInput & { id?: string }) =>
    id ? api.put<CentreAdminView>(`/settings/centres/${id}`, body) : api.post<CentreAdminView>('/settings/centres', body),
  );
}

export function useSaveAssessor() {
  return useAction(({ centreId, assessorId, ...body }: { centreId: string; assessorId?: string; fullName: string; email: string; active: boolean }) =>
    assessorId
      ? api.put<CentreAdminView>(`/settings/centres/${centreId}/assessors/${assessorId}`, body)
      : api.post<CentreAdminView>(`/settings/centres/${centreId}/assessors`, body),
  );
}

export const useCentreSlots = (centreId: string | null) =>
  useQuery({
    queryKey: ['settings', 'centres', centreId, 'slots'],
    queryFn: () => api.get<CentreSlot[]>(`/settings/centres/${centreId}/slots`),
    enabled: !!centreId,
  });

export function useAddSlots() {
  return useAction(({ centreId, ...body }: {
    centreId: string;
    fromDate: string;
    toDate: string;
    weekdays: number[];
    times: string[];
    durationMin: number;
    seats: number;
  }) => api.post<{ added: number; skipped: number }>(`/settings/centres/${centreId}/slots`, body));
}

export function useRemoveSlot() {
  return useAction(({ centreId, slotId }: { centreId: string; slotId: string }) =>
    api.delete<void>(`/settings/centres/${centreId}/slots/${slotId}`),
  );
}

// ---------------------------------------------------------------- panel login

export const usePanelReview = (enabled: boolean) =>
  useQuery({
    queryKey: ['panel', 'review'],
    queryFn: async () => {
      try {
        return await api.get<PanelReviewPage>('/panel/review');
      } catch (error) {
        if ((error as { status?: number }).status === 404) return null;
        throw error;
      }
    },
    enabled,
    retry: false,
  });

export function usePanelAccess() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { employeeId: string; code: string }) => api.post<PanelReviewPage>('/panel/access', body),
    onSuccess: (page) => client.setQueryData(['panel', 'review'], page),
  });
}

export function useSavePanelReview() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ submit, ...body }: ReviewInput & { submit: boolean }) =>
      submit ? api.post<PanelReviewPage>('/panel/review/submit', body) : api.put<PanelReviewPage>('/panel/review', body),
    onSuccess: (page) => client.setQueryData(['panel', 'review'], page),
  });
}

export function usePanelExit() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>('/panel/exit'),
    onSuccess: () => client.setQueryData(['panel', 'review'], null),
  });
}

// ---------------------------------------------------------------- candidate page

export const useCandidatePage = (token: string) =>
  useQuery({
    queryKey: ['candidate-page', token],
    queryFn: () => api.get<CandidatePage>(`/public/candidate/${token}`),
    retry: false,
  });

export function useCandidatePageAction(token: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (action:
      | { kind: 'accept' }
      | { kind: 'message'; body: string }
      | { kind: 'new-time'; reason: RescheduleReason; note: string; availability: Availability[] }) => {
      if (action.kind === 'accept') return api.post<CandidatePage>(`/public/candidate/${token}/accept`);
      if (action.kind === 'message') return api.post<CandidatePage>(`/public/candidate/${token}/messages`, { body: action.body });
      return api.post<CandidatePage>(`/public/candidate/${token}/new-time`, {
        reason: action.reason,
        note: action.note,
        availability: action.availability,
      });
    },
    onSuccess: (page) => client.setQueryData(['candidate-page', token], page),
  });
}

export function useChangeMember() {
  return useTeamAction(({ id, ...body }: { id: string; role?: TeamMember['role']; active?: boolean }) =>
    api.put<TeamMember>(`/team/${id}`, body),
  );
}

export function useResetMemberPassword() {
  return useTeamAction((id: string) => api.post<CreatedLogin>(`/team/${id}/reset-password`));
}

export function useResetMemberTwoStep() {
  return useTeamAction((id: string) => api.post<TeamMember>(`/team/${id}/reset-two-step`));
}

export function useChangePassword() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { currentPassword?: string; newPassword: string }) => api.post<Me>('/auth/password', body),
    onSuccess: (me) => client.setQueryData(keys.me, me),
  });
}
