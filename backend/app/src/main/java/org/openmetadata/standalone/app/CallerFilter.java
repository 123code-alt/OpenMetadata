/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.app;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Set;
import org.openmetadata.standalone.contracts.Caller;
import org.openmetadata.standalone.contracts.Documents;

@Priority(Priorities.AUTHENTICATION)
public final class CallerFilter implements ContainerRequestFilter {
  public static final String CALLER = "semantic-context.caller";
  public static final Caller DEVELOPMENT =
      new Caller(
          "00000000-0000-0000-0000-000000000001",
          "developer",
          true,
          Set.of("user:00000000-0000-0000-0000-000000000001"),
          Set.of("glossary:write", "context:write"));
  private final SemanticContextConfiguration configuration;

  public CallerFilter(SemanticContextConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void filter(ContainerRequestContext context) {
    String authorization = context.getHeaderString("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      byte[] provided = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
      for (var identity : configuration.identities) {
        if (identity.token != null
            && !identity.token.isBlank()
            && MessageDigest.isEqual(provided, identity.token.getBytes(StandardCharsets.UTF_8))) {
          authenticate(context, identity.caller());
          return;
        }
      }
    } else if (authorization == null
        && configuration.development
        && context.getProperty(CALLER) == DEVELOPMENT) {
      authenticate(context, DEVELOPMENT);
      return;
    }
    context.abortWith(
        Response.status(401)
            .header("WWW-Authenticate", "Bearer")
            .entity(
                Documents.JSON
                    .createObjectNode()
                    .put("message", "A configured API token is required"))
            .build());
  }

  private static void authenticate(ContainerRequestContext context, Caller caller) {
    boolean secure = context.getSecurityContext().isSecure();
    context.setSecurityContext(
        new SecurityContext() {
          @Override
          public Principal getUserPrincipal() {
            return caller;
          }

          @Override
          public boolean isUserInRole(String role) {
            return caller.admin() || caller.scopes().contains(role);
          }

          @Override
          public boolean isSecure() {
            return secure;
          }

          @Override
          public String getAuthenticationScheme() {
            return "Bearer";
          }
        });
  }
}
