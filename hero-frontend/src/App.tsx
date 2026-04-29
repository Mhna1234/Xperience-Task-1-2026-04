import { useEffect, useState } from 'react';
import CreateEventForm from './components/FileUpload';
import EventManager    from './components/MessageComposer';
import RsvpPage        from './components/RecipientTable';
import TokenEntry      from './components/ResultsTable';
import './App.css';

/**
 * Hash-based router — no external router library needed.
 *
 * Routes:
 *   #/                         → CreateEventForm (host: create an event)
 *   #/event/<id>               → EventManager   (host: manage event + dashboard)
 *   #/rsvp/<token>             → RsvpPage       (invitee: view invitation + RSVP)
 *   #/rsvp                     → TokenEntry     (invitee: paste token)
 */

type Route =
  | { type: 'home' }
  | { type: 'event'; id: number }
  | { type: 'rsvp'; token: string }
  | { type: 'rsvp-entry' };

function parseHash(hash: string): Route {
  const path = hash.replace(/^#\//, '');
  if (path.startsWith('event/')) {
    const id = parseInt(path.slice('event/'.length));
    if (!isNaN(id)) return { type: 'event', id };
  }
  if (path.startsWith('rsvp/')) {
    const token = path.slice('rsvp/'.length).trim();
    if (token) return { type: 'rsvp', token };
    return { type: 'rsvp-entry' };
  }
  if (path === 'rsvp') return { type: 'rsvp-entry' };
  return { type: 'home' };
}

function navigate(hash: string) {
  window.location.hash = hash;
}

export default function App() {
  const [route, setRoute] = useState<Route>(() => parseHash(window.location.hash));

  useEffect(() => {
    const handler = () => setRoute(parseHash(window.location.hash));
    window.addEventListener('hashchange', handler);
    return () => window.removeEventListener('hashchange', handler);
  }, []);

  switch (route.type) {
    case 'home':
      return (
        <CreateEventForm
          onCreated={id => navigate(`/event/${id}`)}
        />
      );

    case 'event':
      return (
        <EventManager eventId={route.id} onBack={() => navigate('/')} />
      );

    case 'rsvp':
      return (
        <RsvpPage token={route.token} />
      );

    case 'rsvp-entry':
      return (
        <TokenEntry onToken={token => navigate(`/rsvp/${token}`)} />
      );
  }
}
