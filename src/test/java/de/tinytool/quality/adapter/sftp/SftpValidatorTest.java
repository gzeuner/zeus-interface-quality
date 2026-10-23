/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.sftp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpValidatorTest {

    @Test
    void validatesStableRemoteJsonWithoutLeavingDownloadFiles(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}".getBytes(StandardCharsets.UTF_8);
        FakeTransport transport = new FakeTransport(content, metadata(content.length, 100));
        Path profile = writeProfile(directory, null, null, "json");

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(transport.downloadedRemotePath).isEqualTo("/inbox/delivery.json");
        assertThat(transport.movedTo).isEmpty();
        try (var files = Files.list(directory)) {
            assertThat(files.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("zeus-sftp-"))
                    .toList()).isEmpty();
        }
    }

    @Test
    void selectsOneMatchingStableFileFromRemoteDirectory(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                List.of(
                        new SftpRemoteEntry("README.txt", metadata(10, 100)),
                        new SftpRemoteEntry("delivery-20260928.json", fileMetadata),
                        new SftpRemoteEntry("archive", new SftpRemoteMetadata(0, 100, false))));
        Path profile = writeDirectoryProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(transport.downloadedRemotePath).isEqualTo("/inbox/delivery-20260928.json");
    }

    @Test
    void reportsMissingDirectoryDeliveryAfterBoundedPolling(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}"
                .getBytes(StandardCharsets.UTF_8);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(metadata(content.length, 100)),
                List.of());
        Path profile = writeDirectoryProfile(directory);
        Files.writeString(profile, Files.readString(profile).replace(
                "\"maxAttempts\": 1", "\"maxAttempts\": 2"));

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.instancePath()).isEqualTo("/remote/files");
            assertThat(finding.keyword()).isEqualTo("fileCount");
        });
        assertThat(transport.downloadedRemotePath).isNull();
    }

    @Test
    void rejectsAmbiguousDirectoryDeliveryWithoutDownloading(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                List.of(
                        new SftpRemoteEntry("delivery-20260928.json", fileMetadata),
                        new SftpRemoteEntry("delivery-20260929.json", fileMetadata)));
        Path profile = writeDirectoryProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.keyword()).isEqualTo("fileCount"));
        assertThat(transport.downloadedRemotePath).isNull();
    }

    @Test
    void prefixesContentFindingsAndMovesInvalidFileToConfiguredQuarantine(@TempDir Path directory)
            throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":\"wrong\"}"
                .getBytes(StandardCharsets.UTF_8);
        FakeTransport transport = new FakeTransport(content, metadata(content.length, 100));
        Path profile = writeProfile(directory, null, ",\"quarantineRemoteDirectory\":\"/quarantine\"", "json");

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.instancePath()).startsWith("/content/"));
        assertThat(transport.movedTo).hasSize(1);
        assertThat(transport.movedTo.getFirst()).startsWith("/quarantine/delivery.json.");
        assertThat(transport.movedTo.getFirst()).endsWith(".invalid");
    }

    @Test
    void reportsHashMismatchAndQuarantinesBeforeContentValidation(@TempDir Path directory)
            throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}".getBytes(StandardCharsets.UTF_8);
        FakeTransport transport = new FakeTransport(content, metadata(content.length, 100));
        Path profile = writeProfile(
                directory,
                ",\"sha256\":\"0000000000000000000000000000000000000000000000000000000000000000\"",
                ",\"quarantineRemoteDirectory\":\"/quarantine\"",
                "json");

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.instancePath()).isEqualTo("/remote/hash");
            assertThat(finding.keyword()).isEqualTo("sha256");
        });
        assertThat(transport.movedTo).hasSize(1);
    }

    @Test
    void retriesWhenRemoteFileChangesDuringStabilityCheck(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}".getBytes(StandardCharsets.UTF_8);
        FakeTransport unstable = new FakeTransport(
                content,
                List.of(metadata(content.length, 100), metadata(content.length + 1, 101)));
        FakeTransport stable = new FakeTransport(content, metadata(content.length, 200));
        AtomicInteger opens = new AtomicInteger();
        Path profile = writeProfile(
                directory,
                ",\"stabilityChecks\":2,\"stabilityDelaySeconds\":0,\"maxAttempts\":2,\"retryDelaySeconds\":0",
                null,
                "json");

        var result = new SftpValidator(ignored -> opens.getAndIncrement() == 0 ? unstable : stable)
                .validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(opens).hasValue(2);
    }

    @Test
    void retriesTransientConnectionFailure(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}".getBytes(StandardCharsets.UTF_8);
        FakeTransport stable = new FakeTransport(content, metadata(content.length, 100));
        AtomicInteger opens = new AtomicInteger();
        Path profile = writeProfile(
                directory,
                ",\"maxAttempts\":2,\"retryDelaySeconds\":0",
                null,
                "json");

        var result = new SftpValidator(ignored -> {
            if (opens.getAndIncrement() == 0) {
                throw new SftpTransferException("temporary connection failure");
            }
            return stable;
        }).validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(opens).hasValue(2);
    }

    @Test
    void rejectsUnsafeRemotePath(@TempDir Path directory) throws Exception {
        Path profile = writeProfile(directory, null, null, "json")
                .toAbsolutePath();
        String text = Files.readString(profile).replace("/inbox/delivery.json", "/inbox/../delivery.json");
        Files.writeString(profile, text);

        assertThatThrownBy(() -> new SftpValidator(ignored -> new FakeTransport(
                new byte[0], metadata(0, 0))).validate(null, profile))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("remotePath");
    }

    @Test
    void correlatesDeliveryFilesClaimsBothAndArchivesThem(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-100\",\"quantity\":2}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                Map.of(
                        "/inbox", List.of(
                                new SftpRemoteEntry("delivery-D-100.json", fileMetadata),
                                new SftpRemoteEntry("delivery-D-100.meta.json", fileMetadata)),
                        "/claim", List.of(),
                        "/archive", List.of()));
        Path profile = writeLifecycleProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(result.metadata())
                .containsEntry("deliveryId", "D-100")
                .containsEntry("filesValidated", 2)
                .containsEntry("lifecycle", "ARCHIVED");
        assertThat(transport.downloadedRemotePaths)
                .containsExactlyInAnyOrder("/claim/delivery-D-100.json", "/claim/delivery-D-100.meta.json");
        assertThat(transport.movedTo)
                .anyMatch(path -> path.equals("/claim/delivery-D-100.json"))
                .anyMatch(path -> path.equals("/claim/delivery-D-100.meta.json"))
                .anyMatch(path -> path.startsWith("/archive/delivery-D-100.json."))
                .anyMatch(path -> path.startsWith("/archive/delivery-D-100.meta.json."));
    }

    @Test
    void resumesAlreadyClaimedCorrelatedDeliveryWithoutReclaiming(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-101\",\"quantity\":3}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                Map.of(
                        "/inbox", List.of(),
                        "/claim", List.of(
                                new SftpRemoteEntry("delivery-D-101.json", fileMetadata),
                                new SftpRemoteEntry("delivery-D-101.meta.json", fileMetadata)),
                        "/archive", List.of()));
        Path profile = writeLifecycleProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(transport.movedTo)
                .noneMatch(path -> path.equals("/claim/delivery-D-101.json"))
                .noneMatch(path -> path.equals("/claim/delivery-D-101.meta.json"))
                .anyMatch(path -> path.startsWith("/archive/delivery-D-101.json."))
                .anyMatch(path -> path.startsWith("/archive/delivery-D-101.meta.json."));
    }

    @Test
    void completesInterruptedPartialClaimFromMatchingIncomingCompanion(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-103\",\"quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                Map.of(
                        "/inbox", List.of(
                                new SftpRemoteEntry("delivery-D-103.meta.json", fileMetadata)),
                        "/claim", List.of(
                                new SftpRemoteEntry("delivery-D-103.json", fileMetadata)),
                        "/archive", List.of()));
        Path profile = writeLifecycleProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isTrue();
        assertThat(result.metadata())
                .containsEntry("deliveryId", "D-103")
                .containsEntry("filesValidated", 2)
                .containsEntry("lifecycle", "ARCHIVED");
        assertThat(transport.downloadedRemotePaths)
                .containsExactlyInAnyOrder("/claim/delivery-D-103.json", "/claim/delivery-D-103.meta.json");
        assertThat(transport.movedTo)
                .contains("/claim/delivery-D-103.meta.json")
                .anyMatch(path -> path.startsWith("/archive/delivery-D-103.json."))
                .anyMatch(path -> path.startsWith("/archive/delivery-D-103.meta.json."));
    }

    @Test
    void doesNotCombinePartialClaimWithAnotherDelivery(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-103\",\"quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                Map.of(
                        "/inbox", List.of(
                                new SftpRemoteEntry("delivery-D-104.json", fileMetadata),
                                new SftpRemoteEntry("delivery-D-104.meta.json", fileMetadata)),
                        "/claim", List.of(
                                new SftpRemoteEntry("delivery-D-103.json", fileMetadata)),
                        "/archive", List.of()));
        Path profile = writeLifecycleProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.keyword()).isEqualTo("fileCount"));
        assertThat(transport.downloadedRemotePath).isNull();
        assertThat(transport.movedTo).isEmpty();
    }

    @Test
    void rejectsIncompleteCorrelatedDeliveryWithoutDownloading(@TempDir Path directory) throws Exception {
        byte[] content = "{\"deliveryId\":\"D-102\",\"quantity\":4}"
                .getBytes(StandardCharsets.UTF_8);
        SftpRemoteMetadata fileMetadata = metadata(content.length, 100);
        FakeTransport transport = new FakeTransport(
                content,
                List.of(fileMetadata),
                Map.of(
                        "/inbox", List.of(new SftpRemoteEntry("delivery-D-102.json", fileMetadata)),
                        "/claim", List.of(),
                        "/archive", List.of()));
        Path profile = writeLifecycleProfile(directory);

        var result = new SftpValidator(ignored -> transport).validate(null, profile);

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.keyword()).isEqualTo("fileCount"));
        assertThat(transport.downloadedRemotePath).isNull();
        assertThat(transport.movedTo).isEmpty();
    }

    private static SftpRemoteMetadata metadata(long size, long modified) {
        return new SftpRemoteMetadata(size, modified, true);
    }

    private static Path writeProfile(
            Path directory,
            String profileOptions,
            String quarantineOption,
            String contentFormat
    ) throws IOException {
        writeProfileSupportFiles(directory);
        return Files.writeString(directory.resolve("sftp-profile.json"), """
                {
                  "format": "sftp",
                  "version": 1,
                  "host": "sftp.example.test",
                  "username": "quality",
                  "remotePath": "/inbox/delivery.json",
                  "knownHosts": "known_hosts",
                  "privateKey": "id_ed25519",
                  "contentFormat": "%s",
                  "contentProfile": "delivery.schema.json"%s%s
                }
                """.formatted(
                contentFormat,
                profileOptions == null ? "" : profileOptions,
                quarantineOption == null ? "" : quarantineOption));
    }

    private static Path writeDirectoryProfile(Path directory) throws IOException {
        writeProfileSupportFiles(directory);
        return Files.writeString(directory.resolve("sftp-profile.json"), """
                {
                  "format": "sftp",
                  "version": 2,
                  "host": "sftp.example.test",
                  "username": "quality",
                  "remoteDirectory": "/inbox",
                  "filePattern": "^delivery-[0-9]{8}\\\\.json$",
                  "knownHosts": "known_hosts",
                  "privateKey": "id_ed25519",
                  "contentFormat": "json",
                  "contentProfile": "delivery.schema.json",
                  "stabilityChecks": 1,
                  "stabilityDelaySeconds": 0,
                  "maxAttempts": 1,
                  "retryDelaySeconds": 0
                }
                """);
    }

    private static Path writeLifecycleProfile(Path directory) throws IOException {
        writeProfileSupportFiles(directory);
        return Files.writeString(directory.resolve("sftp-profile-v3.json"), """
                {
                  "format": "sftp",
                  "version": 3,
                  "host": "sftp.example.test",
                  "username": "quality",
                  "remoteDirectory": "/inbox",
                  "filePattern": "^delivery-D-[0-9]+\\\\.json$",
                  "companionPattern": "^delivery-D-[0-9]+\\\\.meta\\\\.json$",
                  "deliveryIdPattern": "^delivery-(?<deliveryId>D-[0-9]+)(?:\\\\.meta)?\\\\.json$",
                  "claimRemoteDirectory": "/claim",
                  "archiveRemoteDirectory": "/archive",
                  "knownHosts": "known_hosts",
                  "privateKey": "id_ed25519",
                  "contentFormat": "json",
                  "contentProfile": "delivery.schema.json",
                  "stabilityChecks": 1,
                  "stabilityDelaySeconds": 0,
                  "maxAttempts": 1,
                  "retryDelaySeconds": 0
                }
                """);
    }

    private static void writeProfileSupportFiles(Path directory) throws IOException {
        Files.writeString(directory.resolve("known_hosts"), "example host key");
        Files.writeString(directory.resolve("id_ed25519"), "test key");
        Files.writeString(directory.resolve("delivery.schema.json"), """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["deliveryId", "quantity"],
                  "properties": {
                    "deliveryId": {"type": "string"},
                    "quantity": {"type": "integer", "minimum": 1}
                  },
                  "additionalProperties": false
                }
                """);
    }

    private static final class FakeTransport implements SftpTransport {

        private final byte[] content;
        private final List<SftpRemoteMetadata> metadata;
        private final List<SftpRemoteEntry> entries;
        private final Map<String, List<SftpRemoteEntry>> entriesByDirectory;
        private int statCalls;
        private String downloadedRemotePath;
        private final List<String> downloadedRemotePaths = new ArrayList<>();
        private final List<String> movedTo = new ArrayList<>();

        private FakeTransport(byte[] content, SftpRemoteMetadata metadata) {
            this(content, List.of(metadata),
                    List.of(new SftpRemoteEntry("delivery.json", metadata)));
        }

        private FakeTransport(byte[] content, List<SftpRemoteMetadata> metadata) {
            this(content, metadata,
                    List.of(new SftpRemoteEntry("delivery.json", metadata.getFirst())));
        }

        private FakeTransport(
                byte[] content,
                List<SftpRemoteMetadata> metadata,
                List<SftpRemoteEntry> entries
        ) {
            this(content, metadata, Map.of("/inbox", entries));
        }

        private FakeTransport(
                byte[] content,
                List<SftpRemoteMetadata> metadata,
                Map<String, List<SftpRemoteEntry>> entriesByDirectory
        ) {
            this.content = content;
            this.metadata = metadata;
            this.entriesByDirectory = entriesByDirectory;
            this.entries = entriesByDirectory.values().stream().findFirst().orElse(List.of());
        }

        @Override
        public SftpRemoteMetadata stat(String remotePath) {
            return metadata.get(Math.min(statCalls++, metadata.size() - 1));
        }

        @Override
        public List<SftpRemoteEntry> list(String remoteDirectory) {
            return entriesByDirectory.getOrDefault(remoteDirectory, List.of());
        }

        @Override
        public void download(String remotePath, Path localPath) throws SftpTransferException {
            downloadedRemotePath = remotePath;
            downloadedRemotePaths.add(remotePath);
            try {
                Files.write(localPath, content);
            } catch (IOException e) {
                throw new SftpTransferException("fake download failed", e);
            }
        }

        @Override
        public void move(String remotePath, String targetPath) {
            movedTo.add(targetPath);
        }

        @Override
        public void close() {
        }
    }
}
