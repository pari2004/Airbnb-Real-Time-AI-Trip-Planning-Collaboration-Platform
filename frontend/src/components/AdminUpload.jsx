import React, { useState } from 'react';
import { api } from '../api.js';

const EXAMPLE = {
  destination: 'Porto, Portugal',
  venues: [
    {
      name: 'Ribeira Vegan Table', category: 'dinner', pricePerPerson: 19.5,
      dietaryTags: ['vegetarian', 'vegan'], hours: { open: '12:00', close: '23:00' },
      coordinates: { lat: 41.1403, lng: -8.6110 }, rating: 4.6,
      description: 'Riverside vegan kitchen in the Ribeira district.',
    },
  ],
};

export default function AdminUpload() {
  const [json, setJson] = useState(JSON.stringify(EXAMPLE, null, 2));
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError(''); setResult(null);
    let body;
    try {
      body = JSON.parse(json);
    } catch {
      setError('Invalid JSON');
      return;
    }
    try {
      const res = await api.uploadVenues(body);
      setResult(res);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="container">
      <div className="card">
        <h3>Admin · Venue dataset upload</h3>
        <p className="muted" style={{ fontSize: 12 }}>
          Validates price (&ge; 0), dietary tags (known vocabulary), coordinates (valid lat/lng), and hours
          (close after open). Rejected uploads return a clear error. Admin-only (server-enforced).
        </p>
        <form onSubmit={submit}>
          <textarea rows={18} value={json} onChange={(e) => setJson(e.target.value)} style={{ fontFamily: 'monospace' }} />
          <div style={{ marginTop: 10 }}><button type="submit">Upload &amp; validate</button></div>
        </form>
        {error && <div className="error">{error}</div>}
        {result && (
          <div className="success">
            Inserted {result.inserted} venues for {result.destination}.
            {result.warnings?.length > 0 && <div className="muted">Warnings: {result.warnings.join('; ')}</div>}
          </div>
        )}
      </div>
    </div>
  );
}
