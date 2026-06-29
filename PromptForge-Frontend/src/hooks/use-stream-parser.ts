import { useMemo } from 'react';
import { ChatEvent, ChatEventType } from '@/lib/types';

/**
 * Parses the streaming XML-tagged response from the backend AI.
 *
 * Supported tags (backend PromptUtils defines these):
 *   <thought>...</thought>       → THOUGHT event
 *   <message>...</message>       → MESSAGE event  (markdown)
 *   <tool args="a,b">...</tool>  → TOOL_LOG event
 *   <file path="x">...</file>    → FILE_EDIT event
 *
 * The regex is lenient for streaming: the closing tag is optional on the
 * last (still-streaming) segment so partial content appears live.
 */

const PARSE_REGEX =
  /<(thought|message|tool|file)([^>]*)>([\s\S]*?)(?:<\/\1>|$)/gi;

const ATTR_REGEX = /(?:path|args)="([^"]+)"/i;

const cleanStreamText = (value: string) =>
  value.replace(/\u0000/g, '').replace(/^(?:null)+/i, '').trim();

export const useStreamParser = (streamBuffer: string): ChatEvent[] => {
  return useMemo(() => {
    const events: ChatEvent[] = [];
    const cleanBuffer = cleanStreamText(streamBuffer);
    PARSE_REGEX.lastIndex = 0;

    let match: RegExpExecArray | null;
    while ((match = PARSE_REGEX.exec(cleanBuffer)) !== null) {
      const [fullMatch, tagName, attrStr, content] = match;
      const tag = tagName.toLowerCase();

      const attrMatch = ATTR_REGEX.exec(attrStr);
      const attrValue = attrMatch ? attrMatch[1] : undefined;

      switch (tag) {
        case 'thought':
          events.push({
            type: ChatEventType.THOUGHT,
            content: cleanStreamText(content),
          });
          break;

        case 'message':
          events.push({
            type: ChatEventType.MESSAGE,
            content: cleanStreamText(content),
          });
          break;

        case 'tool':
          events.push({
            type: ChatEventType.TOOL_LOG,
            content: cleanStreamText(content),
            metadata: attrValue,
          });
          break;

        case 'file':
          events.push({
            type: ChatEventType.FILE_EDIT,
            content: cleanStreamText(content),
            filePath: attrValue,
          });
          break;
      }
    }

    // Fallback: if no tags parsed yet but buffer has content, show raw text as MESSAGE
    if (events.length === 0 && cleanBuffer.length > 0) {
      events.push({
        type: ChatEventType.MESSAGE,
        content: cleanBuffer,
      });
    }

    return events;
  }, [streamBuffer]);
};
