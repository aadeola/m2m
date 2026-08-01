package com.migration.repository.jpa;

import com.migration.model.jpa.LineItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineItemJpaRepository extends JpaRepository<LineItemEntity, Integer> {

    List<LineItemEntity> findByOrderIdOrderByLineItemIdAsc(Integer orderId);
}
