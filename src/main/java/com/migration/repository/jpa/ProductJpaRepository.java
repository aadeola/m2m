package com.migration.repository.jpa;

import com.migration.model.jpa.ProductEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Integer> {

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ProductEntity p "
            + "WHERE p.productId = :id AND p.migratedAt IS NOT NULL")
    boolean isMigrated(@Param("id") Integer id);

    List<ProductEntity> findByMigratedAtIsNull();

    List<ProductEntity> findByMigratedAtIsNullOrderByProductIdAsc(org.springframework.data.domain.Pageable pageable);

    List<ProductEntity> findByMigratedAtIsNullAndProductIdGreaterThanOrderByProductIdAsc(
            Integer productId, org.springframework.data.domain.Pageable pageable);
}
