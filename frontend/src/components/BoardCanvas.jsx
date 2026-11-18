import React, { useState } from 'react';

export default function BoardCanvas({ items, venues, venueMap, dayCount, myRole, votes, send }) {
  const canPin = myRole !== 'viewer';
  const canModerate = myRole === 'leader';
  const [dragOverDay, setDragOverDay] = useState(null);

  // pin form
  const [venueId, setVenueId] = useState('');
  const [dayIndex, setDayIndex] = useState(0);
  const [startTime, setStartTime] = useState('17:00');
  const [endTime, setEndTime] = useState('19:00');

  const list = Object.values(items);
  const days = Array.from({ length: dayCount }, (_, i) => i);

  function pin(e) {
    e.preventDefault();
    if (!venueId) return;
    send('trip.board.pin', { venueId, dayIndex: Number(dayIndex), startTime, endTime });
  }

  function onDrop(day) {
    const raw = window.__dragItem;
    if (!raw) return;
    send('trip.board.move', {
      itemId: raw.itemId, dayIndex: day,
      startTime: raw.startTime, endTime: raw.endTime, positionVersion: raw.positionVersion,
    });
    setDragOverDay(null);
  }

  return (
    <div className="card">
      <h3>Itinerary canvas</h3>
      {canPin && (
        <form onSubmit={pin} className="row" style={{ alignItems: 'flex-end', marginBottom: 12 }}>
          <div style={{ flex: 2 }}>
            <label>Venue</label>
            <select value={venueId} onChange={(e) => setVenueId(e.target.value)}>
              <option value="">Select a venue…</option>
              {venues.map((v) => (
                <option key={v.venueId} value={v.venueId}>{v.name} · {v.category} · ${String(v.pricePerPerson)}</option>
              ))}
            </select>
          </div>
          <div style={{ width: 70 }}><label>Day</label>
            <select value={dayIndex} onChange={(e) => setDayIndex(e.target.value)}>
              {days.map((d) => <option key={d} value={d}>{d + 1}</option>)}
            </select>
          </div>
          <div style={{ width: 90 }}><label>Start</label><input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></div>
          <div style={{ width: 90 }}><label>End</label><input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></div>
          <button type="submit">Pin</button>
        </form>
      )}

      <div className="board">
        {days.map((day) => (
          <div
            key={day}
            className={`day-col ${dragOverDay === day ? 'drop' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragOverDay(day); }}
            onDragLeave={() => setDragOverDay(null)}
            onDrop={() => onDrop(day)}
          >
            <h4>Day {day + 1}</h4>
            {list.filter((i) => i.dayIndex === day).sort((a, b) => a.startTime.localeCompare(b.startTime)).map((i) => (
              <Card key={i.itemId} item={i} venue={venueMap[i.venueId]} venues={venues}
                    canModerate={canModerate} canPin={canPin} vote={votes[i.itemId]} send={send} />
            ))}
            {list.filter((i) => i.dayIndex === day).length === 0 && <div className="muted" style={{ fontSize: 12 }}>empty</div>}
          </div>
        ))}
      </div>
    </div>
  );
}

function Card({ item, venue, venues, canModerate, canPin, vote, send }) {
  const [showVote, setShowVote] = useState(false);
  const [candidate2, setCandidate2] = useState('');
  const locked = item.lifecycleStage === 'locked';
  const voting = item.lifecycleStage === 'voting';

  function onDragStart() {
    window.__dragItem = { itemId: item.itemId, startTime: item.startTime, endTime: item.endTime, positionVersion: item.positionVersion };
  }

  function openVote(e) {
    e.preventDefault();
    const candidates = [item.venueId];
    if (candidate2 && candidate2 !== item.venueId) candidates.push(candidate2);
    send('trip.vote.openRequest', { itemId: item.itemId, candidateVenueIds: candidates, durationSeconds: 30 });
    setShowVote(false);
  }

  return (
    <div className={`item-card ${locked ? 'locked' : ''} ${voting ? 'voting' : ''}`}
         draggable={!locked} onDragStart={onDragStart}>
      <div className="t">{venue ? venue.name : item.venueId}</div>
      <div className="meta">{item.startTime}–{item.endTime} · v{item.positionVersion}
        {locked && <span className="pill locked" style={{ marginLeft: 6 }}>locked</span>}
        {voting && <span className="pill voting" style={{ marginLeft: 6 }}>voting</span>}
      </div>

      {vote && (
        <div style={{ marginTop: 6 }}>
          <div className="muted" style={{ fontSize: 11 }}>Vote open — pick one:</div>
          {vote.candidates.map((cv) => (
            <button key={cv} className="secondary" style={{ fontSize: 11, padding: '2px 6px', margin: '2px 4px 0 0' }}
                    onClick={() => send('trip.vote.cast', { itemId: item.itemId, choiceVenueId: cv })}>
              {(venues.find((v) => v.venueId === cv)?.name) || cv} ({vote.tally?.[cv] || 0})
            </button>
          ))}
          {canModerate && <button className="ghost" style={{ fontSize: 11, padding: '2px 6px' }}
                                  onClick={() => send('trip.vote.resolve', { itemId: item.itemId })}>Resolve now</button>}
        </div>
      )}

      <div className="item-actions">
        {canModerate && !locked && <button className="secondary" onClick={() => send('trip.board.lock', { itemId: item.itemId })}>Lock</button>}
        {canModerate && locked && <button className="secondary" onClick={() => send('trip.board.unlock', { itemId: item.itemId })}>Unlock</button>}
        {canModerate && !voting && <button className="ghost" onClick={() => setShowVote((s) => !s)}>Vote</button>}
        {canPin && !locked && <button className="ghost" onClick={() => send('trip.board.remove', { itemId: item.itemId })}>✕</button>}
      </div>

      {showVote && (
        <form onSubmit={openVote} style={{ marginTop: 6 }}>
          <div className="muted" style={{ fontSize: 11 }}>Second candidate:</div>
          <select value={candidate2} onChange={(e) => setCandidate2(e.target.value)}>
            <option value="">pick…</option>
            {venues.filter((v) => v.venueId !== item.venueId).map((v) => (
              <option key={v.venueId} value={v.venueId}>{v.name}</option>
            ))}
          </select>
          <button type="submit" style={{ fontSize: 11, padding: '3px 8px', marginTop: 4 }}>Open vote</button>
        </form>
      )}
    </div>
  );
}
