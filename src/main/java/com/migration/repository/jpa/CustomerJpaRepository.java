package com.migration.repository.jpa;

import com.migration.model.jpa.CustomerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Integer> {

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM CustomerEntity c "
            + "WHERE c.customerId = :id AND c.migratedAt IS NOT NULL")
    boolean isMigrated(@Param("id") Integer id);

    List<CustomerEntity> findByMigratedAtIsNull();

    List<CustomerEntity> findByMigratedAtIsNullOrderByCustomerIdAsc(org.springframework.data.domain.Pageable pageable);

    List<CustomerEntity> findByMigratedAtIsNullAndCustomerIdGreaterThanOrderByCustomerIdAsc(
            Integer customerId, org.springframework.data.domain.Pageable pageable);
}
