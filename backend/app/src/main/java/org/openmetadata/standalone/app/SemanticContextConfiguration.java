/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openmetadata.standalone.contracts.Caller;

public final class SemanticContextConfiguration extends Configuration {
  @JsonProperty @NotBlank public String jdbcUrl;
  @JsonProperty @NotBlank public String databaseUser;
  @JsonProperty public String databasePassword = "";
  @JsonProperty public String blobDirectory = ".runtime/blobs";
  @JsonProperty public boolean development = false;
  @JsonProperty public List<Identity> identities = List.of();

  public static final class Identity {
    @JsonProperty public String token;
    @JsonProperty public String id;
    @JsonProperty public String name;
    @JsonProperty public boolean admin;
    @JsonProperty public Set<String> principalIds = Set.of();
    @JsonProperty public Set<String> scopes = Set.of();

    public Caller caller() {
      var ids = new HashSet<>(principalIds);
      ids.add("user:" + id);
      return new Caller(id, name, admin, ids, scopes);
    }
  }
}
