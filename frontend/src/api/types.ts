/* Backend DTO'lariga mos tiplar. Pul summalari JSON'da number bo'lib keladi (so'm). */

export type Role = 'ADMIN' | 'CREDIT_OFFICER' | 'COLLECTOR' | 'CASHIER';
export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'LATE' | 'CLOSED' | 'CANCELLED';
export type ScheduleStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'LATE';
export type RiskCategory = 'A' | 'B' | 'C' | 'D';
export type Decision = 'APPROVED' | 'REVIEW' | 'REJECTED';
export type Factor = 'INCOME' | 'TENURE' | 'FAMILY' | 'HISTORY' | 'DISCIPLINE' | 'GUARANTOR';
export type StopFactor =
  | 'BLACKLIST' | 'FRAUD' | 'DUP_PINFL' | 'PASSPORT_EXPIRED'
  | 'INCOME_NOT_VERIFIED' | 'COURT' | 'OVERDUE' | 'OVERLIMIT';
export type PaymentMethod = 'CASH' | 'CARD' | 'PAYME' | 'CLICK' | 'UZUM' | 'BANK_TRANSFER';
export type Channel = 'SMS' | 'TELEGRAM' | 'BOTH';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Permission =
  | 'DASHBOARD_VIEW' | 'CLIENTS_VIEW' | 'CLIENTS_EDIT'
  | 'CONTRACTS_VIEW' | 'CONTRACTS_CREATE' | 'CONTRACTS_APPROVE' | 'CONTRACTS_CANCEL'
  | 'PAYMENTS_VIEW' | 'PAYMENTS_ACCEPT' | 'PRODUCTS_VIEW' | 'PRODUCTS_EDIT'
  | 'COLLECTION_VIEW' | 'COLLECTION_WORK' | 'COLLECTION_ASSIGN' | 'COLLECTION_LEGAL'
  | 'NOTIFICATIONS_VIEW' | 'NOTIFICATIONS_EDIT'
  | 'KPI_VIEW_ALL' | 'KPI_MANAGE' | 'FORECAST_VIEW'
  | 'BRANCHES_VIEW' | 'BRANCHES_MANAGE' | 'ALL_BRANCHES'
  | 'USERS_MANAGE' | 'SETTINGS_EDIT' | 'DOCUMENTS_EDIT';

export interface UserView {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  active: boolean;
  branchId?: number;
  branchName?: string;
  permissions: Permission[];
  /** true — xodimga rolidan farqli shaxsiy ruxsatlar berilgan */
  customPermissions: boolean;
}

export interface UserBrief {
  id: number;
  fullName: string;
  role: Role;
  branchId?: number;
  active: boolean;
}

export interface PermissionMatrix {
  permissions: { key: Permission; section: string }[];
  roles: { role: Role; permissions: Permission[]; defaults: Permission[]; customized: boolean; locked: boolean }[];
}

export interface Branch {
  id: number;
  code: string;
  name: string;
  address?: string;
  phone?: string;
  managerName?: string;
  monthlyTarget?: number;
  active: boolean;
}

export interface BranchStats extends Branch {
  staff: number;
  openContracts: number;
  lateContracts: number;
  debt: number;
  overdue: number;
  issuedCount: number;
  issued: number;
  collected: number;
  collectionRate?: number;
  planPct?: number;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  user: UserView;
}

export interface Client {
  id: number;
  fullName: string;
  pinfl: string;
  passportSeries?: string;
  passportExpiry?: string;
  birthDate?: string;
  phone: string;
  extraPhone?: string;
  region?: string;
  district?: string;
  address?: string;
  workplace?: string;
  monthlyIncome?: number;
  familyStatus?: string;
  telegramChatId?: number;
  blacklisted: boolean;
  note?: string;
  createdAt: string;
}

export type ClientRequest = Omit<Client, 'id' | 'createdAt'>;

/** /api/clients ro'yxat qatori: asosiy ma'lumot + nasiya ko'rsatkichlari */
export interface ClientListItem {
  id: number;
  fullName: string;
  pinfl: string;
  phone: string;
  region?: string;
  district?: string;
  workplace?: string;
  monthlyIncome?: number;
  blacklisted: boolean;
  createdAt: string;
  openContracts: number;
  activeDebt: number;
  monthlyObligation: number;
  hasLate: boolean;
  lastScore?: number;
  lastRisk?: RiskCategory;
  creditLimit: number;
}

export interface CreditLimitResult {
  dtiPct: number;
  maxMonthly: number;
  usedMonthly: number;
  freeMonthly: number;
  limit: number;
}

export interface ClientsSummary {
  total: number;
  withDebt: number;
  lateClients: number;
  avgScore?: number;
  gradeAPct?: number;
  totalDebt: number;
}

export interface ContractBrief {
  id: number;
  contractNo: string;
  productName: string;
  salePrice: number;
  paid: number;
  remaining: number;
  status: ContractStatus;
  riskCategory?: RiskCategory;
  nextDue?: string;
}

export interface ClientDetails {
  client: Client;
  activeDebt: number;
  contracts: ContractBrief[];
}

export interface Category {
  id: number;
  code: string;
  nameUz: string;
  nameRu: string;
}

export interface Product {
  id: number;
  categoryId: number;
  categoryCode: string;
  name: string;
  sku?: string;
  price: number;
  markupPct: number;
  termMin: number;
  termMax: number;
  stock: number;
  active: boolean;
}

export interface ScoringRequest {
  points: Partial<Record<Factor, number>>;
  incomeVerified: boolean;
  guarantorPresent: boolean;
  manualStopFactors: StopFactor[];
}

export interface ScoringResult {
  points: Record<Factor, number>;
  total: number;
  risk: RiskCategory;
  stopFactors: StopFactor[];
  decision: Decision;
}

export interface QuoteRequest {
  productId: number;
  price?: number;
  downPayment?: number;
  markupPct?: number;
  termMonths: number;
  firstDueDate?: string;
}

export interface Installment {
  seq: number;
  dueDate: string;
  amount: number;
  principalPart: number;
  markupPart: number;
}

export interface Quote {
  costPrice: number;
  downPayment: number;
  markupPct: number;
  markupAmount: number;
  salePrice: number;
  installmentTotal: number;
  termMonths: number;
  monthlyPayment: number;
  lastPayment: number;
  schedule: Installment[];
}

export interface ScheduleItem extends Installment {
  id: number;
  paidAmount: number;
  status: ScheduleStatus;
  paidAt?: string;
}

export interface ContractListItem {
  id: number;
  contractNo: string;
  clientId: number;
  clientName: string;
  clientPhone: string;
  productName: string;
  salePrice: number;
  downPayment: number;
  installmentTotal: number;
  monthlyPayment: number;
  termMonths: number;
  status: ContractStatus;
  riskCategory?: RiskCategory;
  scoreTotal?: number;
  paidTotal: number;
  remaining: number;
  nextDue?: string;
  createdAt: string;
  branchId?: number;
  branchName?: string;
}

export interface ContractDetails {
  id: number;
  contractNo: string;
  clientId: number;
  clientName: string;
  clientPhone: string;
  productName: string;
  costPrice: number;
  downPayment: number;
  markupPct: number;
  markupAmount: number;
  salePrice: number;
  installmentTotal: number;
  termMonths: number;
  monthlyPayment: number;
  status: ContractStatus;
  scoreTotal?: number;
  riskCategory?: RiskCategory;
  decision?: Decision;
  guarantorName?: string;
  guarantorPhone?: string;
  paidTotal: number;
  remaining: number;
  createdAt: string;
  closedAt?: string;
  schedule: ScheduleItem[];
  branchId?: number;
  branchName?: string;
  officerName?: string;
  collectorId?: number;
  collectorName?: string;
}

export interface PaymentDto {
  id: number;
  contractId: number;
  contractNo: string;
  clientName: string;
  amount: number;
  method: PaymentMethod;
  paidAt: string;
  externalId?: string;
  note?: string;
  cashierName?: string;
}

export type CollectionStage = 'REMINDER' | 'SOFT' | 'HARD' | 'LEGAL';

export type ActionType = 'CALL' | 'SMS' | 'VISIT' | 'LETTER' | 'LEGAL' | 'NOTE';
export type PromiseStatus = 'PENDING' | 'KEPT' | 'BROKEN';
export type LegalStatus = 'PRETRIAL' | 'FILED' | 'HEARING' | 'DECIDED' | 'ENFORCEMENT' | 'CLOSED';

export interface OverdueRow {
  contractId: number;
  contractNo: string;
  clientId: number;
  clientName: string;
  phone: string;
  overdueAmount: number;
  daysLate: number;
  stage: CollectionStage;
  lastActionDate?: string;
  lastActionType?: ActionType;
  lastResult?: string;
  remaining: number;
  monthlyPayment: number;
  overdueInstallments: number;
  branchId?: number;
  branchName?: string;
  collectorId?: number;
  collectorName?: string;
  promisedDate?: string;
  promisedAmount?: number;
  promiseStatus?: PromiseStatus;
  plannedDate?: string;
  plannedType?: ActionType;
  lettersSent: number;
  lastLetterDate?: string;
  legalCaseId?: number;
  legalStatus?: LegalStatus;
}

export interface CollectionActionDto {
  id: number;
  actionType: ActionType;
  result?: string;
  note?: string;
  promisedDate?: string;
  promisedAmount?: number;
  plannedDate?: string;
  userId?: number;
  userName?: string;
  createdAt: string;
}

export interface LegalCaseRow {
  id: number;
  contractId: number;
  contractNo: string;
  clientId: number;
  clientName: string;
  phone: string;
  pinfl: string;
  status: LegalStatus;
  claimAmount: number;
  remaining: number;
  paidSinceOpened: number;
  daysLate: number;
  courtName?: string;
  caseNo?: string;
  filedAt?: string;
  hearingAt?: string;
  decisionAt?: string;
  enforcementNo?: string;
  note?: string;
  assignedUserId?: number;
  assignedUserName?: string;
  branchName?: string;
  createdAt: string;
  updatedAt: string;
}

export type DocCode = 'CONTRACT' | 'DEMAND' | 'CLAIM';

export interface DocTemplate {
  code: DocCode;
  lang: 'uz' | 'ru';
  title: string;
  body: string;
  updatedAt: string;
}

export interface DocLogRow {
  id: number;
  code: DocCode;
  docNo: string;
  lang: string;
  amount?: number;
  userName?: string;
  createdAt: string;
}

export type KpiMetric = 'CONTRACTS' | 'SALES' | 'COLLECTED' | 'RECOVERED' | 'ACTIONS' | 'QUALITY';
export type KpiGrade = 'A' | 'B' | 'C' | 'D';

export interface KpiConfig {
  weights: Record<KpiMetric, number>;
  minScoreForBonus: number;
  bonusCapPct: number;
  achievementCapPct: number;
}

export interface KpiTargets {
  contractsCount: number;
  salesVolume: number;
  collectedAmount: number;
  recoveredAmount: number;
  actionsCount: number;
  bonusAmount: number;
}

export interface KpiMetricResult {
  metric: KpiMetric;
  actual?: number;
  target?: number;
  achievementPct?: number;
  weight: number;
  included: boolean;
}

export interface OperatorKpi {
  userId: number;
  fullName: string;
  role: Role;
  branchId?: number;
  branchName?: string;
  targets: KpiTargets;
  metrics: KpiMetricResult[];
  score?: number;
  grade?: KpiGrade;
  bonusPayout: number;
  openContracts: number;
  lateContracts: number;
  promisesMade: number;
  promisesKept: number;
  rank: number;
}

export interface KpiReport {
  summary: {
    month: string;
    operators: number;
    avgScore?: number;
    totalBonus: number;
    grades: Record<KpiGrade, number>;
    totalSales: number;
    totalCollected: number;
    totalRecovered: number;
  };
  config: KpiConfig;
  operators: OperatorKpi[];
}

export interface KpiHistoryPoint {
  month: string;
  score?: number;
  grade?: KpiGrade;
  sales: number;
  collected: number;
  recovered: number;
  contracts: number;
  actions: number;
}

export interface ForecastPoint {
  month: string;
  seasonality: number;
  scheduled: number;
  fromSchedule: number;
  fromArrears: number;
  newSales: number;
  fromNewSales: number;
  inflow: number;
  profit: number;
  netCash: number;
  cumulativeNet: number;
  debtEnd: number;
  arrearsEnd: number;
}

export interface ForecastTotals {
  inflow: number;
  profit: number;
  newSales: number;
  netCash: number;
  debtEnd: number;
  arrearsEnd: number;
}

export interface Forecast {
  assumptions: {
    onTimeRatePct: number;
    recoveryRatePct: number;
    baseMonthlySales: number;
    avgMarkupPct: number;
    avgTermMonths: number;
    openDebt: number;
    arrears: number;
    historyMonths: number;
  };
  points: ForecastPoint[];
  totals: ForecastTotals;
  scenarios: { key: 'pessimistic' | 'base' | 'optimistic'; salesGrowthPct: number; collectionAdjPct: number; totals: ForecastTotals }[];
  ecl: { key: string; ratePct: number; exposure: number; reserve: number }[];
  eclTotal: number;
  insights: { code: string; level: 'info' | 'success' | 'warn' | 'danger'; value?: string }[];
  seasonality: number[];
  customSeasonality: boolean;
  suggestedSeasonality?: number[];
}

export interface Dashboard {
  kpis: {
    totalDebt: number;
    overdue: number;
    collectedThisMonth: number;
    issuedThisMonth: number;
    todayPayments: number;
    tomorrowPayments: number;
    activeContracts: number;
    lateContracts: number;
    collectionRate?: number;
    npl90Debt: number;
  };
  portfolio: {
    contracts: number;
    contractsThisMonth: number;
    closedContracts: number;
    financed: number;
    expectedProfit: number;
    earnedProfit: number;
    remainingProfit: number;
    avgMarkupPct?: number;
    avgTermMonths?: number;
    avgTicket: number;
  };
  monthly: { month: string; collected: number; issued: number }[];
  profitForecast: { month: string; profit: number }[];
  riskMix: { category: RiskCategory; count: number }[];
  categoryMix: { code: string; nameUz: string; nameRu: string; contracts: number; debt: number }[];
}

export interface NotificationTemplate {
  id: number;
  eventKey: string;
  channel: Channel;
  enabled: boolean;
  offsetDays: number;
  textUz: string;
  textRu: string;
}

export interface NotificationLogDto {
  id: number;
  eventKey: string;
  channel: Channel;
  recipient: string;
  message: string;
  status: 'SENT' | 'FAILED' | 'SKIPPED';
  error?: string;
  createdAt: string;
}

export interface Settings {
  companyName: string;
  brandName: string;
  primaryColor: string;
  logoUrl?: string;
  contractPrefix: string;
  defaultLang: 'uz' | 'ru';
  roundingStep: number;
  maxActiveContracts: number;
  smsSender?: string;
}
