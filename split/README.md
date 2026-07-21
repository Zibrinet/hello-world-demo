# Split

A calm, two-person expense splitter for Android — a deliberately simpler
Splitwise for exactly one shared ledger (you and one other person). Fully
offline: no accounts, no network permission, nothing leaves the device.

## What it does

- **Fast manual entry** — big keypad, currency chip, paid-by toggle. A 50/50
  expense you paid for is: open sheet → type amount → Save.
- **Percent or exact splits** — the percent slider keeps both sides summing to
  100 and shows the live amount each person owes; exact mode auto-balances the
  other share. Rounding is deterministic and never loses a minor unit.
- **Receipt / invoice / email-screenshot scanning** — ML Kit Text Recognition
  v2 on-device, plus the ML Kit Document Scanner for camera capture (with
  automatic edge detection; falls back to the photo picker on devices without
  Google Play services). Multiple photos can be attached at once (multi-page
  scans too); parsed fields merge earliest-image-first. Dates handle Thai
  Buddhist Era years (2569 → 2026) and US month-first ordering. Parsed values
  only ever *pre-fill* an editable review sheet — nothing is auto-saved.
- **Per-currency balances** — one clear "who owes whom" line per currency.
  No invented exchange rates, ever.
- **Settle up** — records a payment (full or partial) against the balance.
- **Soft deletes with undo**, dark mode, haptics, spring animations.

## Build & run

```bash
cd split
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests (split math, balances, OCR parser)
```

Open the `split/` folder in Android Studio (Otter 3 / 2025.2.3 or newer for
AGP 9 support). CI builds the debug APK on every push — grab it from the
workflow run's `split-debug-apk` artifact.

- **minSdk 26** (Android 8.0) · targetSdk 36 · compileSdk 37
- Kotlin 2.4.10 (AGP 9.3 built-in Kotlin), Compose BOM 2026.06.01, Room 2.8.4
- No permissions requested: Photo Picker and the document scanner are
  permission-free, receipts live in app-private storage.

## Known limitations — read this before trusting a scan

**OCR extraction is heuristic and requires your review.** ML Kit returns raw
recognized *text*, not structured `{amount, date, merchant}` fields. Split
parses that text with keyword and pattern heuristics (EN / IT / TH total
keywords, both `1,234.56` and `1.234,56` number styles, common date formats).
That works well on clean printed receipts, less well on crumpled ones, and
**email screenshots are the weakest case** — layouts vary arbitrarily. This is
why every scan opens the editable expense sheet marked "review these values"
instead of saving anything directly. Treat the scan as an assist, not magic.

Other deliberate scope limits: exactly two participants and one ledger; no
backend, accounts, or sync (yet — see below); no cross-currency netting
(that would require a real FX source).

## Architecture (local-first, sync-ready)

MVVM + repository. ViewModels talk only to the `SplitRepository` interface;
Room sits behind it. Bolting on a backend later should not require a schema
or data-layer rewrite:

- **String UUID primary keys** on every entity, so records from two devices
  can merge.
- Every entity carries `createdAt` / `updatedAt`, **soft delete**
  (`isDeleted`, `deletedAt` — user data is never hard-deleted), and a
  `syncStatus` field (`LOCAL` today; `PENDING`/`SYNCED` reserved).
- **Split, rounding, and balance math are pure Kotlin functions**
  (`domain/`) with zero storage/UI dependencies, covered by unit tests.
- Money is stored as **integer minor units** (satang/cents) — never floats.

Notable implementation choices:

- **Document Scanner over a custom CameraX screen** — it was stable
  (`play-services-mlkit-document-scanner 16.0.0`), gives edge detection and
  cleanup for free, and removes the camera permission entirely. The photo
  picker covers gallery images and devices without Play services.
- **Navigation Compose 2.9** (not Navigation 3) — a five-screen app doesn't
  need the new paradigm, and Nav2 is stable and boring.
- **Manual DI** (`AppContainer`) — one database, one repository; Hilt would
  be ceremony.
- If OCR parsing proves too flaky in practice, an AI-vision extraction path
  can be swapped in behind the same `ReceiptAnalyzer` seam.

## Settings

Onboarding seeds the two participant names; Settings edits them and the home
currency (default **THB**, since that's the household base — one tap to
change).
