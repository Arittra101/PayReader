# Phase 5 — Device Setup & Acceptance Runbook

**For:** the person setting up the one dedicated phone that runs this app.
**Goal:** get the app catching bKash SMS reliably, then prove it works before going live.

Do the steps **in order**. Most of Part A can only be done by hand on the phone — the app cannot
do them itself (`requirment.md §3.3`). Part A is what stops the phone from silently killing the app
after a few days; skipping any step is the usual reason payments go missing.

---

## Before you start

- [ ] The signed APK (`app-release.apk` or `app-debug.apk`).
- [ ] The Supabase table and insert-only RLS policy already created (see `requirment.md §7.1–§7.2`).
- [ ] `SUPABASE_URL` and `SUPABASE_ANON_KEY` set in `local.properties` before the APK was built.
- [ ] A SIM that receives the real bKash SMS, or the ability to send a test bKash-format SMS.

---

## Part A — One-time device setup (by hand, in order)

### 1. Install and open the app once
Sideload the APK, then **open the app at least once**. An app that has never been opened is in
Android's "stopped state" and receives **no** SMS broadcasts at all.

- [ ] APK installed
- [ ] App opened once

### 2. Grant the permission dialogs
On first launch the app asks for these. Tap **Allow** on each:

- [ ] Receive SMS
- [ ] Read SMS
- [ ] Notifications (Android 13+)

If you dismissed a dialog by accident, grant it from **Android Settings → Apps → (this app) →
Permissions**.

### 3. Turn battery optimization OFF for this app
Stops Doze from delaying uploads.

- Android Settings → Apps → (this app) → Battery → **Unrestricted** / "Don't optimise".

- [ ] Battery optimization disabled

### 4. Turn Autostart ON — **OEM phones only**
Only needed on **Xiaomi / Redmi / POCO, Oppo, Vivo, Realme, Infinix**. These brands force-stop apps
they think are unused, and a force-stopped app receives **no** SMS broadcasts. This switch is in the
brand's **own Security / Phone Manager app**, not in Android Settings.

- Xiaomi/Redmi/POCO: Security → Permissions → Autostart → enable this app.
- Oppo/Realme: Phone Manager / Settings → App management → Auto-launch.
- Vivo: iManager → App manager → Autostart.
- Infinix: Phone Master → App freezer / Autostart.

- [ ] Autostart enabled (or: not an OEM phone that needs it)

### 5. Remove the screen lock
Set screen lock to **None** (no PIN, no pattern, no fingerprint). After a reboot, a locked phone
does not deliver SMS broadcasts until someone unlocks it — any SMS arriving in that window is lost
and is never replayed.

- Android Settings → Security → Screen lock → **None**.

- [ ] Screen lock removed

### 6. Turn on the keep-alive service in the app
Open the app. In the status bar at the top, turn the **keep-alive switch ON**. Leave it on. A silent
"Listening for bKash SMS" notification will appear — that is expected and required.

- [ ] Keep-alive switch ON

### 7. Confirm the status dot is green
Tap the status bar to open the **Device setup** sheet. All five checks must show a green tick:

- [ ] Receive SMS permission
- [ ] Read SMS permission
- [ ] Notifications permission
- [ ] Battery optimization disabled
- [ ] Keep-alive service running

When all five pass, the dot at the top of the screen turns **green**. That is your one-glance "ready"
signal. If the dot is red, open the sheet and fix whichever item is not ticked.

---

## Part B — Acceptance tests (`requirment.md §12`)

Run these on the real device before going live. **Test 8 is the real proof of Part A** — do not skip
it.

| # | Do this | Expect |
|---|---|---|
| 1 | Send a real bKash receive SMS while the app is **closed** | Row appears as **SUCCESS** within a few seconds |
| 2 | Turn on airplane mode, send an SMS, wait, turn it off | Row goes **PENDING → SUCCESS** on its own |
| 3 | Send the same SMS text **twice** | **One** row locally and **one** row in Supabase |
| 4 | Force-stop the app, send an SMS, then open the app | The catch-up scan (or pull-to-refresh) finds the missed SMS |
| 5 | Reboot the phone (no screen lock), send an SMS **without** opening the app | Row appears normally |
| 6 | Send a bKash SMS in an unknown/garbled format | Row saved as **PARSING_ERROR**, app does not crash |
| 7 | Send an SMS from a **non-bKash** sender containing `TrxID` | Ignored completely — no row |
| 8 | Leave the device **idle for 48 hours**, then send an SMS | Row still appears. If not → re-check Autostart (step 4) |
| 9 | Test the anon key against RLS (see below) | **Insert works, select returns nothing** |
| 10 | Send an SMS while the app is open on the **Success** tab | Row appears in **All** as PENDING, then moves into Success by itself, no refresh |
| 11 | Swipe between the three tabs | Tab indicator follows the swipe; each tab keeps its scroll position |
| 12 | Set the date filter to **Today** in one tab, switch tabs | Filter stays applied in the other tabs |

### Test 9 — RLS check (run from any machine with `curl`)

Insert should succeed:

```bash
curl -i -X POST 'https://YOURPROJECT.supabase.co/rest/v1/transactions' \
  -H "apikey: YOUR_ANON_KEY" \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -H "Content-Type: application/json" \
  -H "Prefer: return=minimal" \
  -d '{"trx_id":"RLS_TEST_1","raw_message":"rls test","received_at":"2026-09-14T12:00:00+06:00"}'
# Expect: 201 Created
```

Select should return nothing (the anon role has no read access):

```bash
curl -s 'https://YOURPROJECT.supabase.co/rest/v1/transactions?select=*' \
  -H "apikey: YOUR_ANON_KEY" \
  -H "Authorization: Bearer YOUR_ANON_KEY"
# Expect: []   (empty — RLS blocks reads)
```

Then delete the `RLS_TEST_1` row from the Supabase dashboard so it doesn't pollute real data.

---

## Part C — Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No SMS arriving after a day or two | OEM killed the app | Re-check **Autostart** (A4) and **battery optimization** (A3); confirm keep-alive is ON |
| Nothing arrives after a reboot | Phone sat on a locked screen | Confirm **screen lock is None** (A5) |
| App never catches anything, even fresh | App never opened, or in stopped state | Open the app once (A1); send a test SMS |
| Rows stuck on **PENDING** | No network, or Supabase URL/key wrong | Check connectivity; verify `SUPABASE_URL`/`SUPABASE_ANON_KEY` in the build |
| Rows stuck on **FAILED** | RLS policy missing or key rejected | Confirm the `anon` insert policy exists (§7.2); re-run Test 9 |
| A row shows **PARSING_ERROR** (grey) | bKash SMS in a format the parser doesn't know | Handle that payment by hand; keep the raw text so the parser can be extended later (§8.2) |
| Status dot is **red** | A setup item is off | Tap the status bar and fix whatever isn't ticked |

---

## Notes

- **The app does not auto-start after a reboot.** The SMS receiver keeps working on its own (it's
  declared in the manifest), but the **keep-alive service must be switched back on by hand** after a
  reboot (`§9.1`). Add "re-open app, turn keep-alive on" to your reboot procedure.
- **Never delete rows** — this is a payment record (`§10.7`, `§11`).
- An SMS is **not** proof of payment on its own (`§8.1`). This app captures; a human or the backend
  confirms.
