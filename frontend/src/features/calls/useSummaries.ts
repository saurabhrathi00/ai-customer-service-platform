import { useQuery } from '@tanstack/react-query';
import { summaries } from '@/api/resources';
import { useAuthStore } from '@/store/auth';
import type { CallSummaryResponse } from '@/types/api';

/** Fetches every summary for the signed-in business and indexes by callLogId
 *  so call rows can look theirs up in O(1). Polls every 30s — summaries
 *  arrive a few seconds after a call ends. */
export function useSummariesByCallId() {
  const businessId = useAuthStore((s) => s.businessId);
  const q = useQuery({
    queryKey: ['summaries', 'by-business', businessId],
    queryFn: () => summaries.list(businessId!),
    enabled: Boolean(businessId),
    refetchInterval: 30_000,
  });
  const map = new Map<string, CallSummaryResponse>();
  for (const s of q.data ?? []) map.set(s.callLogId, s);
  return { map, isLoading: q.isLoading, isFetching: q.isFetching };
}
