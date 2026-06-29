import { useState, useCallback, useEffect, useRef } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { Code, Sparkles, LogOut, MoreVertical, Trash, Download, Edit, ArrowLeft, Users } from "lucide-react";
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from "@/components/ui/resizable";
import { ChatPanel, ChatMessage } from "@/components/ChatPanel";
import { CodePanel } from "@/components/CodePanel";
import { PreviewPanel } from "@/components/PreviewPanel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { api, isAuthenticated, removeAuthToken, getUserInfo, removeUserInfo, setAuthToken, clearAppSessionState } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { RuntimeErrorAlert, RuntimeError } from "@/components/RuntimeErrorAlert";
import { generateGradient, cn } from "@/lib/utils";
import { ProjectMember, ProjectResponse, ChatMessage as BackendChatMessage } from "@/lib/types";
import { ShareDialog } from "@/components/ShareDialog";

type ViewMode = "code" | "preview";

const cleanChatText = (value?: string) =>
  (value ?? "").replace(/\u0000/g, "").replace(/^(?:null)+/i, "").trimStart();

export function ProjectView() {
  const { projectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>("preview");
  const [updatedFiles, setUpdatedFiles] = useState<Map<string, string>>(new Map());
  const [isLoadingHistory, setIsLoadingHistory] = useState(true);
  const [runtimeError, setRuntimeError] = useState<RuntimeError | null>(null);
  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [projectOwner, setProjectOwner] = useState<ProjectMember | null>(null);
  const [isRenameDialogOpen, setIsRenameDialogOpen] = useState(false);
  const [renameName, setRenameName] = useState("");
  const [tokensUsed, setTokensUsed] = useState(0);
  const [tokensLimit, setTokensLimit] = useState(100);
  const [unlimitedAi, setUnlimitedAi] = useState(false);
  const userId = getUserInfo()?.id ?? "anon";
  const chatCacheKey = `chat_messages_${userId}_${projectId ?? "unknown"}`;
  const canEditProject = project?.role === "OWNER" || project?.role === "EDITOR";
  const canManageAccess = project?.role === "OWNER";
  const isSharedProject = !!project && project.role !== "OWNER";
  const tokensRemaining = unlimitedAi ? undefined : Math.max(0, tokensLimit - tokensUsed);

  // Fetch token usage
  const loadUsage = async () => {
    try {
      const [usageResp, limitsResp] = await Promise.allSettled([
        api.getUsageToday(),
        api.getPlanLimits(),
      ]);
      if (limitsResp.status === "fulfilled") {
        const l = limitsResp.value;
        setUnlimitedAi(l.unlimitedAi ?? false);
        setTokensLimit(l.maxTokensPerDay ?? 100);
      }
      if (usageResp.status === "fulfilled") {
        const u = usageResp.value;
        setTokensUsed(u.tokensUsed ?? 0);
        if (u.tokensLimit) setTokensLimit(u.tokensLimit);
      }
    } catch {
      // silently ignore
    }
  };

  const currentEditedFilesRef = useRef<string[]>([]);
  const streamCleanupRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    if (!projectId) return;
    setMessages([]);
    setUpdatedFiles(new Map());
    const cachedRaw = localStorage.getItem(chatCacheKey);
    if (!cachedRaw) return;
    try {
      const parsed = JSON.parse(cachedRaw) as ChatMessage[];
      if (Array.isArray(parsed) && parsed.length > 0) {
        setMessages(parsed);
      }
    } catch {
      // ignore bad cache
    }
  }, [chatCacheKey, projectId]);

  useEffect(() => {
    if (isAuthenticated()) return;
    const tokenFromQuery = searchParams.get("authToken");
    if (tokenFromQuery) {
      setAuthToken(tokenFromQuery);
      return;
    }
    navigate("/login");
  }, [navigate, searchParams]);

  useEffect(() => {
    if (!projectId) return;
    const load = async () => {
      setIsLoadingHistory(true);
      try {
        const [history, projectData] = await Promise.all([
          api.getChatHistory(projectId),
          api.getProject(projectId),
        ]);

        const formatted: ChatMessage[] = history.map((msg: BackendChatMessage) => ({
          id: msg.id.toString(),
          role: msg.role === "USER" ? "user" : "assistant",
          content: cleanChatText(msg.content),
          createdAt: msg.createdAt,
          events: (msg.events || []).map((event) => ({
            ...event,
            content: cleanChatText(event.content),
          })),
        }));

        setMessages((prev) => (formatted.length > 0 ? formatted : prev));
        setProject(projectData);
        try {
          const members = await api.getProjectMembers(projectId);
          setProjectOwner(members.find((member) => member.role === "OWNER") ?? null);
        } catch (memberError) {
          console.error("Failed to load project members:", memberError);
        }
      } catch (error) {
        console.error("Failed to load project data:", error);
        toast({
          title: "Error",
          description: "Failed to load project data",
          variant: "destructive",
        });
      } finally {
        setIsLoadingHistory(false);
      }
    };
    load();
    loadUsage();
  }, [projectId, toast]);

  useEffect(() => {
    if (!projectId) return;
    localStorage.setItem(chatCacheKey, JSON.stringify(messages.slice(-100)));
  }, [chatCacheKey, messages, projectId]);

  const handleLogout = () => {
    clearAppSessionState();
    removeAuthToken();
    removeUserInfo();
    navigate("/login");
  };

  const handleSendMessage = useCallback(
    (content: string) => {
      if (!projectId) return;
      if (isStreaming) return;
      currentEditedFilesRef.current = [];

      const userMsg: ChatMessage = {
        id: Date.now().toString(),
        role: "user",
        content,
      };
      setMessages((prev) => [...prev, userMsg]);
      setIsStreaming(true);

      const aiMsgId = (Date.now() + 1).toString();
      const aiMsg: ChatMessage = {
        id: aiMsgId,
        role: "assistant",
        content: "",
        isStreaming: true,
        editedFiles: [],
      };
      setMessages((prev) => [...prev, aiMsg]);

      streamCleanupRef.current = api.streamChat(
        projectId,
        content,
        (chunk) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsgId
                ? { ...m, content: m.content + chunk, isStreaming: true }
                : m
            )
          );
        },
        (path, fileContent) => {
          setUpdatedFiles((prev) => new Map(prev).set(path, fileContent));
          if (!currentEditedFilesRef.current.includes(path)) {
            currentEditedFilesRef.current.push(path);
          }
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsgId
                ? { ...m, editedFiles: [...currentEditedFilesRef.current] }
                : m
            )
          );
        },
        () => {
          streamCleanupRef.current = null;
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsgId
                ? { ...m, isStreaming: false, editedFiles: [...currentEditedFilesRef.current] }
                : m
            )
          );
          setIsStreaming(false);
          // Refresh usage counter after each successful response
          loadUsage();
        },
        (error) => {
          // Check for 429 = token limit exceeded
          if (error.message && (error.message.includes("Daily") || error.message.includes("limit") || error.message.includes("429"))) {
            // Force usage state to limit so UI blocks further requests
            setTokensUsed(tokensLimit);
            toast({
              title: "Daily limit reached",
              description: error.message,
              variant: "destructive",
            });
          } else {
            toast({ title: "Chat error", description: error.message, variant: "destructive" });
          }
          streamCleanupRef.current = null;
          setMessages((prev) =>
            prev.map((m) =>
              m.id === aiMsgId
                ? { ...m, content: "Sorry, an error occurred.", isStreaming: false }
                : m
            )
          );
          setIsStreaming(false);
          // Refresh usage after a completed/failed request
          loadUsage();
        }
      );
    },
    [projectId, toast, isStreaming]
  );

  const handleStopStreaming = useCallback(() => {
    if (streamCleanupRef.current) {
      streamCleanupRef.current();
      streamCleanupRef.current = null;
    }
    setIsStreaming(false);
    setMessages((prev) =>
      prev.map((m) =>
        m.isStreaming
          ? {
              ...m,
              isStreaming: false,
              content: m.content || "Generation stopped.",
            }
          : m
      )
    );
    toast({ title: "Stopped", description: "AI generation has been stopped." });
  }, [toast]);

  const handleBack = useCallback(() => {
    if (window.history.length > 1) {
      navigate(-1);
    } else {
      navigate("/projects");
    }
  }, [navigate]);

  useEffect(() => {
    return () => {
      if (streamCleanupRef.current) {
        streamCleanupRef.current();
        streamCleanupRef.current = null;
      }
    };
  }, []);

  // Listen for runtime errors from preview iframe
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      const data = event.data;
      if (data?.type === "PreviewError") {
        const err = data.payload;
        setRuntimeError({
          message: err.message,
          source: data.subType,
          stack: err.stack,
          filename: err.source,
          lineno: err.lineno,
          colno: err.colno,
        });
      }
    };
    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, []);

  const handleFixError = useCallback(
    (error: RuntimeError) => {
      const prompt = `I encountered a ${error.source || "runtime error"} in my application:

Error Message: ${error.message}
${error.filename ? `File: ${error.filename}` : ""}
${error.lineno ? `Line: ${error.lineno}` : ""}

Stack Trace:
${error.stack || "No stack trace available"}

Please analyze this error and fix the code to resolve it.`;
      handleSendMessage(prompt);
      setRuntimeError(null);
    },
    [handleSendMessage]
  );

  const handleDeleteProject = async () => {
    if (!projectId) return;
    if (!confirm("Are you sure you want to delete this project?")) return;
    try {
      await api.deleteProject(projectId);
      navigate("/projects");
      toast({ title: "Success", description: "Project deleted successfully" });
    } catch {
      toast({ title: "Error", description: "Failed to delete project", variant: "destructive" });
    }
  };

  const handleDownloadProject = async () => {
    if (!projectId) return;
    try {
      const blob = await api.downloadProjectZip(projectId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `project-${projectId}.zip`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      toast({ title: "Success", description: "Download started" });
    } catch {
      toast({ title: "Error", description: "Failed to download project", variant: "destructive" });
    }
  };

  const openRenameDialog = () => {
    if (project) {
      setRenameName(project.name);
      setIsRenameDialogOpen(true);
    }
  };

  const handleRenameSubmit = async () => {
    if (!projectId || !renameName.trim()) return;
    try {
      const updated = await api.updateProject(projectId, renameName);
      setProject((prev) => (prev ? { ...prev, name: updated.name } : null));
      setIsRenameDialogOpen(false);
      toast({ title: "Success", description: "Project renamed successfully" });
    } catch {
      toast({ title: "Error", description: "Failed to rename project", variant: "destructive" });
    }
  };

  if (!projectId) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-muted-foreground">Invalid project ID</p>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col overflow-hidden bg-background">
      {/* Header */}
      <header className="h-12 shrink-0 border-b border-border/50 bg-panel flex items-center justify-between px-3">
        {/* Left: project name + actions */}
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={handleBack}
            className="h-7 w-7 text-muted-foreground hover:text-foreground"
            aria-label="Go back"
          >
            <ArrowLeft className="w-4 h-4" />
          </Button>
          {project ? (
            <>
              <div className="w-7 h-7 rounded-sm shadow-sm" style={generateGradient(project.name)} />
              <span className="font-semibold text-sm">{project.name}</span>
            </>
          ) : (
            <>
              <div className="w-7 h-7 rounded-lg bg-primary/20 flex items-center justify-center">
                <Sparkles className="w-3.5 h-3.5 text-primary" />
              </div>
              <span className="font-semibold text-sm">Loading...</span>
            </>
          )}

          {project?.role === "OWNER" && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-6 w-6 ml-1 text-muted-foreground">
                  <MoreVertical className="w-4 h-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                <DropdownMenuItem onClick={openRenameDialog}>
                  <Edit className="w-4 h-4 mr-2" />
                  Rename
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleDownloadProject}>
                  <Download className="w-4 h-4 mr-2" />
                  Download
                </DropdownMenuItem>
                <DropdownMenuItem
                  className="text-red-500 focus:text-red-500"
                  onClick={handleDeleteProject}
                >
                  <Trash className="w-4 h-4 mr-2" />
                  Delete
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
          {project?.role !== "OWNER" && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleDownloadProject}
              className="h-8 text-xs text-muted-foreground hover:text-foreground"
            >
              <Download className="w-3.5 h-3.5 mr-1.5" />
              Download
            </Button>
          )}
        </div>

        {/* Center: view mode toggle */}
        <div className="flex items-center bg-muted/30 rounded-lg p-0.5">
          <button
            onClick={() => setViewMode("preview")}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-all rounded-md ${
              viewMode === "preview"
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            <Sparkles className="w-3 h-3" />
            Preview
          </button>
          <button
            onClick={() => setViewMode("code")}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-all rounded-md ${
              viewMode === "code"
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            <Code className="w-3 h-3" />
            Code
          </button>
        </div>

        {/* Right: user + share + logout */}
        <div className="flex items-center gap-2">
          {project && (
            <div className="flex items-center gap-2 px-2 py-1 bg-muted/30 rounded-full border border-border/50">
              <Avatar className="h-6 w-6 border border-primary/20">
                <AvatarFallback className="text-[10px] bg-primary/10 text-primary font-semibold">
                  {getUserInfo()?.name?.charAt(0).toUpperCase() ?? "U"}
                </AvatarFallback>
              </Avatar>
              {project.role && (
                <span
                  className={cn(
                    "text-[10px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded",
                    project.role === "OWNER"
                      ? "bg-primary/10 text-primary"
                      : project.role === "EDITOR"
                      ? "bg-amber-500/10 text-amber-600"
                      : "bg-muted text-muted-foreground"
                  )}
                >
                  {project.role}
                </span>
              )}
              {isSharedProject && (
                <span className="text-[10px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-500">
                  Shared
                </span>
              )}
            </div>
          )}

          {isSharedProject && (
            <div className="hidden md:flex items-center gap-1.5 rounded-full border border-border/50 bg-muted/20 px-2.5 py-1 text-[11px] text-muted-foreground">
              <Users className="h-3.5 w-3.5" />
              Shared by {projectOwner?.name || projectOwner?.username || "owner"}
            </div>
          )}

          <ShareDialog
            projectId={projectId}
            canManageAccess={canManageAccess}
            ownerDisplayName={projectOwner?.name || projectOwner?.username || null}
            trigger={
              <Button
                variant="outline"
                size="sm"
                className="h-8 text-xs font-medium"
              >
                {canManageAccess ? "Share" : "Access"}
              </Button>
            }
          />

          {canEditProject && (
            <Button
              size="sm"
              className="h-8 text-xs bg-primary hover:bg-primary/90"
              onClick={() => navigate(`/billing`)}
            >
              Upgrade
            </Button>
          )}

          <Button
            variant="ghost"
            size="icon"
            onClick={handleLogout}
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
          >
            <LogOut className="w-4 h-4" />
          </Button>
        </div>
      </header>

      {/* Main layout */}
      <div className="flex-1 overflow-hidden">
        <ResizablePanelGroup direction="horizontal" className="h-full">
          <ResizablePanel defaultSize={35} minSize={25} maxSize={50}>
            <div className="h-full border-r border-border/50 bg-panel">
              <ChatPanel
                messages={messages}
                onSendMessage={handleSendMessage}
                onStopStreaming={handleStopStreaming}
                isStreaming={isStreaming}
                isLoading={isLoadingHistory}
                readOnly={!canEditProject}
                tokensRemaining={tokensRemaining}
                tokensLimit={tokensLimit}
                unlimitedAi={unlimitedAi}
              />
            </div>
          </ResizablePanel>

          <ResizableHandle className="w-px bg-border/50 hover:bg-primary/50 transition-colors" />

          <ResizablePanel defaultSize={65} minSize={50} maxSize={75}>
            <div className="h-full relative">
              <div className={cn("h-full absolute inset-0", viewMode !== "code" && "hidden")}>
                <CodePanel
                  projectId={projectId}
                  updatedFiles={updatedFiles}
                  isStreaming={isStreaming}
                  editable={canEditProject}
                />
              </div>
              <div className={cn("h-full absolute inset-0", viewMode !== "preview" && "hidden")}>
                <PreviewPanel
                  projectId={projectId}
                  runtimeError={runtimeError}
                  onDismiss={() => setRuntimeError(null)}
                  onFix={handleFixError}
                />
              </div>
            </div>
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>

      {/* Rename Dialog */}
      <Dialog open={isRenameDialogOpen} onOpenChange={setIsRenameDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rename Project</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <Input
              value={renameName}
              onChange={(e) => setRenameName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleRenameSubmit()}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsRenameDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleRenameSubmit}
              disabled={!renameName.trim() || renameName === project?.name}
            >
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
