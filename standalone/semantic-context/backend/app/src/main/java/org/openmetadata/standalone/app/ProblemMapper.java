/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.app;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.Problem;

public final class ProblemMapper implements ExceptionMapper<Problem> {
  @Override
  public Response toResponse(Problem problem) {
    return Response.status(problem.status())
        .entity(
            Documents.JSON
                .createObjectNode()
                .put("code", problem.status())
                .put("message", problem.getMessage()))
        .build();
  }
}
