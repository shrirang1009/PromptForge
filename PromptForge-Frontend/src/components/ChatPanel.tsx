import { useState, useRef, useEffect } from "react";
import { Send, Loader2, Bot, ThumbsUp, ThumbsDown, Copy, RotateCcw, Square, Zap } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { format } from "date-fns";
import { useStreamParser } from "../hooks/use-stream-parser";
import { ChatEventRenderer } from './ChatEventRenderer';
import { ChatEvent, ChatEventType } from "@/lib/types";

// ─── Types ────────────────────────────────────────────────────────────────────

/**
 * UI-level ChatMessage used inside ChatPanel / ProjectView.
 * id is kept as string for React keys; role uses lowercase for UI logic.
 */
export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  isStreaming?: boolean;
  createdAt?: string;
  events?: ChatEvent[];
  editedFiles?: string[];
}

interface ChatPanelProps {
  messages: ChatMessage[];
  onSendMessage: (message: string) => void;
  onStopStreaming?: () => void;
  isStreaming: boolean;
  isLoading?: boolean;
  readOnly?: boolean;
  tokensRemaining?: number;
  tokensLimit?: number;
  unlimitedAi?: boolean;
}

// ─── ChatPanel ────────────────────────────────────────────────────────────────

export function ChatPanel({
  messages,
  onSendMessage,
  onStopStreaming,
  isStreaming,
  isLoading,
  readOnly,
  tokensRemaining,
  tokensLimit,
  unlimitedAi,
}: ChatPanelProps) {
  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const navigate = useNavigate();

  const isLimitExceeded = !unlimitedAi && tokensRemaining !== undefined && tokensRemaining <= 0;

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isStreaming || isLimitExceeded) return;
    onSendMessage(input.trim());
    setInput("");
    if (textareaRef.current) textareaRef.current.style.height = "auto";
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    const ta = e.target;
    ta.style.height = "auto";
    ta.style.height = `${Math.min(ta.scrollHeight, 200)}px`;
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto">
        {isLoading ? (
          <div className="flex items-center justify-center h-full">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-center p-8">
            <div className="w-14 h-14 rounded-xl bg-primary/20 flex items-center justify-center mb-4">
              <Bot className="w-7 h-7 text-primary" />
            </div>
            <h3 className="text-base font-medium mb-1">Start a conversation</h3>
            <p className="text-sm text-muted-foreground max-w-xs">
              Describe what you want to build or modify
            </p>
          </div>
        ) : (
          <div className="flex flex-col">
            {messages.map((message) => (
              <MessageItem
                key={message.id}
                message={message}
                isStreaming={isStreaming && !!message.isStreaming}
              />
            ))}
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Token limit exceeded banner */}
      {isLimitExceeded && (
        <div className="shrink-0 mx-3 mb-1 mt-2 flex items-center gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-400">
          <Zap className="w-3.5 h-3.5 shrink-0" />
          <span className="flex-1">
            Daily token limit reached ({tokensLimit?.toLocaleString() ?? 0} tokens). Upgrade to continue.
          </span>
          <button
            onClick={() => navigate("/billing")}
            className="ml-1 font-semibold underline underline-offset-2 hover:text-amber-300 whitespace-nowrap"
          >
            Upgrade →
          </button>
        </div>
      )}

      {/* Input */}
      <div className="shrink-0 p-3 border-t border-border/50 bg-card">
        <form onSubmit={handleSubmit} className="relative">
          <Textarea
            ref={textareaRef}
            value={input}
            onChange={handleTextareaChange}
            onKeyDown={handleKeyDown}
            placeholder={
              isLimitExceeded
                ? "Token limit reached — upgrade your plan to continue"
                : readOnly
                ? "You have view-only access to this project"
                : "Describe what you want to build..."
            }
            className="min-h-[48px] max-h-[200px] pr-12 resize-none bg-muted/30 border-border/30 focus:border-primary/50 rounded-xl text-sm"
            disabled={isStreaming || readOnly || isLimitExceeded}
            rows={1}
          />
          {isStreaming ? (
            <Button
              type="button"
              size="icon"
              onClick={onStopStreaming}
              className="absolute right-2 bottom-2 h-8 w-8 rounded-lg bg-destructive hover:bg-destructive/90 text-destructive-foreground"
              aria-label="Stop generation"
            >
              <Square className="w-4 h-4" />
            </Button>
          ) : (
            <Button
              type="submit"
              size="icon"
              disabled={!input.trim() || readOnly}
              className="absolute right-2 bottom-2 h-8 w-8 rounded-lg"
            >
              <Send className="w-4 h-4" />
            </Button>
          )}
        </form>

        <div className="flex items-center justify-between mt-2 px-1">
          <span className="text-xs text-muted-foreground">✨ AI-Powered</span>
          <div className="flex items-center gap-2">
            {!unlimitedAi && tokensRemaining !== undefined && tokensLimit !== undefined && tokensLimit > 0 && (
              <span className={`text-xs font-medium ${
                tokensRemaining <= 0
                  ? "text-red-400"
                  : tokensRemaining < tokensLimit * 0.15
                  ? "text-amber-400"
                  : "text-muted-foreground"
              }`}>
                {tokensRemaining.toLocaleString()} tokens left
              </span>
            )}
            {isStreaming && (
              <span className="text-xs text-muted-foreground flex items-center gap-1 font-medium">
                <Loader2 className="w-3 h-3 animate-spin text-primary" />
                Thinking...
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── MessageItem ──────────────────────────────────────────────────────────────

function MessageItem({
  message,
  isStreaming,
}: {
  message: ChatMessage;
  isStreaming: boolean;
}) {
  // Parse live stream if no DB events present
  const liveEvents = useStreamParser(message.content || "");
  const dbEvents = message.events && message.events.length > 0 ? message.events : null;
  const hasDbMessageEvent = dbEvents?.some((e) => e.type === ChatEventType.MESSAGE) ?? false;
  const eventsToRender =
    dbEvents
      ? (hasDbMessageEvent || !message.content
          ? dbEvents
          : [
              ...dbEvents,
              {
                type: ChatEventType.MESSAGE,
                content: message.content,
              },
            ])
      : liveEvents;

  return (
    <div
      className={`p-5 border-b border-border/10 ${
        message.role === "user" ? "bg-muted/10" : "bg-background"
      }`}
    >
      <div className="max-w-4xl mx-auto">
        {message.role === "user" ? (
          <div className="flex flex-col items-end gap-2">
            <div className="bg-primary/10 text-primary-foreground text-sm py-2.5 px-4 rounded-2xl rounded-tr-none border border-primary/20 max-w-[85%]">
              <p className="text-foreground leading-relaxed whitespace-pre-wrap">
                {message.content}
              </p>
            </div>
            {message.createdAt && (
              <span className="text-[10px] text-muted-foreground px-1 uppercase tracking-tight">
                {format(new Date(message.createdAt), "HH:mm")}
              </span>
            )}
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-col gap-3">
              {eventsToRender.map((event, idx) => {
                const isLast = idx === eventsToRender.length - 1;
                return (
                  <ChatEventRenderer
                    key={idx}
                    event={event}
                    isLoading={isStreaming && isLast}
                  />
                );
              })}
            </div>

            {!message.isStreaming && eventsToRender.length > 0 && (
              <div className="flex items-center gap-1 pt-2">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-muted-foreground hover:text-primary"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-muted-foreground hover:text-primary"
                >
                  <ThumbsUp className="w-3.5 h-3.5" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-muted-foreground hover:text-primary"
                >
                  <ThumbsDown className="w-3.5 h-3.5" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-muted-foreground hover:text-primary"
                >
                  <Copy className="w-3.5 h-3.5" />
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
