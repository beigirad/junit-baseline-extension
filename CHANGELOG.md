## 1.7

- When no baseline file exists yet for a test (and `baseline.record` isn't `true`), the extension now behaves as a
  no-op instead of failing with a generic `BaselineException` — the real failure surfaces normally, as if
  `BaselineExtension` weren't applied at all.

## 1.6

- Remove unused kotest dependency

## 1.5

- Fixed stacktrace of failed test [#3](https://github.com/beigirad/junit-baseline-extension/issues/3)

## 1.4

- The configuration arguments are now optional, and they have followed default value:
  - `baseline.output`: `PROJECT_DIR/test-baseline`
  - `baseline.root`: `null` // disables message sanitization
  - `baseline.record`: `false`

## 1.3

- **BREAKING**: Baseline schema now stores arrays of strings instead of single strings for each test:
    - Schema format: `{"testName": ["error line1", "error line2"]}` instead of `{"testName": "error message"}`
    - Multi-line error messages are automatically split into array elements.
    - Improved JSON formatting in baseline files with 2-space indentation.