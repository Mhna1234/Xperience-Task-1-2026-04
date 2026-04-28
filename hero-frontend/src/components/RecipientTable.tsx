import { useState, useEffect } from 'react';
import { api } from '../services/bulkSendApi';
import type { ApiError, InvitationView, RsvpIntent } from '../types';

interface Props {
  token: string;
}

const STATUS_COPY: Record<string, { label: string; color: string }> = {
  PENDING:        { label: 'You have not responded yet.',         color: 'text-gray-500' },
  YES_CONFIRMED:  { label: "You're confirmed! 🎉",               color: 'text-green-600' },
  YES_WAITLISTED: { label: "You're on the waitlist.",             color: 'text-yellow-600' },
  NO:             { label: "You've declined.",                    color: 'text-red-500' },
  MAYBE:          { label: "You've responded Maybe.",             color: 'text-blue-500' },
};

const EVENT_STATUS_COPY: Record<string, string> = {
  OPEN:     'This event is accepting responses.',
  LOCKED:   'This event has started — responses are locked.',
  CLOSED:   'This event is closed to further responses.',
  CANCELED: 'This event has been canceled.',
};

export default function RsvpPage({ token }: Props) {
  const [view, setView] = useState<InvitationView | null>(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState<RsvpIntent | null>(null);

  useEffect(() => {
    api.getInvitation(token)
      .then(setView)
      .catch((err: ApiError) => setError(err.message ?? 'Invitation not found'));
  }, [token]);

  async function handleRsvp(intent: RsvpIntent) {
    if (!view) return;
    setSubmitting(intent);
    try {
      const outcome = await api.submitRsvp(token, intent);
      setView(prev => prev ? {
        ...prev,
        rsvp: { status: outcome.rsvpStatus, respondedAt: outcome.respondedAt },
      } : prev);
    } catch (err) {
      alert((err as ApiError).message ?? 'Failed to submit RSVP');
    } finally {
      setSubmitting(null);
    }
  }

  const canRespond =
    view?.event.status === 'OPEN';

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl shadow-md p-8 max-w-md w-full text-center">
          <p className="text-red-500 font-medium">{error}</p>
        </div>
      </div>
    );
  }

  if (!view) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <p className="text-gray-400">Loading…</p>
      </div>
    );
  }

  const { event, rsvp } = view;
  const statusInfo = STATUS_COPY[rsvp.status] ?? { label: rsvp.status, color: 'text-gray-500' };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-md w-full max-w-lg p-8 space-y-6">

        {/* Event info */}
        <div>
          <h1 className="text-2xl font-bold text-gray-800">{event.title}</h1>
          {event.description && (
            <p className="mt-2 text-sm text-gray-600">{event.description}</p>
          )}
          <div className="mt-3 text-sm text-gray-500 space-y-1">
            <p>📅 {new Date(event.startTime).toLocaleString()}</p>
            {event.location && <p>📍 {event.location}</p>}
          </div>
          <p className="mt-2 text-xs text-gray-400">{EVENT_STATUS_COPY[event.status]}</p>
        </div>

        <hr />

        {/* Current RSVP status */}
        <div>
          <p className="text-sm text-gray-500">Invited as: <span className="font-medium">{view.email}</span></p>
          <p className={`mt-2 font-semibold ${statusInfo.color}`}>{statusInfo.label}</p>
          {rsvp.respondedAt && (
            <p className="text-xs text-gray-400 mt-1">
              Last responded: {new Date(rsvp.respondedAt).toLocaleString()}
            </p>
          )}
        </div>

        {/* RSVP buttons */}
        {canRespond ? (
          <div>
            <p className="text-sm font-medium text-gray-700 mb-3">Your response:</p>
            <div className="flex gap-3">
              {(['YES', 'MAYBE', 'NO'] as RsvpIntent[]).map(intent => (
                <button
                  key={intent}
                  onClick={() => handleRsvp(intent)}
                  disabled={submitting !== null}
                  className={`flex-1 py-2 rounded-lg font-medium text-sm transition
                    ${intent === 'YES'   ? 'bg-green-500 hover:bg-green-600 text-white' : ''}
                    ${intent === 'MAYBE' ? 'bg-blue-400 hover:bg-blue-500 text-white'  : ''}
                    ${intent === 'NO'    ? 'bg-red-400  hover:bg-red-500  text-white'  : ''}
                    disabled:opacity-50`}
                >
                  {submitting === intent ? '…' : intent === 'YES' ? '✅ Yes' : intent === 'MAYBE' ? '❓ Maybe' : '❌ No'}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-sm text-gray-400 italic">Responses are no longer accepted.</p>
        )}
      </div>
    </div>
  );
}
