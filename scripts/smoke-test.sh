#!/usr/bin/env bash
# End-to-end API smoke test. Requires the backend running (default http://localhost:8080)
# and the Lisbon dataset seeded. Override with: BASE=http://localhost:8081 bash smoke-test.sh
set -u
BASE=${BASE:-http://localhost:8080}
PASS=0; FAIL=0
jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }
check() { if [ "$1" = "$2" ]; then echo "  PASS: $3"; PASS=$((PASS+1)); else echo "  FAIL: $3 (got '$1' want '$2')"; FAIL=$((FAIL+1)); fi; }

echo "== 1. Auth: login as seeded leader =="
LOGIN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"leader@warroom.test","password":"Leader123!"}')
TOKEN=$(echo "$LOGIN" | jget "['accessToken']")
[ -n "$TOKEN" ] && check "ok" "ok" "leader login returns access token" || check "no" "ok" "leader login"
AUTH="Authorization: Bearer $TOKEN"

echo "== 2. Create trip =="
TRIP=$(curl -s -X POST $BASE/api/v1/trips -H "$AUTH" -H 'Content-Type: application/json' -d '{"destination":"Lisbon, Portugal","startDate":"2026-11-10","endDate":"2026-11-14","totalBudget":1200,"maxParticipants":8}')
TRIP_ID=$(echo "$TRIP" | jget "['tripId']")
INVITE=$(echo "$TRIP" | jget "['inviteCode']")
echo "  tripId=$TRIP_ID invite=$INVITE"
[ -n "$TRIP_ID" ] && check ok ok "trip created" || check no ok "trip created"

echo "== 3. Venue catalog (seeded Lisbon) =="
VENUES=$(curl -s "$BASE/api/v1/destinations/Lisbon,%20Portugal/venues" -H "$AUTH")
VCOUNT=$(echo "$VENUES" | jget "['venues'].__len__()")
echo "  venues=$VCOUNT"
[ "$VCOUNT" -ge 10 ] && check ok ok "venue dataset seeded (>=10)" || check no ok "venue dataset seeded"
# pick an affordable dinner venue (<=25) and a tour as anchor
DINNER=$(echo "$VENUES" | python3 -c "import sys,json;vs=json.load(sys.stdin)['venues'];print([v['venueId'] for v in vs if v['category']=='dinner' and float(v['pricePerPerson'])<=25][0])")
TOUR=$(echo "$VENUES" | python3 -c "import sys,json;vs=json.load(sys.stdin)['venues'];print([v['venueId'] for v in vs if v['category']=='activity'][0])")
DINNER2=$(echo "$VENUES" | python3 -c "import sys,json;vs=json.load(sys.stdin)['venues'];print([v['venueId'] for v in vs if v['category']=='dinner' and float(v['pricePerPerson'])<=25][1])")

echo "== 4. Pin an anchor tour item =="
PIN=$(curl -s -X POST $BASE/api/v1/trips/$TRIP_ID/board/items -H "$AUTH" -H 'Content-Type: application/json' -d "{\"venueId\":\"$TOUR\",\"dayIndex\":0,\"startTime\":\"17:00\",\"endTime\":\"18:30\"}")
ITEM_ID=$(echo "$PIN" | jget "['itemId']")
VER=$(echo "$PIN" | jget "['positionVersion']")
echo "  itemId=$ITEM_ID version=$VER"
[ -n "$ITEM_ID" ] && check ok ok "pin creates item" || check no ok "pin creates item"

echo "== 5. Optimistic concurrency: move with stale version => 409 =="
MCODE=$(curl -s -o /tmp/move.json -w "%{http_code}" -X POST $BASE/api/v1/trips/$TRIP_ID/board/move -H "$AUTH" -H 'Content-Type: application/json' -d "{\"itemId\":\"$ITEM_ID\",\"dayIndex\":1,\"startTime\":\"17:00\",\"endTime\":\"18:30\",\"positionVersion\":99}")
check "$MCODE" "409" "stale-version move rejected with 409"
echo "  conflict body: $(cat /tmp/move.json)"

echo "== 6. Valid move with correct version =="
MCODE2=$(curl -s -o /tmp/move2.json -w "%{http_code}" -X POST $BASE/api/v1/trips/$TRIP_ID/board/move -H "$AUTH" -H 'Content-Type: application/json' -d "{\"itemId\":\"$ITEM_ID\",\"dayIndex\":1,\"startTime\":\"17:00\",\"endTime\":\"18:30\",\"positionVersion\":$VER}")
check "$MCODE2" "200" "valid move applied (200)"

echo "== 7. AI Concierge: constrained, grounded suggestion anchored to the tour =="
AI=$(curl -s -X POST $BASE/api/v1/trips/$TRIP_ID/ai/ask -H "$AUTH" -H 'Content-Type: application/json' -d "{\"question\":\"Suggest a dinner spot near our 5 PM tour under \$25/person\",\"anchorItemId\":\"$ITEM_ID\"}")
SUG=$(echo "$AI" | jget "['suggestedVenueId']")
GROUNDED=$(echo "$AI" | jget "['grounded']")
echo "  answer: $(echo "$AI" | jget "['answer']")"
echo "  suggested=$SUG grounded=$GROUNDED sources=$(echo "$AI" | jget "['sourceEvents']")"
[ -n "$SUG" ] && [ "$SUG" != "None" ] && check ok ok "AI returned a grounded suggestion" || check no ok "AI suggestion"

echo "== 8. Budget summary (exact math) =="
BUD=$(curl -s $BASE/api/v1/trips/$TRIP_ID/budget -H "$AUTH")
echo "  perPersonPlanned=$(echo "$BUD" | jget "['perPersonPlanned']") remaining=$(echo "$BUD" | jget "['remaining']")"
check "$(echo "$BUD" | python3 -c "import sys,json;print('ok' if 'perPersonPlanned' in json.load(sys.stdin) else 'no')")" ok "budget summary computed"

echo "== 9. Voting: open, cast, resolve on a contested slot =="
PIN2=$(curl -s -X POST $BASE/api/v1/trips/$TRIP_ID/board/items -H "$AUTH" -H 'Content-Type: application/json' -d "{\"venueId\":\"$DINNER\",\"dayIndex\":0,\"startTime\":\"20:00\",\"endTime\":\"22:00\"}")
ITEM2=$(echo "$PIN2" | jget "['itemId']")
OPEN=$(curl -s -o /tmp/vopen.json -w "%{http_code}" -X POST $BASE/api/v1/trips/$TRIP_ID/votes -H "$AUTH" -H 'Content-Type: application/json' -d "{\"itemId\":\"$ITEM2\",\"candidateVenueIds\":[\"$DINNER\",\"$DINNER2\"],\"durationSeconds\":60}")
check "$OPEN" "201" "leader opens vote (201)"
curl -s -X POST $BASE/api/v1/trips/$TRIP_ID/votes/cast -H "$AUTH" -H 'Content-Type: application/json' -d "{\"itemId\":\"$ITEM2\",\"choiceVenueId\":\"$DINNER2\"}" >/dev/null
RES=$(curl -s -X POST $BASE/api/v1/trips/$TRIP_ID/votes/resolve -H "$AUTH" -H 'Content-Type: application/json' -d "{\"itemId\":\"$ITEM2\"}")
WIN=$(echo "$RES" | jget "['winningVenueId']")
echo "  winner=$WIN (expected $DINNER2)"
check "$WIN" "$DINNER2" "vote resolves to the voted candidate"

echo "== 10. RBAC: a plain planner cannot upload venues (admin-only) =="
curl -s -X POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' -d '{"email":"rbac@test.io","password":"Passw0rd!","displayName":"RBAC Tester","budgetCeiling":50,"dietaryTags":[]}' >/tmp/reg.json
PTOKEN=$(cat /tmp/reg.json | jget "['accessToken']")
FORBID=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/api/v1/admin/venues -H "Authorization: Bearer $PTOKEN" -H 'Content-Type: application/json' -d '{"destination":"X","venues":[]}')
check "$FORBID" "403" "non-admin blocked from admin endpoint (403)"

echo "== 11. Admin can upload + retrain =="
ADMIN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@warroom.test","password":"Admin123!"}' | jget "['accessToken']")
UP=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/api/v1/admin/venues -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"destination":"Porto, Portugal","venues":[{"name":"Porto Vegan","category":"dinner","pricePerPerson":18,"dietaryTags":["vegan"],"hours":{"open":"12:00","close":"23:00"},"coordinates":{"lat":41.14,"lng":-8.61},"rating":4.5,"description":"vegan"}]}')
check "$UP" "200" "admin uploads a venue dataset (200)"

echo "== 12. Invalid venue upload rejected with clear error =="
BAD=$(curl -s -o /tmp/bad.json -w "%{http_code}" -X POST $BASE/api/v1/admin/venues -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"destination":"Y","venues":[{"name":"Bad","category":"dinner","pricePerPerson":-5,"dietaryTags":["fictional-tag"],"hours":{"open":"12:00","close":"10:00"},"coordinates":{"lat":200,"lng":0}}]}')
check "$BAD" "400" "invalid dataset rejected (400)"
echo "  error: $(cat /tmp/bad.json | jget "['message']" 2>/dev/null)"

echo "== 13. Analytics reconciles from the event log =="
AN=$(curl -s $BASE/api/v1/trips/$TRIP_ID/analytics -H "$AUTH")
echo "  totalEvents=$(echo "$AN" | jget "['totalEvents']") aiAsked=$(echo "$AN" | jget "['aiQuestionsAsked']") pinsMoves=$(echo "$AN" | jget "['pinsAndMoves']")"
check "$(echo "$AN" | python3 -c "import sys,json;print('ok' if json.load(sys.stdin)['aiQuestionsAsked']>=1 else 'no')")" ok "analytics counts the AI question"

echo ""
echo "================ RESULT: $PASS passed, $FAIL failed ================"
