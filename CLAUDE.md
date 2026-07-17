# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A JUnit 5 extension (published via JitPack as `com.github.beigirad:junit-baseline-extension`) that snapshot-tests
test *failures*. Instead of asserting expected error messages inline, a test's thrown exception is recorded into a
JSON baseline file; subsequent runs compare the actual failure against that baseline and fail only if it drifts.

## Commands

```bash
./gradlew build                 # compile + test
./gradlew test                  # run tests (asserts against sample-baseline/*.json)
./gradlew test -Dbaseline.record=true   # re-record sample-baseline/*.json instead of asserting
./gradlew test --tests "ir.beigirad.junitbaselineextension.BaselineTest"
./gradlew test --tests "ir.beigirad.junitbaselineextension.BaselineTest.write should create baseline file with recorded failures"
```

There is no separate lint task configured.

## Architecture

Two source files implement the whole extension:

- `BaselineExtension.kt` — the JUnit 5 `Extension` (`TestExecutionExceptionHandler` +
  `Before/AfterAll`/`Before/AfterEach`). It wires JUnit lifecycle callbacks to a `Baseline` instance and decides the
  *identifier* each baseline file is keyed on.
- `Baseline.kt` — pure logic for recording failures, sanitizing messages, reading/writing the JSON file (via Moshi),
  and comparing actual vs. expected. Also defines `BaselineException`, whose `getStackTrace()` is overridden to
  return empty so the *baseline's* own stacktrace doesn't pollute test output — only the wrapped failures' traces are
  printed (via `message`).

Key behavior to preserve when touching this code:

- **Class-level vs. method-level application** matters a lot. `@ExtendWith(BaselineExtension::class)` on a class
  means *one* baseline file for the whole class (keyed by simple class name), aggregating every test method's
  failure. The same annotation on an individual `@Test` method means a baseline file per method, keyed by
  `ClassName-shortenedMethodName`. `BaselineExtension.isAppliedByClass()` / `isNestedTestClass()` are how the
  extension tells these cases apart in the `before*`/`after*` callbacks — don't assume `afterEach`/`afterAll` both
  fire the same logic.
- `shortenMethodName` derives the method-level filename: first 4 words of the (backtick) display name, lowercased,
  alphanumeric-only, joined by `.`, plus `.` plus `name.hashCode()`. This is why sample baseline filenames look like
  `baseline-SampleMethodLevelTest-first.test.-218984446.json`. Changing this format is a breaking change for anyone
  with existing baseline files.
- `handleTestExecutionException` **swallows** the throwable (records it, does not rethrow) — that's what lets a
  "failing" test pass when its failure matches the baseline. The real pass/fail decision happens later in
  `Baseline.compare`, which throws `BaselineException` on mismatch. Exception: when no baseline file exists yet for
  that identifier and `baseline.record` isn't `true`, the extension is a no-op — `handleTestExecutionException`
  rethrows instead of swallowing, and `afterEach`/`afterAll` skip `assertOrWrite` — so a first run without recording
  behaves as if `BaselineExtension` weren't applied at all (real failures surface normally) rather than failing with
  a generic `BaselineException` about an empty baseline. `BaselineExtension` tracks this via `hasBaseline`
  (`Baseline.exists(identifier)`, computed once in `initialize`).
- Configuration (`baseline.output`, `baseline.root`, `baseline.record`) is read via
  `ExtensionContext.getConfigurationParameter`, falling back to `System.getProperty` — both JUnit platform config
  and `-D` system properties must keep working.
- `baseline.root` sanitization strips absolute paths from exception messages so baseline files are portable across
  machines/CI. `baseline.record=true` writes/updates baseline files; otherwise the extension asserts against them.

## Sample/test fixtures

`SampleClassLevelTest.kt` and `SampleMethodLevelTest.kt` are fixture test classes (intentionally throwing) used only
by `BaselineExtensionTest`, which runs them programmatically via `EngineTestKit` (JUnit Platform Testkit) and checks
the resulting events/files rather than letting them run as part of the normal suite directly. `sample-baseline/*.json`
are the checked-in expected baseline outputs for those fixtures — if you change `SampleClassLevelTest` /
`SampleMethodLevelTest` or the filename/format logic, regenerate these with `-Dbaseline.record=true` and diff them
deliberately (they are assertions, not incidental output).

`BaselineTest.kt` unit-tests `Baseline` directly (no JUnit engine involved) and uses Kotest matchers
(`shouldBe`, `shouldThrow`, etc.) even though the `kotest-runner-junit5` engine itself isn't used to run these tests —
it's a `testImplementation` dependency purely for its assertion library.

## Releasing

Version lives in `build.gradle.kts` (`version = "1.7"`). Bump it and add an entry to `CHANGELOG.md` when cutting a
release; JitPack builds directly from git tags/commits, there's no separate publish step in this repo.
