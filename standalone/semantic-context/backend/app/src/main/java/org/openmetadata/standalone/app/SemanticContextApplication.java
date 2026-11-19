/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.app;

import com.codahale.metrics.health.HealthCheck;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.jdbi.v3.core.Jdbi;
import org.openmetadata.standalone.adapters.JdbiEntityStore;
import org.openmetadata.standalone.adapters.LocalBlobStore;
import org.openmetadata.standalone.context.ContextAssembler;
import org.openmetadata.standalone.context.ContextService;
import org.openmetadata.standalone.context.FileService;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.SchemaCatalog;
import org.openmetadata.standalone.glossary.GlossaryService;

public final class SemanticContextApplication extends Application<SemanticContextConfiguration> {
  public static void main(String[] args) throws Exception {
    new SemanticContextApplication().run(args);
  }

  @Override
  public void initialize(Bootstrap<SemanticContextConfiguration> bootstrap) {
    bootstrap.setConfigurationSourceProvider(
        new SubstitutingSourceProvider(
            bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html", "semantic-context-ui"));
  }

  @Override
  public void run(SemanticContextConfiguration config, Environment environment) throws Exception {
    if (!config.development
        && config.identities.stream().noneMatch(i -> i.token != null && !i.token.isBlank())) {
      throw new IllegalArgumentException(
          "Configure API identities or explicitly enable loopback development mode");
    }
    HikariConfig poolConfig = new HikariConfig();
    poolConfig.setJdbcUrl(config.jdbcUrl);
    poolConfig.setUsername(config.databaseUser);
    poolConfig.setPassword(config.databasePassword);
    poolConfig.setMaximumPoolSize(8);
    poolConfig.setMinimumIdle(1);
    poolConfig.setConnectionTimeout(5000);
    var pool = new HikariDataSource(poolConfig);
    environment
        .lifecycle()
        .manage(
            new Managed() {
              @Override
              public void stop() {
                pool.close();
              }
            });
    var store = new JdbiEntityStore(Jdbi.create(pool));
    store.initialize();
    var schemas = new SchemaCatalog(environment.getObjectMapper());
    var files = new FileService(store, new LocalBlobStore(Path.of(config.blobDirectory)));
    var glossary = new GlossaryService(store, schemas);
    var context = new ContextService(store, schemas, GlossaryService::validateGlossaryTags);
    var assembler = new ContextAssembler(store, (type, id, caller) -> Optional.empty());
    environment.jersey().setUrlPattern("/api/*");
    Filter loopbackDevelopment =
        (request, response, chain) -> {
          if (config.development
              && Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1").contains(request.getRemoteAddr())) {
            request.setAttribute(CallerFilter.CALLER, CallerFilter.DEVELOPMENT);
          }
          chain.doFilter(request, response);
        };
    environment
        .servlets()
        .addFilter("loopback-development", loopbackDevelopment)
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/api/*");
    environment.jersey().register(new CallerFilter(config));
    environment.jersey().register(new ProblemMapper());
    var principals =
        new ArrayList<>(
            config.identities.stream()
                .filter(i -> i.token != null && !i.token.isBlank())
                .map(SemanticContextConfiguration.Identity::caller)
                .toList());
    if (config.development
        && principals.stream().noneMatch(p -> p.id().equals(CallerFilter.DEVELOPMENT.id()))) {
      principals.add(CallerFilter.DEVELOPMENT);
    }
    environment
        .jersey()
        .register(new SemanticResource(store, glossary, context, files, assembler, principals));
    environment
        .healthChecks()
        .register(
            "semantic-database",
            new HealthCheck() {
              @Override
              protected Result check() {
                store.read(tx -> tx.all(Kind.GLOSSARY));
                return Result.healthy();
              }
            });
    environment
        .healthChecks()
        .register(
            "static-ui",
            new HealthCheck() {
              @Override
              protected Result check() {
                return getClass().getResource("/assets/index.html") == null
                    ? Result.unhealthy("Build frontend before packaging app")
                    : Result.healthy();
              }
            });
  }
}
