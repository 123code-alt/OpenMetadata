/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.standalone.contracts;

public final class Fqn {
  private Fqn() {}

  public static String quote(String name) {
    if (name == null || name.isBlank() || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
      throw Problem.badRequest("Invalid name");
    }
    String raw = name;
    if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
      String body = name.substring(1, name.length() - 1);
      if (!body.replace("\"\"", "").contains("\"")) {
        raw = body.replace("\"\"", "\"");
      }
    }
    if (raw.isBlank()) {
      throw Problem.badRequest("Invalid name");
    }
    return raw.contains(".") || raw.contains("\"") ? "\"" + raw.replace("\"", "\"\"") + "\"" : raw;
  }

  public static String child(String parent, String name) {
    return parent + "." + quote(name);
  }

  public static boolean descendant(String value, String parent) {
    return value.startsWith(parent + ".");
  }
}
