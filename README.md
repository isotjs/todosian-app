# Todosian

Todosian is an Android app for managing todo lists stored as Markdown files inside your Obsidian vault.

It is designed to work with a folder that is synced to your phone (for example via Syncthing). The app reads and writes the Markdown files directly; it does not import your data into a database.

## AI Usage & Disclaimer

This application was heavily co-developed with an AI agent to test their ability to code an Native Kotlin app from scratch(as they did). While the codebase has been strictly audited for performance, privacy, and security standards, it is provided "as is" without warranties of any kind. 

**Disclaimer:** Todosian modifies your Markdown files directly. The repository owner assumes no liability for any unintended data loss or file corruption. Please ensure you have a reliable backup of your Obsidian vault (e.g., via Git, Syncthing file versioning, or Obsidian Sync) before using this application.

**Trademark Disclaimer:** Todosian is an independent, open-source application and is not affiliated with, endorsed by, or sponsored by Obsidian or the developers of the Obsidian Tasks plugin.

## Features

- Folder-based setup using Android Storage Access Framework (SAF) with persisted permissions.
- Each Markdown file becomes a category.
- Add / edit / toggle / delete todos.
- Create / rename / delete categories (files).
- (Optional) Support for Obsidian Tasks plugin metadata (dates, priority, recurrence).
- Material 3 UI, dynamic color (Android 12+).

## Supported Markdown

Todos are parsed from lines that match:

```text
- [ ] Do something
- [x] Done task
```

When “Tasks plugin support” is enabled, Todosian also parses (and can write) common suffix metadata:

```text
- [ ] Write README 📅 2026-02-22 🔼 🔁 every week
- [x] Ship build ✅ 2026-02-20
```

Recognized suffix tokens:

- Dates: `➕ YYYY-MM-DD` (created), `🛫 YYYY-MM-DD` (start), `⏳ YYYY-MM-DD` (scheduled), `📅 YYYY-MM-DD` (due), `✅ YYYY-MM-DD` (done)
- Priority: `🔺` / `⏫` / `🔼` / `🔽` / `⏬`
- Recurrence: `🔁 <text>`

Non-todo lines are preserved. The app only edits the specific todo lines you change.

## Usage

1. Sync (or copy) your Obsidian todo folder to your Android device (Syncthing works well).
2. Open Todosian and select that folder when prompted.
3. Tap a category (a `.md` file) to manage its todos.

## Build

Requirements:

- Android Studio (or Gradle)
- JDK 17+ recommended (Android Gradle Plugin)

Commands:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lint
```

## Release Signing (optional)

`app/build.gradle.kts` supports release signing via either environment variables or a local `keystore.properties` file:

- `TODOSIAN_RELEASE_STORE_FILE`
- `TODOSIAN_RELEASE_STORE_PASSWORD`
- `TODOSIAN_RELEASE_KEY_ALIAS`
- `TODOSIAN_RELEASE_KEY_PASSWORD`

Do not commit your keystore or signing properties.

## Privacy

Todosian only accesses the folder you pick. There is no account, no analytics, and no network sync built into the app.

## Contributing

Issues and pull requests are welcome.

- Keep changes aligned with the “files are the source of truth” principle.
- Prefer small, testable logic (e.g. Markdown parsing) with unit tests.

## License

Licensed under the Apache License 2.0. See `LICENSE`.

Copyright 2026 İsmail TİRYAKİ (https://github.com/isotjs)
