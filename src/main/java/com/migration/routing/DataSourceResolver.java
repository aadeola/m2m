package com.migration.routing;

import com.migration.debug.AgentDebugLog;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class DataSourceResolver {

    private static final int OBJECT_ID_LENGTH = 24;

    private final CustomerJpaRepository customerJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;

    public DataSourceResolver(
            CustomerJpaRepository customerJpaRepository,
            ProductJpaRepository productJpaRepository,
            OrderJpaRepository orderJpaRepository) {
        this.customerJpaRepository = customerJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
    }

    public DataSource resolveDataSource(EntityType entityType, String id) {
        if (isObjectId(id)) {
            // #region agent log
            AgentDebugLog.log("C", "DataSourceResolver.resolveDataSource", "route decision",
                    "{\"entityType\":\"" + entityType + "\",\"id\":\"" + id + "\",\"reason\":\"objectId\",\"source\":\"MONGO\"}");
            // #endregion
            return DataSource.MONGO;
        }
        if (!isNumericId(id)) {
            throw new IllegalArgumentException("Invalid id format: " + id);
        }
        int numericId = Integer.parseInt(id);
        boolean migrated = switch (entityType) {
            case CUSTOMER -> customerJpaRepository.isMigrated(numericId);
            case PRODUCT -> productJpaRepository.isMigrated(numericId);
            case ORDER -> orderJpaRepository.isMigrated(numericId);
        };
        DataSource source = migrated ? DataSource.MONGO : DataSource.POSTGRES;
        // #region agent log
        AgentDebugLog.log("A,B", "DataSourceResolver.resolveDataSource", "route decision",
                "{\"entityType\":\"" + entityType + "\",\"id\":\"" + id + "\",\"numericId\":" + numericId
                        + ",\"isMigrated\":" + migrated + ",\"source\":\"" + source + "\"}");
        // #endregion
        return source;
    }

    public static boolean isObjectId(String id) {
        if (id == null || id.length() != OBJECT_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNumericId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
