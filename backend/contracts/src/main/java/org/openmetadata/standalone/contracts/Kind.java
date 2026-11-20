/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import java.util.Arrays;

public enum Kind {
  GLOSSARY("glossary", "glossaries", "api/data/createGlossary.json"),
  TERM("glossaryTerm", "glossaryTerms", "api/data/createGlossaryTerm.json"),
  PAGE("page", "pages", "api/data/createPage.json"),
  MEMORY("contextMemory", "contextCenter/memories", "api/context/createContextMemory.json"),
  FILE("contextFile", "contextCenter/drive/files", null);

  public final String entityType;
  public final String path;
  public final String createSchema;

  Kind(String entityType, String path, String createSchema) {
    this.entityType = entityType;
    this.path = path;
    this.createSchema = createSchema;
  }

  public static Kind fromType(String value) {
    return Arrays.stream(values())
        .filter(k -> k.entityType.equals(value))
        .findFirst()
        .orElseThrow(() -> Problem.badRequest("Unsupported entity type: " + value));
  }
}
