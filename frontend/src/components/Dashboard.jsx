import React, { useEffect, useState } from 'react';
import { api } from '../api.js';

export default function Dashboard({ onOpenRoom }) {
  const [dash, setDash] = useState(null);
  const [recs, setRecs] = useState([]);
  const [error, setError] = useState('');
  const [joinCode, setJoinCode] = useState('');

  // create-trip form
  const [destination, setDestination] = useState('Lisbon, Portugal');
  const [startDate, setStartDate] = useState('2026-11-10');
  const [endDate, setEndDate] = useState('2026-11-14');
  const [totalBudget, setTotalBudget] = useState('1200');

  async function load() {
    try {
      const [d, r] = await Promise.all([api.dashboard(), api.recommendations()]);
      setDash(d);
      setRecs(r || []);
    } catch (e) {
      setError(e.message);
    }
  }
  useEffect(() => { load(); }, []);

  async function createTrip(e) {
    e.preventDefault();
    setError('');
    try {
      const res = await api.createTrip({
        destination, startDate, endDate,
        totalBudget: Number(totalBudget), maxParticipants: 50,
      });
      onOpenRoom(res.tripId);
    } catch (err) { setError(err.message); }
  }

  async function join(e) {
    e.preventDefault();
    setError('');
    try {
      const res = await api.joinTrip(joinCode.trim());
      onOpenRoom(res.tripId);
    } catch (err) { setError(err.message); }
  }

  return (
    <div className="container">
      {error && <div className="error">{error}</div>}
      <div className="row">
        <div className="col card">
          <h3>Create a Trip Room</h3>
          <form onSubmit={createTrip}>
            <label>Destination</label>
            <input value={destination} onChange={(e) => setDestination(e.target.value)} />
            <div className="row">
              <div className="col"><label>Start</label><input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></div>
              <div className="col"><label>End</label><input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></div>
            </div>
            <label>Total group budget</label>
            <input type="number" value={totalBudget} onChange={(e) => setTotalBudget(e.target.value)} />
            <div style={{ marginTop: 10 }}><button type="submit">Create &amp; open</button></div>
          </form>
        </div>
        <div className="col card">
          <h3>Join with a code</h3>
          <form onSubmit={join}>
            <label>Invite code</label>
            <input value={joinCode} onChange={(e) => setJoinCode(e.target.value)} placeholder="AB-7Q2K" />
            <div style={{ marginTop: 10 }}><button className="secondary" type="submit">Join room</button></div>
          </form>
          {dash?.profile && (
            <div style={{ marginTop: 16 }}>
              <h4>Your travel profile</h4>
              <div className="muted" style={{ fontSize: 12 }}>
                Budget ceiling: {dash.profile.budgetCeiling ?? '—'} · Dietary: {dash.profile.dietaryTags.join(', ') || 'none'}
                <br />Favored: {dash.profile.favoredCategories.map((c) => `${c.category}(${c.count})`).join(', ') || 'none yet'}
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <h3>Active rooms</h3>
        <TripList trips={dash?.activeRooms} onOpen={onOpenRoom} empty="No active rooms — create one above." />
        <h3 style={{ marginTop: 16 }}>Past trips</h3>
        <TripList trips={dash?.pastTrips} onOpen={onOpenRoom} empty="No finalized trips yet." />
      </div>

      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Recommended for your group</h3>
          <button className="ghost" onClick={async () => { await api.retrain(); load(); }}>Retrain</button>
        </div>
        {recs.length === 0 ? (
          <p className="muted">No recommendations yet — interact with venues (ask the AI, pin items) and retrain.</p>
        ) : (
          <div className="rec-rail">
            {recs.map((r) => (
              <div className="rec" key={r.venueId}>
                <div style={{ fontWeight: 600 }}>#{r.rank} {r.name}</div>
                <div className="muted" style={{ fontSize: 12 }}>{r.category} · ${String(r.pricePerPerson)} · score {r.score.toFixed(3)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function TripList({ trips, onOpen, empty }) {
  if (!trips || trips.length === 0) return <p className="muted">{empty}</p>;
  return (
    <table>
      <thead><tr><th>Destination</th><th>Dates</th><th>Status</th><th>Your role</th><th></th></tr></thead>
      <tbody>
        {trips.map((t) => (
          <tr key={t.tripId}>
            <td>{t.destination}</td>
            <td className="muted">{t.startDate} → {t.endDate}</td>
            <td><span className="pill">{t.status}</span></td>
            <td><span className="pill">{t.roomRole}</span></td>
            <td><button className="secondary" onClick={() => onOpen(t.tripId)}>Open</button></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
