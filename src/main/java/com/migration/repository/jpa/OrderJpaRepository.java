package com.migration.repository.jpa;

import com.migration.model.jpa.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Integer> {

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM OrderEntity o "
            + "WHERE o.orderId = :id AND o.migratedAt IS NOT NULL")
    boolean isMigrated(@Param("id") Integer id);

    List<OrderEntity> findByMigratedAtIsNull();

    List<OrderEntity> findByMigratedAtIsNullAndCustomerId(Integer customerId);

    List<OrderEntity> findByMigratedAtIsNullOrderByOrderIdAsc(org.springframework.data.domain.Pageable pageable);

    List<OrderEntity> findByMigratedAtIsNullAndOrderIdGreaterThanOrderByOrderIdAsc(
            Integer orderId, org.springframework.data.domain.Pageable pageable);
}
