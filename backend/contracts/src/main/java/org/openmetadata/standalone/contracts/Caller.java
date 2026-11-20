/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import java.util.Set;

public record Caller(
    String id, String name, boolean admin, Set<String> principalIds, Set<String> scopes)
    implements Principal {
  public Caller {
    principalIds = Set.copyOf(principalIds);
    scopes = Set.copyOf(scopes);
  }

  @Override
  public String getName() {
    return name;
  }

  public void requireWrite(Kind kind) {
    String scope = kind == Kind.GLOSSARY || kind == Kind.TERM ? "glossary:write" : "context:write";
    if (!admin && !scopes.contains(scope)) {
      throw Problem.forbidden();
    }
  }

  public boolean owns(JsonNode entity) {
    for (JsonNode owner : entity.path("owners")) {
      if ("user".equals(owner.path("type").asText()) && id.equals(owner.path("id").asText())) {
        return true;
      }
    }
    return false;
  }
}
