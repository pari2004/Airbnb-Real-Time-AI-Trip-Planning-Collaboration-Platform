// WebSocket client for a Trip Room, with auto-reconnect/resync (PRD 6.1, 10.3).

const WS_BASE = import.meta.env.VITE_WS_BASE || 'ws://localhost:8080';

export class RoomSocket {
  constructor(tripId, token) {
    this.tripId = tripId;
    this.token = token;
    this.listeners = new Map();   // event -> Set<fn>
    this.ws = null;
    this.reconnectDelay = 500;
    this.closedByUser = false;
    this.connected = false;
  }

  on(event, fn) {
    if (!this.listeners.has(event)) this.listeners.set(event, new Set());
    this.listeners.get(event).add(fn);
    return () => this.listeners.get(event)?.delete(fn);
  }

  emit(event, payload) {
    this.listeners.get(event)?.forEach((fn) => fn(payload));
    this.listeners.get('*')?.forEach((fn) => fn(event, payload));
  }

  connect() {
    this.closedByUser = false;
    const url = `${WS_BASE}/ws/trips/${this.tripId}?token=${encodeURIComponent(this.token)}`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      this.connected = true;
      this.reconnectDelay = 500;
      this.emit('_status', { connected: true });
    };
    this.ws.onmessage = (ev) => {
      try {
        const env = JSON.parse(ev.data);
        this.emit(env.event, env.payload);
      } catch {
        /* ignore malformed frames */
      }
    };
    this.ws.onclose = () => {
      this.connected = false;
      this.emit('_status', { connected: false });
      if (!this.closedByUser) {
        // Reconnect with capped backoff; the server resends a full snapshot on rejoin.
        setTimeout(() => this.connect(), this.reconnectDelay);
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, 5000);
      }
    };
    this.ws.onerror = () => this.ws && this.ws.close();
  }

  send(event, payload) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ event, tripId: this.tripId, payload, ts: Date.now() }));
    }
  }

  close() {
    this.closedByUser = true;
    this.ws && this.ws.close();
  }
}
