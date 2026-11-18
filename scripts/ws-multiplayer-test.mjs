// Two-client real-time sync proof (PRD Epic 1). Uses Node's built-in global WebSocket + fetch.
// Requires the backend running. Override port with: PORT=8081 node ws-multiplayer-test.mjs
const PORT = process.env.PORT || '8080';
const BASE = `http://localhost:${PORT}`;
const WS = `ws://localhost:${PORT}`;
let pass = 0, fail = 0;
const check = (ok, msg) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}: ${msg}`); ok ? pass++ : fail++; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(email, password) {
  const r = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  return (await r.json()).accessToken;
}
async function api(method, path, token, body) {
  const r = await fetch(`${BASE}${path}`, {
    method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  return r.json();
}
function connect(tripId, token, onEvent) {
  const ws = new WebSocket(`${WS}/ws/trips/${tripId}?token=${encodeURIComponent(token)}`);
  ws.onmessage = (e) => { const env = JSON.parse(e.data); onEvent(env.event, env.payload); };
  return ws;
}

const main = async () => {
  console.log('== Real-time multiplayer sync (two WebSocket clients) ==');
  const leader = await login('leader@warroom.test', 'Leader123!');
  const traveler = await login('traveler@warroom.test', 'Traveler123!');

  const trip = await api('POST', '/api/v1/trips', leader, {
    destination: 'Lisbon, Portugal', startDate: '2026-11-10', endDate: '2026-11-14',
    totalBudget: 1000, maxParticipants: 8,
  });
  const tripId = trip.tripId;
  await api('POST', '/api/v1/trips/join', traveler, { inviteCode: trip.inviteCode });
  const cat = await api('GET', '/api/v1/destinations/Lisbon,%20Portugal/venues', leader);
  const venueId = cat.venues[0].venueId;

  let travelerGotUpdate = null, travelerGotChat = null, travelerPresence = 0, leaderSnapshot = false;
  const events = [];

  const wsL = connect(tripId, leader, (ev) => { if (ev === 'trip.sync.snapshot') leaderSnapshot = true; });
  const wsT = connect(tripId, traveler, (ev, p) => {
    events.push(ev);
    if (ev === 'trip.sync.snapshot') travelerPresence = p.presenceCount;
    if (ev === 'trip.board.update' && !travelerGotUpdate) travelerGotUpdate = { at: Date.now(), p };
    if (ev === 'trip.chat.message' && p.msgType === 'chat') travelerGotChat = p;
    if (ev === 'trip.presence.update') travelerPresence = p.participantCount;
  });

  await new Promise((res) => { wsL.onopen = res; });
  await new Promise((res) => { wsT.onopen = res; });
  await sleep(400); // allow snapshot + presence

  check(leaderSnapshot, 'leader received a board snapshot on join');
  check(travelerPresence >= 1, `presence broadcast received (count=${travelerPresence})`);

  // Leader pins -> traveler should see trip.board.update quickly.
  const sentAt = Date.now();
  wsL.send(JSON.stringify({ event: 'trip.board.pin', tripId,
    payload: { venueId, dayIndex: 0, startTime: '12:00', endTime: '13:00' }, ts: Date.now() }));

  for (let i = 0; i < 50 && !travelerGotUpdate; i++) await sleep(20);
  if (travelerGotUpdate) {
    const drift = travelerGotUpdate.at - sentAt;
    check(true, `traveler received leader's pin live (drift ~${drift}ms)`);
    check(travelerGotUpdate.p.venueId === venueId, 'propagated item carries the correct venue');
  } else {
    check(false, 'traveler received leader pin');
  }

  // Chat propagation leader -> traveler.
  wsL.send(JSON.stringify({ event: 'trip.chat.send', tripId, payload: { body: 'hello team' }, ts: Date.now() }));
  for (let i = 0; i < 50 && !travelerGotChat; i++) await sleep(20);
  check(!!travelerGotChat && travelerGotChat.body.includes('hello team'), 'chat message propagated to the other client');

  // Latency ping/pong.
  let pong = null;
  const wsL2events = (ev, p) => { if (ev === 'trip.sync.pong') pong = p; };
  // reuse wsL by attaching a handler
  wsL.onmessage = (e) => { const env = JSON.parse(e.data); wsL2events(env.event, env.payload); };
  wsL.send(JSON.stringify({ event: 'trip.sync.ping', tripId, payload: { clientSentAt: Date.now() }, ts: Date.now() }));
  for (let i = 0; i < 30 && !pong; i++) await sleep(20);
  check(!!pong, 'sync ping/pong round-trip works (latency measurement)');

  wsL.close(); wsT.close();
  await sleep(100);
  console.log(`\n================ WS RESULT: ${pass} passed, ${fail} failed ================`);
  process.exit(fail === 0 ? 0 : 1);
};
main().catch((e) => { console.error('ERROR', e); process.exit(1); });
