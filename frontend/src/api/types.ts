export type Uuid = string;
export type IsoInstant = string;
export type IsoDate = string;

export type JobStatus = 'DRAFT' | 'OPEN' | 'SHORTLIST_CONFIRMED' | 'ON_HOLD' | 'CLOSED' | 'ARCHIVED';
export type RoundKind = 'AI_INTERVIEW' | 'PARTNER' | 'PANEL';
export type ContractType = 'PERMANENT' | 'FIXED_TERM' | 'CONTRACT' | 'PART_TIME' | 'INTERNSHIP';
export type InterviewMode = 'ONLINE' | 'PHONE' | 'IN_PERSON';
export type CriterionKind = 'MUST_HAVE' | 'NICE_TO_HAVE' | 'CERTIFICATION';
export type AiSuggestion = 'SHORTLIST' | 'REVIEW' | 'REJECT';
export type ShortlistDecision = 'SHORTLISTED' | 'HOLD' | 'REJECTED';
export type ApplicationStage =
  | 'SCREENING'
  | 'SHORTLIST'
  | 'ROUND1'
  | 'ROUND2'
  | 'ROUND3'
  | 'ROUND4'
  | 'ROUND5'
  | 'ROUND6'
  | 'ROUND7'
  | 'ROUND8'
  | 'OFFER'
  | 'CLOSED';
export type ApplicationStatus = 'ACTIVE' | 'REJECTED' | 'WITHDRAWN' | 'HIRED';
export type ApplicationSource = 'HR_UPLOAD' | 'EMAIL' | 'PORTAL' | 'REFERRAL' | 'AGENCY' | 'JOB_BOARD';
export type CvStatus = 'UPLOADED' | 'PARSED' | 'FAILED' | 'DELETED';
export type InterviewStatus =
  | 'DRAFT'
  | 'INVITED'
  | 'CONFIRMED'
  | 'RESCHEDULE_REQUESTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';
export type RescheduleReason = 'CLASH' | 'ILLNESS' | 'TRAVEL' | 'OTHER';
export type RescheduleStatus = 'PENDING' | 'APPROVED' | 'DECLINED';
export type TimeOfDay = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANY';
export type StageDecisionType = 'ADVANCE' | 'HOLD' | 'REJECT';
export type Recommendation = StageDecisionType;
export type SenderKind = 'HR_USER' | 'CANDIDATE' | 'ETT_STAFF' | 'SYSTEM';
export type EvidenceStrength = 'RECENT_ROLE' | 'OLDER_ROLE' | 'SKILLS_ONLY' | 'MENTION_ONLY' | 'NEGATED' | 'NOT_FOUND';
export type UploadStatus = 'QUEUED' | 'READING' | 'NEEDS_DETAILS' | 'ADDED' | 'DUPLICATE' | 'FAILED';
export type Role = 'admin' | 'recruiter' | 'hiring_manager' | 'interviewer' | 'panel';
export type PanelRole = 'lead' | 'panelist' | 'observer';
export type ReportType = 'shortlisting' | 'ai_interview' | 'partner' | 'panel';

/** What HR should do next for a candidate. The same value drives every list and the "Decide now" panel. */
export type NextAction =
  | 'answer_reschedule'
  | 'review_flags'
  | 'decide_shortlist'
  | 'schedule_round'
  | 'decide_round'
  | 'record_offer'
  | 'waiting'
  | 'none';

export interface Me {
  id: Uuid;
  fullName: string;
  email: string;
  role: Role;
  canManageHiring: boolean;
  company: { id: Uuid; name: string; country: string | null };
  mustChangePassword: boolean;
  /** Shortlist and round decisions (HR admins, recruiters, hiring managers). */
  canDecide: boolean;
  /** HR admins see every job; others see their own and any they're covering. */
  seesAllJobs: boolean;
}

export interface TeamMember {
  id: Uuid;
  fullName: string;
  email: string;
  role: 'ADMIN' | 'RECRUITER' | 'HIRING_MANAGER' | 'INTERVIEWER' | 'PANEL';
  status: 'INVITED' | 'ACTIVE' | 'DISABLED';
  twoStepSetUp: boolean;
  mustChangePassword: boolean;
  lastSignInAt: IsoInstant | null;
  addedAt: IsoInstant | null;
  me: boolean;
}

/** A login with its one-time password, shown once. */
export interface CreatedLogin {
  id: Uuid;
  fullName: string;
  email: string;
  role: TeamMember['role'];
  oneTimePassword: string;
}

export interface PasswordStepResult {
  /** 'done' for the shared panel login, which has no authenticator step, or when two-step verification is off. */
  nextStep: 'totp' | 'totp_setup' | 'done';
  otpauthUri: string | null;
  secret: string | null;
}

export interface Branding {
  companyName: string | null;
  hasLogo: boolean;
  logoUpdatedAt: IsoInstant | null;
  sampleReports: boolean;
}

// ---------------------------------------------------------------- jobs

export interface JobCounts {
  cvs: number;
  screened: number;
  aiShortlist: number;
  shortlisted: number;
  interviews: number;
}

/** Candidates in one round of a job right now, and how many are past the round's target time. */
export interface RoundCount {
  round: number;
  name: string;
  kind: RoundKind;
  count: number;
  overdue: number;
}

export interface JobPipeline {
  screening: number;
  shortlisted: number;
  rounds: RoundCount[];
  offer: number;
  hired: number;
  notProgressing: number;
  withdrawn: number;
}

export interface PersonRef {
  id: Uuid;
  name: string | null;
}

/** Who created and last changed a record (from the audit trail). */
/** Where a candidate is: from the CV, the AI interview or HR. */
export type LocationSource = 'cv' | 'ai_interview' | 'hr';

/**
 * known: placed on the map; unplaced: written but not found; ask_ai: not known yet, the AI interviewer will ask;
 * missing: not known.
 */
export type LocationStatus = 'known' | 'unplaced' | 'ask_ai' | 'missing';

export interface LocationSummary {
  text: string | null;
  /** "Sivakasi, Virudhunagar" */
  label: string | null;
  source: LocationSource | null;
  status: LocationStatus;
}

/** The candidate page's location, with the nearest partner assessment centre city. */
export interface CandidateLocation {
  text: string | null;
  /** "Sivakasi, Virudhunagar, Tamil Nadu 626123" */
  label: string | null;
  source: LocationSource | null;
  status: LocationStatus;
  updatedAt: IsoInstant | null;
  nearestCity: string | null;
  nearestCityMinutes: number | null;
  nearestCityKm: number | null;
  nearestCityCentres: number;
}

export interface Changes {
  createdByName: string | null;
  createdAt: IsoInstant | null;
  lastUpdatedByName: string | null;
  lastUpdatedAt: IsoInstant | null;
}

export interface JobSummary {
  id: Uuid;
  jobRef: string;
  title: string;
  location: string | null;
  contractType: ContractType | null;
  openings: number | null;
  status: JobStatus;
  publishedAt: IsoInstant | null;
  createdAt: IsoInstant;
  counts: JobCounts;
  aiShortlisting: boolean;
  statusReason: string | null;
  owner: PersonRef | null;
  hiringManager: PersonRef | null;
  changes: Changes | null;
  rounds: RoundCount[];
  /** Starred by the signed-in person (stars are personal). */
  starred: boolean;
}

export interface Criterion {
  id: Uuid;
  kind: CriterionKind;
  value: string;
  minYears: number | null;
  alsoMatches: string[];
}

/** One round of a job's interview plan. */
export interface Round {
  round: number;
  name: string;
  kind: RoundKind;
  mode: InterviewMode | null;
  durationMin: number | null;
  targetDays: number | null;
}

export interface RoundInput {
  name: string;
  kind: RoundKind;
  mode: InterviewMode;
  durationMin: number;
  targetDays: number | null;
}

export interface JobDetail extends Omit<JobSummary, 'counts' | 'rounds' | 'changes'> {
  minExperienceYrs: number | null;
  shortlistConfirmedAt: IsoInstant | null;
  createdByName: string | null;
  criteria: Criterion[];
  rounds: Round[];
  counts: JobCounts;
  criteriaLocked: boolean;
  criteriaVersion: number;
  maxNoticeDays: number | null;
  maxCtcLpa: number | null;
  acceptedLocations: string[];
  pipeline: JobPipeline;
  description: string | null;
  descriptionFileName: string | null;
  statusChangedAt: IsoInstant | null;
  statusChangedByName: string | null;
  /** Rounds up to this one already have candidates: their type and order can't change. */
  roundsLockedUpTo: number;
  changes: Changes;
  /** Set when the signed-in person sees this job because they're covering for its owner. */
  coveringFor: string | null;
}

export interface PersonOption {
  id: Uuid;
  fullName: string;
  role: Role;
}

export interface DescriptionRead {
  text: string | null;
  fileName: string | null;
  mustHave: string[];
  niceToHave: string[];
}

export interface CriterionInput {
  kind: CriterionKind;
  value: string;
  minYears?: number | null;
}

export interface JobRequest {
  title: string;
  location: string | null;
  contractType: ContractType | null;
  openings: number | null;
  minExperienceYrs: number | null;
  criteria: CriterionInput[];
  rounds: RoundInput[];
  maxNoticeDays: number | null;
  maxCtcLpa: number | null;
  acceptedLocations: string[];
  rescore?: boolean;
  aiShortlisting: boolean;
  description: string | null;
  descriptionFileName: string | null;
  hiringManagerId: Uuid | null;
  ownerId?: Uuid | null;
}

export interface CriteriaCheckItem {
  kind: CriterionKind;
  value: string;
  display: string;
  alsoMatches: string[];
  warning: string | null;
  blocking: boolean;
}

export interface CriteriaCheck {
  items: CriteriaCheckItem[];
  blocking: boolean;
}

// ---------------------------------------------------------------- CVs and screening

export interface ApplicationRow {
  id: Uuid;
  candidateId: Uuid;
  candidateRef: string;
  candidateName: string;
  city: string | null;
  country: string | null;
  cvStatus: CvStatus | null;
  source: ApplicationSource | null;
  addedAt: IsoInstant;
  matchScore: number | null;
  suggestion: AiSuggestion | null;
  mustHaveMatched: number;
  mustHaveTotal: number;
  experienceYears: number | null;
  certificationsFound: string | null;
  decision: ShortlistDecision | null;
  decidedAt: IsoInstant | null;
  stage: ApplicationStage;
  status: ApplicationStatus;
  mustHaveBacked: number;
  summary: string | null;
  reason: string | null;
  redFlags: number;
  redFlagsOpen: number;
  checkFlags: number;
  sourceDetail: string | null;
  criteriaVersion: number | null;
  nextAction: NextAction;
  location: LocationSummary;
}

export interface CandidateDetails {
  fullName: string | null;
  email: string | null;
  mobile: string | null;
  city: string | null;
  country: string | null;
}

export interface UploadRow {
  id: Uuid;
  batchId: Uuid;
  fileName: string;
  status: UploadStatus;
  errorCode: string | null;
  message: string | null;
  applicationId: Uuid | null;
  candidateName: string | null;
  matchScore: number | null;
  suggestion: AiSuggestion | null;
  detected: CandidateDetails | null;
  queuedAt: IsoInstant;
  finishedAt: IsoInstant | null;
  existingCandidate: { candidateId: Uuid; candidateRef: string; fullName: string; jobs: number } | null;
}

export interface Evidence {
  id: Uuid;
  criterionId: Uuid;
  kind: CriterionKind;
  value: string;
  matched: boolean;
  quote: string | null;
  flagged: boolean;
  flagNote: string | null;
  flaggedByName: string | null;
  strength: EvidenceStrength | null;
  strengthLabel: string | null;
  points: number | null;
  cvSection: string | null;
  matchedTerm: string | null;
  minYears: number | null;
}

export interface Flag {
  id: Uuid;
  severity: 'red' | 'check';
  kind: string;
  message: string;
  quote: string | null;
  reviewOutcome: 'fine' | 'concern' | null;
  reviewNote: string | null;
  reviewedByName: string | null;
  reviewedAt: IsoInstant | null;
}

export interface Screening {
  id: Uuid;
  modelVersion: string;
  screenedAt: IsoInstant;
  matchScore: number;
  mustHavePts: number;
  niceToHavePts: number;
  experiencePts: number;
  certificationPts: number;
  experienceYears: number | null;
  certificationsFound: string | null;
  suggestion: AiSuggestion;
  evidence: Evidence[];
  criteriaVersion: number | null;
  totalYears: number | null;
  relevantYears: number | null;
  summary: string | null;
  reason: string | null;
  readingQuality: 'good' | 'check' | null;
  shortlistCutoff: number | null;
  reserveCutoff: number | null;
  flags: Flag[];
}

export interface ShortlistHistoryItem {
  decision: ShortlistDecision;
  aiSuggestion: AiSuggestion | null;
  decidedByName: string | null;
  decidedAt: IsoInstant | null;
  reason: string | null;
}

// ---------------------------------------------------------------- interviews

export interface InterviewRow {
  id: Uuid;
  applicationId: Uuid;
  jobId: Uuid;
  jobTitle: string;
  candidateName: string;
  candidateCountry: string | null;
  round: number;
  mode: InterviewMode | null;
  startAt: IsoInstant | null;
  candidateTz: string | null;
  durationMin: number | null;
  status: InterviewStatus;
  replyBy: IsoInstant | null;
  invitedAt: IsoInstant | null;
  pendingRequestId: Uuid | null;
  scheduledByName: string | null;
  meetingLink: string | null;
  arriveBy: IsoInstant | null;
  centreName: string | null;
  centreAddress: string | null;
  assessorName: string | null;
  panelSize: number;
  feedbackCount: number;
  completedAt: IsoInstant | null;
  roundName: string | null;
  roundKind: RoundKind | null;
  venue: string | null;
  /** Panel rounds: the code panel members enter with their employee ID. */
  panelCode: string | null;
  panel: PanelMemberRef[];
  changes: Changes | null;
  /** Why HR booked a partner assessment centre outside the candidate's nearest centre city. */
  centreNote: string | null;
  /** When the panel's interview code opens and stops working. */
  panelCodeFrom: IsoInstant | null;
  panelCodeUntil: IsoInstant | null;
}

/** Someone on an interview's panel, as HR typed them in. */
export interface PanelMemberRef {
  id: Uuid;
  employeeId: string;
  fullName: string;
  email: string;
  reviewSubmitted: boolean;
  reviewStarted: boolean;
}

export interface MemberInput {
  employeeId: string;
  fullName: string;
  email: string;
}

export interface ReadyRow {
  applicationId: Uuid;
  jobId: Uuid;
  jobTitle: string;
  jobRef: string;
  candidateName: string;
  candidateRef: string;
  city: string | null;
  round: number;
  roundName: string | null;
  roundKind: RoundKind | null;
  matchScore: number | null;
  readySince: IsoInstant | null;
  dueAt: IsoInstant | null;
  location: LocationSummary;
}

export interface SlotView {
  id: Uuid;
  startAt: IsoInstant;
  endAt: IsoInstant;
  seatsLeft: number;
  /** Assessors at the centre with no other interview at this time. */
  freeAssessorIds: Uuid[];
}

export interface CentreView {
  id: Uuid;
  name: string;
  address: string | null;
  city: string | null;
  postcode: string | null;
  stepFreeAccess: boolean | null;
  partnerOrg: string | null;
  timeZone: string;
  /** In the candidate's nearest centre city. */
  inNearestCity: boolean;
  recommended: boolean;
  roadKm: number | null;
  travelMinutes: number | null;
  firstSuitableSlotId: Uuid | null;
  /** One of several centres in the nearest city that take turns (round robin). */
  inRotation: boolean;
  /** Place in the queue (1 = next in turn); null if it sits out this turn or isn't in the rotation. */
  turn: number | null;
  /** When a candidate was last booked here. */
  lastAssignedAt: string | null;
  reasons: string[];
  slots: SlotView[];
  assessors: { id: Uuid; fullName: string }[];
}

/** Partner assessment centres for one candidate: the nearest centre city's centres, and other cities on request. */
export interface CentreOptions {
  candidate: {
    text: string | null;
    label: string | null;
    source: LocationSource | null;
    found: boolean;
  };
  nearestCity: string | null;
  nearestCityMinutes: number | null;
  nearestCityKm: number | null;
  otherCitiesShown: boolean;
  /** How many other cities have centres. */
  otherCities: number;
  method: string;
  centres: CentreView[];
}

export interface PlanItem {
  applicationId: Uuid;
  candidateName: string;
  city: string | null;
  country: string | null;
  candidateZone: string;
  matchScore: number | null;
  interviewId: Uuid | null;
  status: InterviewStatus | null;
  startAt: IsoInstant | null;
  replyBy: IsoInstant | null;
  suggested: boolean;
}

export interface Round1Plan {
  jobId: Uuid;
  jobTitle: string;
  jobRef: string;
  round: number;
  roundName: string;
  durationMin: number;
  mode: InterviewMode;
  hrZone: string;
  defaultReplyBy: IsoInstant;
  items: PlanItem[];
}

export interface RescheduleDetail {
  id: Uuid;
  status: RescheduleStatus;
  reason: RescheduleReason | null;
  note: string | null;
  availableDays: IsoDate[];
  timeOfDay: TimeOfDay | null;
  requestedAt: IsoInstant;
  newStartAt: IsoInstant | null;
  resolvedByName: string | null;
  interview: InterviewRow;
  suggestions: IsoInstant[];
  /** The dates the candidate said they're free (from their candidate page). */
  availability: Availability[];
  requestedByCandidate: boolean;
}

/** A date a candidate is free: morning, afternoon, any time, or a from-to range (HH:mm). */
export interface Availability {
  date: IsoDate;
  window: 'morning' | 'afternoon' | 'any' | 'range';
  from: string | null;
  to: string | null;
}

// ---------------------------------------------------------------- reports and decisions

export interface Competency {
  competency: string;
  aiDraftScore: number | null;
  finalScore: number | null;
  reviewerComment: string | null;
}

export interface Round1Report {
  id: Uuid;
  interviewId: Uuid;
  interviewAt: IsoInstant | null;
  reviewerName: string | null;
  aiOverall: number | null;
  finalOverall: number | null;
  recommendation: Recommendation | null;
  taskSummary: string | null;
  integrityStatus: 'CLEAR' | 'FLAGGED' | 'UNDER_REVIEW' | null;
  reviewerNotes: string | null;
  releasedAt: IsoInstant | null;
  competencies: Competency[];
  identityCheck: 'verified' | 'mismatch' | 'not_done' | null;
  identityNote: string | null;
  surroundingsCheck: 'clear' | 'concern' | 'not_done' | null;
  surroundingsNote: string | null;
  highlights: string[];
  sample: boolean;
  recheckRequestedAt: IsoInstant | null;
  recheckNote: string | null;
  status: 'released' | 'in_review' | 'draft';
  /** Where the candidate told the AI interviewer they are. */
  candidateLocation: string | null;
  candidatePin: string | null;
  /** Asked by the AI interviewer because the CV left them out. */
  candidateNoticeDays: number | null;
  candidateExpectedCtcLpa: number | null;
}

export interface Round2Report {
  interviewId: Uuid;
  interviewAt: IsoInstant | null;
  centreName: string | null;
  assessorName: string | null;
  score: number | null;
  recommendation: Recommendation | null;
  idChecked: boolean | null;
  idCheckNote: string | null;
  areaScores: Record<string, number>;
  notes: string | null;
  submittedAt: IsoInstant | null;
  sample: boolean;
}

/** One score on a panel review: a skill from the job or a general measure (1-5). */
export interface ReviewScore {
  name: string;
  score: number | null;
  comment: string | null;
}

export type PanelRecommendation = 'strong_hire' | 'hire' | 'no_hire' | 'strong_no_hire';

export interface MemberReview {
  memberId: Uuid;
  fullName: string;
  employeeId: string;
  email: string;
  submittedAt: IsoInstant | null;
  overallScore: number | null;
  recommendation: PanelRecommendation | null;
  skills: ReviewScore[];
  measures: ReviewScore[];
  strengths: string | null;
  concerns: string | null;
  questions: string | null;
  summary: string | null;
  nextRoundNotes: string | null;
  sample: boolean;
}

export interface ScoreAverage {
  name: string;
  mustHave: boolean;
  average: number | null;
  scored: number;
}

/** All the panel's reviews for one round, combined. */
export interface PanelReport {
  interviewId: Uuid;
  round: number;
  roundName: string | null;
  interviewAt: IsoInstant | null;
  members: number;
  submitted: number;
  overallAverage: number | null;
  recommendations: Record<PanelRecommendation, number>;
  combined: Recommendation | null;
  skills: ScoreAverage[];
  measures: ScoreAverage[];
  reviews: MemberReview[];
  sample: boolean;
}

export interface StageDecisionView {
  id: Uuid;
  stage: ApplicationStage;
  decision: StageDecisionType;
  reason: string | null;
  decidedByName: string | null;
  decidedAt: IsoInstant | null;
  challengedAt: IsoInstant | null;
  /** Until when the decision can be undone (the candidate's email waits until then); null if it can't. */
  undoUntil: IsoInstant | null;
}

export interface ReviewView {
  reportType: ReportType;
  outcome: 'agree' | 'override' | 'recheck';
  note: string | null;
  reviewedByName: string | null;
  reviewedAt: IsoInstant | null;
}

export interface ReportRow {
  applicationId: Uuid;
  jobId: Uuid;
  jobTitle: string;
  jobRef: string;
  candidateName: string;
  candidateRef: string;
  type: ReportType;
  round: number;
  roundName: string | null;
  readyAt: IsoInstant | null;
  score: number | null;
  recommendation: string | null;
  sample: boolean;
  redFlags: number;
  redFlagsOpen: number;
  toReview: boolean;
  decision: string | null;
  decidedAt: IsoInstant | null;
  recheckRequested: boolean;
}

export interface ReportCounts {
  shortlisting: number;
  aiInterview: number;
  partner: number;
  panel: number;
  total: number;
}

// ---------------------------------------------------------------- candidates

export interface MessageView {
  id: Uuid;
  applicationId: Uuid;
  interviewId: Uuid | null;
  jobId: Uuid;
  candidateName: string;
  jobTitle: string;
  senderKind: SenderKind;
  senderName: string | null;
  direction: 'INBOUND' | 'OUTBOUND';
  subject: string | null;
  body: string | null;
  sentAt: IsoInstant | null;
  readAt: IsoInstant | null;
  deliveryStatus: string | null;
}

export interface ApplicationDetail {
  id: Uuid;
  candidate: {
    id: Uuid;
    candidateRef: string;
    fullName: string;
    email: string;
    mobile: string | null;
    city: string | null;
    country: string | null;
  };
  job: {
    id: Uuid;
    jobRef: string;
    title: string;
    status: JobStatus;
    aiShortlisting: boolean;
    statusReason: string | null;
    roundsTotal: number;
  };
  stage: ApplicationStage;
  status: ApplicationStatus;
  cvStatus: CvStatus | null;
  cvAvailable: boolean;
  cvDeleteAfter: IsoDate | null;
  addedAt: IsoInstant;
  addedByName: string | null;
  screening: Screening | null;
  shortlistHistory: ShortlistHistoryItem[];
  interviews: InterviewRow[];
  rounds: RoundDetail[];
  stageDecisions: StageDecisionView[];
  messages: MessageView[];
  source: ApplicationSource | null;
  sourceDetail: string | null;
  statusReason: string | null;
  nextStep: NextStep;
  reviews: ReviewView[];
  otherJobs: {
    applicationId: Uuid;
    jobId: Uuid;
    jobTitle: string;
    jobRef: string;
    stage: ApplicationStage;
    status: ApplicationStatus;
  }[];
  changes: Changes;
  coveringFor: string | null;
  location: CandidateLocation;
}

export interface NextStep {
  action: NextAction;
  round: number | null;
  roundName: string | null;
  roundKind: RoundKind | null;
  pendingRequestId: Uuid | null;
  redFlagsOpen: number;
  dueAt: IsoInstant | null;
  overdue: boolean;
}

/** One round of the job's plan for this candidate, with its interview, report and decision. */
export interface RoundDetail {
  round: number;
  name: string;
  kind: RoundKind;
  mode: InterviewMode | null;
  durationMin: number | null;
  targetDays: number | null;
  state: 'passed' | 'current' | 'upcoming' | 'stopped' | 'not_reached';
  dueAt: IsoInstant | null;
  overdue: boolean;
  interview: InterviewRow | null;
  aiReport: Round1Report | null;
  partnerReport: Round2Report | null;
  panelReport: PanelReport | null;
  decision: StageDecisionView | null;
}

export interface DirectoryEntry {
  applicationId: Uuid;
  candidateId: Uuid;
  candidateRef: string;
  candidateName: string;
  email: string | null;
  city: string | null;
  country: string | null;
  jobId: Uuid;
  jobTitle: string;
  jobRef: string;
  stage: ApplicationStage;
  status: ApplicationStatus;
  matchScore: number | null;
  suggestion: AiSuggestion | null;
  decision: ShortlistDecision | null;
  source: ApplicationSource | null;
  sourceDetail: string | null;
  addedAt: IsoInstant;
  nextAction: NextAction;
  openRedFlags: number;
  flags: number;
  currentRound: number | null;
  roundName: string | null;
  dueAt: IsoInstant | null;
  overdue: boolean;
  changes: Changes | null;
  location: LocationSummary;
}

export interface DirectoryPage {
  items: DirectoryEntry[];
  total: number;
  page: number;
  size: number;
}

export interface KeyFacts {
  noticePeriodDays: number | null;
  servingNotice: boolean | null;
  lastWorkingDay: IsoDate | null;
  currentCtcLpa: number | null;
  expectedCtcLpa: number | null;
  currentLocation: string | null;
  preferredLocations: string[];
  totalExperienceYears: number | null;
  relevantExperienceYears: number | null;
  willingToRelocate: boolean | null;
  workMode: string | null;
  /** Where the notice period and expected CTC came from. */
  noticeSource?: 'cv' | 'ai_interview' | 'hr' | null;
  ctcSource?: 'cv' | 'ai_interview' | 'hr' | null;
}

export interface Note {
  id: Uuid;
  body: string;
  authorName: string | null;
  mine: boolean;
  createdAt: IsoInstant;
}

export interface TimelineEvent {
  at: IsoInstant;
  actorKind: 'hr_user' | 'ett_staff' | 'candidate' | 'system';
  actorName: string;
  action: string;
  text: string;
}

export interface MessageTemplate {
  key: string;
  label: string;
  subject: string;
  body: string;
}

// ---------------------------------------------------------------- notifications, overview, settings

export interface NotificationView {
  id: Uuid;
  kind: string;
  title: string;
  body: string | null;
  link: string | null;
  jobId: Uuid | null;
  applicationId: Uuid | null;
  createdAt: IsoInstant;
  readAt: IsoInstant | null;
}

// ---------------------------------------------------------------- dashboard

export interface ToDo {
  kind: string;
  title: string;
  detail: string;
  link: string;
  at: IsoInstant | null;
  overdue: boolean;
}

export interface ToDoGroup {
  key: 'needs_attention' | 'to_book';
  count: number;
  items: ToDo[];
}

export interface TodayInterview {
  interviewId: Uuid;
  applicationId: Uuid;
  startAt: IsoInstant;
  durationMin: number | null;
  candidateName: string;
  jobTitle: string;
  roundLabel: string;
  kind: RoundKind | null;
  mode: string | null;
  where: string | null;
  panelSize: number;
  status: string;
  /** The signed-in person is on this interview's panel. */
  onPanel: boolean;
  /** The meeting link, given only to panel members. */
  meetingLink: string | null;
}

export interface MessagePreview {
  id: Uuid;
  applicationId: Uuid;
  candidateName: string;
  jobTitle: string;
  subject: string | null;
  preview: string;
  at: IsoInstant;
}

export interface Dashboard {
  firstName: string;
  companyName: string;
  timeZone: string;
  needsAttention: ToDoGroup;
  toBook: ToDoGroup;
  messages: { unread: number; latest: MessagePreview[] };
  today: TodayInterview[];
  covering: string[];
}

export interface CloseImpact {
  interviewsToCancel: number;
  candidatesWithInterviews: number;
  otherCandidates: number;
}

// ---------------------------------------------------------------- leave cover

export interface LeaveCover {
  id: Uuid;
  userId: Uuid;
  userName: string;
  coverUserId: Uuid;
  coverUserName: string;
  startsOn: IsoDate;
  endsOn: IsoDate;
  note: string | null;
  state: 'upcoming' | 'active' | 'finished' | 'ended';
  canChange: boolean;
  createdByName: string | null;
}

// ---------------------------------------------------------------- audit trail

export interface AuditEntry {
  id: number;
  at: IsoInstant;
  who: string;
  actorKind: string | null;
  action: string;
  type: 'job' | 'candidate' | 'interview';
  recordName: string | null;
  detail: string | null;
  link: string | null;
  changes: { field: string; from: string | null; to: string | null }[];
}

export interface AuditPage {
  items: AuditEntry[];
  more: boolean;
}

// ---------------------------------------------------------------- settings

export interface CompanyDetails {
  name: string;
  address: string | null;
  timeZone: string;
  country: string | null;
}

export interface EmailTemplate {
  key: string;
  label: string;
  description: string;
  placeholders: string[];
  subject: string;
  body: string;
  customised: boolean;
  defaultSubject: string;
  defaultBody: string;
}

export interface CentreAdminView {
  id: Uuid;
  name: string;
  address: string | null;
  city: string;
  postcode: string | null;
  country: string | null;
  latitude: number | null;
  longitude: number | null;
  timeZone: string;
  stepFreeAccess: boolean | null;
  partnerOrg: string | null;
  active: boolean;
  editable: boolean;
  assessors: { id: Uuid; fullName: string; email: string | null; active: boolean }[];
  slotsNext4Weeks: number;
  seatsNext4Weeks: number;
  bookedNext4Weeks: number;
  nextSlotAt: IsoInstant | null;
}

export interface CentreSlot {
  id: Uuid;
  startAt: IsoInstant;
  endAt: IsoInstant;
  seats: number;
  booked: number;
}

// ---------------------------------------------------------------- panel login

export interface ReviewInput {
  skills: ReviewScore[];
  measures: ReviewScore[];
  strengths: string | null;
  concerns: string | null;
  questions: string | null;
  summary: string | null;
  nextRoundNotes: string | null;
  recommendation: PanelRecommendation | null;
}

export interface PanelReviewPage {
  interviewId: Uuid;
  candidateName: string;
  jobTitle: string;
  companyName: string;
  round: number;
  roundName: string | null;
  kind: RoundKind | null;
  mode: InterviewMode | null;
  startAt: IsoInstant;
  durationMin: number | null;
  meetingLink: string | null;
  venue: string | null;
  cvAvailable: boolean;
  jobDescription: string | null;
  skills: { name: string; mustHave: boolean }[];
  measures: string[];
  memberName: string;
  employeeId: string;
  review: ReviewInput | null;
  submittedAt: IsoInstant | null;
  overallScore: number | null;
  editable: boolean;
  openFrom: IsoInstant;
  editableUntil: IsoInstant;
  closedReason: string | null;
}

// ---------------------------------------------------------------- candidate page

export interface CandidatePage {
  firstName: string;
  jobTitle: string;
  companyName: string;
  timeZone: string;
  interview: {
    id: Uuid;
    round: number;
    roundLabel: string;
    kind: RoundKind | null;
    status: string;
    startAt: IsoInstant | null;
    timeZone: string | null;
    durationMin: number | null;
    format: string;
    meetingLink: string | null;
    venue: string | null;
    centreName: string | null;
    centreAddress: string | null;
    arriveBy: IsoInstant | null;
    replyBy: IsoInstant | null;
    canAccept: boolean;
    canAskNewTime: boolean;
    pendingRequest: { requestedAt: IsoInstant; reason: string; availability: Availability[]; note: string | null } | null;
  } | null;
  messages: { id: Uuid; from: string; fromCandidate: boolean; subject: string | null; body: string; at: IsoInstant }[];
  linkExpiresAt: IsoInstant;
  applicationActive: boolean;
}

export interface Thresholds {
  shortlistCutoff: number;
  reserveCutoff: number;
  gapFlagMonths: number;
  shortJobMonths: number;
}

export interface SkillView {
  id: Uuid | null;
  skill: string;
  otherNames: string[];
  custom: boolean;
  alsoCounts: string[];
}

export interface Accuracy {
  screened: number;
  decided: number;
  agreed: number;
  agreementPct: number | null;
  bySuggestion: {
    suggestion: 'shortlist' | 'review' | 'reject';
    screened: number;
    hrShortlisted: number;
    hrRejected: number;
    hrHold: number;
    passedRound1: number;
  }[];
  byScore: { band: string; from: number; to: number; screened: number; hrShortlisted: number; passedRound1: number }[];
  shortlistCutoff: number;
  reserveCutoff: number;
}

export interface AssessorLinkView {
  candidateName: string;
  jobTitle: string;
  companyName: string;
  startAt: IsoInstant | null;
  centreName: string | null;
  assessorName: string | null;
  areas: string[];
  submitted: boolean;
  expired: boolean;
}
