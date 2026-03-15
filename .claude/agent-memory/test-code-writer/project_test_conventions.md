---
name: project_test_conventions
description: Confirmed test framework setup, directory conventions, and patterns for the Aideo project
type: project
---

## Test Framework — :features:core

- JUnit 5 (Jupiter) via `useJUnitPlatform()` in `testOptions.unitTests.all`
- Kotest assertions: `io.kotest.matchers.*` — use `shouldBe`, `shouldHaveSize`, `shouldBeExactly`, etc.
- `kotlinx.coroutines.test.runTest` for all suspend function tests
- Dependencies already declared: `coroutines.test`, `bundles.testing` (junit4 + jupiter-api + mockk), `bundles.kotest`, `junit.jupiter.engine` (runtimeOnly)

## Test Directory Convention

- Unit tests go in `src/test/kotlin/<package>/` (must be created manually if the module has no existing tests)
- Mirror production package exactly: e.g., `jinproject.aideo.core.media.audio`
- No instrumented tests (`src/androidTest/`) needed for pure Kotlin logic

## Internal Buffer Reuse Warning

`ChunkedAudioProcessor.onChunkReady` passes its internal `buffer` directly on the "remainder-to-window" path. Always call `it.copyOf()` inside the test callback collection lambda to capture a snapshot, or the array contents will be overwritten by subsequent feeds.

## Confirmed Pattern: Suspend Callback Testing

Collect callback results before asserting:
```kotlin
val chunks = mutableListOf<FloatArray>()
val processor = ChunkedAudioProcessor { chunks.add(it.copyOf()) }
runTest { processor.feed(...) }
chunks shouldHaveSize n
```

## `shouldBeExactly` for Float Precision

Use `io.kotest.matchers.floats.shouldBeExactly` (not `shouldBe`) when asserting exact `Float` values to avoid Kotest's default tolerance-based comparison for `Double`.
