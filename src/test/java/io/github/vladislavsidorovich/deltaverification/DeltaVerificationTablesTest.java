/*
 * MIT License
 *
 * Copyright (c) 2026 Vladislav Sidorovich
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

package io.github.vladislavsidorovich.deltaverification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeltaVerificationTablesTest {

    @Test
    void discoversAllGoldenTablesAndMetadata() {
        List<String> tableNames = DeltaVerificationTables.tableNames();
        List<String> allNames = DeltaVerificationTables.allFixtureNames();

        assertEquals(108, tableNames.size());
        assertEquals(111, allNames.size());

        List<String> sorted = new ArrayList<String>(allNames);
        Collections.sort(sorted);
        assertEquals(sorted, allNames);

        assertTrue(tableNames.contains("snapshot-data0"));
        assertTrue(tableNames.contains("hive/deltatbl-partitioned"));
        assertTrue(tableNames.contains("data-reader-date-types-America/Los_Angeles"));
        assertFalse(tableNames.contains("no-delta-log-folder"));
        assertTrue(allNames.contains("no-delta-log-folder"));

        DeltaTableInfo dvTable = DeltaVerificationTables.fixture("dv-partitioned-with-checkpoint").orElseThrow();
        assertTrue(dvTable.hasDeltaLog());
        assertTrue(dvTable.hasCheckpoint());
        assertEquals(Integer.valueOf(3), dvTable.minReaderVersion().orElseThrow());
        assertEquals(Integer.valueOf(7), dvTable.minWriterVersion().orElseThrow());
        assertTrue(dvTable.readerFeatures().contains("deletionVectors"));
        assertTrue(dvTable.writerFeatures().contains("deletionVectors"));
        assertFalse(dvTable.notice().isPresent());

        DeltaTableInfo noDeltaLog = DeltaVerificationTables.fixture("no-delta-log-folder").orElseThrow();
        assertFalse(noDeltaLog.hasDeltaLog());

        List<DeltaTableInfo> parsedOptionalNotice = JsonIndexParser.parseTables(
                "{\"schemaVersion\":1,\"tables\":["
                        + "{\"name\":\"custom-no-notice\",\"resourcePath\":\"golden/custom-no-notice\","
                        + "\"hasDeltaLog\":true,\"latestVersion\":0,\"hasCheckpoint\":false,"
                        + "\"minReaderVersion\":1,\"minWriterVersion\":2,"
                        + "\"readerFeatures\":[],\"writerFeatures\":[],"
                        + "\"files\":[\"_delta_log/00000000000000000000.json\"]},"
                        + "{\"name\":\"custom-with-notice\",\"resourcePath\":\"golden/custom-with-notice\","
                        + "\"hasDeltaLog\":true,\"latestVersion\":0,\"hasCheckpoint\":false,"
                        + "\"minReaderVersion\":1,\"minWriterVersion\":2,"
                        + "\"readerFeatures\":[],\"writerFeatures\":[],"
                        + "\"notice\":\"Custom table attribution\","
                        + "\"files\":[\"_delta_log/00000000000000000000.json\"]}"
                        + "]}");
        assertEquals(2, parsedOptionalNotice.size());
        assertFalse(parsedOptionalNotice.get(0).notice().isPresent());
        assertEquals("Custom table attribution", parsedOptionalNotice.get(1).notice().orElseThrow());
    }

    @Test
    void extractsSpecialCharacterAndCrcFixtures(@TempDir Path tempDir) throws IOException {
        Path escaped = DeltaVerificationTables.extractTable("data-reader-escaped-chars", tempDir);
        assertTrue(Files.isDirectory(escaped.resolve("_delta_log")));
        assertTrue(Files.isDirectory(escaped.resolve("_2=bar+%2523")));

        Path hiveSpecial = DeltaVerificationTables.extractTable(
                "hive/deltatbl-special-chars-in-partition-column", tempDir);
        assertTrue(Files.isDirectory(hiveSpecial.resolve("c2=+ %3D%251")));

        Path crcTable = DeltaVerificationTables.extractTable("125-iterator-bug", tempDir);
        assertTrue(Files.isRegularFile(
                crcTable.resolve("_delta_log/.00000000000000000010.checkpoint.parquet.crc")));

        Path directCopy = tempDir.resolve("custom-dest");
        DeltaVerificationTables.copyTableContents("snapshot-data0", directCopy);
        assertTrue(Files.isRegularFile(directCopy.resolve("_delta_log/00000000000000000000.json")));

        try (InputStream in = DeltaVerificationTables.openFileStream(
                "snapshot-data0", "_delta_log/00000000000000000000.json")) {
            String commitJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(commitJson.contains("\"protocol\""));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> DeltaVerificationTables.extractTable("non-existent-table", tempDir));
    }

    @Test
    void packagedJarIncludesLicensesAndExtractsEveryFixtureByteForByte(@TempDir Path tempDir)
            throws Exception {
        String jarProp = System.getProperty("delta.verification.jar");
        String goldenRootProp = System.getProperty("delta.verification.goldenRoot");
        assertNotNull(jarProp, "System property delta.verification.jar must be set");
        assertNotNull(goldenRootProp, "System property delta.verification.goldenRoot must be set");

        Path jarPath = Paths.get(jarProp);
        Path goldenRoot = Paths.get(goldenRootProp);
        assertTrue(Files.isRegularFile(jarPath), "Built JAR does not exist: " + jarPath);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry licenseEntry = jar.getJarEntry("META-INF/LICENSE");
            JarEntry noticeEntry = jar.getJarEntry("META-INF/NOTICE");
            assertNotNull(licenseEntry, "META-INF/LICENSE must be packaged inside JAR");
            assertNotNull(noticeEntry, "META-INF/NOTICE must be packaged inside JAR");

            try (InputStream licIn = jar.getInputStream(licenseEntry)) {
                String licenseText = new String(licIn.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(licenseText.contains("MIT License"));
                assertTrue(licenseText.contains("Apache License"));
                assertTrue(licenseText.contains("Version 2.0, January 2004"));
            }

            try (InputStream noticeIn = jar.getInputStream(noticeEntry)) {
                String noticeText = new String(noticeIn.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(noticeText.contains("The Delta Lake Project Authors"));
                assertTrue(noticeText.contains("Apache License, Version 2.0"));
            }
        }

        try (URLClassLoader isolatedJarLoader =
                new URLClassLoader(new URL[] {jarPath.toUri().toURL()}, null)) {
            Class<?> clazz = Class.forName(
                    "io.github.vladislavsidorovich.deltaverification.DeltaVerificationTables",
                    true,
                    isolatedJarLoader);
            Method extractAll = clazz.getMethod("extractAll", Path.class);
            extractAll.invoke(null, tempDir);
        }

        final List<Path> verifiedFiles = new ArrayList<Path>();
        Files.walkFileTree(goldenRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path expectedFile, BasicFileAttributes attrs)
                    throws IOException {
                if (".DS_Store".equals(expectedFile.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path rel = goldenRoot.relativize(expectedFile);
                Path extractedFile = tempDir.resolve(rel);
                assertTrue(
                        Files.isRegularFile(extractedFile),
                        "Missing extracted file from JAR: " + rel);
                assertArrayEquals(
                        Files.readAllBytes(expectedFile),
                        Files.readAllBytes(extractedFile),
                        "Byte mismatch for extracted file: " + rel);
                verifiedFiles.add(rel);
                return FileVisitResult.CONTINUE;
            }
        });

        int expectedTotalFiles = 0;
        for (DeltaTableInfo info : DeltaVerificationTables.allFixtures()) {
            expectedTotalFiles += info.files().size();
        }
        assertTrue(verifiedFiles.size() >= 1193);
        assertEquals(expectedTotalFiles, verifiedFiles.size());
    }
}
