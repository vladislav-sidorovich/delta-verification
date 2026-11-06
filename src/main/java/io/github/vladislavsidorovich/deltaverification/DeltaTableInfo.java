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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata describing a static Delta Lake verification fixture packaged in this library.
 */
public final class DeltaTableInfo {

    private final String name;
    private final String resourcePath;
    private final boolean hasDeltaLog;
    private final Integer latestVersion;
    private final boolean hasCheckpoint;
    private final Integer minReaderVersion;
    private final Integer minWriterVersion;
    private final List<String> readerFeatures;
    private final List<String> writerFeatures;
    private final String notice;
    private final List<String> files;

    /**
     * Creates a new {@link DeltaTableInfo} instance without an attribution notice.
     *
     * @param name relative fixture name under {@code golden/}
     * @param resourcePath classpath resource directory path
     * @param hasDeltaLog whether the fixture contains a {@code _delta_log} directory
     * @param latestVersion highest Delta log version present, or {@code null} if not applicable
     * @param hasCheckpoint whether the {@code _delta_log} contains at least one checkpoint file
     * @param minReaderVersion protocol {@code minReaderVersion}, or {@code null} if absent
     * @param minWriterVersion protocol {@code minWriterVersion}, or {@code null} if absent
     * @param readerFeatures protocol {@code readerFeatures}
     * @param writerFeatures protocol {@code writerFeatures}
     * @param files relative file paths belonging to this fixture
     */
    public DeltaTableInfo(
            String name,
            String resourcePath,
            boolean hasDeltaLog,
            Integer latestVersion,
            boolean hasCheckpoint,
            Integer minReaderVersion,
            Integer minWriterVersion,
            List<String> readerFeatures,
            List<String> writerFeatures,
            List<String> files) {
        this(
                name,
                resourcePath,
                hasDeltaLog,
                latestVersion,
                hasCheckpoint,
                minReaderVersion,
                minWriterVersion,
                readerFeatures,
                writerFeatures,
                null,
                files);
    }

    /**
     * Creates a new {@link DeltaTableInfo} instance.
     *
     * @param name relative fixture name under {@code golden/}
     * @param resourcePath classpath resource directory path
     * @param hasDeltaLog whether the fixture contains a {@code _delta_log} directory
     * @param latestVersion highest Delta log version present, or {@code null} if not applicable
     * @param hasCheckpoint whether the {@code _delta_log} contains at least one checkpoint file
     * @param minReaderVersion protocol {@code minReaderVersion}, or {@code null} if absent
     * @param minWriterVersion protocol {@code minWriterVersion}, or {@code null} if absent
     * @param readerFeatures protocol {@code readerFeatures}
     * @param writerFeatures protocol {@code writerFeatures}
     * @param notice optional license/provenance notice for this table, or {@code null}
     * @param files relative file paths belonging to this fixture
     */
    public DeltaTableInfo(
            String name,
            String resourcePath,
            boolean hasDeltaLog,
            Integer latestVersion,
            boolean hasCheckpoint,
            Integer minReaderVersion,
            Integer minWriterVersion,
            List<String> readerFeatures,
            List<String> writerFeatures,
            String notice,
            List<String> files) {
        this.name = Objects.requireNonNull(name, "name");
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        this.hasDeltaLog = hasDeltaLog;
        this.latestVersion = latestVersion;
        this.hasCheckpoint = hasCheckpoint;
        this.minReaderVersion = minReaderVersion;
        this.minWriterVersion = minWriterVersion;
        this.readerFeatures = unmodifiableCopy(readerFeatures);
        this.writerFeatures = unmodifiableCopy(writerFeatures);
        this.notice = notice;
        this.files = unmodifiableCopy(files);
    }

    private static List<String> unmodifiableCopy(List<String> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(items));
    }

    /** Returns the fixture name relative to {@code golden/} (for example {@code "snapshot-data0"}). */
    public String name() {
        return name;
    }

    /** Returns the classpath resource path (for example {@code "golden/snapshot-data0"}). */
    public String resourcePath() {
        return resourcePath;
    }

    /** Returns {@code true} if this fixture is a Delta table containing {@code _delta_log}. */
    public boolean hasDeltaLog() {
        return hasDeltaLog;
    }

    /** Returns the highest version present in {@code _delta_log}, if available. */
    public Optional<Integer> latestVersion() {
        return Optional.ofNullable(latestVersion);
    }

    /** Returns {@code true} if {@code _delta_log} includes at least one checkpoint file. */
    public boolean hasCheckpoint() {
        return hasCheckpoint;
    }

    /** Returns the Delta protocol {@code minReaderVersion}, if present in the commit log. */
    public Optional<Integer> minReaderVersion() {
        return Optional.ofNullable(minReaderVersion);
    }

    /** Returns the Delta protocol {@code minWriterVersion}, if present in the commit log. */
    public Optional<Integer> minWriterVersion() {
        return Optional.ofNullable(minWriterVersion);
    }

    /** Returns an immutable list of Delta protocol {@code readerFeatures}. */
    public List<String> readerFeatures() {
        return readerFeatures;
    }

    /** Returns an immutable list of Delta protocol {@code writerFeatures}. */
    public List<String> writerFeatures() {
        return writerFeatures;
    }

    /** Returns the optional provenance or attribution notice associated with this fixture. */
    public Optional<String> notice() {
        return Optional.ofNullable(notice);
    }

    /** Returns an immutable sorted list of all relative file paths within this fixture. */
    public List<String> files() {
        return files;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeltaTableInfo)) {
            return false;
        }
        DeltaTableInfo that = (DeltaTableInfo) o;
        return hasDeltaLog == that.hasDeltaLog
                && hasCheckpoint == that.hasCheckpoint
                && Objects.equals(name, that.name)
                && Objects.equals(resourcePath, that.resourcePath)
                && Objects.equals(latestVersion, that.latestVersion)
                && Objects.equals(minReaderVersion, that.minReaderVersion)
                && Objects.equals(minWriterVersion, that.minWriterVersion)
                && Objects.equals(readerFeatures, that.readerFeatures)
                && Objects.equals(writerFeatures, that.writerFeatures)
                && Objects.equals(notice, that.notice)
                && Objects.equals(files, that.files);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                resourcePath,
                hasDeltaLog,
                latestVersion,
                hasCheckpoint,
                minReaderVersion,
                minWriterVersion,
                readerFeatures,
                writerFeatures,
                notice,
                files);
    }

    @Override
    public String toString() {
        return "DeltaTableInfo{name='" + name + "', hasDeltaLog=" + hasDeltaLog
                + ", minReaderVersion=" + minReaderVersion
                + ", minWriterVersion=" + minWriterVersion
                + ", files=" + files.size() + '}';
    }
}
