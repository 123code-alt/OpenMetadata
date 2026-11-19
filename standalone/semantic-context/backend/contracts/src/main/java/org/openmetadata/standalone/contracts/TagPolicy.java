/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface TagPolicy {
  void validate(EntityStore.Session transaction, ObjectNode entity);
}
