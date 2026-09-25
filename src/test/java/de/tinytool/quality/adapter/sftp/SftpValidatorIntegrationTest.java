package de.tinytool.quality.adapter.sftp;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the production JSch transport against an embedded SFTP server.
 * No Docker daemon, public endpoint, or test account is required.
 */
class SftpValidatorIntegrationTest {

    private static final String USERNAME = "quality";
    private static final String REMOTE_FILE = "/inbox/delivery.json";

    private SshServer server;
    private Path remoteRoot;
    private Path profileDirectory;

    @BeforeEach
    void startSftpServer(@TempDir Path temporaryDirectory) throws Exception {
        remoteRoot = temporaryDirectory.resolve("remote-root");
        profileDirectory = temporaryDirectory.resolve("profile");
        Files.createDirectories(remoteRoot.resolve("inbox"));
        Files.createDirectories(remoteRoot.resolve("quarantine"));
        Files.createDirectories(profileDirectory);

        KeyPair serverKey = generateRsaKeyPair();
        KeyPair clientKey = generateRsaKeyPair();

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(serverKey));
        server.setPublickeyAuthenticator((username, publicKey, session) ->
                USERNAME.equals(username) && publicKey.equals(clientKey.getPublic()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(remoteRoot));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        server.start();

        writePrivateKey(profileDirectory.resolve("id_ed25519"), clientKey);
        writeKnownHosts(profileDirectory.resolve("known_hosts"), serverKey.getPublic(), server.getPort());
        Files.writeString(profileDirectory.resolve("delivery.schema.json"), """
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
        writeProfile();
    }

    @AfterEach
    void stopSftpServer() throws IOException {
        if (server != null) {
            server.stop(true);
        }
    }

    @Test
    void validatesAndQuarantinesThroughRealJschTransport() throws Exception {
        Files.writeString(remoteRoot.resolve("inbox/delivery.json"),
                "{\"deliveryId\":\"D-100\",\"quantity\":2}");

        ValidationResultAssertions.assertValid(new SftpValidator().validate(
                null, profileDirectory.resolve("sftp-profile.json")));
        assertThat(Files.exists(remoteRoot.resolve("inbox/delivery.json"))).isTrue();
        assertThat(listFiles(remoteRoot.resolve("quarantine"))).isEmpty();

        Files.writeString(remoteRoot.resolve("inbox/delivery.json"),
                "{\"deliveryId\":\"D-100\",\"quantity\":\"wrong\"}");

        var result = new SftpValidator().validate(
                null, profileDirectory.resolve("sftp-profile.json"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.instancePath()).isEqualTo("/content/quantity"));
        assertThat(Files.exists(remoteRoot.resolve("inbox/delivery.json"))).isFalse();
        assertThat(listFiles(remoteRoot.resolve("quarantine")))
                .anyMatch(path -> path.getFileName().toString().startsWith("delivery.json."));
    }

    private void writeProfile() throws IOException {
        Files.writeString(profileDirectory.resolve("sftp-profile.json"), """
                {
                  "format": "sftp",
                  "version": 1,
                  "host": "127.0.0.1",
                  "port": %d,
                  "username": "%s",
                  "remotePath": "%s",
                  "knownHosts": "known_hosts",
                  "privateKey": "id_ed25519",
                  "contentFormat": "json",
                  "contentProfile": "delivery.schema.json",
                  "quarantineRemoteDirectory": "/quarantine",
                  "stabilityChecks": 1,
                  "stabilityDelaySeconds": 0,
                  "maxAttempts": 1,
                  "retryDelaySeconds": 0,
                  "connectTimeoutSeconds": 5
                }
                """.formatted(server.getPort(), USERNAME, REMOTE_FILE));
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void writePrivateKey(Path path, KeyPair keyPair) throws Exception {
        try (OutputStream output = Files.newOutputStream(path)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(
                    keyPair, "test-key", null, output);
        }
    }

    private static void writeKnownHosts(Path path, java.security.PublicKey hostKey, int port)
            throws Exception {
        var publicKeyEntry = new ByteArrayOutputStream();
        OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(
                hostKey, "test-host", publicKeyEntry);
        Files.writeString(path, "[127.0.0.1]:" + port + " "
                + publicKeyEntry.toString(StandardCharsets.UTF_8)
                + System.lineSeparator());
    }

    private static List<Path> listFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.toList();
        }
    }

    private static final class ValidationResultAssertions {

        private static void assertValid(de.tinytool.quality.core.ValidationResult result) {
            assertThat(result.isValid()).isTrue();
            assertThat(result.findings()).isEmpty();
        }
    }
}
