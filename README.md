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

Push to GitHub — workflow **Build Android APK** produces debug + release APK artifacts.

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
