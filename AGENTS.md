# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Android app built with Gradle Kotlin DSL. The main app lives in `app/`, shared data code in `data/`, reusable Compose UI in `design/`, and feature code under `features/` such as `gallery`, `player`, `library`, `setting`, and `core`. AI asset-pack modules (`ai_speech_*`, `ai_translation`) and device-targeted dynamic feature modules (`htp_*`) are included from the repo root. Source files follow standard Android layout: `src/main/kotlin`, `src/main/res`, `src/test`, and `src/androidTest`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root.

- `./gradlew assembleDebug`: build the debug app bundle with local/test config.
- `./gradlew :app:bundleRelease`: create the release AAB.
- `./gradlew testDebugUnitTest`: run JVM unit tests across debug variants.
- `./gradlew :features:core:testDebugUnitTest`: run a single module’s unit tests.
- `./gradlew connectedDebugAndroidTest`: run device/emulator instrumentation tests.
- `./scripts/setup-git-hooks.sh`: install local git hooks if you use the repo’s hook workflow.

## Coding Style & Naming Conventions
Follow existing Kotlin conventions: 4-space indentation, braces on the same line, and descriptive camelCase names. Types, composables, and objects use `PascalCase`; functions and properties use `camelCase`; constants use `UPPER_SNAKE_CASE`. Keep package names under `jinproject.aideo.*`. Prefer small, focused modules and keep UI code in `design/` or feature modules rather than `app/`. No dedicated formatter config is checked in, so match the surrounding style before submitting.

## Testing Guidelines
Unit tests use JUnit 5 in core modules, with Kotest and MockK available; some Android-facing modules also keep JUnit 4 and Espresso for instrumentation. Name tests `*Test.kt` and place them beside the owning module, for example `features/core/src/test/.../ChunkedAudioProcessorTest.kt`. Add or update tests for pipeline logic, translators, and media processing when behavior changes.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit prefixes such as `fix:`, `refactor:`, and `docs:` with concise summaries. Keep commits scoped to one change. Pull requests should explain the user-visible impact, list touched modules, link related issues, and include screenshots for Compose/UI updates. If a release or README badge changes, note that `.github/workflows/update_docs_workflows.yml` may create follow-up docs automation.

## Security & Configuration Tips
Do not commit `local.properties`, keystores, service account JSON, generated asset packs, or build outputs. Release signing, AdMob IDs, and Play publishing credentials are read from local properties, so verify local keys before running release tasks.
