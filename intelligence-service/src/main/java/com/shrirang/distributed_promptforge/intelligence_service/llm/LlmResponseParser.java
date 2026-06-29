package com.shrirang.distributed_promptforge.intelligence_service.llm;

import com.mayur.distributed_promptforge.common_lib.enums.ChatEventStatus;
import com.mayur.distributed_promptforge.common_lib.enums.ChatEventType;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatEvent;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LlmResponseParser {

    /**
     * Regex Breakdown:
     * Group 1: Opening Tag (<tag ...>)
     * Group 2: Tag Name (message|file|tool)
     * Group 3: Attributes part (e.g., ' path="foo"' or ' args="a,b"')
     * Group 4: Content (The stuff inside)
     * Group 5: Closing Tag (</tag>)
     */

    private static final Pattern GENERIC_TAG_PATTERN = Pattern.compile(
            "<(thought|message|file|tool)([^>]*)>([\\s\\S]*?)(?:</\\1>|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Helper to extract specific attributes (path="..." or args="...") from Group 3
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile(
            "```([a-zA-Z0-9_+-]*)\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    public List<ChatEvent> parseChatEvents(String fullResponse, ChatMessage parentMessage) {
        List<ChatEvent> events = new ArrayList<>();
        int orderCounter = 1;

        Matcher matcher = GENERIC_TAG_PATTERN.matcher(fullResponse);

        while (matcher.find()) {
            String tagName = matcher.group(1).toLowerCase();
            String attributes = matcher.group(2);
            String content = matcher.group(3).trim();

            // Extract attributes map
            Map<String, String> attrMap = extractAttributes(attributes);

            ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                    .status(ChatEventStatus.CONFIRMED)
                    .chatMessage(parentMessage)
                    .content(content) // This is your Markdown content
                    .sequenceOrder(orderCounter++);

            switch (tagName) {
                case "thought" -> builder.type(ChatEventType.THOUGHT);
                case "message" -> builder.type(ChatEventType.MESSAGE);
                case "file" -> {
                    builder.type(ChatEventType.FILE_EDIT);
                    builder.status(ChatEventStatus.PENDING);
                    builder.filePath(attrMap.get("path")); // Required for files
//                    builder.content(null);
                }
                case "tool" -> {
                    builder.type(ChatEventType.TOOL_LOG);
                    builder.metadata(attrMap.get("args")); // Store raw file list in metadata
                }
                default -> { continue; }
            }

            events.add(builder.build());
        }

        boolean hasFileEdit = events.stream().anyMatch(e -> e.getType() == ChatEventType.FILE_EDIT);
        if (!hasFileEdit) {
            List<ChatEvent> inferred = inferFileEventsFromPlainCode(fullResponse, parentMessage, orderCounter);
            events.addAll(inferred);
        }

        return events;
    }

    private Map<String, String> extractAttributes(String attributeString) {
        Map<String, String> attributes = new HashMap<>();
        if (attributeString == null) return attributes;

        Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributeString);
        while (matcher.find()) {
            attributes.put(matcher.group(1).toLowerCase(), matcher.group(2));
        }
        return attributes;
    }

    private List<ChatEvent> inferFileEventsFromPlainCode(String fullResponse, ChatMessage parentMessage, int startOrder) {
        List<ChatEvent> inferred = new ArrayList<>();
        if (fullResponse == null || fullResponse.isBlank()) {
            return inferred;
        }

        Matcher codeBlockMatcher = MARKDOWN_CODE_BLOCK_PATTERN.matcher(fullResponse);
        if (!codeBlockMatcher.find()) {
            // Do not infer any file events if there are no markdown code blocks.
            return inferred;
        }

        String language = codeBlockMatcher.group(1);
        String contentCandidate = codeBlockMatcher.group(2);

        if (contentCandidate == null || contentCandidate.isBlank()) {
            return inferred;
        }

        String inferredPath = inferPath(language, contentCandidate);
        if (inferredPath == null) {
            return inferred;
        }

        log.warn("No <file> tags found. Inferred FILE_EDIT for path={} from markdown code block", inferredPath);
        inferred.add(ChatEvent.builder()
                .status(ChatEventStatus.PENDING)
                .type(ChatEventType.FILE_EDIT)
                .chatMessage(parentMessage)
                .filePath(inferredPath)
                .content(contentCandidate.trim())
                .sequenceOrder(startOrder)
                .build());

        return inferred;
    }

    private String inferPath(String language, String content) {
        if (language != null && !language.isBlank()) {
            String lang = language.trim().toLowerCase();
            if (lang.equals("html")) {
                return "index.html";
            }
            if (lang.equals("css")) {
                return "style.css";
            }
            if (lang.equals("javascript") || lang.equals("js")) {
                return "script.js";
            }
            if (lang.equals("typescript") || lang.equals("tsx") || lang.equals("jsx")) {
                return "src/App.tsx";
            }
        }

        String lower = content.toLowerCase();

        if (lower.contains("<!doctype") || lower.contains("<html") || lower.contains("</html>")
                || lower.contains("<body") || lower.contains("</body>")) {
            return "index.html";
        }

        boolean looksLikeCss = lower.contains("{") && lower.contains("}")
                && lower.matches("(?s).*[a-z-]+\\s*:\\s*[^;]+;.*");
        if (looksLikeCss) {
            return "style.css";
        }

        // Strict JS regex matching actual assignments or function signatures
        boolean looksLikeJavaScript = 
                lower.contains("addeventlistener(")
                || lower.contains("queryselector(")
                || java.util.regex.Pattern.compile("\\bdocument\\s*\\.\\s*[a-zA-Z_$]").matcher(lower).find()
                || java.util.regex.Pattern.compile("\\bconsole\\s*\\.\\s*log\\s*\\(").matcher(lower).find()
                || java.util.regex.Pattern.compile("\\b(const|let|var)\\s+(?:[a-zA-Z_$][a-zA-Z0-9_$]*|\\{[^}]*\\}|\\[[^\\]]*\\])\\s*(?:=|;\\s*$|\\bof\\b|\\bin\\b)").matcher(lower).find()
                || java.util.regex.Pattern.compile("\\bfunction\\s*[a-zA-Z_$]*\\s*\\(").matcher(lower).find()
                || java.util.regex.Pattern.compile("\\bconst\\s+[a-zA-Z_$][a-zA-Z0-9_$]*\\s*=\\s*\\([^)]*\\)\\s*=>").matcher(lower).find();

        if (looksLikeJavaScript && !lower.contains("export default") && !lower.contains("return (")) {
            return "script.js";
        }

        // Strict React/TSX/JSX matching patterns
        boolean looksLikeTsx = 
                lower.contains("export default")
                || lower.contains("usestate(")
                || lower.contains("useeffect(")
                || lower.contains("import react")
                || lower.contains("import {")
                || java.util.regex.Pattern.compile("<[A-Z][a-zA-Z0-9_]*[^>]*>").matcher(content).find()
                || (lower.contains("return (") && lower.contains("</"));

        if (looksLikeTsx) {
            return "src/App.tsx";
        }

        return null;
    }

}
