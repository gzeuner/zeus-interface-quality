/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.sftp;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;
import de.tinytool.quality.core.ValidationLanguage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * Selects and downloads a stable SFTP file, verifies its integrity, and
 * delegates its contents to the existing JSON, CSV, or fixed-width validator.
 */
public final class SftpValidator implements Validator {

    private static final DateTimeFormatter QUARANTINE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                    .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final SftpTransportFactory transportFactory;
    private final Validator jsonValidator;
    private final Validator csvValidator;
    private final Validator fixedWidthValidator;

    public SftpValidator() {
        this(
                new ObjectMapper(),
                new JschSftpTransportFactory(),
                new JsonSchemaValidator(),
                new CsvValidator(),
                new FixedWidthValidator());
    }

    SftpValidator(SftpTransportFactory transportFactory) {
        this(
                new ObjectMapper(),
                transportFactory,
                new JsonSchemaValidator(),
                new CsvValidator(),
                new FixedWidthValidator());
    }

    SftpValidator(
            ObjectMapper objectMapper,
            SftpTransportFactory transportFactory,
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator
    ) {
        this.objectMapper = objectMapper;
        this.transportFactory = transportFactory;
        this.jsonValidator = jsonValidator;
        this.csvValidator = csvValidator;
        this.fixedWidthValidator = fixedWidthValidator;
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        return validate(input, schema, ValidationLanguage.DE);
    }

    @Override
    public ValidationResult validate(Path input, Path schema, ValidationLanguage language) throws ValidationException {
        if (input != null) {
            throw new ValidationException(
                    "SFTP validation does not accept --input; the remote location is in the profile");
        }

        Path profileFile = LocalFiles.requireRegularFile(schema, "SFTP profile");
        SftpProfile profile = SftpProfile.read(profileFile, objectMapper);
        Validator contentValidator = contentValidator(profile.contentFormat());

        Path temporaryDirectory = createTemporaryDirectory();
        try {
            return downloadAndValidate(profile, contentValidator, temporaryDirectory, language);
        } finally {
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    private ValidationResult downloadAndValidate(
            SftpProfile profile,
            Validator contentValidator,
            Path temporaryDirectory,
            ValidationLanguage language
    ) throws ValidationException {
        SftpTransferException lastTransferFailure = null;
        for (int attempt = 1; attempt <= profile.maxAttempts(); attempt++) {
            try (SftpTransport transport = transportFactory.open(profile)) {
                RemoteSelection selection = selectRemotePath(transport, profile);
                if (selection.matchCount() > 1) {
                    return directoryFinding(
                            profile.correlatedDelivery()
                                    ? "SFTP-Verzeichnis enthält mehrere vollständige oder unvollständige Lieferungen"
                                    : "SFTP-Verzeichnis enthält mehrere passende reguläre Dateien");
                }
                if (selection.files().isEmpty()) {
                    if (attempt == profile.maxAttempts()) {
                        return directoryFinding(
                                profile.lifecycleEnabled()
                                        ? "SFTP-Verzeichnis enthält keine passende oder beanspruchte Lieferung"
                                        : "SFTP-Verzeichnis enthält keine passende reguläre Datei");
                    }
                    sleep(profile.retryDelay(), "SFTP directory retry delay");
                    continue;
                }
                RemoteSelection claimedSelection = claimIfConfigured(transport, profile, selection);
                List<Finding> findings = new ArrayList<>();
                for (int index = 0; index < claimedSelection.files().size(); index++) {
                    SelectedRemoteFile selectedFile = claimedSelection.files().get(index);
                    Path temporaryFile = temporaryDirectory.resolve(
                            index + "-" + fileName(selectedFile.remotePath()));
                    SftpRemoteMetadata before = stableMetadata(
                            transport, profile, selectedFile.remotePath());
                    deleteTemporaryFile(temporaryFile);
                    transport.download(selectedFile.remotePath(), temporaryFile);
                    SftpRemoteMetadata after = transport.stat(selectedFile.remotePath());
                    if (!before.sameContentAs(after)) {
                        throw new SftpTransferException(
                                "Remote SFTP file changed while it was being downloaded");
                    }

                    if (index == 0 && profile.expectedSha256() != null) {
                        String actualSha256 = sha256(temporaryFile);
                        if (!profile.expectedSha256().equalsIgnoreCase(actualSha256)) {
                            findings.add(new Finding(
                                    "/remote/hash",
                                    "/sha256",
                                    "sha256",
                                    "SHA-256-Prüfsumme stimmt nicht mit dem SFTP-Profil überein"));
                            break;
                        }
                    }

                    ValidationResult contentResult = contentValidator instanceof CsvValidator csvValidator
                            ? csvValidator.validate(temporaryFile, profile.contentProfile(),
                                    fileName(selectedFile.remotePath()), language)
                            : contentValidator.validate(temporaryFile, profile.contentProfile(), language);
                    if (!contentResult.isValid()) {
                        findings.addAll(prefixContentFindings(
                                contentResult, selectedFile, profile.version() == 1));
                    }
                }

                if (!findings.isEmpty()) {
                    LifecycleOutcome outcome = quarantineIfConfigured(
                            transport, profile, claimedSelection);
                    return ValidationResult.invalid(findings, outcome.metadata());
                }

                LifecycleOutcome outcome = archiveIfConfigured(
                        transport, profile, claimedSelection);
                return outcome.metadata().isEmpty()
                        ? ValidationResult.valid()
                        : ValidationResult.valid(outcome.metadata());
            } catch (SftpTransferException e) {
                lastTransferFailure = e;
                if (attempt == profile.maxAttempts()) {
                    throw e;
                }
                sleep(profile.retryDelay(), "SFTP retry delay");
            }
        }

        throw lastTransferFailure == null
                ? new ValidationException("SFTP validation did not complete")
                : lastTransferFailure;
    }

    private static RemoteSelection selectRemotePath(
            SftpTransport transport,
            SftpProfile profile
    ) throws ValidationException {
        if (!profile.directorySelection()) {
            return new RemoteSelection(
                    List.of(new SelectedRemoteFile(
                            fileName(profile.remotePath()), profile.remotePath(), "primary", null, false)),
                    null,
                    1);
        }

        if (profile.lifecycleEnabled()) {
            RemoteSelection claimed = selectFromDirectory(
                    transport.list(profile.claimRemoteDirectory()),
                    profile,
                    profile.claimRemoteDirectory(),
                    true);
            if (!claimed.files().isEmpty() || claimed.matchCount() > 1) {
                return claimed;
            }
        }
        return selectFromDirectory(
                transport.list(profile.remoteDirectory()), profile, profile.remoteDirectory(), false);
    }

    private static RemoteSelection selectFromDirectory(
            List<SftpRemoteEntry> entries,
            SftpProfile profile,
            String directory,
            boolean claimed
    ) throws ValidationException {
        List<SftpRemoteEntry> primary = entries.stream()
                .filter(entry -> entry.metadata() != null && entry.metadata().regularFile())
                .filter(entry -> profile.fileNamePattern().matcher(entry.name()).matches())
                .sorted(Comparator.comparing(SftpRemoteEntry::name))
                .toList();
        if (!profile.correlatedDelivery()) {
            if (primary.isEmpty()) {
                return new RemoteSelection(List.of(), null, 0);
            }
            String deliveryId = deliveryId(profile, primary.getFirst().name());
            return new RemoteSelection(
                    List.of(toSelectedFile(primary.getFirst(), directory, "primary", deliveryId, claimed)),
                    deliveryId,
                    primary.size());
        }

        List<SftpRemoteEntry> companions = entries.stream()
                .filter(entry -> entry.metadata() != null && entry.metadata().regularFile())
                .filter(entry -> profile.companionFileNamePattern().matcher(entry.name()).matches())
                .sorted(Comparator.comparing(SftpRemoteEntry::name))
                .toList();
        Map<String, List<SftpRemoteEntry>> primaryById = groupByDeliveryId(primary, profile);
        Map<String, List<SftpRemoteEntry>> companionById = groupByDeliveryId(companions, profile);
        Map<String, DeliveryCandidate> candidates = new LinkedHashMap<>();
        primaryById.forEach((id, files) -> candidates
                .computeIfAbsent(id, ignored -> new DeliveryCandidate())
                .primary.addAll(files));
        companionById.forEach((id, files) -> candidates
                .computeIfAbsent(id, ignored -> new DeliveryCandidate())
                .companions.addAll(files));
        List<Map.Entry<String, DeliveryCandidate>> complete = candidates.entrySet().stream()
                .filter(entry -> entry.getValue().primary.size() == 1
                        && entry.getValue().companions.size() == 1)
                .toList();
        if (complete.size() != 1 || candidates.size() != 1) {
            return new RemoteSelection(List.of(), null, complete.size() + candidates.size());
        }
        String deliveryId = complete.getFirst().getKey();
        DeliveryCandidate candidate = complete.getFirst().getValue();
        return new RemoteSelection(
                List.of(
                        toSelectedFile(candidate.primary.getFirst(), directory, "primary", deliveryId, claimed),
                        toSelectedFile(candidate.companions.getFirst(), directory,
                                "companion", deliveryId, claimed)),
                deliveryId,
                1);
    }

    private static ValidationResult directoryFinding(String message) {
        return ValidationResult.invalid(List.of(new Finding(
                "/remote/files",
                "/filePattern",
                "fileCount",
                message)));
    }

    private static Map<String, List<SftpRemoteEntry>> groupByDeliveryId(
            List<SftpRemoteEntry> entries,
            SftpProfile profile
    ) {
        Map<String, List<SftpRemoteEntry>> grouped = new LinkedHashMap<>();
        for (SftpRemoteEntry entry : entries) {
            Matcher matcher = profile.deliveryIdPattern().matcher(entry.name());
            if (matcher.matches()) {
                grouped.computeIfAbsent(matcher.group("deliveryId"), ignored -> new ArrayList<>())
                        .add(entry);
            }
        }
        return grouped;
    }

    private static String deliveryId(SftpProfile profile, String name) {
        if (profile.deliveryIdPattern() == null) {
            return null;
        }
        Matcher matcher = profile.deliveryIdPattern().matcher(name);
        return matcher.matches() ? matcher.group("deliveryId") : null;
    }

    private static SelectedRemoteFile toSelectedFile(
            SftpRemoteEntry entry,
            String directory,
            String role,
            String deliveryId,
            boolean claimed
    ) {
        return new SelectedRemoteFile(
                entry.name(), joinRemoteDirectory(directory, entry.name()), role, deliveryId, claimed);
    }

    private static RemoteSelection claimIfConfigured(
            SftpTransport transport,
            SftpProfile profile,
            RemoteSelection selection
    ) throws ValidationException {
        if (!profile.lifecycleEnabled() || selection.files().stream().allMatch(SelectedRemoteFile::claimed)) {
            return selection;
        }
        List<SelectedRemoteFile> claimedFiles = new ArrayList<>();
        try {
            for (SelectedRemoteFile file : selection.files()) {
                String target = joinRemoteDirectory(profile.claimRemoteDirectory(), file.name());
                transport.move(file.remotePath(), target);
                claimedFiles.add(new SelectedRemoteFile(
                        file.name(), target, file.role(), file.deliveryId(), true));
            }
        } catch (SftpTransferException e) {
            for (SelectedRemoteFile file : claimedFiles) {
                try {
                    transport.move(file.remotePath(), joinRemoteDirectory(
                            profile.remoteDirectory(), file.name()));
                } catch (SftpTransferException ignored) {
                    // The original transfer exception remains the actionable failure.
                }
            }
            throw new SftpTransferException("Could not claim the SFTP delivery: "
                    + LocalFiles.safeMessage(e), e);
        }
        return new RemoteSelection(List.copyOf(claimedFiles), selection.deliveryId(), selection.matchCount());
    }

    private static SftpRemoteMetadata stableMetadata(
            SftpTransport transport,
            SftpProfile profile,
            String remotePath
    ) throws ValidationException {
        SftpRemoteMetadata first = requireRegularFile(
                transport.stat(remotePath), remotePath);
        for (int check = 1; check < profile.stabilityChecks(); check++) {
            sleep(profile.stabilityDelay(), "SFTP stability check delay");
            SftpRemoteMetadata current = requireRegularFile(
                    transport.stat(remotePath), remotePath);
            if (!first.sameContentAs(current)) {
                throw new SftpTransferException(
                        "Remote SFTP file is still changing; stable snapshot was not established");
            }
        }
        return first;
    }

    private static SftpRemoteMetadata requireRegularFile(
            SftpRemoteMetadata metadata,
            String remotePath
    ) throws ValidationException {
        if (metadata == null || !metadata.regularFile()) {
            throw new SftpTransferException("Remote SFTP path is not a regular file: " + remotePath);
        }
        return metadata;
    }

    private static String sha256(Path file) throws ValidationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ValidationException("Could not calculate downloaded SFTP file hash: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static LifecycleOutcome quarantineIfConfigured(
            SftpTransport transport,
            SftpProfile profile,
            RemoteSelection selection
    ) throws ValidationException {
        if (profile.quarantineRemoteDirectory() == null) {
            return new LifecycleOutcome(lifecycleMetadata(
                    selection, "CLAIMED_INVALID", List.of(), List.of()));
        }
        List<String> targets = new ArrayList<>();
        for (SelectedRemoteFile file : selection.files()) {
            String target = joinRemoteDirectory(
                    profile.quarantineRemoteDirectory(), file.name())
                    + "."
                    + QUARANTINE_TIMESTAMP.format(Instant.now())
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8)
                    + ".invalid";
            try {
                transport.move(file.remotePath(), target);
                targets.add(target);
            } catch (SftpTransferException e) {
                throw new ValidationException("Could not quarantine invalid SFTP file: "
                        + LocalFiles.safeMessage(e), e);
            }
        }
        return new LifecycleOutcome(lifecycleMetadata(
                selection, "QUARANTINED", List.of(), targets));
    }

    private static LifecycleOutcome archiveIfConfigured(
            SftpTransport transport,
            SftpProfile profile,
            RemoteSelection selection
    ) throws ValidationException {
        if (!profile.lifecycleEnabled()) {
            return new LifecycleOutcome(Map.of());
        }
        List<String> targets = new ArrayList<>();
        for (SelectedRemoteFile file : selection.files()) {
            String target = joinRemoteDirectory(
                    profile.archiveRemoteDirectory(), file.name())
                    + "."
                    + QUARANTINE_TIMESTAMP.format(Instant.now())
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8)
                    + ".validated";
            try {
                transport.move(file.remotePath(), target);
                targets.add(target);
            } catch (SftpTransferException e) {
                for (int moved = 0; moved < targets.size(); moved++) {
                    SelectedRemoteFile alreadyArchived = selection.files().get(moved);
                    try {
                        transport.move(targets.get(moved), alreadyArchived.remotePath());
                    } catch (SftpTransferException ignored) {
                        // The archive failure remains the actionable error.
                    }
                }
                throw new ValidationException("Could not archive valid SFTP file: "
                        + LocalFiles.safeMessage(e), e);
            }
        }
        return new LifecycleOutcome(lifecycleMetadata(
                selection, "ARCHIVED", targets, List.of()));
    }

    private static Map<String, Object> lifecycleMetadata(
            RemoteSelection selection,
            String lifecycle,
            List<String> archivedFiles,
            List<String> quarantinedFiles
    ) {
        if (selection.files().stream().noneMatch(SelectedRemoteFile::claimed)) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (selection.deliveryId() != null) {
            metadata.put("deliveryId", selection.deliveryId());
        }
        metadata.put("filesValidated", selection.files().size());
        metadata.put("lifecycle", lifecycle);
        if (!archivedFiles.isEmpty()) {
            metadata.put("archivedFiles", archivedFiles);
        }
        if (!quarantinedFiles.isEmpty()) {
            metadata.put("quarantinedFiles", quarantinedFiles);
        }
        return metadata;
    }

    private static List<Finding> prefixContentFindings(
            ValidationResult result,
            SelectedRemoteFile selectedFile,
            boolean legacyPath
    ) {
        List<Finding> findings = new ArrayList<>();
        String instancePrefix = legacyPath || selectedFile.role().equals("primary")
                ? "/content"
                : "/companion/" + selectedFile.name() + "/content";
        String schemaPrefix = legacyPath || selectedFile.role().equals("primary")
                ? "/content/profile"
                : "/companion/profile";
        for (Finding finding : result.findings()) {
            findings.add(new Finding(
                    prefix(finding.instancePath(), instancePrefix),
                    prefix(finding.schemaPath(), schemaPrefix),
                    finding.keyword(),
                    finding.message()));
        }
        return findings;
    }

    private Validator contentValidator(String contentFormat) throws ValidationException {
        return switch (contentFormat) {
            case "json" -> jsonValidator;
            case "csv" -> csvValidator;
            case "fixed-width" -> fixedWidthValidator;
            default -> throw new ValidationException("Unsupported SFTP content format: " + contentFormat);
        };
    }

    private static String prefix(String path, String prefix) {
        if (path == null || path.isEmpty()) {
            return prefix;
        }
        return prefix + (path.startsWith("/") ? path : "/" + path);
    }

    private static Path createTemporaryDirectory() throws ValidationException {
        try {
            return Files.createTempDirectory("zeus-sftp-");
        } catch (IOException e) {
            throw new ValidationException("Could not create temporary SFTP download directory: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static void deleteTemporaryFile(Path file) throws ValidationException {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new ValidationException("Could not prepare temporary SFTP download: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static void deleteTemporaryDirectory(Path directory) {
        try (var files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary data is best-effort cleanup and never changes the report result.
                }
            });
        } catch (IOException ignored) {
            // Temporary data is best-effort cleanup and never changes the report result.
        }
    }

    private static void sleep(java.time.Duration duration, String description)
            throws ValidationException {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException(description + " was interrupted", e);
        }
    }

    private static String fileName(String remotePath) throws ValidationException {
        int separator = remotePath.lastIndexOf('/');
        String name = separator >= 0 ? remotePath.substring(separator + 1) : remotePath;
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new ValidationException("SFTP remotePath must identify a file name");
        }
        return name;
    }

    private static String joinRemoteDirectory(String directory, String fileName) {
        return "/".equals(directory) ? "/" + fileName : directory + "/" + fileName;
    }

    private record RemoteSelection(
            List<SelectedRemoteFile> files,
            String deliveryId,
            int matchCount
    ) {
    }

    private record SelectedRemoteFile(
            String name,
            String remotePath,
            String role,
            String deliveryId,
            boolean claimed
    ) {
    }

    private static final class DeliveryCandidate {

        private final List<SftpRemoteEntry> primary = new ArrayList<>();
        private final List<SftpRemoteEntry> companions = new ArrayList<>();
    }

    private record LifecycleOutcome(Map<String, Object> metadata) {
    }
}
