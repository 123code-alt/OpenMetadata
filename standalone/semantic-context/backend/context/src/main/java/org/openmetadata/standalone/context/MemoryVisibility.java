/*
 *  Copyright 2024 Collate
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

package org.openmetadata.standalone.context;

import com.fasterxml.jackson.databind.JsonNode;
import org.openmetadata.standalone.contracts.Caller;

public final class MemoryVisibility {
  private MemoryVisibility() {}

  public static boolean canRead(JsonNode memory, Caller caller) {
    if (caller.admin() || caller.owns(memory)) {
      return true;
    }
    String visibility = memory.path("shareConfig").path("visibility").asText("Private");
    if ("Entity".equals(visibility)) {
      return true;
    }
    return "Shared".equals(visibility) && sharedRole(memory, caller) != null;
  }

  public static boolean canEdit(JsonNode memory, Caller caller) {
    return caller.admin() || caller.owns(memory) || "Editor".equals(sharedRole(memory, caller));
  }

  private static String sharedRole(JsonNode memory, Caller caller) {
    if (!"Shared".equals(memory.path("shareConfig").path("visibility").asText())) {
      return null;
    }
    for (JsonNode share : memory.path("shareConfig").path("sharedWith")) {
      JsonNode principal = share.path("principal");
      String type = principal.path("type").asText();
      if (("user".equals(type) || "team".equals(type) || "domain".equals(type))
          && caller.principalIds().contains(type + ":" + principal.path("id").asText())) {
        return share.path("role").asText("Viewer");
      }
    }
    return null;
  }
}
