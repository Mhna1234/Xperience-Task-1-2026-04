/**
 * ResultsTable — "Enter your invite token" landing page.
 *
 * Invitees arrive at /#/rsvp/<token> directly from their invite link.
 * If someone lands at /#/rsvp with no token, this form lets them paste one in.
 */
import { useState } from 'react';

interface Props {
  onToken: (token: string) => void;
}

export default function TokenEntry({ onToken }: Props) {
  const [value, setValue] = useState('');

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const t = value.trim();
    if (t) onToken(t);
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-md w-full max-w-md p-8">
        <h1 className="text-2xl font-bold text-gray-800 mb-2">Find your invitation</h1>
        <p className="text-sm text-gray-500 mb-6">
          Paste the invite token from your invitation link below.
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="Paste token here…"
            value={value}
            onChange={e => setValue(e.target.value)}
          />
          <button
            type="submit"
            disabled={!value.trim()}
            className="w-full bg-blue-600 text-white rounded-lg py-2 font-medium hover:bg-blue-700 disabled:opacity-40"
          >
            View my invitation
          </button>
        </form>
      </div>
    </div>
  );
}
