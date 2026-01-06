# JUnit Baseline Extension

A JUnit 5 extension that enables baseline testing by recording and comparing test failures against expected baselines.
This is particularly useful for snapshot testing and ensuring that known failures remain consistent across test runs.

## Features

- 📸 **Snapshot Testing**: Record test failures as baselines and verify future runs match
- 🎯 **Flexible Application**: Use at class-level or method-level
- 🔧 **Configurable**: Support for both JUnit configuration parameters and system properties
- 📁 **JSON-based Storage**: Human-readable baseline files in JSON format
- 🧹 **Path Sanitization**: Automatically removes absolute paths from error messages

## Requirements

- JDK 17 or higher
- Kotlin 2.1.21
- JUnit Jupiter 5.x

## Installation

Add the following dependencies to your `build.gradle.kts`:

[![](https://jitpack.io/v/beigirad/junit-baseline-extension.svg)](https://jitpack.io/#beigirad/junit-baseline-extension)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation 'com.github.beigirad:junit-baseline-extension:VERSION'
}
```

## Usage

### Class-Level Extension

Apply the extension to all tests in a class:

```kotlin
@ExtendWith(BaselineExtension::class)
class MyTest {
    @Test
    fun `test that might fail`() {
        throw AssertionError("Expected failure")
    }
}
```

### Method-Level Extension

Apply the extension to specific test methods:

```kotlin
class MyTest {
    @Test
    @ExtendWith(BaselineExtension::class)
    fun `test with baseline`() {
        throw AssertionError("Expected failure")
    }

    @Test
    fun `regular test without baseline`() {
        // This test won't use baseline
    }
}
```

## Configuration

The extension requires two configuration parameters:

- **`baseline.root`**: The root directory of your project (used for path sanitization)
- **`baseline.output`**: The directory where baseline files will be stored
- **`baseline.record`**: Set to `true` to record/update baselines, `false` to assert against them

### Configuration via System Properties

```bash
./gradlew test -Dbaseline.root=/path/to/project \
                -Dbaseline.output=/path/to/baselines \
                -Dbaseline.record=true
```

### Configuration via JUnit Parameters

In `build.gradle.kts`:

```kotlin
tasks.test {
    useJUnitPlatform()
    systemProperty("baseline.root", projectDir.absolutePath)
    systemProperty("baseline.output", file("baselines").absolutePath)
}
```

Or via `junit-platform.properties`:

```properties
baseline.root=/path/to/project
baseline.output=/path/to/baselines
baseline.record=false
```

## Workflow

### 1. Record Baseline

Run your tests with `baseline.record=true` to create baseline files:

```bash
./gradlew test -Dbaseline.record=true
```

This creates JSON files in your baseline output directory:

- `baseline-MyTestClass.json` (for class-level extensions)
- `baseline-MyTestClass-shortened.method.name.<hash>.json` (for method-level extensions)

### 2. Verify Against Baseline

Run tests normally (without `baseline.record` or with `baseline.record=false`):

```bash
./gradlew test
```

The extension will:

- ✅ Pass if failures match the baseline exactly
- ❌ Fail if new failures appear
- ❌ Fail if existing failures are missing or changed

### 3. Update Baseline

When you intentionally change test behavior, update the baseline:

```bash
./gradlew test -Dbaseline.record=true
```

## Baseline File Format

Baselines are stored as JSON with test IDs as keys and error messages as values:

```json
{
   "test method name": "Expected failure",
   "anotherTestName": "Another expected failure"
}
```

## How It Works

1. **Recording Phase**: When `baseline.record=true`, the extension captures test failures and writes them to JSON files
2. **Assertion Phase**: When `baseline.record=false` (default behavior), the extension compares actual failures against
   the stored baseline
3. **Path Sanitization**: Absolute paths are removed from error messages to ensure portability across environments
4. **Comparison**: Uses Kotest assertions to provide detailed failure messages when baselines don't match

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
