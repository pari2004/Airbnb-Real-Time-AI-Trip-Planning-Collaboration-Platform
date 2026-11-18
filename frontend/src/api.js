// Thin REST client for the Trip War Room API. Handles JWT access/refresh transparently.

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

let accessToken = localStorage.getItem('accessToken') || null;
let refreshToken = localStorage.getItem('refreshToken') || null;

export function getToken() {
  return accessToken;
}

export function setTokens(access, refresh) {
  accessToken = access;
  refreshToken = refresh;
  if (access) localStorage.setItem('accessToken', access);
  else localStorage.removeItem('accessToken');
  if (refresh) localStorage.setItem('refreshToken', refresh);
  else localStorage.removeItem('refreshToken');
}

export function clearTokens() {
  setTokens(null, null);
}

async function request(method, path, body, retry = true) {
  const headers = { 'Content-Type': 'application/json' };
  if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401 && retry && refreshToken) {
    // Try one silent refresh, then replay the original request.
    const ok = await tryRefresh();
    if (ok) return request(method, path, body, false);
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const message = data && data.message ? data.message : `HTTP ${res.status}`;
    throw new Error(message);
  }
  return data;
}

async function tryRefresh() {
  try {
    const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = await res.json();
    setTokens(data.accessToken, data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

export const api = {
  base: API_BASE,
  // ---- auth ----
  register: (b) => request('POST', '/api/v1/auth/register', b),
  login: (b) => request('POST', '/api/v1/auth/login', b),
  me: () => request('GET', '/api/v1/auth/me'),
  logout: () => request('POST', '/api/v1/auth/logout', {}),
  // ---- trips ----
  createTrip: (b) => request('POST', '/api/v1/trips', b),
  getTrip: (id) => request('GET', `/api/v1/trips/${id}`),
  joinTrip: (inviteCode) => request('POST', '/api/v1/trips/join', { inviteCode }),
  invite: (id) => request('POST', `/api/v1/trips/${id}/invite`, {}),
  assignRole: (id, userId, role) => request('POST', `/api/v1/trips/${id}/participants/role`, { userId, role }),
  // ---- venues ----
  venues: (destination, near, radiusKm) => {
    const q = new URLSearchParams();
    if (near) q.set('near', near);
    if (radiusKm) q.set('radiusKm', radiusKm);
    const qs = q.toString();
    return request('GET', `/api/v1/destinations/${encodeURIComponent(destination)}/venues${qs ? '?' + qs : ''}`);
  },
  uploadVenues: (b) => request('POST', '/api/v1/admin/venues', b),
  // ---- board / budget ----
  board: (id) => request('GET', `/api/v1/trips/${id}/board`),
  pin: (id, b) => request('POST', `/api/v1/trips/${id}/board/items`, b),
  move: (id, b) => request('POST', `/api/v1/trips/${id}/board/move`, b),
  lock: (id, itemId) => request('POST', `/api/v1/trips/${id}/board/items/${itemId}/lock`, {}),
  unlock: (id, itemId) => request('POST', `/api/v1/trips/${id}/board/items/${itemId}/unlock`, {}),
  removeItem: (id, itemId) => request('DELETE', `/api/v1/trips/${id}/board/items/${itemId}`),
  budget: (id) => request('GET', `/api/v1/trips/${id}/budget`),
  // ---- votes ----
  openVote: (id, b) => request('POST', `/api/v1/trips/${id}/votes`, b),
  castVote: (id, b) => request('POST', `/api/v1/trips/${id}/votes/cast`, b),
  resolveVote: (id, b) => request('POST', `/api/v1/trips/${id}/votes/resolve`, b),
  // ---- concierge ----
  ask: (id, b) => request('POST', `/api/v1/trips/${id}/ai/ask`, b),
  // ---- dashboard / recs ----
  dashboard: () => request('GET', '/api/v1/me/dashboard'),
  recommendations: () => request('GET', '/api/v1/me/recommendations'),
  recordInteraction: (b) => request('POST', '/api/v1/me/interactions', b),
  retrain: () => request('POST', '/api/v1/me/recommendations/retrain', {}),
  analytics: (id) => request('GET', `/api/v1/trips/${id}/analytics`),
};
