// ── Enums ──────────────────────────────────────────────────────────────────

export type EventStatus = 'OPEN' | 'CLOSED' | 'CANCELED' | 'LOCKED';
export type RsvpStatus = 'PENDING' | 'YES_CONFIRMED' | 'YES_WAITLISTED' | 'NO' | 'MAYBE';
export type RsvpIntent = 'YES' | 'NO' | 'MAYBE';

// ── API response shapes ────────────────────────────────────────────────────

export interface EventResponse {
  id: number;
  hostId: string;
  title: string;
  description: string | null;
  startTime: string;
  location: string | null;
  maxCapacity: number | null;
  status: EventStatus;
  createdAt: string;
}

export interface InvitationDetail {
  invitationId: number;
  email: string;
  inviteToken: string;
}

export interface InviteResponse {
  created: InvitationDetail[];
  duplicates: string[];
}

export interface RsvpOutcome {
  rsvpStatus: RsvpStatus;
  respondedAt: string;
}

export interface InvitationView {
  invitationId: number;
  email: string;
  event: {
    id: number;
    title: string;
    description: string | null;
    startTime: string;
    location: string | null;
    status: EventStatus;
  };
  rsvp: {
    status: RsvpStatus;
    respondedAt: string | null;
  };
}

export interface DashboardCounts {
  confirmed: number;
  waitlisted: number;
  no: number;
  maybe: number;
  pending: number;
  total: number;
}

export interface AttendeeDetail {
  invitationId: number;
  email: string;
  inviteToken: string;
  rsvpStatus: RsvpStatus;
  respondedAt: string | null;
}

export interface Dashboard {
  eventId: number;
  title: string;
  status: EventStatus;
  startTime: string;
  counts: DashboardCounts;
  attendees: AttendeeDetail[];
}

export interface ApiError {
  error: string;
  message: string;
}
