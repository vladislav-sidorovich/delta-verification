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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Entry point for discovering and extracting static Delta Lake golden and reference tables
 * packaged inside the {@code delta-verification} artifact.
 *
 * <p>Because engines and table libraries (such as Apache Iceberg, Apache Spark, Delta Kernel,
 * Delta Standalone, Trino, and DuckDB) expect filesystem paths rather than {@code jar:} URLs,
 * use {@link #extractTable(String, Path)} or {@link #copyTableContents(String, Path)} to
 * materialize a table into a temporary directory during tests.
 */
public final class DeltaVerificationTables {

    private static final String INDEX_RESOURCE = "delta-verification/index.json";
    private static final String GOLDEN_PREFIX = "golden";

    private static final List<DeltaTableInfo> ALL_FIXTURES;
    private static final List<DeltaTableInfo> DELTA_TABLES;
    private static final List<String> ALL_FIXTURE_NAMES;
    private static final List<String> DELTA_TABLE_NAMES;
    private static final Map<String, DeltaTableInfo> BY_NAME;

    static {
        List<DeltaTableInfo> all = loadIndex();
        List<DeltaTableInfo> deltaOnly = new ArrayList<DeltaTableInfo>();
        List<String> allNames = new ArrayList<String>(all.size());
        List<String> deltaNames = new ArrayList<String>();
        Map<String, DeltaTableInfo> map = new LinkedHashMap<String, DeltaTableInfo>(all.size() * 2);

        for (DeltaTableInfo info : all) {
            allNames.add(info.name());
            map.put(info.name(), info);
            if (info.hasDeltaLog()) {
                deltaOnly.add(info);
                deltaNames.add(info.name());
            }
        }

        ALL_FIXTURES = Collections.unmodifiableList(all);
        DELTA_TABLES = Collections.unmodifiableList(deltaOnly);
        ALL_FIXTURE_NAMES = Collections.unmodifiableList(allNames);
        DELTA_TABLE_NAMES = Collections.unmodifiableList(deltaNames);
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private DeltaVerificationTables() {
    }

    /**
     * Returns the sorted names of all Delta Lake tables (fixtures containing a {@code _delta_log}
     * directory).
     *
     * @return immutable list of Delta table names
     */
    public static List<String> tableNames() {
        return DELTA_TABLE_NAMES;
    }

    /**
     * Returns the sorted names of all packaged fixtures, including negative or auxiliary fixtures
     * that intentionally omit {@code _delta_log} (such as {@code no-delta-log-folder}).
     *
     * @return immutable list of all fixture names
     */
    public static List<String> allFixtureNames() {
        return ALL_FIXTURE_NAMES;
    }

    /**
     * Returns metadata for all Delta Lake tables (fixtures containing {@code _delta_log}).
     *
     * @return immutable list of {@link DeltaTableInfo}
     */
    public static List<DeltaTableInfo> tables() {
        return DELTA_TABLES;
    }

    /**
     * Returns metadata for all packaged fixtures, including negative or auxiliary fixtures.
     *
     * @return immutable list of {@link DeltaTableInfo}
     */
    public static List<DeltaTableInfo> allFixtures() {
        return ALL_FIXTURES;
    }

    /**
     * Looks up metadata for a specific table or fixture by name.
     *
     * @param name relative fixture name (for example {@code "snapshot-data0"})
     * @return optional {@link DeltaTableInfo}
     */
    public static Optional<DeltaTableInfo> fixture(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /**
     * Extracts the named fixture under {@code targetRoot/<name>} and returns the path to the
     * extracted fixture directory.
     *
     * @param name fixture name (for example {@code "snapshot-data0"})
     * @param targetRoot parent directory into which the fixture subdirectory will be created
     * @return path to {@code targetRoot.resolve(name)}
     * @throws IllegalArgumentException if {@code name} is unknown or attempts path traversal
     * @throws IOException if extraction fails
     */
    public static Path extractTable(String name, Path targetRoot) throws IOException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(targetRoot, "targetRoot");
        DeltaTableInfo info = requireFixture(name);
        Path normalizedRoot = targetRoot.toAbsolutePath().normalize();
        Path tableDir = normalizedRoot.resolve(info.name()).normalize();
        if (!tableDir.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Invalid fixture path: " + name);
        }
        copyFixtureFiles(info, tableDir);
        return tableDir;
    }

    /**
     * Copies the contents of the named fixture directly into {@code destinationDir} (so
     * {@code destinationDir/_delta_log} is created directly inside {@code destinationDir}).
     *
     * @param name fixture name (for example {@code "snapshot-data0"})
     * @param destinationDir target table directory
     * @return normalized {@code destinationDir}
     * @throws IllegalArgumentException if {@code name} is unknown
     * @throws IOException if copying fails
     */
    public static Path copyTableContents(String name, Path destinationDir) throws IOException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(destinationDir, "destinationDir");
        DeltaTableInfo info = requireFixture(name);
        Path normalizedDest = destinationDir.toAbsolutePath().normalize();
        copyFixtureFiles(info, normalizedDest);
        return normalizedDest;
    }

    /**
     * Extracts all packaged fixtures under {@code targetRoot}.
     *
     * @param targetRoot root directory to populate
     * @return normalized {@code targetRoot}
     * @throws IOException if extraction fails
     */
    public static Path extractAll(Path targetRoot) throws IOException {
        Objects.requireNonNull(targetRoot, "targetRoot");
        Path normalizedRoot = targetRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);

        Path jarOrDir = codeSourceLocation();
        if (jarOrDir != null && Files.isRegularFile(jarOrDir)) {
            try (JarFile jarFile = new JarFile(jarOrDir.toFile())) {
                for (DeltaTableInfo info : ALL_FIXTURES) {
                    Path tableDir = normalizedRoot.resolve(info.name()).normalize();
                    if (!tableDir.startsWith(normalizedRoot)) {
                        throw new IllegalArgumentException("Invalid fixture path: " + info.name());
                    }
                    extractFromJar(jarFile, info, tableDir);
                }
            }
            return normalizedRoot;
        }

        for (DeltaTableInfo info : ALL_FIXTURES) {
            extractTable(info.name(), normalizedRoot);
        }
        return normalizedRoot;
    }

    /**
     * Opens an {@link InputStream} for a single file within a fixture.
     *
     * @param name fixture name
     * @param relativeFile relative file path within the fixture (as listed in {@link DeltaTableInfo#files()})
     * @return open {@link InputStream}; caller is responsible for closing it
     * @throws IOException if the file cannot be found or opened
     */
    public static InputStream openFileStream(String name, String relativeFile) throws IOException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(relativeFile, "relativeFile");
        DeltaTableInfo info = requireFixture(name);
        if (!info.files().contains(relativeFile)) {
            throw new FileNotFoundException(
                    "File '" + relativeFile + "' is not part of fixture '" + name + "'");
        }
        String entryPath = info.resourcePath() + "/" + relativeFile;

        Path jarOrDir = codeSourceLocation();
        if (jarOrDir != null && Files.isRegularFile(jarOrDir)) {
            final JarFile jarFile = new JarFile(jarOrDir.toFile());
            JarEntry entry = jarFile.getJarEntry(entryPath);
            if (entry != null) {
                final InputStream raw = jarFile.getInputStream(entry);
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return raw.read();
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        return raw.read(b, off, len);
                    }

                    @Override
                    public void close() throws IOException {
                        try {
                            raw.close();
                        } finally {
                            jarFile.close();
                        }
                    }
                };
            }
            jarFile.close();
        }

        Path unpackedGolden = unpackedGoldenDirectory();
        if (unpackedGolden != null) {
            Path candidate = unpackedGolden.resolve(info.name()).resolve(relativeFile).normalize();
            if (candidate.startsWith(unpackedGolden) && Files.isRegularFile(candidate)) {
                return Files.newInputStream(candidate);
            }
        }

        InputStream stream = classLoader().getResourceAsStream(entryPath);
        if (stream == null) {
            throw new FileNotFoundException("Resource not found on classpath: " + entryPath);
        }
        return stream;
    }

    private static DeltaTableInfo requireFixture(String name) {
        DeltaTableInfo info = BY_NAME.get(name);
        if (info == null) {
            throw new IllegalArgumentException("Unknown Delta verification fixture: '" + name + "'");
        }
        return info;
    }

    private static void copyFixtureFiles(DeltaTableInfo info, Path targetTableDir) throws IOException {
        Files.createDirectories(targetTableDir);

        Path jarOrDir = codeSourceLocation();
        if (jarOrDir != null && Files.isRegularFile(jarOrDir)) {
            try (JarFile jarFile = new JarFile(jarOrDir.toFile())) {
                extractFromJar(jarFile, info, targetTableDir);
            }
            return;
        }

        Path unpackedGolden = unpackedGoldenDirectory();
        if (unpackedGolden != null) {
            Path srcTableDir = unpackedGolden.resolve(info.name()).normalize();
            if (Files.isDirectory(srcTableDir)) {
                for (String rel : info.files()) {
                    Path src = srcTableDir.resolve(rel).normalize();
                    Path dst = resolveSafe(targetTableDir, rel);
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
        }

        for (String rel : info.files()) {
            Path dst = resolveSafe(targetTableDir, rel);
            Files.createDirectories(dst.getParent());
            try (InputStream in = openFileStream(info.name(), rel)) {
                Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void extractFromJar(JarFile jarFile, DeltaTableInfo info, Path targetTableDir)
            throws IOException {
        Files.createDirectories(targetTableDir);
        String prefix = info.resourcePath() + "/";
        for (String rel : info.files()) {
            String entryName = prefix + rel;
            JarEntry entry = jarFile.getJarEntry(entryName);
            if (entry == null) {
                throw new FileNotFoundException("Missing JAR entry: " + entryName);
            }
            Path dst = resolveSafe(targetTableDir, rel);
            Files.createDirectories(dst.getParent());
            try (InputStream in = jarFile.getInputStream(entry)) {
                Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path resolveSafe(Path baseDir, String relativePath) {
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Refusing to write outside target directory: " + relativePath);
        }
        return resolved;
    }

    private static Path codeSourceLocation() {
        try {
            CodeSource cs = DeltaVerificationTables.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                return Paths.get(cs.getLocation().toURI());
            }
        } catch (URISyntaxException | SecurityException | IllegalArgumentException ignored) {
            // Fall through to ClassLoader lookup
        }
        return null;
    }

    private static Path unpackedGoldenDirectory() {
        URL url = classLoader().getResource(GOLDEN_PREFIX);
        if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
            try {
                URI uri = url.toURI();
                Path path = Paths.get(uri).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) {
                    return path;
                }
            } catch (URISyntaxException | IllegalArgumentException ignored) {
                // Ignore
            }
        }
        return null;
    }

    private static List<DeltaTableInfo> loadIndex() {
        try (InputStream in = classLoader().getResourceAsStream(INDEX_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource on classpath: " + INDEX_RESOURCE);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return JsonIndexParser.parseTables(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + INDEX_RESOURCE, e);
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader cl = DeltaVerificationTables.class.getClassLoader();
        return cl != null ? cl : ClassLoader.getSystemClassLoader();
    }
}
