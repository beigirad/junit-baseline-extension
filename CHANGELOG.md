## Unreleased

### Changed

- **BREAKING**: Baseline schema now stores arrays of strings instead of single strings for each test:
    - Schema format: `{"testName": ["error line1", "error line2"]}` instead of `{"testName": "error message"}`
    - Multi-line error messages are automatically split into array elements.
    - Improved JSON formatting in baseline files with 2-space indentation.