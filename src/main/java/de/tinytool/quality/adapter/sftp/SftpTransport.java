/*
 * Author: tiny-tool.de
 * Copyright 2026 tiny-tool.de
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE or https://www.apache.org/licenses/LICENSE-2.0.
 */

package de.tinytool.quality.adapter.sftp;

import de.tinytool.quality.core.ValidationException;

import java.nio.file.Path;
import java.util.List;

interface SftpTransport extends AutoCloseable {

    SftpRemoteMetadata stat(String remotePath) throws SftpTransferException;

    List<SftpRemoteEntry> list(String remoteDirectory) throws SftpTransferException;

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

record SftpRemoteEntry(String name, SftpRemoteMetadata metadata) {
}

final class SftpTransferException extends ValidationException {

    SftpTransferException(String message) {
        super(message);
    }

    SftpTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
