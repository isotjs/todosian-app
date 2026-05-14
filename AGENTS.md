# Working with Todosian as an AI agent

Todosian is an Android todo app that reads and writes Markdown files directly from your Obsidian vault (or any folder you sync to your phone). It follows Material 3 design guidelines and operates with zero network permissions.

## Project Structure

The project is a standard Android application with the following structure:

```
Todosian/
├── app/                         # Main application module
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/isotjs/todosian/
│   │   │   ├── data/
│   │   │   │   ├── model/        # Data models (Category, Todo, TasksPriority)
│   │   │   │   ├── settings/     # AppSettings + repository
│   │   │   │   ├── FileRepository.kt
│   │   │   │   └── PreferencesManager.kt
│   │   │   ├── notifications/   # Due reminders (WorkManager)
│   │   │   ├── ui/
│   │   │   │   ├── category/
│   │   │   │   ├── components/
│   │   │   │   ├── home/
│   │   │   │   ├── onboarding/
│   │   │   │   ├── settings/
│   │   │   │   └── theme/
│   │   │   └── utils/            # MarkdownParser
│   │   └── res/                  # Android resources
│   └── build.gradle.kts
├── fastlane/                    # F-Droid metadata (descriptions, changelogs, screenshots)
├── gradle/                      # Gradle wrapper and version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## Core Architecture Principles

1. **Files are the source of truth**: Todosian reads and writes Markdown files directly. Never introduce any form of database that acts as the primary data store.
2. **Privacy-first**: No network permissions, no analytics, no telemetry. The app only accesses the folder selected by the user via Storage Access Framework (SAF).
3. **Markdown integrity**: Preserve all non-todo lines exactly as they appear. Only modify the specific todo lines that the user explicitly changes.
4. **Obsidian compatibility**: Support (but don't require) Obsidian Tasks plugin metadata format.

Note: the app works with any folder you pick via SAF. Many users point it at an Obsidian vault or a synced subfolder, but Obsidian itself is not required.

## Rules for working on the project

1. Always create a new branch for feature work.
2. Branch naming conventions: `fix/...`, `feat/...`, `ref/...`, `docs/...`, `chore/...`.
3. Keep branch descriptions concise and specific.
4. Keep your branch up to date with `main` to minimize conflicts.
5. Commit message format: `type(scope): short description` (scope optional).
6. All user-facing strings go in `app/src/main/res/values/strings.xml`.
7. Do not edit non-English translations unless you are fluent in that language.
8. Follow Kotlin + Android best practices.

## AI-only guidelines

1. You are NOT allowed to use the following commands:
    - You are not to commit, push, or merge any changes to any branch.
    - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
2. Always follow the guidelines and instructions provided by human contributors.
3. Ensure the absolutely highest code quality in all contributions, including proper formatting, clear variable naming, and comprehensive comments where necessary.
4. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
5. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
6. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
7. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
8. You are absolutely **not allowed to bump the version** of the app in ANY way. Version bumps are only done by the core development team after manual review.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following commands from the root directory of the project:

```bash
./gradlew :app:assembleDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.

3. Run unit tests (especially important for Markdown parsing changes):

```bash
./gradlew :app:testDebugUnitTest
```

4. Run linter to ensure code quality:

```bash
./gradlew :app:lint
```

5. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/debug/` and ask a human for help testing the specific features you worked on.

## Markdown Parsing & File Operations

### Critical Rules

1. **Preserve non-todo content**: When modifying a Markdown file, preserve all lines that are not todo items exactly as they appear (including whitespace, comments, headings, etc.).
2. **Line-by-line operations**: Only modify the specific todo lines that changed. Do not rewrite entire files.
3. **Encoding**: Always use UTF-8 encoding when reading/writing files.
4. **Atomic writes**: Use atomic file operations (write to temp file, then rename) to prevent data loss on crashes.

Current implementation note:
The current SAF-based writer writes the full set of lines back via an output stream (even if only one todo line changed). SAF providers do not always support true atomic rename semantics, but the goal remains to avoid partial writes and corruption.

### Testing Markdown Logic

When modifying Markdown parsing or writing logic:

1. Add unit tests in `app/src/test/java/`
2. Test edge cases:
    - Empty files
    - Files with only non-todo content
    - Mixed todo and non-todo lines
    - Unicode characters and emojis
    - Malformed task metadata
    - Very long lines
3. Verify that non-todo content is preserved character-by-character

### Obsidian Tasks Plugin Format

When "Tasks plugin support" is enabled, the app should recognize these suffix tokens:

- Dates: `➕ YYYY-MM-DD` (created), `🛫 YYYY-MM-DD` (start), `⏳ YYYY-MM-DD` (scheduled), `📅 YYYY-MM-DD` (due), `✅ YYYY-MM-DD` (done)
- Priority: `🔺` (highest) / `⏫` (high) / `🔼` (medium) / `🔽` (low) / `⏬` (lowest)
- Recurrence: `🔁 <text>` (e.g., `🔁 every week`)

When disabled, the app should ignore (but preserve) these tokens.

## Storage Access Framework (SAF) Testing

When modifying file access logic:

1. Test with different folder locations:
    - Internal storage
    - SD card
    - Cloud storage providers (Google Drive, Dropbox mounted via SAF)
2. Test permission persistence across app restarts
3. Test graceful handling of:
    - Folder deleted externally
    - Permissions revoked
    - Files modified by external apps (Obsidian, text editors)
4. Test concurrent access scenarios (e.g., Syncthing syncing while app is open)

## Categories, File Selection, and Naming

1. Only `.md` files are treated as categories.
2. Files whose name contains `sync-conflict` are ignored.
3. Category creation/rename should avoid invalid filename characters and avoid `sync-conflict` in the name.

## F-Droid Publishing

When preparing for a release:

1. **Do NOT bump version numbers** - this is done by core maintainers only
2. Update changelogs in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Ensure no proprietary dependencies are introduced
4. Verify that no network permissions are added (check `AndroidManifest.xml`)
5. Test that the app builds reproducibly:
    ```bash
    ./gradlew clean
    ./gradlew :app:assembleRelease
    ```
6. Metadata updates (screenshots, descriptions) go to `fastlane/metadata/android/`

## Notifications & Due Reminders

1. The app uses WorkManager to periodically scan the selected folder for due-soon tasks.
2. Reminders are tied to Tasks plugin support (enabled in Settings).
3. Android 13+ requires `POST_NOTIFICATIONS` to show reminders.
4. No network permission is used.

## Resource Management

### String Resources

All user-facing strings must be in `app/src/main/res/values/strings.xml`:

```xml
<string name="example_key">Example text</string>
```

Access in code:
```kotlin
// In Compose
Text(text = stringResource(R.string.example_key))

// In non-Compose code
context.getString(R.string.example_key)
```

### Resource Access in Compose

When accessing resources in Jetpack Compose, **do not** use `LocalContext.current` to query resource values (e.g., `context.getString()`, `context.getColor()`, `context.resources.getDrawable()`). Changes to the Configuration object will not invalidate `LocalContext.current` reads, leading to stale values when the system configuration changes (e.g., locale, theme, orientation).

**Always use Compose resource APIs instead:**
- `stringResource()` for strings
- `colorResource()` for colors
- `painterResource()` for drawables/images

If you need direct `Resources` access, use `LocalResources.current` instead of `LocalContext.current.resources`.

If you need to resolve strings outside of a composable (for example, inside a callback), prefer passing resource IDs through events and resolving them in the UI layer, or ensure the lookup is configuration-safe.

This prevents the lint error:
```
Issue id: LocalContextGetResourceValueCall
```

## Settings & Preferences

If you need to add a new preference:

1. Define it in the appropriate data class (likely in `data/settings/`)
2. Add UI in the settings screen
3. Ensure it persists correctly
4. Consider migration logic if changing existing preference structure

Example preference pattern:
```kotlin
// Data class
data class AppSettings(
    val enableTasksPluginSupport: Boolean = false,
    val tasksPluginUseEmojisInUi: Boolean = false,
)

// In settings screen
Switch(
    checked = settings.enableTasksPluginSupport,
    onCheckedChange = { newValue ->
        // Update settings
    }
)
```

Current implementation note:
Settings are stored in SharedPreferences (`SharedPrefsAppSettingsRepository`). Existing settings include theme mode, dynamic color, daily focus banner, category sort, todo grouping/sort, Tasks plugin support, and an optional “use emojis in UI” flag for Tasks metadata.

## Common Pitfalls

1. **Do not load entire files into memory**: For large Markdown files, use streaming or line-by-line processing.

Current implementation note:
The current SAF reader uses `readLines()` and loads the whole document into memory. Keep an eye on very large Markdown files when changing parsing or I/O behavior.
2. **Do not assume file structure**: Users may have arbitrary folder structures, nested folders, and file naming conventions.
3. **Do not cache file contents**: Always re-read files when displaying to the user (they may have been modified externally).
4. **Do not block the UI thread**: File I/O operations must be done on background threads (coroutines).
5. **Do not introduce dependencies** that require network permissions, analytics, crash reporting, or closed-source libraries.

## Testing Checklist

Before submitting your work for review:

- [ ] Code builds successfully (`./gradlew :app:assembleDebug`)
- [ ] Unit tests pass (`./gradlew :app:testDebugUnitTest`)
- [ ] Linter passes with no errors (`./gradlew :app:lint`)
- [ ] Tested on emulator or physical device
- [ ] Verified that non-todo content in Markdown files is preserved
- [ ] Verified that the app works without network permissions
- [ ] Verified that the change doesn't break Obsidian compatibility
- [ ] Added/updated unit tests for any Markdown parsing changes
- [ ] Updated strings.xml if any user-facing text was added
- [ ] Followed Kotlin code style and Android best practices

## Build Notes (Current)

1. Module: `:app`
2. `minSdk`: 30
3. `targetSdk`: 36
4. Compose UI (Material 3)
5. Java source/target compatibility is set in Gradle (see `app/build.gradle.kts`).

## Questions?

If you have any questions or need clarification, ask a human contributor before proceeding. It's better to ask than to make incorrect assumptions.