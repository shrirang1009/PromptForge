import {
  AuthResponse,
  ChatMessage,
  CheckoutRequest,
  CheckoutResponse,
  ConfirmPaymentRequest,
  DeployResponse,
  FileNode,
  LoginCredentials,
  PortalResponse,
  ProjectMember,
  ProjectRequest,
  ProjectResponse,
  ProjectRole,
  ProjectSummaryResponse,
  SignupRequest,
  SubscriptionResponse,
  UsageTodayResponse,
  PlanLimitsResponse,
  PublicPlanResponse,
  AdminDashboardResponse,
  AdminPlanUpsertRequest,
  AdminUserResponse,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  SignupOtpRequest,
  VerifyOtpRequest,
  SignupCompleteRequest,
} from "./types";

const BASE_URL = "http://localhost:8080";

export const getAuthToken = () => localStorage.getItem("auth_token");
export const setAuthToken = (token: string) =>
  localStorage.setItem("auth_token", token);
export const removeAuthToken = () => localStorage.removeItem("auth_token");
export const isAuthenticated = () => !!getAuthToken();

const getAuthHeaders = (): HeadersInit => {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export const setUserInfo = (user: {
  id: number;
  username: string;
  name: string;
  role?: "USER" | "ADMIN";
}) => {
  localStorage.setItem("user_info", JSON.stringify(user));
};

export const getUserInfo = (): {
  id: number;
  username: string;
  name: string;
  role?: "USER" | "ADMIN";
} | null => {
  const info = localStorage.getItem("user_info");
  return info ? JSON.parse(info) : null;
};

export const removeUserInfo = () => localStorage.removeItem("user_info");

export const clearAppSessionState = () => {
  const keysToRemove: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (!key) continue;
    if (
      key.startsWith("chat_messages_") ||
      key.startsWith("preview_url_") ||
      key.startsWith("open_tabs_") ||
      key.startsWith("active_tab_")
    ) {
      keysToRemove.push(key);
    }
  }
  keysToRemove.forEach((key) => localStorage.removeItem(key));
};

export const PREVIEW_URL_KEY = "preview_url";
export const OPEN_TABS_KEY = "open_tabs";
export const ACTIVE_TAB_KEY = "active_tab";

// ─── Error parsing ────────────────────────────────────────────────────────────

/**
 * Parse a backend error response into a human-readable string.
 * Handles:
 *  - error.com.shrirang.distributed_promptforge.api_gateway.ApiError JSON: { status, message, errors? }
 *  - Spring Boot default error JSON: { timestamp, status, error, message, path }
 *  - Plain text
 */
async function parseApiError(response: Response): Promise<string> {
  const text = await response.text().catch(() => "");
  if (!text) return `Server error (${response.status})`;

  try {
    const json = JSON.parse(text);
    // error.com.shrirang.distributed_promptforge.api_gateway.ApiError / Spring Boot error body with message
    if (typeof json.message === "string" && json.message) return json.message;
    // Validation errors array
    if (Array.isArray(json.errors) && json.errors.length > 0) {
      return json.errors
        .map((e: { field?: string; message?: string; defaultMessage?: string }) =>
          e.field ? `${e.field}: ${e.message ?? e.defaultMessage}` : (e.message ?? e.defaultMessage ?? "Validation error")
        )
        .join(", ");
    }
    if (json.error) return `${json.error} (${json.status ?? response.status})`;
    return `Server error (${response.status})`;
  } catch {
    return text.length < 300 ? text : `Server error (${response.status})`;
  }
}

function normalizeStreamChunk(raw: string): string {
  if (!raw) return "";
  const withoutNullBytes = raw.replace(/\u0000/g, "");
  const cleaned = withoutNullBytes.replace(/^(?:null)+/i, "");
  const trimmed = cleaned.trim();
  if (!trimmed) return "";
  if (trimmed.toLowerCase() === "null") return "";
  return cleaned;
}

// ─── File tree helpers ────────────────────────────────────────────────────────

interface FilesApiResponse {
  files: { path: string }[];
}

function buildFileTree(paths: { path: string }[]): FileNode[] {
  const root: FileNode[] = [];
  const nodeMap = new Map<string, FileNode>();
  const sortedPaths = [...paths].sort((a, b) => a.path.localeCompare(b.path));

  for (const { path } of sortedPaths) {
    const parts = path.split("/");
    let currentPath = "";

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const parentPath = currentPath;
      currentPath = currentPath ? `${currentPath}/${part}` : part;

      if (nodeMap.has(currentPath)) continue;

      const isFile = i === parts.length - 1;
      const node: FileNode = {
        name: part,
        path: currentPath,
        type: isFile ? "file" : "directory",
        children: isFile ? undefined : [],
      };

      nodeMap.set(currentPath, node);

      if (parentPath) {
        const parent = nodeMap.get(parentPath);
        if (parent?.children) parent.children.push(node);
      } else {
        root.push(node);
      }
    }
  }

  const sortNodes = (nodes: FileNode[]) => {
    nodes.sort((a, b) => {
      if (a.type === "directory" && b.type === "file") return -1;
      if (a.type === "file" && b.type === "directory") return 1;
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => {
      if (node.children) sortNodes(node.children);
    });
  };

  sortNodes(root);
  return root;
}

// ─── API ─────────────────────────────────────────────────────────────────────

export const api = {
  // ── Auth ────────────────────────────────────────────────────────────────────

  // Both login and signup use AuthController → /auth/login and /auth/signup
  // Gateway rewrites /api/auth/** → /auth/** on account-service (port 9050)

  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    const response = await fetch(`${BASE_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async sendSignupOtp(data: SignupOtpRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/auth/signup/send-code`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async verifySignupOtp(data: VerifyOtpRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/auth/signup/verify-code`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async completeSignup(data: SignupCompleteRequest): Promise<AuthResponse> {
    const response = await fetch(`${BASE_URL}/api/auth/signup/complete`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async forgotPassword(data: ForgotPasswordRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/auth/forgot-password`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async resetPassword(data: ResetPasswordRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/auth/reset-password`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async verifyResetCode(data: VerifyOtpRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/auth/verify-reset-code`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  // ── Files ───────────────────────────────────────────────────────────────────

  async getFiles(projectId: string): Promise<FileNode[]> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/files`,
      { headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
    const data: FilesApiResponse = await response.json();
    return buildFileTree(data.files ?? []);
  },

  async getFileContent(projectId: string, path: string): Promise<string> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/files/content?path=${encodeURIComponent(path)}`,
      { headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
    const data = await response.json();
    return data.content;
  },

  async saveFileContent(projectId: string, path: string, content: string): Promise<void> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/files/content`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", ...getAuthHeaders() },
        body: JSON.stringify({ path, content }),
      }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  // ── Deploy ──────────────────────────────────────────────────────────────────

  async deploy(projectId: string): Promise<DeployResponse> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/deploy`,
      { method: "POST", headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  // ── Projects ────────────────────────────────────────────────────────────────

  async getProjects(): Promise<ProjectSummaryResponse[]> {
    const response = await fetch(`${BASE_URL}/api/projects`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async createProject(name: string): Promise<ProjectSummaryResponse> {
    const response = await fetch(`${BASE_URL}/api/projects`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ name }),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async getProject(id: string): Promise<ProjectResponse> {
    const response = await fetch(`${BASE_URL}/api/projects/${id}`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async updateProject(id: string, name: string): Promise<ProjectResponse> {
    const response = await fetch(`${BASE_URL}/api/projects/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ name }),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async deleteProject(id: string): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/projects/${id}`, {
      method: "DELETE",
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async downloadProjectZip(id: string): Promise<Blob> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${id}/files/download-zip`,
      { headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.blob();
  },

  // ── Members ─────────────────────────────────────────────────────────────────

  async getProjectMembers(projectId: string): Promise<ProjectMember[]> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/members`,
      { headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async inviteMember(
    projectId: string,
    username: string,
    role: ProjectRole
  ): Promise<void> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/members`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", ...getAuthHeaders() },
        body: JSON.stringify({ username, role }),
      }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async updateMemberRole(
    projectId: string,
    userId: number,
    role: ProjectRole
  ): Promise<void> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/members/${userId}`,
      {
        method: "PATCH",
        headers: { "Content-Type": "application/json", ...getAuthHeaders() },
        body: JSON.stringify({ role }),
      }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async removeMember(projectId: string, userId: number): Promise<void> {
    const response = await fetch(
      `${BASE_URL}/api/projects/${projectId}/members/${userId}`,
      { method: "DELETE", headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  // ── Chat ────────────────────────────────────────────────────────────────────

  async getChatHistory(projectId: string): Promise<ChatMessage[]> {
    const response = await fetch(
      `${BASE_URL}/api/chat/projects/${projectId}`,
      { headers: { ...getAuthHeaders() } }
    );
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  /**
   * Stream chat from the backend SSE endpoint.
   * Backend sends: data: {"text":"..."}
   */
  streamChat(
    projectId: string,
    message: string,
    onChunk: (chunk: string) => void,
    onFile: (path: string, content: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ): () => void {
    const controller = new AbortController();

    fetch(`${BASE_URL}/api/chat/stream`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ message, projectId: Number(projectId) }),
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(await parseApiError(response));
        }

        const reader = response.body?.getReader();
        if (!reader) throw new Error("No reader available");

        const decoder = new TextDecoder();
        let sseBuffer = "";
        let fullBuffer = "";

        const FILE_BLOCK_RE = /<file\s+path="([^"]+)">([\s\S]*?)<\/file>/gi;
        let lastFileMatch = 0;

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          sseBuffer += decoder.decode(value, { stream: true });
          const lines = sseBuffer.split("\n");
          sseBuffer = lines.pop() ?? "";

          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            const dataStr = trimmed.slice(5).trim();
            if (!dataStr) continue;

            try {
              const parsed = JSON.parse(dataStr) as { text: string };
              const text = normalizeStreamChunk(parsed.text ?? "");
              if (!text) continue;

              onChunk(text);
              fullBuffer += text;

              FILE_BLOCK_RE.lastIndex = lastFileMatch;
              let m: RegExpExecArray | null;
              while ((m = FILE_BLOCK_RE.exec(fullBuffer)) !== null) {
                onFile(m[1], m[2]);
                lastFileMatch = FILE_BLOCK_RE.lastIndex;
              }
            } catch {
              // Non-JSON line – ignore
            }
          }
        }

        onComplete();
      })
      .catch((error: Error) => {
        if (error.name !== "AbortError") {
          console.error("Stream error:", error);
          onError(error);
        }
      });

    return () => controller.abort();
  },

  // ── Billing ─────────────────────────────────────────────────────────────────

  async getMySubscription(): Promise<SubscriptionResponse[]> {
    const response = await fetch(`${BASE_URL}/api/me/subscription`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async cancelSubscription(id: number): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/payments/subscriptions/${id}/cancel`, {
      method: "POST",
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async getUsageToday(): Promise<UsageTodayResponse> {
    const response = await fetch(`${BASE_URL}/api/me/usage-today`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async getPlanLimits(): Promise<PlanLimitsResponse> {
    const response = await fetch(`${BASE_URL}/api/me/plan-limits`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async getPlans(): Promise<PublicPlanResponse[]> {
    const response = await fetch(`${BASE_URL}/api/plans`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async createCheckout(request: CheckoutRequest): Promise<CheckoutResponse> {
    const response = await fetch(`${BASE_URL}/api/payments/checkout`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async openPortal(): Promise<PortalResponse> {
    const response = await fetch(`${BASE_URL}/api/payments/portal`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async confirmPayment(request: ConfirmPaymentRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/payments/confirm`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async getAdminDashboard(): Promise<AdminDashboardResponse> {
    const response = await fetch(`${BASE_URL}/api/admin/dashboard`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async getAdminUsers(query?: string): Promise<AdminUserResponse[]> {
    const suffix = query ? `?q=${encodeURIComponent(query)}` : "";
    const response = await fetch(`${BASE_URL}/api/admin/users${suffix}`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async setAdminUserBlocked(userId: number, blocked: boolean): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/admin/users/${userId}/block?blocked=${blocked}`, {
      method: "PATCH",
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async deleteAdminUser(userId: number): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/admin/users/${userId}`, {
      method: "DELETE",
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async getAdminPlans(): Promise<PublicPlanResponse[]> {
    const response = await fetch(`${BASE_URL}/api/admin/plans`, {
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
    return response.json();
  },

  async setAdminPlanActive(planId: number, active: boolean): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/admin/plans/${planId}/active?active=${active}`, {
      method: "PATCH",
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async createAdminPlan(request: AdminPlanUpsertRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/admin/plans`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async updateAdminPlan(planId: number, request: AdminPlanUpsertRequest): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/admin/plans/${planId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },

  async deleteAdminPlan(planId: number): Promise<void> {
    const response = await fetch(`${BASE_URL}/api/admin/plans/${planId}`, {
      method: "DELETE",
      headers: { ...getAuthHeaders() },
    });
    if (!response.ok) throw new Error(await parseApiError(response));
  },
};
