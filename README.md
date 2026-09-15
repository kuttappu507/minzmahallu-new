# Minz Mahallu Management System — Android

Native **Kotlin + custom Jetpack Compose** port of the [Electron desktop app](https://github.com/kuttappu507/minzmahallu-electron).

Same modules, same SQLite schema/migrations, same PBKDF2-SHA256 auth, same business wiring — rebuilt for Android with a **custom fluid UI (no Material Design components)**.

## Modules (parity with desktop)

| Section | Screens |
|---------|---------|
| Core | Splash, Login / Initial Setup, Dashboard |
| Management | Families, Members, Staff, Committee, Subscriptions, Donations, WhatsApp |
| Finance | Accounting, Assets |
| Registers | Marriage, Death, Welfare, Certificates, Tokens |
| System | Reports, Settings, Users, Audit Log, Backup & Restore |

## Architecture

```
UI (custom Compose)  →  MmsRepository (window.mms.* facade)
                     →  AuthService (PBKDF2-SHA256, lockout, setup)
                     →  DatabaseManager (schema.sql + migrations + seed)
                     →  SQLite (mms.db)
```

- **Design tokens** mirror Electron `globals.css` (teal accent, light/dark, module tints).
- **i18n** English + Malayalam from the desktop string tables.
- **Auth** identical hash format `pbkdf2_sha256$200000$salt$hash`, 5-strike lockout, first-run admin setup.
- **SQL** assets copied from Electron `resources/sql/` (schema, seed, V002–V036 migrations).

## Build

### Local

```bash
# JDK 17 + Android SDK required
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### CI

Push to GitHub — workflow **Build Android APK** (runs on `main`, `arena/**` and PRs) produces debug + release APK artifacts. Main-branch pushes additionally publish a GitHub release with both APKs.

## App capabilities

- **Receipts everywhere** — subscriptions & donations open a receipt sheet with PDF export, text share and one-tap WhatsApp delivery to the family's number.
- **Reports that work** — 10 live reports (directory, rolls, defaulters, donations, P&L, collections, registers, welfare, certificates) with date ranges, on-screen tables, PDF export and share.
- **Certificates** — issue all 7 types, QR verification code on every certificate, in-app verifier, printable certificate PDFs, revoke + reprint tracking.
- **WhatsApp Connect** — message families (greeting / dues reminder / meeting templates) and send receipts through the WhatsApp app; nothing is ever sent automatically.
- **Money controls** — transaction void with admin password + reason, donation edit with admin approval, salary payments with history, welfare approve → disburse with committee-minutes enforcement.
- **People** — family/member detail sheets, member family-tree links, archive/restore, staff & committee terms, full user management (roles, lock, reset).
- **Shell** — global search across families/members/receipts, alerts bell for dues & welfare, offline Malayalam/English UI with dark mode.

## First launch

1. Splash loads schema into app-private `mms.db`.
2. **Initial Setup** creates the Administrator (no default password).
3. Sign in and use all modules offline.

## Tech

| Layer | Choice |
|-------|--------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose (custom, no Material) |
| DB | Android SQLite + asset migrations |
| Min SDK | 26 |
| Target SDK | 35 |
| Package | `com.mms.minzmahallu` |

## License

MIT
