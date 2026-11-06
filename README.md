# Delta Verification

A repository and Maven Central artifact of static [Delta Lake](https://delta.io/) reference and golden tables for read conformance and integration testing across implementations.

Inspired by [Apache Iceberg Verification (`apache/iceberg-verification`)](https://github.com/apache/iceberg-verification).

## Motivation

Each Delta Lake implementation and connector (Apache Iceberg `iceberg-delta-lake`, Delta Kernel, Delta Standalone, Apache Spark, Apache Flink, Trino, DuckDB, `delta-rs`) reads the Delta transaction log (`_delta_log`), checkpoints, deletion vectors, column mapping metadata, and Parquet files independently.

Generating test tables dynamically in every test suite requires spinning up Apache Spark at test time, slows down builds, and couples reader tests to a single writer dependency. By packaging static, pre-generated Delta Lake golden tables into a lightweight, zero-dependency JAR on Maven Central, any JVM or non-JVM project can test Delta read conformance directly against known static artifacts.

## Maven & Gradle Coordinates

[![Maven Central](https://img.shields.io/maven-central/v/io.github.vladislav-sidorovich/delta-verification.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.vladislav-sidorovich/delta-verification)

### Gradle (Groovy DSL)

```groovy
dependencies {
    testImplementation 'io.github.vladislav-sidorovich:delta-verification:0.1.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    testImplementation("io.github.vladislav-sidorovich:delta-verification:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.vladislav-sidorovich</groupId>
    <artifactId>delta-verification</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

The published JAR targets **Java 8+** and carries **zero runtime dependencies**.

## Usage (Java / Kotlin / Scala)

Because filesystem-based table readers (`Hadoop FileSystem`, `DeltaLog`, `DeltaKernel`, `Iceberg`, etc.) expect real directory paths rather than `jar:file:...!/` URLs, use `DeltaVerificationTables` to inspect and materialize any fixture into a temporary test directory:

```java
import io.github.vladislavsidorovich.deltaverification.DeltaTableInfo;
import io.github.vladislavsidorovich.deltaverification.DeltaVerificationTables;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MyDeltaReaderTest {

    @Test
    void readsGoldenTable(@TempDir Path tempDir) throws Exception {
        // List all 108 Delta tables (fixtures containing `_delta_log`)
        List<String> tables = DeltaVerificationTables.tableNames();

        // Inspect table protocol metadata before running reader assertions
        DeltaTableInfo info = DeltaVerificationTables
                .fixture("dv-partitioned-with-checkpoint")
                .orElseThrow();
        // info.minReaderVersion() -> Optional[3]
        // info.readerFeatures()   -> ["deletionVectors"]

        // Materialize a static Delta table from the JAR onto the local filesystem
        Path tablePath = DeltaVerificationTables.extractTable("snapshot-data0", tempDir);
        // `tablePath` points to `<tempDir>/snapshot-data0` containing `_delta_log/` and `*.parquet`
    }
}
```

### Language-Neutral Usage

Non-JVM implementations can pin this repository as a Git submodule or checkout and read:
- [`src/main/resources/delta-verification/index.json`](src/main/resources/delta-verification/index.json) — machine-readable catalog of every fixture, its Delta protocol versions (`minReaderVersion`, `minWriterVersion`), table features (`readerFeatures`, `writerFeatures`), checkpoint presence, and relative file list.
- [`src/main/resources/golden/`](src/main/resources/golden) — the static Delta Lake tables.

## Repository Layout

```text
.
├── build.gradle                                      # Gradle build & Maven Central publishing config
├── settings.gradle                                   # Gradle settings (preserves ._*.crc & %/# paths)
├── dev/
│   ├── generate_index.py                             # Regenerates delta-verification/index.json
│   └── validate_fixtures.py                          # Verifies index.json matches golden/ 1:1
├── src/
│   ├── main/
│   │   ├── java/.../deltaverification/
│   │   │   ├── DeltaTableInfo.java                   # Immutable fixture metadata model
│   │   │   └── DeltaVerificationTables.java          # Zero-dependency discovery & extraction API
│   │   └── resources/
│   │       ├── delta-verification/index.json         # Machine-readable index of all 111 fixtures
│   │       └── golden/                               # Unmodified Delta Lake golden tables
│   └── test/
│       └── java/.../deltaverification/
│           └── DeltaVerificationTablesTest.java      # Verifies discovery & byte-exact JAR extraction
├── LICENSE                                           # MIT License + Apache-2.0 third-party license text
└── NOTICE                                            # Attribution notices for Delta Lake golden tables
```

## Building & Verifying Locally

```bash
# Run compilation, unit tests, isolated JAR extraction tests, and Python fixture validation
./gradlew check

# Install artifact to local ~/.m2 repository
./gradlew publishToMavenLocal
```

## Publishing to Maven Central

Publishing is configured via [`com.vanniktech.maven.publish`](https://github.com/vanniktech/gradle-maven-publish-plugin) targeting the [Sonatype Central Portal](https://central.sonatype.com/) under group ID `io.github.vladislav-sidorovich`.

## License & Attribution

- The project structure, build configuration, tooling, and Java helper library in this repository are licensed under the **[MIT License](LICENSE)**.
- The static Delta Lake golden table fixtures under `src/main/resources/golden/` originate unmodified from the [Delta Lake project](https://github.com/delta-io/delta/tree/master/connectors/golden-tables/src/main/resources/golden) (`Copyright (2021) The Delta Lake Project Authors`) and remain under their original **[Apache License, Version 2.0](LICENSE)**. Both the full Apache License 2.0 text and the upstream [`NOTICE`](NOTICE) attributions are included in the repository root and inside `META-INF/` of every published JAR artifact in compliance with Section 4 of the Apache License 2.0.
- "Delta Lake" is a trademark of LF Projects, LLC and is used solely to describe the table format and origin of the bundled test fixtures.
