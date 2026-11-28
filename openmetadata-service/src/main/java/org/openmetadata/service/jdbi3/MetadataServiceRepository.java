package org.openmetadata.service.jdbi3;

import org.openmetadata.schema.entity.services.MetadataConnection;
import org.openmetadata.schema.entity.services.MetadataService;
import org.openmetadata.schema.entity.services.ServiceType;
import org.openmetadata.service.Entity;

public class MetadataServiceRepository
    extends ServiceEntityRepository<MetadataService, MetadataConnection> {
  private static final String UPDATE_FIELDS = "owners,tags,connection";

  public MetadataServiceRepository() {
    super(
        "/v1/services/metadataServices/",
        Entity.METADATA_SERVICE,
        Entity.getCollectionDAO().metadataServiceDAO(),
        MetadataConnection.class,
        UPDATE_FIELDS,
        ServiceType.METADATA);
    supportsSearch = true;
  }
}
