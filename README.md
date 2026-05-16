<h1 align="center">Todosian</h1>

<p align="center">
  <a href="https://github.com/isotjs/todosian-app/releases/latest"><img src="https://img.shields.io/github/v/release/isotjs/todosian-app?label=release&color=6A40F9" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-5030C0" alt="License"></a>
  <a href="https://github.com/isotjs/todosian-app/releases"><img src="https://img.shields.io/github/downloads/isotjs/todosian-app/total?color=8B5CF6" alt="Downloads"></a>
</p>

Todosian is an Android app for managing todo lists stored as Markdown files inside your Obsidian vault.

It is designed to work with a folder that is synced to your phone (for example via Syncthing). The app reads and writes the Markdown files directly; it does not import your data into a database.

<h2 align="center">Features</h2>

- Folder-based setup using Android Storage Access Framework (SAF) with persisted permissions.
- Each Markdown file becomes a category.
- Add / edit / toggle / delete todos.
- Create / rename / delete categories (files).
- (Optional) Support for Obsidian Tasks plugin metadata (dates, priority, recurrence).
- Material 3 UI, dynamic color (Android 12+).

<h2 align="center">Supported Markdown</h2>

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

<h2 align="center">Usage</h2>

1. Sync (or copy) your Obsidian todo folder to your Android device (Syncthing works well).
2. Open Todosian and select that folder when prompted.
3. Tap a category (a `.md` file) to manage its todos. 

<h2 align="center">Download Now</h2>

<div align="center">
<table>
  <tr>
    <th align="center">F-Droid</th>
    <th align="center">GitHub</th>
    <th align="center">OpenApk</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://f-droid.org/packages/com.isotjs.todosian">
        <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="75">
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/isotjs/todosian-app/releases/latest">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png?raw=true" alt="Download from GitHub" height="75">
      </a>
    </td>
    <td align="center">
      <a href="https://www.openapk.net/todosian-obsidian-markdown-todos/com.isotjs.todosian/">
        <img src="https://www.openapk.net/images/openapk-badge.png" alt="Download from OpenApk" height="75">
      </a>
    </td>
  </tr>
</table>
</div>

<h2 align="center">Privacy</h2>

Todosian only accesses the folder you pick. There is no account, no analytics, and no network sync built into the app.

<h2 align="center">Contributing</h2>

Issues and pull requests are welcome.

- Keep changes aligned with the “files are the source of truth” principle.
- Prefer small, testable logic (e.g. Markdown parsing) with unit tests.

<h2 align="center">AI Usage &amp; Disclaimer</h2>

This application was heavily co-developed with an AI agent to test their ability to code an Native Kotlin app from scratch(as they did). While the codebase has been strictly audited for performance, privacy, and security standards, it is provided "as is" without warranties of any kind. 

**Disclaimer:** Todosian modifies your Markdown files directly. The repository owner assumes no liability for any unintended data loss or file corruption. Please ensure you have a reliable backup of your Obsidian vault (e.g., via Git, Syncthing file versioning, or Obsidian Sync) before using this application.

**Trademark Disclaimer:** Todosian is an independent, open-source application and is not affiliated with, endorsed by, or sponsored by Obsidian or the developers of the Obsidian Tasks plugin.

<h2 align="center">License</h2>

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).