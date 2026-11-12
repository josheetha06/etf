import type {
  AiSuggestion,
  ApplicationSource,
  EvidenceStrength,
  NextAction,
  ReportType,
  Role,
  RoundKind,
  UploadStatus,
  ApplicationStage,
  ApplicationStatus,
  ContractType,
  CriterionKind,
  InterviewMode,
  InterviewStatus,
  JobStatus,
  LocationSource,
  LocationStatus,
  RescheduleReason,
  ShortlistDecision,
  StageDecisionType,
  TimeOfDay,
} from '../api/types';

type Tone = { label: string; color: string };

export const jobStatus: Record<JobStatus, Tone> = {
  DRAFT: { label: 'Draft', color: 'gray' },
  OPEN: { label: 'Open', color: 'ett' },
  SHORTLIST_CONFIRMED: { label: 'Open', color: 'ett' },
  ON_HOLD: { label: 'On hold', color: 'orange' },
  CLOSED: { label: 'Closed', color: 'dark' },
  ARCHIVED: { label: 'Archived', color: 'dark' },
};

export const contractType: Record<ContractType, string> = {
  PERMANENT: 'Permanent',
  FIXED_TERM: 'Fixed term',
  CONTRACT: 'Contract',
  PART_TIME: 'Part time',
  INTERNSHIP: 'Internship',
};

export const criterionKind: Record<CriterionKind, string> = {
  MUST_HAVE: 'Must-have',
  NICE_TO_HAVE: 'Nice-to-have',
  CERTIFICATION: 'Certification',
};

export const interviewMode: Record<InterviewMode, string> = {
  ONLINE: 'Online',
  PHONE: 'Phone',
  IN_PERSON: 'In person',
};

export const suggestion: Record<AiSuggestion, Tone> = {
  SHORTLIST: { label: 'Shortlist', color: 'ett' },
  REVIEW: { label: 'Reserve', color: 'yellow' },
  REJECT: { label: 'Not recommended', color: 'gray' },
};

export const shortlistDecision: Record<ShortlistDecision, Tone> = {
  SHORTLISTED: { label: 'Shortlist', color: 'ett' },
  HOLD: { label: 'Reserve', color: 'yellow' },
  REJECTED: { label: 'Not now', color: 'gray' },
};

export const interviewStatus: Record<InterviewStatus, Tone> = {
  DRAFT: { label: 'Draft · not sent', color: 'gray' },
  INVITED: { label: 'Awaiting reply', color: 'blue' },
  CONFIRMED: { label: 'Accepted', color: 'ett' },
  RESCHEDULE_REQUESTED: { label: 'Reschedule requested', color: 'orange' },
  COMPLETED: { label: 'Completed', color: 'grape' },
  CANCELLED: { label: 'Cancelled', color: 'dark' },
  NO_SHOW: { label: 'Did not attend', color: 'red' },
};

export const stage: Record<ApplicationStage, string> = {
  SCREENING: 'CV screening',
  SHORTLIST: 'Shortlisted',
  ROUND1: 'Round 1',
  ROUND2: 'Round 2',
  ROUND3: 'Round 3',
  ROUND4: 'Round 4',
  ROUND5: 'Round 5',
  ROUND6: 'Round 6',
  ROUND7: 'Round 7',
  ROUND8: 'Round 8',
  OFFER: 'Offer',
  CLOSED: 'Closed',
};

export const applicationStatus: Record<ApplicationStatus, Tone> = {
  ACTIVE: { label: 'In progress', color: 'ett' },
  REJECTED: { label: 'Not progressing', color: 'gray' },
  WITHDRAWN: { label: 'Withdrawn', color: 'gray' },
  HIRED: { label: 'Hired', color: 'green' },
};

export const stageDecision: Record<StageDecisionType, Tone> = {
  ADVANCE: { label: 'Progress', color: 'ett' },
  HOLD: { label: 'Hold', color: 'yellow' },
  REJECT: { label: 'Not progressing', color: 'gray' },
};

export const rescheduleReason: Record<RescheduleReason, string> = {
  CLASH: 'Clash with another commitment',
  ILLNESS: 'Illness',
  TRAVEL: 'Travel',
  OTHER: 'Something else',
};

export const source: Record<ApplicationSource, string> = {
  HR_UPLOAD: 'Uploaded by HR',
  EMAIL: 'Email',
  PORTAL: 'Careers page',
  REFERRAL: 'Employee referral',
  AGENCY: 'Recruitment agency',
  JOB_BOARD: 'Job board',
};

/** The sources HR picks from when adding CVs. */
export const sourceOptions: { value: ApplicationSource; label: string; detail: string | null }[] = [
  { value: 'JOB_BOARD', label: 'Job board', detail: 'Job board name' },
  { value: 'REFERRAL', label: 'Employee referral', detail: 'Referred by' },
  { value: 'AGENCY', label: 'Recruitment agency', detail: 'Agency name' },
  { value: 'EMAIL', label: 'Email', detail: null },
  { value: 'PORTAL', label: 'Careers page', detail: null },
  { value: 'HR_UPLOAD', label: 'Other', detail: 'Where from?' },
];

export const uploadStatus: Record<UploadStatus, Tone> = {
  QUEUED: { label: 'Waiting', color: 'gray' },
  READING: { label: 'Reading', color: 'blue' },
  NEEDS_DETAILS: { label: 'Needs details', color: 'orange' },
  ADDED: { label: 'Added', color: 'ett' },
  DUPLICATE: { label: 'Already added', color: 'gray' },
  FAILED: { label: "Couldn't add", color: 'red' },
};

export const strength: Record<EvidenceStrength, Tone> = {
  RECENT_ROLE: { label: 'Recent job', color: 'ett' },
  OLDER_ROLE: { label: 'Older job', color: 'teal' },
  SKILLS_ONLY: { label: 'Skills list only', color: 'yellow' },
  MENTION_ONLY: { label: 'Mentioned only', color: 'orange' },
  NEGATED: { label: 'Says no', color: 'red' },
  NOT_FOUND: { label: 'Not found', color: 'gray' },
};

export const recommendation: Record<StageDecisionType, Tone> = {
  ADVANCE: { label: 'Progress', color: 'ett' },
  HOLD: { label: 'Hold', color: 'yellow' },
  REJECT: { label: 'Not progressing', color: 'red' },
};

export const reportType: Record<ReportType, { title: string; short: string; by: string }> = {
  shortlisting: { title: 'AI shortlisting', short: 'Shortlisting', by: 'AI agent' },
  ai_interview: { title: 'AI interview report', short: 'AI interview', by: 'AI interviewer, checked by ETT' },
  partner: { title: 'Partner assessment centre report', short: 'Partner assessment centre', by: 'The centre\'s assessor' },
  panel: { title: 'Panel reviews', short: 'Panel', by: 'Your interview panel' },
};

/** The three kinds of round a job can use, in any order. */
export const roundKind: Record<RoundKind, { label: string; short: string; color: string; where: string; help: string }> = {
  AI_INTERVIEW: {
    label: 'AI interview',
    short: 'AI',
    color: 'violet',
    where: 'Online',
    help: 'The ETT AI interviewer runs it online and checks Aadhaar and PAN on camera. ETT releases the report.',
  },
  PARTNER: {
    label: 'Partner assessment centre',
    short: 'Assessment centre',
    color: 'teal',
    where: 'In person at a partner assessment centre',
    help: 'In person at a partner assessment centre in the candidate\'s nearest centre city. The centres there take turns.',
  },
  PANEL: {
    label: 'Panel interview',
    short: 'Panel',
    color: 'blue',
    where: 'Online or in person',
    help: 'Your own interviewers, online or at your office. Each panel member sends a detailed review.',
  },
};

/** "Technical Level 1 (Round 2)", or "Round 2" when the round has no name. */
export function roundLabel(name: string | null | undefined, round: number | null | undefined): string {
  if (!round) return name ?? '';
  return name ? `${name} (Round ${round})` : `Round ${round}`;
}

export const panelRecommendation: Record<string, Tone> = {
  strong_hire: { label: 'Strong hire', color: 'green' },
  hire: { label: 'Hire', color: 'ett' },
  no_hire: { label: 'No hire', color: 'orange' },
  strong_no_hire: { label: 'Strong no hire', color: 'red' },
};

export const roleLabel: Record<Role, string> = {
  admin: 'HR admin',
  recruiter: 'Recruiter',
  hiring_manager: 'Hiring manager',
  interviewer: 'Interviewer (no longer used)',
  panel: 'Panel login',
};

/** What HR needs to do next, in a few words. */
export const nextAction: Record<NextAction, Tone & { verb: string }> = {
  answer_reschedule: { label: 'New time asked', color: 'orange', verb: 'Answer request' },
  review_flags: { label: 'Check red flags', color: 'red', verb: 'Check flags' },
  decide_shortlist: { label: 'To shortlist', color: 'blue', verb: 'Decide' },
  schedule_round: { label: 'Ready to book', color: 'violet', verb: 'Book' },
  decide_round: { label: 'Report to review', color: 'blue', verb: 'Decide' },
  record_offer: { label: 'Record offer', color: 'grape', verb: 'Record' },
  waiting: { label: 'In progress', color: 'gray', verb: 'Open' },
  none: { label: 'No action', color: 'gray', verb: 'Open' },
};

export const timeOfDay: Record<TimeOfDay, string> = {
  MORNING: 'Mornings',
  AFTERNOON: 'Afternoons',
  EVENING: 'Evenings',
  ANY: 'Any time',
};

/** Where a candidate's location came from. */
export const locationSource: Record<LocationSource, string> = {
  cv: 'From CV',
  ai_interview: 'From AI interview',
  hr: 'Set by HR',
};

/** What to show when there's no usable location. */
export const locationStatus: Record<Exclude<LocationStatus, 'known'>, { label: string; color: string }> = {
  unplaced: { label: 'Not found on the map', color: 'orange' },
  ask_ai: { label: 'To ask in AI interview', color: 'violet' },
  missing: { label: 'Not known', color: 'gray' },
};

/** "45 min", "1 h 10 min" for journey times. */
export function journey(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) return '';
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h${minutes % 60 ? ` ${minutes % 60} min` : ''}`;
}
