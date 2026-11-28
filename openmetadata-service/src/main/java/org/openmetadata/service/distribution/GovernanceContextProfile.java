package org.openmetadata.service.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openmetadata.schema.utils.JsonUtils;

public final class GovernanceContextProfile {
  private static final String RESOURCE_PREFIX = "org.openmetadata.service.resources.";
  private static final Set<String> SHARED_PACKAGES =
      Set.of(
          "activity",
          "apps",
          "attachments",
          "audit",
          "automations",
          "bots",
          "context",
          "csv",
          "docstore",
          "drive",
          "drives",
          "entityProfiles",
          "events",
          "feeds",
          "filters",
          "glossary",
          "governance",
          "knowledge",
          "learning",
          "limits",
          "mcp",
          "permissions",
          "policies",
          "rdf",
          "scim",
          "search",
          "settings",
          "system",
          "tags",
          "tasks",
          "teams",
          "types",
          "version");
  private static final Set<String> REPOSITORIES =
      Set.of(
          "AnnouncementRepository",
          "AppRepository",
          "AppMarketPlaceRepository",
          "BotRepository",
          "ClassificationRepository",
          "ContextFileRepository",
          "ContextFileContentRepository",
          "ContextMemoryRepository",
          "DirectoryRepository",
          "DocumentRepository",
          "DomainRepository",
          "DriveServiceRepository",
          "EntityProfileRepository",
          "EventSubscriptionRepository",
          "FeedRepository",
          "FileRepository",
          "FolderRepository",
          "GlossaryRepository",
          "GlossaryTermRepository",
          "IngestionPipelineRepository",
          "IntakeFormRepository",
          "KnowledgePageRepository",
          "LearningResourceRepository",
          "McpExecutionRepository",
          "McpServerRepository",
          "McpServiceRepository",
          "NotificationTemplateRepository",
          "PersonaRepository",
          "PolicyRepository",
          "RoleRepository",
          "SessionRepository",
          "SpreadsheetRepository",
          "TagRepository",
          "TaskRepository",
          "TaskFormSchemaRepository",
          "TeamRepository",
          "TestConnectionDefinitionRepository",
          "TokenRepository",
          "TypeRepository",
          "UserRepository",
          "UserPreferencesRepository",
          "WorkflowRepository",
          "WorkflowDefinitionRepository",
          "WorkflowInstanceRepository",
          "WorkflowInstanceStateRepository",
          "WorksheetRepository");
  private static final Set<String> INDEX_TYPES =
      Set.of(
          "directory",
          "file",
          "spreadsheet",
          "worksheet",
          "domain",
          "glossary",
          "glossaryTerm",
          "tag",
          "classification",
          "user",
          "team",
          "driveService",
          "mcpService",
          "mcpServer",
          "mcpExecution",
          "ingestionPipeline",
          "page",
          "folder",
          "contextFile",
          "contextMemory");
  private static final Set<String> WRITE_TYPES =
      Set.of(
          "glossary",
          "glossaryTerm",
          "classification",
          "tag",
          "page",
          "contextMemory",
          "contextFile",
          "folder");
  private static final Set<String> READ_TYPES =
      Set.of(
          "glossary",
          "glossaryTerm",
          "classification",
          "tag",
          "page",
          "contextMemory",
          "contextFile",
          "contextFileContent",
          "spreadsheet",
          "worksheet",
          "folder",
          "file",
          "directory",
          "driveService",
          "user",
          "team",
          "role",
          "policy",
          "persona",
          "type",
          "domain",
          "workflowDefinition");
  private static final Set<String> GENERIC_TOOLS =
      Set.of(
          "get_entity_details",
          "create_entity",
          "patch_entity",
          "describe_entity_type",
          "search_metadata",
          "semantic_search");

  private GovernanceContextProfile() {}

  public static boolean enabled() {
    String mode =
        System.getProperty(
            "openmetadata.distribution",
            System.getenv().getOrDefault("OPENMETADATA_DISTRIBUTION", "governance-context"));
    if ("full".equals(mode)) {
      return false;
    }
    if ("governance-context".equals(mode)) {
      return true;
    }
    throw new IllegalArgumentException("Unknown OpenMetadata distribution: " + mode);
  }

  public static boolean includesRepository(String className) {
    return !enabled() || REPOSITORIES.contains(className.substring(className.lastIndexOf('.') + 1));
  }

  public static boolean includesIndex(String entityType) {
    return !enabled() || INDEX_TYPES.contains(entityType);
  }

  public static boolean includesResource(String name) {
    if (!enabled()) {
      return true;
    }
    if (!name.startsWith(RESOURCE_PREFIX)) {
      return false;
    }
    String relative = name.substring(RESOURCE_PREFIX.length());
    int separator = relative.indexOf('.');
    if (separator < 0) {
      return "ChangeSummaryResource".equals(relative);
    }
    String area = relative.substring(0, separator);
    if ("domains".equals(area)) {
      return relative.equals("domains.DomainResource");
    }
    if ("ai".equals(area)) {
      return relative.equals("ai.AIContextResource") || relative.startsWith("ai.Mcp");
    }
    if ("services".equals(area)) {
      return relative.startsWith("services.mcp.")
          || relative.startsWith("services.drive.")
          || relative.startsWith("services.ingestionpipelines.");
    }
    return SHARED_PACKAGES.contains(area);
  }

  public static Map<String, Object> scopeToolArguments(String tool, Map<String, Object> arguments) {
    if (!enabled() || arguments == null || !GENERIC_TOOLS.contains(tool)) {
      return arguments;
    }
    boolean write = "create_entity".equals(tool) || "patch_entity".equals(tool);
    validateType(
        arguments.get("entityType"),
        write,
        tool.endsWith("search") || "search_metadata".equals(tool));
    for (Object item : listArgument(arguments.get("entities"))) {
      if (item instanceof Map<?, ?> entity) {
        validateType(entity.get("entityType"), false, false);
      } else if (item instanceof String reference && reference.contains(":")) {
        validateType(reference.substring(0, reference.indexOf(':')).trim(), false, false);
      }
    }
    if ("get_entity_details".equals(tool)
        && listArgument(arguments.get("include")).stream()
            .map(String::valueOf)
            .map(String::trim)
            .anyMatch(section -> "lineage".equals(section) || "quality".equals(section))) {
      throw new IllegalArgumentException(
          "Lineage and quality sections are unavailable in governance-context");
    }
    if ("semantic_search".equals(tool)) {
      Map<String, Object> scoped = new HashMap<>(arguments);
      Map<String, Object> filters = new HashMap<>();
      Object original = arguments.get("filters");
      if (original instanceof String json) {
        try {
          original = JsonUtils.getObjectMapper().readValue(json, Map.class);
        } catch (IOException e) {
          throw new IllegalArgumentException("Invalid semantic filters", e);
        }
      }
      if (original != null) {
        if (!(original instanceof Map<?, ?> originalMap)) {
          throw new IllegalArgumentException("Semantic filters must be an object");
        }
        originalMap.forEach((key, value) -> filters.put(String.valueOf(key), value));
      }
      Object types = filters.get("entityType");
      if (types instanceof List<?> requested && !requested.isEmpty()) {
        requested.forEach(type -> validateType(type, false, false));
      } else if (types instanceof String type) {
        validateType(type, false, false);
      } else {
        filters.put("entityType", READ_TYPES.stream().sorted().toList());
      }
      scoped.put("filters", filters);
      return scoped;
    }
    if (!"search_metadata".equals(tool)) {
      return arguments;
    }
    Map<String, Object> scoped = new HashMap<>(arguments);
    Object allowed = Map.of("terms", Map.of("entityType", READ_TYPES.stream().sorted().toList()));
    Object original = arguments.get("queryFilter");
    Object query = allowed;
    if (original != null) {
      try {
        JsonNode node =
            original instanceof String json
                ? JsonUtils.getObjectMapper().readTree(json)
                : JsonUtils.getObjectMapper().valueToTree(original);
        query =
            Map.of(
                "bool",
                Map.of("filter", List.of(allowed, node.has("query") ? node.get("query") : node)));
      } catch (IOException e) {
        throw new IllegalArgumentException("Invalid queryFilter", e);
      }
    }
    scoped.put("queryFilter", Map.of("query", query));
    return scoped;
  }

  private static List<?> listArgument(Object value) {
    if (value instanceof List<?> list) {
      return list;
    }
    return value instanceof String csv ? Arrays.asList(csv.split(",")) : List.of();
  }

  private static void validateType(Object value, boolean write, boolean search) {
    if (!(value instanceof String type)) {
      return;
    }
    if (search && ("all".equals(type) || "dataAsset".equals(type))) {
      return;
    }
    Set<String> allowed = write ? WRITE_TYPES : READ_TYPES;
    if (!allowed.contains(type)) {
      throw new IllegalArgumentException(
          "Entity type '"
              + type
              + "' is unavailable in governance-context. Supported: "
              + allowed.stream().sorted().toList());
    }
  }
}
