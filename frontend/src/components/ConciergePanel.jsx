import React, { useState } from 'react';
import { api } from '../api.js';

export default function ConciergePanel({ tripId, items, venueMap }) {
  const [question, setQuestion] = useState('Suggest a dinner spot near our 5 PM tour under $25/person');
  const [anchorItemId, setAnchorItemId] = useState('');
  const [answer, setAnswer] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const itemList = Object.values(items);

  async function ask(e) {
    e.preventDefault();
    setBusy(true); setError(''); setAnswer(null);
    try {
      const res = await api.ask(tripId, { question, anchorItemId: anchorItemId || null });
      setAnswer(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card">
      <h3>AI Concierge</h3>
      <form onSubmit={ask}>
        <label>Anchor on a pinned item (optional)</label>
        <select value={anchorItemId} onChange={(e) => setAnchorItemId(e.target.value)}>
          <option value="">No anchor</option>
          {itemList.map((i) => (
            <option key={i.itemId} value={i.itemId}>
              {(venueMap[i.venueId]?.name) || i.venueId} · day {i.dayIndex + 1} {i.startTime}
            </option>
          ))}
        </select>
        <label>Question</label>
        <textarea rows={2} value={question} onChange={(e) => setQuestion(e.target.value)} />
        <div style={{ marginTop: 8 }}><button disabled={busy} type="submit">{busy ? 'Thinking…' : 'Ask'}</button></div>
      </form>
      {error && <div className="error">{error}</div>}
      {answer && (
        <div className="concierge-answer">
          <div>{answer.answer}</div>
          {answer.suggestedVenueId && (
            <div className="muted" style={{ fontSize: 11, marginTop: 4 }}>
              suggested: {answer.suggestedVenueId} · sources: {(answer.sourceEvents || []).join(', ')}
            </div>
          )}
          {answer.constraintsApplied?.length > 0 && (
            <div className="muted" style={{ fontSize: 11 }}>constraints: {answer.constraintsApplied.join(' · ')}</div>
          )}
          {answer.candidates?.map((c) => (
            <div className="candidate" key={c.venueId}>
              {c.name} — ${String(c.pricePerPerson)} · score {c.score}{c.distanceKm != null ? ` · ${c.distanceKm} km` : ''}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
