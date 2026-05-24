import type { LeadResponse, LeadType } from '@/types/api';
import { formatDistanceToNow } from 'date-fns';

export function leadTypeLabel(t: LeadType): string {
  switch (t) {
    case 'APPOINTMENT':   return 'Appointment';
    case 'HIGH_INTEREST': return 'Hot lead';
    case 'HUMAN_REQUEST': return 'Needs a human';
  }
}

export function leadTypeVariant(t: LeadType): 'default' | 'success' | 'warning' {
  switch (t) {
    case 'APPOINTMENT':   return 'default';
    case 'HIGH_INTEREST': return 'success';
    case 'HUMAN_REQUEST': return 'warning';
  }
}

export function leadStatusLabel(s: LeadResponse['status']): string {
  switch (s) {
    case 'NEW':      return 'Pending';
    case 'APPROVED': return 'Approved';
    case 'DECLINED': return 'Declined';
    case 'IGNORED':  return 'Ignored';
  }
}

export function leadStatusVariant(s: LeadResponse['status']):
  'default' | 'success' | 'destructive' | 'secondary' {
  switch (s) {
    case 'NEW':      return 'default';
    case 'APPROVED': return 'success';
    case 'DECLINED': return 'destructive';
    case 'IGNORED':  return 'secondary';
  }
}

export function leadAge(l: LeadResponse): string {
  return formatDistanceToNow(new Date(l.createdAt), { addSuffix: true });
}

export function formatInterest(r: number | null | undefined): string {
  if (r == null) return '—';
  return Number(r).toFixed(1);
}
