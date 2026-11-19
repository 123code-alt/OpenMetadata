/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Persistence boundary. Mutations and cascading FQN changes share one transaction. */
public interface EntityStore {
  <T> T read(Function<Session, T> work);

  <T> T write(Function<Session, T> work);

  interface Session {
    Optional<ObjectNode> find(Kind kind, String id);

    Optional<ObjectNode> byName(Kind kind, String fqn);

    List<ObjectNode> all(Kind kind);

    List<ObjectNode> history(Kind kind, String id);

    void save(Kind kind, ObjectNode entity);
  }
}
