/* Backend DTO'lariga mos tiplar. Pul summalari JSON'da number bo'lib keladi (so'm). */

export type Role = 'ADMIN' | 'CREDIT_OFFICER' | 'COLLECTOR' | 'CASHIER' | 'RISK_ANALYST' | 'LEGAL_OFFICER' | 'BRANCH_MANAGER';
export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'LATE' | 'CLOSED' | 'CANCELLED';
export type ScheduleStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'LATE';
export type RiskCategory = 'A' | 'B' | 'C' | 'D';
export type Decision = 'APPROVED' | 'REVIEW' | 'REJECTED';
export type Factor = 'INCOME' | 'TENURE' | 'FAMILY' | 'HISTORY' | 'DISCIPLINE' | 'GUARANTOR';
export type StopFactor =
  | 'BLACKLIST' | 'FRAUD' | 'DUP_PINFL' | 'PASSPORT_EXPIRED'
  | 'INCOME_NOT_VERIFIED' | 'COURT' | 'OVERDUE' | 'OVERLIMIT' | 'EMPLOYEE_RESTRICTED' | 'MIB_DEBT';
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
  | 'KPI_VIEW_ALL' | 'KPI_MANAGE' | 'FORECAST_VIEW' | 'ANALYTICS_VIEW'
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
  customRoleId?: number;
  pinfl?: string;
  passportSeries?: string;
  birthDate?: string;
  homeAddress?: string;
  phone?: string;
  email?: string;
  allowNasiya: boolean;
  nasiyaNote?: string;
}

export interface CustomRole {
  id: number;
  name: string;
  description?: string;
  color: string;
  baseRole: Role;
  permissions: Permission[];
  users: number;
}

export interface CreditReport {
  id: number;
  source: string;
  reportDate: string;
  score?: number;
  rating?: 'A' | 'B' | 'C' | 'D' | 'E';
  activeLoans: number;
  totalDebt: number;
  monthlyObligation: number;
  overdueFlag: boolean;
  maxOverdueDays: number;
  note?: string;
  enteredBy?: string;
  createdAt: string;
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
  supplierId?: number;
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

export interface CartItem {
  productId: number;
  quantity: number;
  unitPrice?: number;
}

export interface QuoteRequest {
  /** Savat (bir nechta mahsulot). Berilsa productId/price e'tiborga olinmaydi. */
  items?: CartItem[];
  productId?: number;
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
  items: ContractItem[];
  applicationId?: number;
}

export interface ContractItem {
  productId?: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'CONTRACTED' | 'REJECTED' | 'PARTNER';

export interface ApplicationView {
  id: number;
  applicationNo: string;
  clientId: number;
  clientName: string;
  clientPhone: string;
  itemsSummary: string;
  costPrice: number;
  downPayment: number;
  markupPct: number;
  salePrice: number;
  installmentTotal: number;
  termMonths: number;
  monthlyPayment: number;
  scoreTotal?: number;
  riskCategory?: RiskCategory;
  decision?: Decision;
  status: ApplicationStatus;
  rejectionReason?: string;
  officerId?: number;
  officerName?: string;
  branchId?: number;
  branchName?: string;
  decidedByName?: string;
  decidedAt?: string;
  contractId?: number;
  contractNo?: string;
  createdAt: string;
  scoreDetails?: { stopFactors?: StopFactor[]; points?: Record<Factor, number> };
  partnerNotes?: string;
  partnerSerials?: { name: string; serial: string }[];
  provider: string;
  partnerName?: string;
  partnerTxId?: string;
  partnerCommission?: number;
  partnerHandoverAt?: string;
}

export interface Partner {
  id: number;
  code: string;
  name: string;
  commissionPct: number;
  maxTerm: number;
  minDownPct: number;
  dtiPct: number;
  active: boolean;
}

export interface PartnerOffer {
  code: string;
  name: string;
  limit: number;
  maxMonthly: number;
  maxTerm: number;
  commissionPct: number;
  minDownPct: number;
  eligible: boolean;
  reason: 'OK' | 'TERM' | 'DOWN' | 'LIMIT' | 'RISK';
  commission: number;
}

export interface PartnerOffers {
  internal: { limit: number; freeMonthly: number; covers: boolean };
  partners: PartnerOffer[];
  recommendation: { provider?: string; reason: 'INTERNAL_COVERS' | 'PARTNER_LOWEST_COMMISSION' | 'NO_LIMIT' };
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
  stateFee: number;
  arrestRequestedAt?: string;
  arrestNote?: string;
  esudSubmittedAt?: string;
  esudRef?: string;
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

export type KpiMetric = 'APPLICATIONS' | 'CONTRACTS' | 'SALES' | 'COLLECTED' | 'RECOVERED' | 'ACTIONS' | 'QUALITY';
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
  applicationsCount: number;
  baseSalary: number;
  salesBonusPct: number;
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
  applications: number;
  conversionPct?: number;
  baseSalary: number;
  salesBonus: number;
  totalPayout: number;
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
    totalPayroll: number;
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
  partnerVolume: number;
  partnerProfit: number;
}

export interface ForecastTotals {
  inflow: number;
  profit: number;
  newSales: number;
  netCash: number;
  debtEnd: number;
  arrearsEnd: number;
  partnerVolume: number;
  partnerProfit: number;
}

export type StressLevel = 'LOW' | 'MODERATE' | 'HIGH';

export interface ForecastFinancing {
  ownCapital: number;
  peakNeed: number;
  ownUsed: number;
  externalRequired: number;
  ownSharePct: number;
  externalSharePct: number;
  fundingCost: number;
  opex: number;
  grossProfit: number;
  partnerProfit: number;
  netProfit: number;
  capitalSavedByPartners: number;
  netLiquidity: number;
  eclProvision: number;
  recommendedReserve: number;
  estimatedNplPct?: number;
  stress: { level: StressLevel; profitLoss: number; inflowLoss: number; nplIncreasePct: number };
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
    histDownPct: number;
    ownCapital: number;
    monthlyOpex: number;
    fundingCostPct: number;
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
  financing: ForecastFinancing;
  financingPoints: { month: string; liquidity: number; externalBalance: number; fundingCost: number; netProfit: number }[];
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

export type VerificationMode = 'NONE' | 'OTP' | 'BIOMETRIC' | 'ANY' | 'BOTH';

export interface AdvancedSettings {
  verificationMode: VerificationMode;
  otpExpirySeconds: number;
  minFaceMatchPct: number;
  monthlyOpex: number;
  ownCapital: number;
  fundingCostPct: number;
  mibMode: MibMode;
  mibValidityDays: number;
}

export type MibMode = 'OFF' | 'OPTIONAL' | 'REQUIRED';

export interface MibCheck {
  id: number;
  status: 'CLEAR' | 'DEBT' | 'ERROR';
  source: 'API' | 'MANUAL';
  totalDebt: number;
  caseCount: number;
  cases: Record<string, string | number>[];
  message?: string;
  checkedBy?: string;
  createdAt: string;
}

export interface MibState {
  mode: MibMode;
  validityDays: number;
  provider: 'none' | 'stub' | 'http';
  providerConfigured: boolean;
  latest?: MibCheck;
  decisive?: MibCheck;
  fresh: boolean;
  needsCheck: boolean;
  history: MibCheck[];
}

export interface ApplicationDraft {
  id: number;
  clientId: number;
  clientName: string;
  clientPhone: string;
  clientPinfl?: string;
  step: number;
  summary?: string;
  amount?: number;
  officerId: number;
  officerName: string;
  branchId?: number;
  branchName?: string;
  createdAt: string;
  updatedAt: string;
  payload?: Record<string, unknown>;
}

/** Hamkor tizimida ochilgan buyurtma: hamkor mijozga tasdiqlash kodi yuboradi. */
export interface PartnerOrder {
  partnerCode: string;
  partnerName: string;
  transactionId: string;
  mock: boolean;
  message?: string;
}

export interface DailyPlan {
  sales: number;
  contracts: number;
  collection: number;
}

export interface DailyMonitor {
  date: string;
  branchId?: number;
  plan: DailyPlan;
  actual: DailyPlan;
  dayProgressPct: number;
}

export interface CreditMonitorReport {
  dtiNormPct: number;
  clients: number;
  avgScore?: number;
  avgDtiPct?: number;
  cleanClients: number;
  flaggedClients: number;
  rows: {
    clientId: number; fullName: string; pinfl: string; phone: string; blacklisted: boolean; monthlyIncome?: number;
    reportDate: string; source: string; score?: number; rating?: string; activeLoans: number; externalDebt: number;
    externalMonthly: number; internalMonthly: number; internalDebt: number; dtiPct?: number; overdueFlag: boolean;
    maxOverdueDays: number; note?: string;
  }[];
}

export interface PnlReport {
  from: string;
  to: string;
  days: number;
  principalCollected: number;
  markupCollected: number;
  otherReceipts: number;
  grossReceipts: number;
  fundingCost: number;
  acquiringFee: number;
  partnerCommission: number;
  financingCosts: number;
  grossMargin: number;
  opex: number;
  netProfit: number;
  marginPct: number;
  eclReserve: number;
  afterProvision: number;
  issuedVolume: number;
  issuedContracts: number;
  partnerVolume: number;
  fundingCostPct: number;
  acquiringPct: number;
}

export interface RollRate {
  fromDate: string;
  toDate: string;
  buckets: string[];
  cells: { from: string; to: string; count: number; amount: number }[];
}

export interface PayLink {
  url: string;
  amount: number;
  mock: boolean;
  smsStatus?: string;
}

export type ExternalLimitStatus = 'AVAILABLE' | 'NO_LIMIT' | 'NOT_REGISTERED' | 'ERROR';

export interface ExternalLimit {
  provider: string;
  providerName: string;
  status: ExternalLimitStatus;
  total: number;
  used: number;
  available: number;
  maxTermMonths?: number;
  mock: boolean;
  message?: string;
}

export interface ExternalLimitReport {
  clientId: number;
  mock: boolean;
  checkedAt: string;
  totalAvailable: number;
  items: ExternalLimit[];
}

export interface VerificationStatus {
  mode: VerificationMode;
  minFaceMatchPct: number;
  verified: ('OTP' | 'BIOMETRIC')[];
  satisfied: boolean;
}

export interface OtpSent {
  id: number;
  maskedPhone: string;
  expiresAt: string;
  /** Faqat dev profilda: SMS provayder ulanmagan muhitda sinash uchun */
  devCode?: string;
}

export interface VerificationResult {
  id: number;
  method: 'OTP' | 'BIOMETRIC';
  status: 'PENDING' | 'VERIFIED' | 'FAILED' | 'EXPIRED';
  matchPct?: number;
  verifiedAt?: string;
}

export interface AnalyticsReport {
  months: number;
  categories: {
    code: string; nameUz: string; nameRu: string; contracts: number; volume: number; avgTermMonths?: number;
    avgMarkupPct?: number; expectedProfit: number; earnedProfit: number; debt: number; overdue: number;
    overduePct?: number; nplDebt: number; nplPct?: number; sharePct?: number;
  }[];
  terms: { bucket: string; contracts: number; volume: number; avgMarkupPct?: number; debt: number; overduePct?: number; nplPct?: number }[];
  aging: { key: 'current' | 'd1_30' | 'd31_60' | 'd61_90' | 'd90'; contracts: number; debt: number; sharePct?: number }[];
  branches: { id: number; name: string; contracts: number; volume: number; collected: number; debt: number; overduePct?: number; target?: number; planPct?: number }[];
  managers: { id: number; name: string; branch?: string; contracts: number; volume: number; avgScore?: number; debt: number; overduePct?: number }[];
  monthly: { month: string; issued: number; collected: number; profit: number; partnerVolume: number; partnerCommission: number }[];
  partners: { code: string; name: string; deals: number; volume: number; commission: number }[];
  pnl: { profit: number; opex: number; operatingProfit: number; eclProvision: number; profitAfterProvision: number; partnerCommission: number; months: number };
}

export type StockStatus = 'OUT' | 'CRITICAL' | 'LOW' | 'OK' | 'OVER' | 'DEAD';

export interface WarehouseProduct {
  id: number; name: string; sku?: string; category: string; supplierId?: number; supplierName?: string;
  stock: number; price: number; stockValue: number; sold30: number; sold90: number; perDay: number;
  coverDays?: number; leadDays: number; incoming: number; recommendedQty: number; recommendedCost: number;
  status: StockStatus;
}

export interface WarehouseAnalytics {
  coverDays: number;
  summary: {
    products: number; units: number; stockValue: number; out: number; critical: number; low: number; dead: number;
    deadValue: number; turnoverPerYear?: number; toOrderUnits: number; toOrderCost: number;
  };
  products: WarehouseProduct[];
  suggestions: { supplierId?: number; supplierName?: string; leadDays: number; lines: number; units: number; cost: number }[];
}

export interface Supplier {
  id: number; name: string; phone?: string; contactPerson?: string; leadTimeDays: number; note?: string; active: boolean;
}

export type OrderStatus = 'DRAFT' | 'APPROVED' | 'RECEIVED' | 'CANCELLED';

export interface PurchaseOrder {
  id: number; orderNo: string; supplierId: number; supplierName: string; status: OrderStatus; eta?: string;
  notes?: string; createdBy?: string; approvedBy?: string; createdAt: string; approvedAt?: string; receivedAt?: string;
  units: number; total: number;
  items: { productId: number; productName: string; sku?: string; quantity: number; unitCost: number; lineTotal: number }[];
}
