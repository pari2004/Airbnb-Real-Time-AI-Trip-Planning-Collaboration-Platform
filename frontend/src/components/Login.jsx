import React, { useState } from 'react';
import { api, setTokens } from '../api.js';

export default function Login({ onAuthed }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('leader@warroom.test');
  const [password, setPassword] = useState('Leader123!');
  const [displayName, setDisplayName] = useState('');
  const [budget, setBudget] = useState('150');
  const [dietary, setDietary] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const body = mode === 'login'
        ? { email, password }
        : {
            email, password, displayName,
            budgetCeiling: budget ? Number(budget) : null,
            dietaryTags: dietary ? dietary.split(',').map((s) => s.trim()).filter(Boolean) : [],
          };
      const res = await (mode === 'login' ? api.login(body) : api.register(body));
      setTokens(res.accessToken, res.refreshToken);
      onAuthed(res.user);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="card">
        <div className="tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>Log in</button>
          <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>Register</button>
        </div>
        <form onSubmit={submit}>
          <label>Email</label>
          <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
          <label>Password</label>
          <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" required />
          {mode === 'register' && (
            <>
              <label>Display name</label>
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
              <label>Budget ceiling (per person)</label>
              <input value={budget} onChange={(e) => setBudget(e.target.value)} type="number" />
              <label>Dietary tags (comma-separated)</label>
              <input value={dietary} onChange={(e) => setDietary(e.target.value)} placeholder="vegetarian, vegan" />
            </>
          )}
          {error && <div className="error">{error}</div>}
          <div style={{ marginTop: 12 }}>
            <button disabled={busy} type="submit">{busy ? '…' : mode === 'login' ? 'Log in' : 'Create account'}</button>
          </div>
        </form>
        <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
          Seeded demo logins: admin@warroom.test / Admin123! · leader@warroom.test / Leader123! · traveler@warroom.test / Traveler123!
        </p>
      </div>
    </div>
  );
}
