# Requirements Document: bKash SMS Transaction Extractor & Supabase Sync

**Version:** 2.0
**Target:** One dedicated Android device. Not for Play Store. Sideload only.

---

## 1. Executive Summary

An Android app that listens for incoming bKash SMS on one dedicated device. When a "money received" SMS arrives, the app reads it, pulls out the payment details, saves them locally, and sends them to a Supabase backend.

The app is used for payment verification. One device, one operator, no multi-user support.

### 1.1 Design Rules (read this first)

These rules decide almost every choice in this document:

1. **Local first, network second.** Every parsed SMS is saved to the local database *before* any API call. The database is the source of truth. The upload can fail and retry forever; the data is never lost.
2. **An SMS is not proof of payment on its own.** See §8.1.
3. **`trx_id` is unique everywhere.** Local table and Supabase table. Re-processing the same SMS must never create a second row.
4. **Nothing depends on the app being open.** The SMS receiver works even when the app is closed.
5. **Missed messages are recoverable.** If the app was killed and missed an SMS, an inbox scan finds it later. See §6.

---

## 2. Technical Stack & Architecture

* **Platform:** Android (minSdk 26 / Android 8.0, targetSdk 35+)
* **Language:** Kotlin
* **Architecture:** Clean Architecture + MVVM, Jetpack Compose UI
* **SMS capture:** manifest-declared `BroadcastReceiver`
* **Background upload:** WorkManager (expedited, with retry)
* **Local database:** Room
* **Backend:** Supabase, called through Ktor client
* **Keep-alive:** foreground service, `specialUse` type, manual toggle

---

## 3. Permissions & Device Setup

### 3.1 Manifest Permissions

| Permission | Why |
| --- | --- |
| `RECEIVE_SMS` | Catch incoming SMS in real time |
| `READ_SMS` | Read the SMS inbox for the catch-up scan (§6) |
| `INTERNET` | Call the Supabase API |
| `ACCESS_NETWORK_STATE` | Check connectivity before upload |
| `POST_NOTIFICATIONS` | Android 13+, needed to show the service notification |
| `FOREGROUND_SERVICE` | Run the keep-alive service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+, required for the `specialUse` type |

**Not used:** `RECEIVE_BOOT_COMPLETED`. The app does not auto-start after reboot. See §9.1 for why this is safe.

### 3.2 Runtime Permission Flow

1. On first launch, request `RECEIVE_SMS`, `READ_SMS`, and `POST_NOTIFICATIONS`.
2. Then show a setup screen with the manual device steps below. The app cannot do these itself — a human must do them once.

### 3.3 Manual Device Setup (one time, by hand)

This is the most important part of making the app reliable. Without these steps the phone will silently kill the app after a few days.

| Step | Where | Why |
| --- | --- | --- |
| Battery optimization → **Off** for this app | Android Settings, or `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` from the app | Stops Doze from delaying uploads |
| **Autostart → On** | Xiaomi / Oppo / Vivo / Realme / Infinix only. Their own security app, not Android Settings | These brands force-stop apps they think are unused. A force-stopped app receives **no** SMS broadcasts at all |
| **Remove the screen lock** (no PIN, no pattern) | Android Settings | After a reboot, a locked phone does not deliver broadcasts until someone unlocks it. Any SMS arriving in that window is lost and is never replayed |
| Open the app once after install | — | An app that has never been opened is in "stopped state" and receives no broadcasts |

The setup screen in the app must show each of these as a checklist so the operator can confirm them.

---

## 4. SMS Capture

### 4.1 Receiver (`SmsReceiver`)

Declare in the manifest (not in code):

```xml
<receiver android:name=".SmsReceiver" android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter android:priority="999">
        <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
</receiver>
```

**Note for implementation:** Android 8+ blocks most implicit broadcasts declared in the manifest, but `SMS_RECEIVED` is on the official exception list. A manifest receiver is correct here and works on Android 15 and 16. Do not replace it with a runtime-registered receiver.

### 4.2 Filtering Rules

Accept a message only when **all** of these are true:

1. Sender ID is exactly `bKash` (case-insensitive, exact match — not "contains").
2. Body contains a receive keyword: `received deposit`, `You have received`, or `received Tk`.
3. Body contains `TrxID`.

Reject everything else silently. Do not log rejected messages — only bKash messages that failed *parsing* get logged (§8.2).

The exact sender match matters: anyone can send an SMS with a fake body, so a loose filter widens the attack surface for no benefit.

### 4.3 What the receiver does

The receiver has about 10 seconds before Android may kill it. Keep it short:

```
onReceive():
  1. filter the message (§4.2)
  2. parse it (§5)
  3. insert into Room with status = PENDING   <- must finish here
  4. enqueue an expedited UploadWorker
  5. return
```

Do **not** make the network call inside `onReceive()`. Step 3 is the only step that must not fail.

---

## 5. Parsing

### 5.1 Example Message

```
You have received deposit from iBanking of Tk 500.00 from City Bank.
Fee Tk 0.00. Balance Tk 540.58. TrxID DI94C4NOI8 at 09/09/2026 22:08
```

### 5.2 Fields

| Field | Type | Rule | Example |
| --- | --- | --- | --- |
| `trx_id` | String | Alphanumeric text after `TrxID ` | `DI94C4NOI8` |
| `amount` | Double | The `Tk` value in the *received* part, before `Fee`. Never take the `Balance` or `Fee` value | `500.00` |
| `sender` | String | Source between `from` and `. Fee`. May be a bank name or a mobile number | `iBanking of City Bank` |
| `occurred_at` | ISO 8601 | Date and time at the end, `DD/MM/YYYY HH:mm`, read as Asia/Dhaka (UTC+6) | `2026-09-09T22:08:00+06:00` |
| `raw_message` | String | The full original SMS body, always stored | full text |
| `received_at` | ISO 8601 | Phone clock time when the app saw the SMS | — |

**Important — use `occurred_at`, not `received_at`, as the payment time.** If the phone was switched off, the operator holds the SMS and delivers it later. The body still carries the real payment time; the phone clock does not.

Store both. `received_at` is useful for spotting delivery delays.

### 5.3 Amount parsing warning

The message has three `Tk` values: received amount, `Fee Tk`, and `Balance Tk`. A lazy regex will grab the wrong one. Anchor the amount regex to the text **before** the word `Fee`.

---

## 6. Catch-Up Inbox Scan (required)

The receiver misses messages in three situations:

* the app was force-stopped (by a person, or by the OEM battery manager)
* the phone rebooted and sat on a locked screen
* the app was reinstalled or updated

Android never replays a missed `SMS_RECEIVED`. The only recovery is to read the inbox.

### 6.1 Behaviour

Run a scan:
* every time the app is opened
* every 15 minutes via periodic WorkManager

The scan:
1. Reads the SMS inbox with `READ_SMS`, filtered to sender `bKash`.
2. Takes messages newer than the newest `occurred_at` already stored, minus 24 hours of overlap for safety.
3. Runs them through the same filter and parser as §4 and §5.
4. Inserts them under the same unique `trx_id` rule, so anything already stored is skipped.

Re-reading old messages is harmless because of the unique constraint. Always prefer overlap over a gap.

---

## 7. Upload to Supabase

### 7.1 Table Shape

```json
{
  "trx_id": "DI94C4NOI8",
  "amount": 500.00,
  "sender": "iBanking of City Bank",
  "raw_message": "You have received deposit from iBanking...",
  "occurred_at": "2026-09-09T22:08:00+06:00",
  "received_at": "2026-09-09T22:08:03+06:00"
}
```

`trx_id` is the **primary key or a unique constraint**. Inserts use upsert / ignore-on-conflict so a repeat never creates a second row and never returns an error the app has to handle.

### 7.2 Security

* Use the **anon key**, never the `service_role` key. An APK can be unzipped and read by anyone who gets the phone.
* Turn on Row Level Security. The anon role gets **insert only** on this table — no select, no update, no delete.
* This limits the damage if the key leaks: someone could insert junk rows, but could not read your payment history or delete it.

### 7.3 Upload Worker

* `UploadWorker` picks up all rows with status `PENDING` or `FAILED`.
* Constraint: `NetworkType.CONNECTED`.
* Mark expedited: `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.
* Backoff: exponential, starting at 10 seconds.
* On success → status `SUCCESS`. On failure → status `FAILED`, then retry. Never delete a failed row.

### 7.4 Why this app needs expedited work

Android lowers an app's background priority when nobody opens it. This app will sit idle for long stretches between payments, so it will often be in a low-activity group. Normal WorkManager jobs can be delayed by many minutes there, which is too slow for payment confirmation. Expedited work avoids most of that delay.

---

## 8. Trust and Error Handling

### 8.1 An SMS is not proof of payment

Anyone can send an SMS with a fake sender name, a fake amount, and a fake TrxID. Nothing in the message can be verified by the phone.

So the backend must **not** treat a row in this table as a confirmed payment on its own. Pick one:

* mark the row as *pending* and let a human confirm it, or
* match it against an order that already exists (amount + time window), or
* use it as a fast signal, and confirm later against the real bKash merchant API.

This app is a **capture tool**, not an authorisation system. Write this into the backend design, not only here.

### 8.2 Parsing failures

If the message passed the filter (§4.2) but a required field could not be parsed:

* store the row with status `PARSING_ERROR` and the full `raw_message`
* never crash
* show it in the UI list in a different colour so the operator can see it and handle the payment by hand
* still upload it, so the pattern can be fixed later from real data

### 8.3 Duplicates

Handled by the unique `trx_id` at both layers. No other de-duplication logic is needed.

---

## 9. Foreground Service (keep-alive)

### 9.1 Behaviour

* A manual switch in the UI turns it on and off. **Recommended: leave it ON.**
* The service does nothing itself. Its only job is to keep the app counted as active, so Android and the OEM battery manager do not push it into a low group or force-stop it.
* SMS capture works with the service off. The service only makes upload timing more reliable.
* The service does **not** start automatically after a reboot. Someone must open the app and turn it on again. This is safe because the SMS receiver is in the manifest and keeps working after a reboot on its own — as long as the screen lock is removed (§3.3).

### 9.2 Service Type — use `specialUse`, not `dataSync`

```xml
<service
    android:name=".KeepAliveService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Listens for bKash payment SMS and syncs to backend" />
</service>
```

Reason: on Android 15+, a `dataSync` foreground service is limited to 6 hours in any 24-hour window. After that the system refuses to start it again until a person brings the app to the foreground. That limit would break an always-on service every day. `specialUse` has no such limit.

(`specialUse` needs a written justification when publishing on Play. Not relevant here — this app is not published.)

---

## 10. UI / UX (Jetpack Compose)

### 10.1 Screen Structure — one screen only

The whole app is **a single screen**. No navigation graph, no second destination. Anything extra (setup checklist, raw message) opens as a bottom sheet or dialog on top of the same screen.

Layout from top to bottom:

```
┌─────────────────────────────────────────┐
│  Status bar (collapsible)               │  ← §10.2
│  ● Listening    Last SMS: 2 min ago     │
│  [ Keep-alive service    (•—) ON  ]     │
├─────────────────────────────────────────┤
│   All  │  Success  │  Failed            │  ← TabRow, §10.3
│  ──────                                 │
├─────────────────────────────────────────┤
│                                         │
│   HorizontalPager                       │  ← §10.4
│   (LazyColumn of transaction rows)      │
│                                         │
│                                         │
└─────────────────────────────────────────┘
```

Use `TabRow` + `HorizontalPager` (Compose Foundation). Do not use the old `ViewPager` / `ViewPager2` view — they need XML and a fragment host, which does not fit a Compose-only single-Activity app.

Tabs and pager must stay in sync in both directions: tapping a tab animates the pager, and swiping the pager moves the tab indicator. Wire this with `pagerState.currentPage` and `scope.launch { pagerState.animateScrollToPage(index) }`.

### 10.2 Status Bar (top of screen)

A compact row, always visible:

* A coloured dot: green when the app is set up correctly, red when something is missing.
* `Last SMS: <relative time>` — if this is older than expected, the operator knows something broke before a payment goes missing.
* Keep-alive service switch (§9).

Tapping the status bar opens a **setup bottom sheet** with the full checklist:
* `RECEIVE_SMS` granted
* `READ_SMS` granted
* Notifications granted
* Battery optimization disabled
* Keep-alive service running
* Last successful upload (time)

The dot is green only when all five setup items pass.

### 10.3 The Three Tabs

| # | Tab | Which rows it shows | Empty text |
| --- | --- | --- | --- |
| 0 | **All** | Every row, whatever the status | "No transactions yet" |
| 1 | **Success** | `SUCCESS` only — the backend accepted it | "No confirmed payments yet" |
| 2 | **Failed** | `FAILED` and `PARSING_ERROR` | "No failed payments" |

Notes on this split:

* `PENDING` rows appear in **All only**. A pending row is not a failure — it is normally a few seconds old, and moving it into "Failed" would make the operator panic over nothing.
* `PARSING_ERROR` goes in **Failed** because it also needs a human. The SMS arrived but the app could not read it, so the payment must be checked by hand. See §8.2.
* Each tab shows a count badge in the tab label, e.g. `Failed (3)`. The Failed count is the number the operator watches.

### 10.4 Sorting and Date Filter

**Default sort: newest first** (recent to old), in every tab, by `occurred_at`. Never by `received_at` — a delayed SMS would jump to the top and look like a new payment.

A date filter sits above the list, shared by all three tabs (changing it in one tab changes it everywhere):

* Quick chips: `Today`, `7 days`, `30 days`, `All`
* `Custom` chip opens a date range picker
* Default on app open: **Today**

The sort direction can be flipped with a small icon button next to the filter (newest first ⇄ oldest first). Newest first is the default every time the app opens.

### 10.5 Transaction Row

A `LazyColumn` item inside each pager page. Each row shows:

```
┌──────────────────────────────────────────┐
│  ৳ 500.00                     ● SUCCESS  │
│  iBanking of City Bank                   │
│  DI94C4NOI8          09 Sep 2026, 10:08 PM│
└──────────────────────────────────────────┘
```

* Amount is the largest text — it is what the operator scans for.
* Status is a small coloured pill: green `SUCCESS`, amber `PENDING`, red `FAILED`, grey `PARSING_ERROR`.
* For a `PARSING_ERROR` row, the amount and sender are unknown, so show the first line of `raw_message` instead and mark it clearly.

### 10.6 Actions

| Action | Result |
| --- | --- |
| Pull to refresh (any tab) | Runs the catch-up inbox scan (§6) |
| Tap a row | Opens a bottom sheet with all fields and the full `raw_message`, plus a copy button for `trx_id` |
| Tap a `FAILED` row's retry button | Enqueues the upload again right away |
| Long press a row | Copies `trx_id` |

There is a `Retry all` button in the Failed tab header when the tab is not empty.

### 10.7 UX Rules

* **The list is live.** It reads from Room with a `Flow`, so a new SMS appears without a refresh, and a `PENDING` row turns into `SUCCESS` in place. The operator should never have to reopen the app to see current state.
* **Never block the screen with a loading spinner.** The data is local and instant. A spinner belongs only on the pull-to-refresh indicator.
* **No delete.** There is no way to remove a row from the UI. This is a payment record.
* Amounts use the Bangladeshi Taka sign `৳` and two decimal places.
* Times are shown in Asia/Dhaka, in a readable format (`09 Sep 2026, 10:08 PM`), not ISO.

---

## 11. What This App Does NOT Do

Stating these so nobody adds them by mistake:

* No auto-start after reboot.
* No multi-device, multi-user, or login.
* No second screen and no navigation library. One screen, three tabs (§10.1).
* No deleting rows from the UI.
* No reading of sent SMS or any non-bKash sender.
* No deleting or modifying SMS.
* No payment authorisation decisions (§8.1).
* No Play Store release.

---

## 12. Acceptance Tests

The build is done when all of these pass on the real device:

| # | Test | Expected |
| --- | --- | --- |
| 1 | Send a real bKash receive SMS while the app is closed | Row appears with `SUCCESS` within a few seconds |
| 2 | Turn on airplane mode, send SMS, wait, turn it off | Row goes `PENDING` → `SUCCESS` on its own |
| 3 | Send the same SMS text twice | Only one row locally and one row in Supabase |
| 4 | Force-stop the app, send SMS, then open the app | The catch-up scan finds the missed SMS |
| 5 | Reboot the phone (no screen lock), send SMS without opening the app | Row appears normally |
| 6 | Send a bKash SMS in an unknown format | Row saved as `PARSING_ERROR`, app does not crash |
| 7 | Send an SMS from a non-bKash sender containing `TrxID` | Ignored completely |
| 8 | Leave the device idle for 48 hours, then send an SMS | Row still appears. If not, check Autostart settings |
| 9 | Test the anon key against RLS | Insert works, select returns nothing |
| 10 | Send an SMS while the app is open on the Success tab | Row appears in All as `PENDING`, then moves into Success by itself, with no refresh |
| 11 | Swipe between the three tabs | Tab indicator follows the swipe; each tab keeps its own scroll position |
| 12 | Set the date filter to `Today` in one tab, then switch tabs | The filter stays applied in the other tabs |

Test 8 is the real test of the device setup in §3.3. Run it before going live.

---

## 13. Build Phases

**Phase 1 — Parser.** Write the parser and its unit tests first, with no Android around it. Collect real bKash SMS texts (P2P receive, bank to bKash, agent deposit, merchant payment) and test each one. A wrong parser is the only failure mode that produces silently wrong data instead of a visible error.

**Phase 2 — Capture and store.** Receiver, Room, UI list. No backend yet. Confirm every SMS lands locally.

**Phase 3 — Supabase.** Table, RLS insert-only policy, anon key, `UploadWorker`.

**Phase 4 — Reliability.** Catch-up scan, keep-alive service, setup checklist screen.

**Phase 5 — Device setup and the 48-hour test** (§12, test 8).