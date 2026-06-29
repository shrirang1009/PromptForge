export interface LoginCredentials {
  username: string;
  password: string;
}

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "directory";
  children?: FileNode[];
}

export interface DeployResponse {
  previewUrl: string;
}

// ─── Chat ────────────────────────────────────────────────────────────────────

export enum ChatEventType {
  THOUGHT = 'THOUGHT',
  MESSAGE = 'MESSAGE',
  FILE_EDIT = 'FILE_EDIT',
  TOOL_LOG = 'TOOL_LOG'
}

export interface ChatEvent {
  id?: number;
  type: ChatEventType;
  content: string;
  metadata?: string;
  filePath?: string;
  sequenceOrder?: number;
}

export interface ChatMessage {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content?: string;
  events: ChatEvent[];
  tokensUsed?: number;
  createdAt?: string;
}

// ─── Projects ────────────────────────────────────────────────────────────────

export type ProjectRole = 'OWNER' | 'EDITOR' | 'VIEWER';

export interface ProjectSummaryResponse {
  id: number;
  name: string;
  description?: string;
  thumbnailUrl?: string;
  role: ProjectRole;
  createdAt: string;
  updatedAt?: string;
}

export interface ProjectResponse {
  id: number;
  name: string;
  role: ProjectRole;
  createdAt: string;
  updatedAt?: string;
}

export interface ProjectRequest {
  name: string;
}

// ─── Members ─────────────────────────────────────────────────────────────────

export interface ProjectMember {
  userId: number;
  username: string;
  name?: string;
  /** Backend sends 'projectRole'; normalized to 'role' in api layer */
  role: ProjectRole;
  invitedAt?: string;
}

export interface InviteMemberRequest {
  username: string;
  role: ProjectRole;
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export interface SignupRequest {
  username: string;   // email used as username (validated @Email in backend)
  name: string;
  password: string;
}

/**
 * Unified auth response — returned by both /auth/login and /auth/signup
 * via AuthController → AuthService → AuthResponse record in backend.
 */
export interface AuthResponse {
  token?: string;
  user?: {
    id: number;
    username: string;
    name: string;
    role?: "USER" | "ADMIN";
  };
}

export interface VerifyOtpRequest {
  email: string;
  code: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  email: string;
  code: string;
  newPassword?: string;
}

export interface SignupOtpRequest {
  email: string;
}

export interface SignupCompleteRequest {
  email: string;
  name: string;
  password?: string;
}

// ─── Billing / Subscription ──────────────────────────────────────────────────

export interface PlanDto {
  id?: number;
  name: string;
  maxTokensPerDay?: number;
  maxProjects?: number;
  unlimitedAi?: boolean;
  price?: string;
  displayName?: string;
}

export interface PublicPlanResponse {
  id: number;
  name: string;
  displayName: string;
  priceInPaise?: number;
  maxProjects?: number;
  maxTokensPerDay?: number;
  unlimitedAi?: boolean;
  validityDays?: number;
  active?: boolean;
}

export interface SubscriptionResponse {
  id?: number;
  plan: PlanDto | null;
  status: string;
  currentPeriodEnd?: string;
  tokensUsedThisCycle?: number;
}

export interface PlanLimitsResponse {
  planName: string;
  maxTokensPerDay: number;
  maxProjects: number;
  unlimitedAi: boolean;
}

export interface UsageTodayResponse {
  tokensUsed: number;
  tokensLimit: number;
  tokensRemaining: number;
  tokensAllowed: number;
  previewsRunning: number;
  previewsLimit: number;
}

export interface AdminDashboardResponse {
  totalUsers: number;
  blockedUsers: number;
  activeSubscriptions: number;
}

export interface AdminUserResponse {
  id: number;
  username: string;
  name: string;
  role: "USER" | "ADMIN";
  blocked: boolean;
}

export interface AdminPlanUpsertRequest {
  name: string;
  priceInPaise: number;
  maxProjects?: number;
  maxTokensPerDay?: number;
  unlimitedAi: boolean;
  validityDays: number;
  active: boolean;
}

export interface CheckoutRequest {
  planId: number;
  successUrl?: string;
  cancelUrl?: string;
}

export interface CheckoutResponse {
  checkoutUrl: string;
  orderId?: string;
  keyId?: string;
  amount?: number;
  currency?: string;
  customerName?: string;
  customerEmail?: string;
}

export interface ConfirmPaymentRequest {
  orderId: string;
  paymentId: string;
}

export interface PortalResponse {
  portalUrl: string;
}
