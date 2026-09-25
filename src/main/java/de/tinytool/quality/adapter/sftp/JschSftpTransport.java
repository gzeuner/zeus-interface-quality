package de.tinytool.quality.adapter.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import de.tinytool.quality.adapter.support.LocalFiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSch-backed SFTP transport with strict known-host verification. */
final class JschSftpTransportFactory implements SftpTransportFactory {

    @Override
    public SftpTransport open(SftpProfile profile) throws SftpTransferException {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        try {
            jsch.setKnownHosts(profile.knownHosts().toString());
            String passphrase = readPassphrase(profile);
            if (passphrase == null) {
                jsch.addIdentity(profile.privateKey().toString());
            } else {
                jsch.addIdentity(
                        profile.privateKey().toString(),
                        passphrase.getBytes(StandardCharsets.UTF_8));
            }

            session = jsch.getSession(profile.username(), profile.host(), profile.port());
            session.setConfig("StrictHostKeyChecking", "yes");
            session.connect(Math.toIntExact(profile.connectTimeout().toMillis()));

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(Math.toIntExact(profile.connectTimeout().toMillis()));
            return new JschSftpTransport(session, channel);
        } catch (JSchException e) {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            throw new SftpTransferException(
                    "Could not open SFTP connection: " + LocalFiles.safeMessage(e), e);
        }
    }

    private static String readPassphrase(SftpProfile profile) throws SftpTransferException {
        if (profile.privateKeyPassphraseEnv() == null) {
            return null;
        }
        String passphrase = System.getenv(profile.privateKeyPassphraseEnv());
        if (passphrase == null) {
            throw new SftpTransferException("SFTP private-key passphrase environment variable is not set: "
                    + profile.privateKeyPassphraseEnv());
        }
        return passphrase;
    }
}

final class JschSftpTransport implements SftpTransport {

    private final Session session;
    private final ChannelSftp channel;

    JschSftpTransport(Session session, ChannelSftp channel) {
        this.session = session;
        this.channel = channel;
    }

    @Override
    public SftpRemoteMetadata stat(String remotePath) throws SftpTransferException {
        try {
            SftpATTRS attributes = channel.stat(remotePath);
            return new SftpRemoteMetadata(
                    attributes.getSize(),
                    attributes.getMTime(),
                    !attributes.isDir());
        } catch (SftpException e) {
            throw new SftpTransferException(
                    "Could not stat remote SFTP file: " + LocalFiles.safeMessage(e), e);
        }
    }

    @Override
    public void download(String remotePath, Path localPath) throws SftpTransferException {
        try (var output = Files.newOutputStream(localPath)) {
            channel.get(remotePath, output, null);
        } catch (SftpException e) {
            throw new SftpTransferException(
                    "Could not download remote SFTP file: " + LocalFiles.safeMessage(e), e);
        } catch (java.io.IOException e) {
            throw new SftpTransferException(
                    "Could not prepare local SFTP download: " + LocalFiles.safeMessage(e), e);
        }
    }

    @Override
    public void move(String remotePath, String targetPath) throws SftpTransferException {
        try {
            channel.rename(remotePath, targetPath);
        } catch (SftpException e) {
            throw new SftpTransferException(
                    "Could not move remote SFTP file to quarantine: "
                            + LocalFiles.safeMessage(e), e);
        }
    }

    @Override
    public void close() {
        channel.disconnect();
        session.disconnect();
    }
}
