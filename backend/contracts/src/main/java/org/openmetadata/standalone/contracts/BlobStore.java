/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import java.io.IOException;
import java.io.InputStream;

/** Object storage boundary. Blob keys are server-issued content UUIDs. */
public interface BlobStore {
  void put(String key, byte[] bytes) throws IOException;

  InputStream open(String key) throws IOException;

  void delete(String key) throws IOException;
}
