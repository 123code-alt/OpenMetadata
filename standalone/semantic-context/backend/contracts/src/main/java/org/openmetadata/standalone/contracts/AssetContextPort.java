/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/** Implement with the host platform's asset API, including its authorization checks. */
public interface AssetContextPort {
  Optional<ObjectNode> resolve(String entityType, String id, Caller caller);
}
