package de.tinytool.quality.adapter.sftp;

import de.tinytool.quality.core.ValidationException;

import java.nio.file.Path;

interface SftpTransport extends AutoCloseable {

    SftpRemoteMetadata stat(String remotePath) throws SftpTransferException;

    void download(String remotePath, Path localPath) throws SftpTransferException;

    void move(String remotePath, String targetPath) throws SftpTransferException;

    @Override
    void close();
}

@FunctionalInterface
interface SftpTransportFactory {

    SftpTransport open(SftpProfile profile) throws SftpTransferException;
}

record SftpRemoteMetadata(long size, long modifiedEpochSeconds, boolean regularFile) {

    boolean sameContentAs(SftpRemoteMetadata other) {
        return other != null
                && regularFile
                && other.regularFile
                && size == other.size
                && modifiedEpochSeconds == other.modifiedEpochSeconds;
    }
}

final class SftpTransferException extends ValidationException {

    SftpTransferException(String message) {
        super(message);
    }

    SftpTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
