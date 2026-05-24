import type { CallLogResponse } from '@/types/api';
import { format, formatDistanceToNow, isSameDay } from 'date-fns';

export function callDuration(c: CallLogResponse) {
  if (c.callDurationSecs != null) return c.callDurationSecs;
  if (c.callEndedAt && c.callStartedAt) {
    return Math.max(0, Math.round((+new Date(c.callEndedAt) - +new Date(c.callStartedAt)) / 1000));
  }
  return null;
}

export function callTimeLabel(iso: string) {
  const d = new Date(iso);
  if (isSameDay(d, new Date())) return format(d, 'HH:mm');
  return format(d, 'MMM d, HH:mm');
}

export function callRelative(iso: string) {
  return formatDistanceToNow(new Date(iso), { addSuffix: true });
}

/** transcript column on the wire is a JSON-serialised string. Returns
 *  parsed turns or null if unparseable / empty. */
export interface TranscriptTurn {
  speaker?: string;
  role?: string;
  text?: string;
  content?: string;
  timestamp?: string;
}
export function parseTranscript(raw: string | null): TranscriptTurn[] | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed as TranscriptTurn[];
    return null;
  } catch {
    return null;
  }
}

export function interestColor(rating: number | null | undefined) {
  if (rating == null) return 'secondary';
  if (rating >= 4) return 'success';
  if (rating >= 2) return 'warning';
  return 'destructive';
}

export function feedbackLabel(score: number | null | undefined) {
  if (score == null) return null;
  if (score === 1) return { label: 'Helpful', variant: 'success' as const };
  if (score === 2) return { label: 'Not helpful', variant: 'destructive' as const };
  return { label: 'Other', variant: 'secondary' as const };
}
