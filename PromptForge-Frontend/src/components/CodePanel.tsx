import { useState, useEffect, useCallback, useMemo } from "react";
import { FileTree } from "./FileTree";
import { CodeEditor } from "./CodeEditor";
import { FileTabs } from "./FileTabs";
import { api, OPEN_TABS_KEY, ACTIVE_TAB_KEY } from "@/lib/api";
import { FileNode } from "@/lib/types";

interface CodePanelProps {
  projectId: string;
  updatedFiles: Map<string, string>;
  isStreaming: boolean;
  editable?: boolean;
}

function findFileInTree(files: FileNode[], targetPath: string): boolean {
  for (const node of files) {
    if (node.path === targetPath) return true;
    if (node.children && findFileInTree(node.children, targetPath)) return true;
  }
  return false;
}

const getTabsKey = (projectId: string) => `${OPEN_TABS_KEY}_${projectId}`;
const getActiveTabKey = (projectId: string) => `${ACTIVE_TAB_KEY}_${projectId}`;

export function CodePanel({ projectId, updatedFiles, isStreaming, editable = false }: CodePanelProps) {
  const [files, setFiles] = useState<FileNode[]>([]);
  const [openTabs, setOpenTabs] = useState<string[]>([]);
  const [activeTab, setActiveTab] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string>("");
  const [draftContent, setDraftContent] = useState<string>("");
  const [isLoadingTree, setIsLoadingTree] = useState(true);
  const [isLoadingFile, setIsLoadingFile] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const updatedPaths = useMemo(() => Array.from(updatedFiles.keys()), [updatedFiles]);

  // Load tabs from localStorage
  useEffect(() => {
    const savedTabs = localStorage.getItem(getTabsKey(projectId));
    const savedActiveTab = localStorage.getItem(getActiveTabKey(projectId));
    if (savedTabs) {
      try {
        const tabs = JSON.parse(savedTabs);
        if (Array.isArray(tabs) && tabs.length > 0) {
          setOpenTabs(tabs);
          setActiveTab(savedActiveTab || tabs[0]);
          return;
        }
      } catch {
        // ignore
      }
    }
  }, [projectId]);

  // Persist tabs
  useEffect(() => {
    if (openTabs.length > 0) {
      localStorage.setItem(getTabsKey(projectId), JSON.stringify(openTabs));
    } else {
      localStorage.removeItem(getTabsKey(projectId));
    }
  }, [openTabs, projectId]);

  // Persist active tab
  useEffect(() => {
    if (activeTab) {
      localStorage.setItem(getActiveTabKey(projectId), activeTab);
    } else {
      localStorage.removeItem(getActiveTabKey(projectId));
    }
  }, [activeTab, projectId]);

  const loadFiles = useCallback(async (showLoader = false) => {
    if (showLoader) setIsLoadingTree(true);
    try {
      const fileTree = await api.getFiles(projectId);
      setFiles(fileTree);

      if (openTabs.length === 0) {
        const defaultPaths = ["src/pages/Index.tsx", "pages/Index.tsx", "index.html"];
        for (const defaultPath of defaultPaths) {
          if (findFileInTree(fileTree, defaultPath)) {
            setOpenTabs([defaultPath]);
            setActiveTab(defaultPath);
            break;
          }
        }
      }
    } catch (error) {
      console.error("Failed to load files:", error);
    } finally {
      if (showLoader) setIsLoadingTree(false);
    }
  }, [projectId, openTabs.length]);

  // Initial load
  useEffect(() => {
    loadFiles(true);
  }, [loadFiles]);

  // Poll file tree while AI is streaming to surface tool-written files quickly
  useEffect(() => {
    if (!isStreaming) return;
    const id = window.setInterval(() => {
      loadFiles(false);
    }, 3000);
    return () => window.clearInterval(id);
  }, [isStreaming, loadFiles]);

  // Immediately refresh file tree when AI writes new files, so refresh is not needed.
  useEffect(() => {
    if (updatedPaths.length === 0) return;
    loadFiles(false);
    const lastUpdatedPath = updatedPaths[updatedPaths.length - 1];
    if (lastUpdatedPath && !openTabs.includes(lastUpdatedPath)) {
      setOpenTabs((prev) => [...prev, lastUpdatedPath]);
      setActiveTab(lastUpdatedPath);
    }
  }, [updatedPaths, loadFiles, openTabs]);

  // Load file content when active tab changes
  useEffect(() => {
    if (!activeTab) {
      setFileContent("");
      setDraftContent("");
      return;
    }
    if (updatedFiles.has(activeTab)) {
      const updatedContent = updatedFiles.get(activeTab)!;
      setFileContent(updatedContent);
      setDraftContent(updatedContent);
      return;
    }
    const loadContent = async () => {
      setIsLoadingFile(true);
      try {
        const content = await api.getFileContent(projectId, activeTab);
        setFileContent(content);
        setDraftContent(content);
      } catch {
        setFileContent("// Error loading file");
        setDraftContent("// Error loading file");
      } finally {
        setIsLoadingFile(false);
      }
    };
    loadContent();
  }, [projectId, activeTab, updatedFiles]);

  // Live update when streaming writes a file
  useEffect(() => {
    if (activeTab && updatedFiles.has(activeTab)) {
      const updatedContent = updatedFiles.get(activeTab)!;
      setFileContent(updatedContent);
      setDraftContent(updatedContent);
    }
  }, [activeTab, updatedFiles]);

  useEffect(() => {
    if (!editable || !activeTab) return;
    if (draftContent === fileContent) return;

    const timeoutId = window.setTimeout(async () => {
      try {
        setIsSaving(true);
        await api.saveFileContent(projectId, activeTab, draftContent);
        setFileContent(draftContent);
      } catch (error) {
        console.error("Failed to save file:", error);
      } finally {
        setIsSaving(false);
      }
    }, 700);

    return () => window.clearTimeout(timeoutId);
  }, [activeTab, draftContent, editable, fileContent, projectId]);

  const handleSelectFile = useCallback(
    (path: string) => {
      if (!openTabs.includes(path)) setOpenTabs((prev) => [...prev, path]);
      setActiveTab(path);
    },
    [openTabs]
  );

  const handleCloseTab = useCallback(
    (path: string) => {
      setOpenTabs((prev) => {
        const newTabs = prev.filter((t) => t !== path);
        if (activeTab === path) {
          const idx = prev.indexOf(path);
          setActiveTab(newTabs[Math.min(idx, newTabs.length - 1)] || null);
        }
        return newTabs;
      });
    },
    [activeTab]
  );

  const handleSelectTab = useCallback((path: string) => setActiveTab(path), []);

  return (
    <div className="flex h-full">
      {/* File Tree */}
      <div className="w-56 shrink-0 border-r border-border/50 overflow-y-auto bg-panel">
        <div className="panel-header">
          <span className="text-sm font-medium">Files</span>
        </div>
        <FileTree
          files={files}
          selectedPath={activeTab}
          onSelectFile={handleSelectFile}
          isLoading={isLoadingTree}
        />
      </div>

      {/* Code Editor with Tabs */}
      <div className="flex-1 flex flex-col min-w-0">
        <FileTabs
          openTabs={openTabs}
          activeTab={activeTab}
          onSelectTab={handleSelectTab}
          onCloseTab={handleCloseTab}
        />
        <div className="flex items-center justify-between border-b border-border/40 bg-muted/20 px-3 py-1 text-xs text-muted-foreground">
          <span>{editable ? "Editable mode" : "View-only mode"}</span>
          <span>{isSaving ? "Saving..." : editable ? "All changes saved" : "Read only"}</span>
        </div>
        <div className="flex-1 overflow-hidden">
          <CodeEditor
            content={draftContent}
            filePath={activeTab}
            isLoading={isLoadingFile}
            editable={editable}
            onCodeChange={setDraftContent}
          />
        </div>
      </div>
    </div>
  );
}
