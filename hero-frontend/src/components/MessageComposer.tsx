import { useState, useEffect, useCallback } from 'react';
import { api } from '../services/bulkSendApi';
import type { ApiError, Dashboard, EventStatus } from '../types';

interface Props {
  eventId: number;
}

const STATUS_LABEL: Record<EventStatus, string> = {
  OPEN: '🟢 Open',
  LOCKED: '🔒 Locked (past start time)',
  CLOSED: '🔴 Closed',
  CANCELED: '⛔ Canceled',
};

const RSVP_LABEL: Record<string, string> = {
  PENDING: 'Pending',
  YES_CONFIRMED: '✅ Confirmed',
  YES_WAITLISTED: '⏳ Waitlisted',
  NO: '❌ No',
  MAYBE: '❓ Maybe',
};

export default function EventManager({ eventId }: Props) {
  const hostId = localStorage.getItem('hostId') ?? '';
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [error, setError] = useState('');
  const [emails, setEmails] = useState('');
  const [inviteResult, setInviteResult] = useState('');
  const [inviteLoading, setInviteLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  const loadDashboard = useCallback(async () => {
    try {
      const data = await api.getDashboard(hostId, eventId);
      setDashboard(data);
      setError('');
    } catch (err) {
      setError((err as ApiError).message ?? 'Failed to load dashboard');
    }
  }, [hostId, eventId]);

  useEffect(() => {
    loadDashboard();
    const interval = setInterval(loadDashboard, 10000);
    return () => clearInterval(interval);
  }, [loadDashboard]);

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    setInviteResult('');
    const emailList = emails.split(/[\n,]+/).map(s => s.trim()).filter(Boolean);
    if (!emailList.length) return;
    setInviteLoading(true);
    try {
      const res = await api.invitePeople(hostId, eventId, emailList);
      const lines: string[] = [];
      if (res.created.length) {
        lines.push('✅ Invitation links (share these with each person):');
        res.created.forEach(d => {
          const link = `${window.location.origin}${window.location.pathname}#/rsvp/${d.inviteToken}`;
          lines.push(`  ${d.email}  →  ${link}`);
        });
      }
      if (res.duplicates.length) {
        lines.push(`⚠️ Already invited: ${res.duplicates.join(', ')}`);
      }
      setInviteResult(lines.join('\n'));
      setEmails('');
      loadDashboard();
    } catch (err) {
      setInviteResult(`Error: ${(err as ApiError).message}`);
    } finally {
      setInviteLoading(false);
    }
  }

  async function handleClose() {
    if (!confirm('Close this event to further responses?')) return;
    setActionLoading(true);
    try { await api.closeEvent(hostId, eventId); loadDashboard(); }
    catch (err) { alert((err as ApiError).message); }
    finally { setActionLoading(false); }
  }

  async function handleCancel() {
    if (!confirm('Cancel this event? This cannot be undone.')) return;
    setActionLoading(true);
    try { await api.cancelEvent(hostId, eventId); loadDashboard(); }
    catch (err) { alert((err as ApiError).message); }
    finally { setActionLoading(false); }
  }

  const canInvite = dashboard?.status === 'OPEN' || dashboard?.status === 'LOCKED';

  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-3xl mx-auto space-y-6">

        {/* Header */}
        <div className="bg-white rounded-2xl shadow-md p-6">
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-2xl font-bold text-gray-800">
                {dashboard?.title ?? `Event #${eventId}`}
              </h1>
              <p className="text-sm text-gray-500 mt-1">
                {dashboard ? new Date(dashboard.startTime).toLocaleString() : ''}
                {dashboard?.status && (
                  <span className="ml-3 font-medium">{STATUS_LABEL[dashboard.status]}</span>
                )}
              </p>
              <p className="text-xs text-gray-400 mt-1">Host ID: {hostId || '(none)'}</p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={loadDashboard}
                className="text-xs text-blue-600 hover:underline"
              >Refresh</button>
            </div>
          </div>

          {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

          {/* Counts */}
          {dashboard && (
            <div className="mt-4 grid grid-cols-3 gap-3 sm:grid-cols-6">
              {[
                { label: 'Confirmed', value: dashboard.counts.confirmed, color: 'text-green-600' },
                { label: 'Waitlisted', value: dashboard.counts.waitlisted, color: 'text-yellow-600' },
                { label: 'Maybe', value: dashboard.counts.maybe, color: 'text-blue-500' },
                { label: 'No', value: dashboard.counts.no, color: 'text-red-500' },
                { label: 'Pending', value: dashboard.counts.pending, color: 'text-gray-400' },
                { label: 'Total', value: dashboard.counts.total, color: 'text-gray-700' },
              ].map(c => (
                <div key={c.label} className="text-center">
                  <div className={`text-2xl font-bold ${c.color}`}>{c.value}</div>
                  <div className="text-xs text-gray-500">{c.label}</div>
                </div>
              ))}
            </div>
          )}

          {/* Actions */}
          <div className="mt-4 flex gap-3">
            <button
              onClick={handleClose}
              disabled={actionLoading || dashboard?.status === 'CLOSED' || dashboard?.status === 'CANCELED'}
              className="px-4 py-2 text-sm bg-yellow-500 text-white rounded-lg hover:bg-yellow-600 disabled:opacity-40"
            >Close</button>
            <button
              onClick={handleCancel}
              disabled={actionLoading || dashboard?.status === 'CANCELED'}
              className="px-4 py-2 text-sm bg-red-500 text-white rounded-lg hover:bg-red-600 disabled:opacity-40"
            >Cancel</button>
          </div>
        </div>

        {/* Invite people */}
        <div className="bg-white rounded-2xl shadow-md p-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-3">Invite People</h2>
          <form onSubmit={handleInvite} className="space-y-3">
            <textarea
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              rows={3}
              placeholder="one@example.com, two@example.com"
              value={emails}
              onChange={e => setEmails(e.target.value)}
            />
            {inviteResult && (
              <pre className="text-xs text-gray-600 whitespace-pre-wrap bg-gray-50 rounded p-2">{inviteResult}</pre>
            )}
            <button
              type="submit"
              disabled={inviteLoading || !canInvite}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {inviteLoading ? 'Sending…' : 'Send Invitations'}
            </button>
            {!canInvite && dashboard && (
              <p className="text-xs text-gray-400">Invitations cannot be added when event is {dashboard.status.toLowerCase()}.</p>
            )}
          </form>
        </div>

        {/* Attendee table */}
        {dashboard && dashboard.attendees.length > 0 && (
          <div className="bg-white rounded-2xl shadow-md p-6">
            <h2 className="text-lg font-semibold text-gray-800 mb-3">
              Attendees ({dashboard.attendees.length})
            </h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="text-xs text-gray-500 uppercase border-b">
                  <tr>
                    <th className="pb-2 pr-4">Email</th>
                    <th className="pb-2 pr-4">Status</th>
                    <th className="pb-2 pr-4">Responded</th>
                    <th className="pb-2">Invite link</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.attendees.map(a => (
                    <tr key={a.invitationId} className="border-b last:border-0">
                      <td className="py-2 pr-4 font-mono text-xs">{a.email}</td>
                      <td className="py-2 pr-4">{RSVP_LABEL[a.rsvpStatus] ?? a.rsvpStatus}</td>
                      <td className="py-2 pr-4 text-gray-400 text-xs">
                        {a.respondedAt ? new Date(a.respondedAt).toLocaleString() : '—'}
                      </td>
                      <td className="py-2 text-xs text-gray-400">
                        #{a.invitationId}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
