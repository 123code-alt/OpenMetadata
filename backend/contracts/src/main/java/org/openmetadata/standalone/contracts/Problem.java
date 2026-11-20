/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

public final class Problem extends RuntimeException {
  private final int status;

  public Problem(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static Problem badRequest(String message) {
    return new Problem(400, message);
  }

  public static Problem forbidden() {
    return new Problem(403, "Access denied");
  }

  public static Problem notFound() {
    return new Problem(404, "Entity not found");
  }

  public static Problem conflict(String message) {
    return new Problem(409, message);
  }
}
