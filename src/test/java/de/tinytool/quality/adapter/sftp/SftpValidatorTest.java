package de.tinytool.quality.adapter.sftp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private static SftpRemoteMetadata metadata(long size, long modified) {
        return new SftpRemoteMetadata(size, modified, true);
    }

    private static Path writeProfile(
            Path directory,
            String profileOptions,
            String quarantineOption,
            String contentFormat
    ) throws IOException {
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

    private static final class FakeTransport implements SftpTransport {

        private final byte[] content;
        private final List<SftpRemoteMetadata> metadata;
        private int statCalls;
        private String downloadedRemotePath;
        private final List<String> movedTo = new ArrayList<>();

        private FakeTransport(byte[] content, SftpRemoteMetadata metadata) {
            this(content, List.of(metadata));
        }

        private FakeTransport(byte[] content, List<SftpRemoteMetadata> metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        @Override
        public SftpRemoteMetadata stat(String remotePath) {
            return metadata.get(Math.min(statCalls++, metadata.size() - 1));
        }

        @Override
        public void download(String remotePath, Path localPath) throws SftpTransferException {
            downloadedRemotePath = remotePath;
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
