package org.openmetadata.service.distribution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmetadata.schema.utils.JsonUtils;

class GovernanceContextProfileTest {
  private String previous;

  @BeforeEach
  void setProfile() {
    previous = System.getProperty("openmetadata.distribution");
    System.setProperty("openmetadata.distribution", "governance-context");
  }

  @AfterEach
  void restoreProfile() {
    if (previous == null) {
      System.clearProperty("openmetadata.distribution");
    } else {
      System.setProperty("openmetadata.distribution", previous);
    }
  }

  @Test
  void retainKnowledgeAndMcpResourcesButExcludeAssetAndFrameworkApis() {
    assertTrue(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.glossary.GlossaryResource"));
    assertTrue(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.drive.ContextFileResource"));
    assertTrue(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.ai.McpServerResource"));
    assertFalse(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.databases.TableResource"));
    assertFalse(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.ai.AIGovernanceFrameworkResource"));
    assertFalse(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.domains.DataProductResource"));
    assertFalse(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.searchindex.SearchIndexResource"));
    assertTrue(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.domains.DomainResource"));
  }

  @Test
  void retainOnlyRequiredRepositoriesAndIndexes() {
    assertTrue(
        GovernanceContextProfile.includesRepository(
            "org.openmetadata.service.jdbi3.GlossaryTermRepository"));
    assertTrue(
        GovernanceContextProfile.includesRepository(
            "org.openmetadata.service.jdbi3.ContextFileRepository"));
    assertTrue(
        GovernanceContextProfile.includesRepository(
            "org.openmetadata.service.jdbi3.WorkflowInstanceRepository"));
    assertFalse(
        GovernanceContextProfile.includesRepository(
            "org.openmetadata.service.jdbi3.TableRepository"));
    assertFalse(
        GovernanceContextProfile.includesRepository(
            "org.openmetadata.service.jdbi3.TestCaseRepository"));
    assertTrue(GovernanceContextProfile.includesIndex("folder"));
    assertTrue(GovernanceContextProfile.includesIndex("glossaryTerm"));
    assertFalse(GovernanceContextProfile.includesIndex("table"));
    assertFalse(GovernanceContextProfile.includesIndex("testCase"));
  }

  @Test
  void rejectUnsupportedGenericWritesAndMixedBulkReads() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GovernanceContextProfile.scopeToolArguments(
                "create_entity", Map.of("entityType", "table")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GovernanceContextProfile.scopeToolArguments(
                "get_entity_details",
                Map.of("entities", List.of("page:Handbook", "table:removed.asset"))));
  }

  @Test
  void rejectLegacyCsvBatchAndInlineSections() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GovernanceContextProfile.scopeToolArguments(
                "get_entity_details", Map.of("entities", "page:Handbook,table:removed.asset")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GovernanceContextProfile.scopeToolArguments(
                "get_entity_details", Map.of("entityType", "page", "include", "content,quality")));
  }

  @Test
  void rejectRemovedInlineSections() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GovernanceContextProfile.scopeToolArguments(
                "get_entity_details", Map.of("entityType", "page", "include", List.of("lineage"))));
  }

  @Test
  void combineDistributionAndCallerSearchFiltersWithoutChangingInput() {
    Map<String, Object> input =
        Map.of("queryFilter", "{\"query\":{\"term\":{\"name\":\"Revenue\"}}}");
    String result =
        JsonUtils.pojoToJson(GovernanceContextProfile.scopeToolArguments("search_metadata", input));
    assertTrue(result.contains("contextMemory"));
    assertTrue(result.contains("Revenue"));
    assertTrue(result.contains("filter"));
    assertFalse(input.containsKey("entityType"));
  }

  @Test
  void scopeSemanticFiltersAndRejectRemovedEntityTypes() {
    Map<String, Object> input =
        Map.of("query", "revenue", "filters", Map.of("tags", List.of("PII")));
    String scoped =
        JsonUtils.pojoToJson(GovernanceContextProfile.scopeToolArguments("semantic_search", input));
    assertTrue(scoped.contains("contextMemory"));
    assertTrue(scoped.contains("PII"));
    assertFalse(JsonUtils.pojoToJson(input).contains("entityType"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GovernanceContextProfile.scopeToolArguments(
                "semantic_search",
                Map.of(
                    "query",
                    "revenue",
                    "filters",
                    Map.of("entityType", List.of("page", "table")))));
  }

  @Test
  void fullModeRemainsAvailableForUpstreamCompatibility() {
    System.setProperty("openmetadata.distribution", "full");
    assertTrue(
        GovernanceContextProfile.includesResource(
            "org.openmetadata.service.resources.databases.TableResource"));
    GovernanceContextProfile.scopeToolArguments("create_entity", Map.of("entityType", "table"));
  }
}
