import { useState } from 'react';
import { api } from '../services/bulkSendApi';
import type { ApiError } from '../types';

interface Props {
  onCreated: (eventId: number) => void;
}

export default function CreateEventForm({ onCreated }: Props) {
  const [hostId, setHostId] = useState(() => localStorage.getItem('hostId') ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startTime, setStartTime] = useState('');
  const [location, setLocation] = useState('');
  const [maxCapacity, setMaxCapacity] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (!hostId.trim()) { setError('Host ID is required'); return; }
    if (!title.trim())  { setError('Title is required'); return; }
    if (!startTime)     { setError('Start time is required'); return; }

    localStorage.setItem('hostId', hostId.trim());
    setLoading(true);
    try {
      const event = await api.createEvent(hostId.trim(), {
        title: title.trim(),
        description: description.trim() || undefined,
        startTime: new Date(startTime).toISOString(),
        location: location.trim() || undefined,
        maxCapacity: maxCapacity ? parseInt(maxCapacity) : undefined,
      });
      onCreated(event.id);
    } catch (err) {
      setError((err as ApiError).message ?? 'Failed to create event');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-md w-full max-w-lg p-8">
        <h1 className="text-2xl font-bold text-gray-800 mb-6">Create Event</h1>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Your Host ID <span className="text-red-500">*</span>
            </label>
            <input
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. alice"
              value={hostId}
              onChange={e => setHostId(e.target.value)}
            />
            <p className="text-xs text-gray-400 mt-1">This identifies you as the host. Save it — you'll need it to manage the event.</p>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Title <span className="text-red-500">*</span>
            </label>
            <input
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Team off-site"
              value={title}
              onChange={e => setTitle(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              rows={3}
              placeholder="Optional details..."
              value={description}
              onChange={e => setDescription(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Start time <span className="text-red-500">*</span>
            </label>
            <input
              type="datetime-local"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={startTime}
              onChange={e => setStartTime(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Location</label>
            <input
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Conference room B"
              value={location}
              onChange={e => setLocation(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Max capacity</label>
            <input
              type="number"
              min="1"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Leave empty for unlimited"
              value={maxCapacity}
              onChange={e => setMaxCapacity(e.target.value)}
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white rounded-lg py-2 font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Creating…' : 'Create Event'}
          </button>
        </form>
      </div>
    </div>
  );
}
