# Version Synchronization

**Priority**: High (Quick Win)
**Effort**: 1-2 days
**Phase**: Foundation & Quick Wins

## Current State

### The Problem

The version is currently defined in two places with mismatched values:

1. **build.sbt** (line 18):
   ```scala
   ThisBuild / version := "0.1.0-RC14"
   ```

2. **core/src/main/scala/Main.scala** (line 15):
   ```scala
   override protected def programVersion: String = "0.1.0-RC11"
   ```

**Impact**:
- Users see incorrect version in CLI output (`--version` flag)
- Confusing for debugging and support
- Manual sync required for every release
- Easy to forget to update both locations

### Why This Happened

The version in `Main.scala` is hardcoded, likely from when the project was first created. As releases continued, `build.sbt` was updated but the hardcoded value was forgotten.

## Proposed Solution

**Single Source of Truth**: Generate version from `build.sbt` and make it available to runtime code.

### Approach

Use SBT's `buildInfo` plugin to generate a Scala object containing build metadata at compile time.

**Benefits**:
- Automatic synchronization
- No manual updates needed
- Can include additional build metadata (git hash, build timestamp, etc.)
- Standard practice in Scala projects
- Zero runtime overhead

## Implementation Details

### Step 1: Add BuildInfo Plugin

**File**: `project/plugins.sbt`

Add:
```scala
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
```

### Step 2: Configure BuildInfo in build.sbt

**File**: `build.sbt`

Add to core module settings (around line 105-150):
```scala
lazy val core = (project in file("core"))
  .enablePlugins(BuildInfoPlugin)  // Add this
  .settings(
    commonSettings,
    name := "xlcr-core",

    // Add BuildInfo configuration
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.action("buildTime") {
        java.time.Instant.now().toString
      },
      BuildInfoKey.action("gitCommit") {
        scala.sys.process.Process("git rev-parse --short HEAD").!!.trim
      }
    ),
    buildInfoPackage := "com.tjclp.xlcr.build",
    buildInfoOptions += BuildInfoOption.BuildTime,

    // ... rest of settings
  )
```

This will generate:
- `core/target/scala-2.12/src_managed/main/com/tjclp/xlcr/build/BuildInfo.scala`

### Step 3: Update Main.scala

**File**: `core/src/main/scala/Main.scala` (line 15)

Change from:
```scala
override protected def programVersion: String = "0.1.0-RC11"
```

To:
```scala
override protected def programVersion: String = com.tjclp.xlcr.build.BuildInfo.version
```

### Step 4: Update AbstractMain.scala

**File**: `core/src/main/scala/cli/AbstractMain.scala`

Consider enhancing the version display to show more build info:

```scala
// Add method to show detailed version info
protected def showDetailedVersion(): Unit = {
  import com.tjclp.xlcr.build.BuildInfo

  println(s"$programName version ${BuildInfo.version}")
  println(s"Scala version: ${BuildInfo.scalaVersion}")
  println(s"Built: ${BuildInfo.buildTime}")
  println(s"Git commit: ${BuildInfo.gitCommit}")
}
```

Add flag parsing for `--version-detailed` or similar.

### Step 5: Validate in CI

**File**: `.github/workflows/ci.yml`

Add validation step after build:
```yaml
- name: Validate version consistency
  run: |
    # Extract version from generated JAR name
    JAR_VERSION=$(ls core/target/scala-*/*.jar | grep -o 'xlcr-core.*\.jar' | sed 's/xlcr-core-//; s/\.jar//')
    # Verify it matches build.sbt
    BUILD_VERSION=$(grep 'ThisBuild / version :=' build.sbt | cut -d'"' -f2)
    if [ "$JAR_VERSION" != "$BUILD_VERSION" ]; then
      echo "Version mismatch: JAR=$JAR_VERSION, build.sbt=$BUILD_VERSION"
      exit 1
    fi
    echo "Version validation passed: $BUILD_VERSION"
```

### Step 6: Clean Up Other Modules

Check for version hardcoding in:
- `core-aspose/src/main/scala/*.scala`
- `core-libreoffice/src/main/scala/*.scala`
- Any README or documentation

Update to reference `BuildInfo.version` if needed.

## Success Criteria

1. ✅ `sbt-buildinfo` plugin added and configured
2. ✅ `BuildInfo` object generated with version, build time, git commit
3. ✅ `Main.scala` reads version from `BuildInfo.version`
4. ✅ `sbt run --version` shows correct version matching `build.sbt`
5. ✅ CI validates version consistency
6. ✅ No hardcoded versions remain in codebase
7. ✅ Documentation updated to explain version management

## Testing Strategy

### Manual Testing

```bash
# 1. Update version in build.sbt
echo 'ThisBuild / version := "0.1.0-RC15"' >> build.sbt

# 2. Recompile
sbt clean compile

# 3. Check generated BuildInfo
cat core/target/scala-2.12/src_managed/main/com/tjclp/xlcr/build/BuildInfo.scala

# 4. Run and check version
sbt "run --version"
# Should show: xlcr version 0.1.0-RC15

# 5. Check assembled JAR name
sbt assembly
ls core/target/scala-2.12/*.jar
# Should include: xlcr-core-assembly-0.1.0-RC15.jar
```

### Automated Testing

Create test to verify BuildInfo accessibility:

**File**: `core/src/test/scala/build/BuildInfoSpec.scala`

```scala
package com.tjclp.xlcr.build

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BuildInfoSpec extends AnyFlatSpec with Matchers {

  "BuildInfo" should "be accessible" in {
    BuildInfo.version should not be empty
  }

  it should "have valid version format" in {
    BuildInfo.version should fullyMatch regex """^\d+\.\d+\.\d+(-\w+)?$"""
  }

  it should "have build metadata" in {
    BuildInfo.scalaVersion should not be empty
    BuildInfo.buildTime should not be empty
  }

  it should "have git commit info" in {
    BuildInfo.gitCommit should fullyMatch regex """^[0-9a-f]{7,}$"""
  }
}
```

## Rollout Plan

1. **Development** (Day 1):
   - Add plugin
   - Configure BuildInfo
   - Update Main.scala
   - Test locally

2. **Testing** (Day 1):
   - Run test suite
   - Verify all modules compile
   - Test CLI version output
   - Check assembly artifacts

3. **Documentation** (Day 2):
   - Update CLAUDE.md if needed
   - Add note to CONTRIBUTING.md about version management
   - Update any README references

4. **CI Integration** (Day 2):
   - Add validation step
   - Test in CI environment
   - Verify across Scala versions (2.12, 2.13, 3.3.4)

5. **Merge** (Day 2):
   - Create PR
   - Review
   - Merge to main

## Additional Improvements

### Optional Enhancements

1. **Detailed Version Command**:
   ```bash
   xlcr --version          # Short: "0.1.0-RC15"
   xlcr --version-detailed # Full build info
   ```

2. **Backend Version Info**:
   Include Aspose library versions in BuildInfo:
   ```scala
   buildInfoKeys += BuildInfoKey.action("asposeVersion") {
     "com.aspose.slides: 24.6"
   }
   ```

3. **Build Environment**:
   ```scala
   buildInfoKeys += BuildInfoKey.action("buildEnv") {
     sys.env.getOrElse("BUILD_ENV", "development")
   }
   ```

4. **Version in Logs**:
   Log version at startup:
   ```scala
   logger.info(s"XLCR ${BuildInfo.version} (${BuildInfo.gitCommit}) starting...")
   ```

## Dependencies

**None** - This is a standalone improvement with no dependencies on other planning documents.

## Risks and Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Git command fails in CI | Low | Medium | Add fallback to "unknown" or environment variable |
| Build time increases | Low | Low | BuildInfo generation is fast (~100ms) |
| Scala 3 compatibility | Low | Medium | sbt-buildinfo supports Scala 3 |

## References

- [sbt-buildinfo GitHub](https://github.com/sbt/sbt-buildinfo)
- [SBT Best Practices](https://www.scala-sbt.org/1.x/docs/Best-Practices.html)
- Similar implementation in other projects (e.g., Apache Spark's version management)
