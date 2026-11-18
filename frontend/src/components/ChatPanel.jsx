import React, { useEffect, useRef, useState } from 'react';

export default function ChatPanel({ chat, venueMap, send }) {
  const [text, setText] = useState('');
  const logRef = useRef(null);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [chat]);

  function submit(e) {
    e.preventDefault();
    if (!text.trim()) return;
    send('trip.chat.send', { body: text });
    setText('');
  }

  return (
    <div className="card">
      <h3>Chat &amp; activity</h3>
      <div className="chat-log" ref={logRef}>
        {chat.map((m, i) => (
          <div key={m.messageId || i} className={`msg ${m.msgType}`}>
            {m.msgType === 'chat' && <span className="who">{short(m.userId)}: </span>}
            {m.msgType === 'ai_answer' && <span className="who">🤖 Concierge: </span>}
            {m.msgType === 'conflict' && <span>⚠ </span>}
            {m.msgType === 'system' && <span>• </span>}
            {m.body}
          </div>
        ))}
        {chat.length === 0 && <div className="muted" style={{ fontSize: 12 }}>No messages yet.</div>}
      </div>
      <form onSubmit={submit} className="row" style={{ marginTop: 8 }}>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Message the group…" />
        <button type="submit">Send</button>
      </form>
    </div>
  );
}

function short(userId) { return userId ? userId : 'someone'; }
