import type {
  Dashboard,
  EventResponse,
  InvitationView,
  InviteResponse,
  RsvpIntent,
  RsvpOutcome,
} from '../types';

const BASE = '/api';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
    ...options,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: 'UNKNOWN', message: 'Request failed' }));
    throw err;
  }
  return res.json() as Promise<T>;
}

export const api = {
  createEvent(
    hostId: string,
    data: { title: string; description?: string; startTime: string; location?: string; maxCapacity?: number }
  ): Promise<EventResponse> {
    return request('/events', {
      method: 'POST',
      headers: { 'X-Host-Id': hostId },
      body: JSON.stringify(data),
    });
  },

  invitePeople(hostId: string, eventId: number, emails: string[]): Promise<InviteResponse> {
    return request(`/events/${eventId}/invitations`, {
      method: 'POST',
      headers: { 'X-Host-Id': hostId },
      body: JSON.stringify({ emails }),
    });
  },

  closeEvent(hostId: string, eventId: number): Promise<EventResponse> {
    return request(`/events/${eventId}/close`, {
      method: 'POST',
      headers: { 'X-Host-Id': hostId },
    });
  },

  cancelEvent(hostId: string, eventId: number): Promise<EventResponse> {
    return request(`/events/${eventId}/cancel`, {
      method: 'POST',
      headers: { 'X-Host-Id': hostId },
    });
  },

  getDashboard(hostId: string, eventId: number): Promise<Dashboard> {
    return request(`/events/${eventId}/dashboard`, {
      headers: { 'X-Host-Id': hostId },
    });
  },

  getInvitation(token: string): Promise<InvitationView> {
    return request(`/invitations/${token}`);
  },

  submitRsvp(token: string, intent: RsvpIntent): Promise<RsvpOutcome> {
    return request(`/invitations/${token}/rsvp`, {
      method: 'PUT',
      body: JSON.stringify({ intent }),
    });
  },
};
