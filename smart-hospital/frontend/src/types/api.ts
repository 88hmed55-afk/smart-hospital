export interface Branch {
  id: string;
  code: string;
  name: string;
  timezone: string;
  defaultCurrency: string;
  active: boolean;
}

export interface CurrentSession {
  userId: string;
  staffId: string | null;
  username: string;
  roleCodes: string[];
  branchIds: string[];
  defaultBranchId: string;
  mfaEnabled: boolean;
  mfaEnrollmentRequired: boolean;
}

export interface LoginResult {
  token: string | null;
  tokenType: string;
  expiresAt: string | null;
  mustChangePassword: boolean;
  passwordRotationEnforced: boolean;
  mfaRequired: boolean;
  mfaChallengeToken: string | null;
  mfaEnrollmentRequired: boolean;
  user: CurrentSession | null;
}

export interface MfaEnrollmentStart {
  secret: string;
  otpauthUri: string;
  issuer: string;
  account: string;
  digits: number;
  period: number;
  algorithm: string;
  pendingSince: string;
}

export interface MfaEnrollmentConfirm {
  recoveryCodes: string[];
  activatedAt: string;
}

export interface MfaRecoveryCodes {
  recoveryCodes: string[];
  generatedAt: string;
}

export interface MfaStatus {
  enabled: boolean;
  recoveryCodesRemaining: number;
  enrolledAt: string | null;
}

export interface DashboardMetrics {
  branchId: string;
  branchName: string;
  currencyCode: string;
  periodStart: string;
  periodEnd: string;
  appointmentsToday: number;
  visitsToday: number;
  checkedInAppointments: number;
  activeAdmissions: number;
  labTurnaroundSamples: number;
  operationalBeds: number;
  occupiedBeds: number;
  bedOccupancyPercent: number;
  pendingLabItems: number;
  averageLabTurnaroundMinutes: number;
  lowStockMedicationPolicies: number;
  postedRevenueToday: number;
  openDecisionCards: number;
}

export type DecisionCardStatus = 'OPEN' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'COMPLETED' | 'DISMISSED';

export interface DecisionCard {
  id: string;
  cardCode: string;
  category: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  status: DecisionCardStatus;
  title: string;
  problemStatement: string;
  analysisSummary: string;
  recommendation: string;
  evidenceWindowStart: string;
  evidenceWindowEnd: string;
  observedValue: number;
  thresholdValue: number;
  unit: string;
  confidencePercent: number;
  assumptionsJson: string;
  expectedImpactJson: string;
  generatedAt: string;
}

export interface ApiProblem {
  code?: string;
  message?: string;
  detail?: string;
}


export interface PageResponse<T> {
  items: T[];
  total: number;
  offset: number;
  limit: number;
}

export interface PatientSummary {
  id: string;
  mrn: string;
  firstName: string;
  middleName?: string | null;
  lastName: string;
  dateOfBirth?: string | null;
  sex: string;
  primaryPhone?: string | null;
  status: string;
}

export interface PatientContact {
  id: string;
  type: string;
  name?: string | null;
  relationship?: string | null;
  phoneNumber?: string | null;
  email?: string | null;
  primary: boolean;
}

export interface PatientAllergy {
  id: string;
  allergenName: string;
  reaction?: string | null;
  severity: string;
  verificationStatus: string;
  recordedAt: string;
}

export interface PatientRecord extends PatientSummary {
  bloodGroup?: string | null;
  email?: string | null;
  addressText?: string | null;
  preferredLanguage: string;
  createdAt: string;
  contacts: PatientContact[];
  allergies: PatientAllergy[];
}

export interface Department {
  id: string;
  branchId: string;
  code: string;
  name: string;
  departmentType: string;
  active: boolean;
}

export interface DoctorOption {
  staffId: string;
  employeeNumber: string;
  fullName: string;
  departmentId: string | null;
  departmentName: string | null;
  specialty: string;
}

export interface AppointmentItem {
  id: string;
  branchId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  doctorStaffId: string;
  doctorName: string;
  departmentId: string;
  departmentName: string;
  startsAt: string;
  endsAt: string;
  reason?: string | null;
  status: string;
}

export interface SystemSetting {
  key: string;
  label: string;
  description: string;
  unit: string;
  defaultValue: number;
  globalValue: number | null;
  branchValue: number | null;
  effectiveValue: number;
  effectiveSource: 'BRANCH' | 'GLOBAL' | 'DEFAULT';
  minimum: number;
  maximum: number;
  updatedAt: string | null;
}

export interface AuditLogItem {
  id: string;
  actorUsername: string;
  branchId: string | null;
  branchName: string | null;
  actionCode: string;
  resourceType: string;
  resourceId: string | null;
  outcome: 'SUCCESS' | 'DENIED' | 'FAILURE';
  occurredAt: string;
}

export interface NotificationItem {
  id: string;
  notificationType: string;
  title: string;
  body: string;
  referenceType: string | null;
  referenceId: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationUnreadCount {
  unreadCount: number;
  updatedCount: number;
}
