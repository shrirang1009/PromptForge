import { useState, useEffect, useRef, useCallback } from "react";
import { Play, Loader2, ExternalLink, RefreshCw, Globe } from "lucide-react";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";
import { RuntimeErrorAlert, RuntimeError } from "@/components/RuntimeErrorAlert";

interface PreviewPanelProps {
  projectId: string;
  runtimeError: RuntimeError | null;
  onDismiss: () => void;
  onFix: (error: RuntimeError) => void;
}

export function PreviewPanel({ projectId, runtimeError, onDismiss, onFix }: PreviewPanelProps) {
  const storageKey = `preview_url_${projectId}`;
  const isBuilderProjectRoute = (url: URL): boolean =>
    url.origin === window.location.origin &&
    (/^\/projects\/\d+/.test(url.pathname) || /^\/project\/\d+/.test(url.pathname));

  const sanitizePreviewUrl = (rawUrl: string | null): string | null => {
    if (!rawUrl) return null;
    try {
      const parsed = new URL(rawUrl, window.location.origin);
      if (isBuilderProjectRoute(parsed)) {
        return null;
      }
      return parsed.toString();
    } catch {
      return null;
    }
  };

  const [previewUrl, setPreviewUrl] = useState<string | null>(() => {
    return sanitizePreviewUrl(localStorage.getItem(storageKey));
  });
  const [isDeploying, setIsDeploying] = useState(false);
  const [isBuildingLocalPreview, setIsBuildingLocalPreview] = useState(false);
  const [isIframeLoading, setIsIframeLoading] = useState(false);
  const [hasAttemptedIframeFallback, setHasAttemptedIframeFallback] = useState(false);
  const fallbackTimerRef = useRef<number | null>(null);
  const { toast } = useToast();

  const clearFallbackTimer = () => {
    if (fallbackTimerRef.current !== null) {
      window.clearTimeout(fallbackTimerRef.current);
      fallbackTimerRef.current = null;
    }
  };

  const isPreviewReachable = useCallback(async (url: string): Promise<boolean> => {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), 3500);
    try {
      await fetch(url, {
        method: "GET",
        mode: "no-cors",
        cache: "no-store",
        signal: controller.signal,
      });
      return true;
    } catch {
      return false;
    } finally {
      window.clearTimeout(timeoutId);
    }
  }, []);

  const fallbackToLocalPreview = useCallback(async () => {
    if (!previewUrl || previewUrl.startsWith("blob:")) return;
    if (hasAttemptedIframeFallback) return;

    setHasAttemptedIframeFallback(true);
    setIsBuildingLocalPreview(true);
    clearFallbackTimer();

    try {
      const localUrl = await buildLocalPreview();
      if (localUrl) {
        setPreviewUrl(localUrl);
        toast({
          title: "Remote preview unreachable",
          description: "Showing local preview from generated files.",
        });
      } else {
        toast({
          title: "Preview failed",
          description: "Remote preview is unreachable and local preview could not be built.",
          variant: "destructive",
        });
      }
    } finally {
      setIsIframeLoading(false);
      setIsBuildingLocalPreview(false);
    }
  }, [hasAttemptedIframeFallback, previewUrl, toast, projectId]);

  // Persist per-project preview URL
  useEffect(() => {
    if (previewUrl) {
      localStorage.setItem(storageKey, previewUrl);
    } else {
      localStorage.removeItem(storageKey);
    }
  }, [previewUrl, storageKey]);

  useEffect(() => {
    clearFallbackTimer();

    if (!previewUrl || previewUrl.startsWith("blob:")) {
      setIsIframeLoading(false);
      return;
    }

    setIsIframeLoading(true);
    fallbackTimerRef.current = window.setTimeout(() => {
      void fallbackToLocalPreview();
    }, 7000);

    return () => {
      clearFallbackTimer();
    };
  }, [previewUrl, fallbackToLocalPreview]);

  const buildLocalPreview = async (): Promise<string | null> => {
    const tree = await api.getFiles(projectId);
    const filePaths: string[] = [];
    const walk = (nodes: typeof tree) => {
      for (const node of nodes) {
        if (node.type === "file") filePaths.push(node.path);
        if (node.children) walk(node.children);
      }
    };
    walk(tree);

    const indexCandidates = ["index.html", "public/index.html", "src/index.html"];
    const indexPath = indexCandidates.find((p) => filePaths.includes(p));
    if (!indexPath) return null;

    const contents = new Map<string, string>();
    await Promise.all(
      filePaths.map(async (path) => {
        try {
          const c = await api.getFileContent(projectId, path);
          contents.set(path, c);
        } catch {
          // ignore unreadable file
        }
      })
    );

    let html = contents.get(indexPath) ?? "";
    if (!html) return null;

    html = html.replace(/<link[^>]+href=["']([^"']+)["'][^>]*>/gi, (full, href) => {
      const clean = String(href).replace(/^\.?\//, "");
      const css = contents.get(clean);
      if (!css) return full;
      return `<style data-inline-from="${clean}">\n${css}\n</style>`;
    });

    html = html.replace(/<script[^>]+src=["']([^"']+)["'][^>]*><\/script>/gi, (full, src) => {
      const clean = String(src).replace(/^\.?\//, "");
      const js = contents.get(clean);
      if (!js) return full;
      return `<script data-inline-from="${clean}">\n${js}\n</script>`;
    });

    const blob = new Blob([html], { type: "text/html;charset=utf-8" });
    return URL.createObjectURL(blob);
  };

  const handleDeploy = async () => {
    setIsDeploying(true);
    try {
      const response = await api.deploy(projectId);
      const safeUrl = sanitizePreviewUrl(response.previewUrl);
      if (!safeUrl) {
        setPreviewUrl(null);
        localStorage.removeItem(storageKey);
        toast({
          title: "Invalid preview target",
          description: "Preview runtime URL points back to the builder. Configure a separate preview host.",
          variant: "destructive",
        });
        return;
      }
      const reachable = await isPreviewReachable(safeUrl);
      if (!reachable) {
        setPreviewUrl(safeUrl);
        await fallbackToLocalPreview();
        return;
      }

      setHasAttemptedIframeFallback(false);
      setPreviewUrl(safeUrl);
      toast({ title: "Deployment successful", description: "Your preview is now ready" });
    } catch (error) {
      setIsBuildingLocalPreview(true);
      try {
        const localUrl = await buildLocalPreview();
        if (localUrl) {
          setPreviewUrl(localUrl);
          toast({
            title: "Local preview ready",
            description: "Loaded preview directly from generated project files.",
          });
        } else {
          toast({
            title: "Deployment failed",
            description: error instanceof Error ? error.message : "Something went wrong",
            variant: "destructive",
          });
        }
      } finally {
        setIsBuildingLocalPreview(false);
      }
    } finally {
      setIsDeploying(false);
    }
  };

  const handleRefresh = () => {
    const iframe = document.querySelector<HTMLIFrameElement>(`iframe[data-project="${projectId}"]`);
    if (iframe) iframe.src = iframe.src;
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* URL Bar */}
      <div className="h-12 shrink-0 flex items-center gap-2 px-3 border-b border-border/50 bg-panel">
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            onClick={handleRefresh}
            disabled={!previewUrl}
            className="h-7 w-7 text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </Button>
        </div>

        <div className="flex-1 flex items-center h-8 px-3 rounded-md bg-muted/50 text-sm text-muted-foreground">
          <Globe className="w-3.5 h-3.5 mr-2 shrink-0" />
          <span className="truncate">{previewUrl || "Click 'Run Preview' to deploy"}</span>
        </div>

        <div className="flex items-center gap-1">
          {previewUrl && (
            <Button
              variant="ghost"
              size="icon"
              onClick={() => window.open(previewUrl, "_blank")}
              className="h-7 w-7 text-muted-foreground hover:text-foreground"
            >
              <ExternalLink className="w-3.5 h-3.5" />
            </Button>
          )}
          <Button
            onClick={handleDeploy}
            disabled={isDeploying || isBuildingLocalPreview}
            size="sm"
            className="h-7 px-3 bg-primary hover:bg-primary/90 text-xs font-medium"
          >
            {isDeploying || isBuildingLocalPreview ? (
              <>
                <Loader2 className="w-3 h-3 mr-1.5 animate-spin" />
                {isBuildingLocalPreview ? "Building" : "Deploying"}
              </>
            ) : (
              <>
                <Play className="w-3 h-3 mr-1.5" />
                Run Preview
              </>
            )}
          </Button>
        </div>
      </div>

      {/* Preview Area */}
      <div className="flex-1 relative bg-[#1a1a1a]">
        {previewUrl ? (
          <iframe
            data-project={projectId}
            src={previewUrl}
            className="w-full h-full border-0"
            title="Preview"
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
            onLoad={() => {
              setIsIframeLoading(false);
              clearFallbackTimer();
            }}
            onError={() => {
              void fallbackToLocalPreview();
            }}
          />
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-center p-8">
            <div className="w-16 h-16 rounded-xl bg-muted/20 flex items-center justify-center mb-4">
              <Globe className="w-8 h-8 text-muted-foreground/50" />
            </div>
            <p className="text-sm text-muted-foreground">
              No preview available yet. Click "Run Preview" to deploy.
            </p>
          </div>
        )}

        {/* Error Alert Overlay */}
        <RuntimeErrorAlert error={runtimeError} onDismiss={onDismiss} onFix={onFix} />
      </div>
    </div>
  );
}
