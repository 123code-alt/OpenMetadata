/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.adapters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.openmetadata.standalone.contracts.BlobStore;

public final class LocalBlobStore implements BlobStore {
  private final Path root;

  public LocalBlobStore(Path root) throws IOException {
    this.root = root.toAbsolutePath().normalize();
    Files.createDirectories(this.root);
  }

  @Override
  public void put(String key, byte[] bytes) throws IOException {
    Path destination = path(key);
    Path temporary = Files.createTempFile(root, ".upload-", ".tmp");
    try {
      Files.write(temporary, bytes);
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  @Override
  public InputStream open(String key) throws IOException {
    return Files.newInputStream(path(key));
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(path(key));
  }

  private Path path(String key) {
    String canonical = UUID.fromString(key).toString();
    if (!canonical.equals(key)) {
      throw new IllegalArgumentException("Invalid content ID");
    }
    return root.resolve(canonical);
  }
}
