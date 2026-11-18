import React, { useEffect, useMemo, useRef, useState } from 'react';
import { api, getToken } from '../api.js';
import { RoomSocket } from '../ws.js';
import BoardCanvas from './BoardCanvas.jsx';
import BudgetPanel from './BudgetPanel.jsx';
import ChatPanel from './ChatPanel.jsx';
import ConciergePanel from './ConciergePanel.jsx';

export default function Room({ tripId, user, onLeave }) {
  const [trip, setTrip] = useState(null);
  const [venues, setVenues] = useState([]);
  const [items, setItems] = useState({});        // itemId -> ItemView
  const [budget, setBudget] = useState(null);
  const [chat, setChat] = useState([]);
  const [presence, setPresence] = useState({ count: 0, connected: false, leaderConnected: true });
  const [votes, setVotes] = useState({});         // itemId -> {candidates, tally}
  const [banner, setBanner] = useState('');
  const [latency, setLatency] = useState(null);
  const socketRef = useRef(null);

  const venueMap = useMemo(() => {
    const m = {};
    venues.forEach((v) => { m[v.venueId] = v; });
    return m;
  }, [venues]);

  const myRole = useMemo(() => {
    if (!trip) return 'planner';
    const p = (trip.participants || []).find((x) => x.userId === userIdOf(user));
    return p ? p.roomRole : 'planner';
  }, [trip, user]);

  useEffect(() => {
    let sock;
    (async () => {
      const t = await api.getTrip(tripId);
      setTrip(t);
      try {
        const cat = await api.venues(t.destination);
        setVenues(cat.venues || []);
      } catch { /* catalog optional */ }

      sock = new RoomSocket(tripId, getToken());
      socketRef.current = sock;
      sock.on('_status', (s) => setPresence((p) => ({ ...p, connected: s.connected })));
      sock.on('trip.sync.snapshot', (snap) => {
        const map = {};
        (snap.items || []).forEach((i) => { map[i.itemId] = i; });
        setItems(map);
        setBudget(snap.budget || null);
        setChat(snap.chatHistory || []);
        if (typeof snap.presenceCount === 'number') setPresence((p) => ({ ...p, count: snap.presenceCount }));
      });
      sock.on('trip.room.state', (s) => setPresence((p) => ({ ...p, leaderConnected: s.leaderConnected })));
      sock.on('trip.board.update', (i) => setItems((prev) => ({ ...prev, [i.itemId]: i })));
      sock.on('trip.board.remove', (p) => setItems((prev) => { const n = { ...prev }; delete n[p.itemId]; return n; }));
      sock.on('trip.board.conflict', (c) => {
        flash(setBanner, `Conflict: ${c.details || c.conflictType}`);
        if (c.current) setItems((prev) => ({ ...prev, [c.current.itemId]: c.current }));
      });
      sock.on('trip.presence.update', (p) => setPresence((prev) => ({ ...prev, count: p.participantCount })));
      sock.on('trip.chat.message', (m) => setChat((prev) => [...prev, m]));
      sock.on('trip.budget.update', (b) => setBudget(b));
      sock.on('trip.vote.open', (v) => setVotes((prev) => ({ ...prev, [v.itemId]: { candidates: v.candidateVenueIds, closesAt: v.closesAt, tally: {} } })));
      sock.on('trip.vote.cast', (v) => setVotes((prev) => {
        const cur = prev[v.itemId] || { candidates: [], tally: {} };
        const tally = { ...cur.tally, [v.choiceVenueId]: (cur.tally[v.choiceVenueId] || 0) + 1 };
        return { ...prev, [v.itemId]: { ...cur, tally } };
      }));
      sock.on('trip.vote.resolved', (v) => {
        setVotes((prev) => { const n = { ...prev }; delete n[v.itemId]; return n; });
        flash(setBanner, `Vote resolved → ${venueName(venueMapRef.current, v.winningVenueId)}`);
      });
      sock.on('trip.error', (e) => flash(setBanner, `Error: ${e.message}`));
      sock.connect();

      // latency probe every 5s
      const ping = setInterval(() => sock.send('trip.sync.ping', { clientSentAt: Date.now() }), 5000);
      sock.on('trip.sync.pong', (p) => setLatency(Date.now() - p.clientSentAt));
      sock._pingTimer = ping;
    })();

    return () => {
      if (sock) { clearInterval(sock._pingTimer); sock.close(); }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tripId]);

  const venueMapRef = useRef(venueMap);
  useEffect(() => { venueMapRef.current = venueMap; }, [venueMap]);

  const dayCount = useMemo(() => {
    if (!trip) return 3;
    const s = new Date(trip.startDate), e = new Date(trip.endDate);
    const days = Math.round((e - s) / 86400000) + 1;
    return Math.max(days, 3);
  }, [trip]);

  function send(event, payload) { socketRef.current?.send(event, payload); }

  if (!trip) return <div className="container">Loading room…</div>;

  return (
    <div className="container">
      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2 style={{ margin: 0 }}>{trip.destination}</h2>
            <span className="muted">{trip.startDate} → {trip.endDate} · budget ${String(trip.totalBudget)} · you are <span className="pill">{myRole}</span></span>
          </div>
          <div className="row" style={{ alignItems: 'center' }}>
            <span className="pill">invite {trip.inviteCode}</span>
            <span><span className={`dot ${presence.connected ? 'on' : 'off'}`}></span>{presence.connected ? 'live' : 'reconnecting…'}</span>
            <span className="muted">{presence.count} online{latency != null ? ` · ${latency}ms` : ''}</span>
            {!presence.leaderConnected && <span className="pill locked">leader disconnected</span>}
            <button className="ghost" onClick={async () => alert(JSON.stringify(await api.analytics(tripId), null, 2))}>Analytics</button>
            <button className="secondary" onClick={onLeave}>Leave</button>
          </div>
        </div>
        {banner && <div className="error" style={{ marginTop: 8 }}>{banner}</div>}
      </div>

      <div className="room-grid">
        <div>
          <BoardCanvas
            items={items} venues={venues} venueMap={venueMap} dayCount={dayCount}
            myRole={myRole} votes={votes} send={send}
          />
        </div>
        <div>
          <BudgetPanel budget={budget} />
          <ConciergePanel tripId={tripId} items={items} venueMap={venueMap} />
          <ChatPanel chat={chat} venueMap={venueMap} send={send} />
        </div>
      </div>
    </div>
  );
}

function flash(setter, msg) { setter(msg); setTimeout(() => setter(''), 4000); }
function userIdOf(user) { return user?.id; }
function venueName(map, venueId) { return map?.[venueId]?.name || venueId; }
