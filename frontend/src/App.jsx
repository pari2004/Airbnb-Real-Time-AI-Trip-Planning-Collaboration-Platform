import React, { useEffect, useState } from 'react';
import { api, getToken, clearTokens } from './api.js';
import Login from './components/Login.jsx';
import Dashboard from './components/Dashboard.jsx';
import Room from './components/Room.jsx';
import AdminUpload from './components/AdminUpload.jsx';

export default function App() {
  const [user, setUser] = useState(null);
  const [view, setView] = useState('loading'); // loading | login | dashboard | room | admin
  const [activeTripId, setActiveTripId] = useState(null);

  useEffect(() => {
    if (getToken()) {
      api.me().then((u) => { setUser(u); setView('dashboard'); })
        .catch(() => { clearTokens(); setView('login'); });
    } else {
      setView('login');
    }
  }, []);

  function onAuthed(u) {
    setUser(u);
    setView('dashboard');
  }

  function logout() {
    api.logout().catch(() => {});
    clearTokens();
    setUser(null);
    setView('login');
  }

  function openRoom(tripId) {
    setActiveTripId(tripId);
    setView('room');
  }

  if (view === 'loading') return <div className="container">Loading…</div>;

  return (
    <div>
      <div className="topbar">
        <div className="brand">AIR<span>BNB</span> · Trip War Room</div>
        {user && (
          <div className="row" style={{ alignItems: 'center' }}>
            <span className="muted">{user.displayName} · <span className="pill">{user.role}</span></span>
            <button className="ghost" onClick={() => setView('dashboard')}>Dashboard</button>
            {user.role === 'admin' && <button className="ghost" onClick={() => setView('admin')}>Admin</button>}
            <button className="secondary" onClick={logout}>Logout</button>
          </div>
        )}
      </div>

      {view === 'login' && <Login onAuthed={onAuthed} />}
      {view === 'dashboard' && <Dashboard user={user} onOpenRoom={openRoom} />}
      {view === 'room' && <Room tripId={activeTripId} user={user} onLeave={() => setView('dashboard')} />}
      {view === 'admin' && <AdminUpload />}
    </div>
  );
}
