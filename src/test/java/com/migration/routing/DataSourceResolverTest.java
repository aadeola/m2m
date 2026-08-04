package com.migration.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.jpa.OrderJpaRepository;
import com.migration.repository.jpa.ProductJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceResolverTest {

    private static final String OBJECT_ID = "507f191e810c19729de860ea";

    @Mock
    private CustomerJpaRepository customerJpaRepository;

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private OrderJpaRepository orderJpaRepository;

    private DataSourceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DataSourceResolver(customerJpaRepository, productJpaRepository, orderJpaRepository);
    }

    @Test
    void isObjectId_acceptsValid24CharHex() {
        assertTrue(DataSourceResolver.isObjectId(OBJECT_ID));
        assertTrue(DataSourceResolver.isObjectId("ABCDEF0123456789abcdef01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "123", "not-an-object-id", "zzzzzzzzzzzzzzzzzzzzzzzz"})
    void isObjectId_rejectsInvalidValues(String id) {
        assertFalse(DataSourceResolver.isObjectId(id));
    }

    @Test
    void isObjectId_rejectsNull() {
        assertFalse(DataSourceResolver.isObjectId(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "999999"})
    void isNumericId_acceptsDigitsOnly(String id) {
        assertTrue(DataSourceResolver.isNumericId(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12a", "abc", OBJECT_ID})
    void isNumericId_rejectsNonDigits(String id) {
        assertFalse(DataSourceResolver.isNumericId(id));
    }

    @Test
    void resolveDataSource_objectIdAlwaysRoutesToMongo() {
        assertEquals(DataSource.MONGO, resolver.resolveDataSource(EntityType.ORDER, OBJECT_ID));
        assertEquals(DataSource.MONGO, resolver.resolveDataSource(EntityType.CUSTOMER, OBJECT_ID));
        assertEquals(DataSource.MONGO, resolver.resolveDataSource(EntityType.PRODUCT, OBJECT_ID));
    }

    @Test
    void resolveDataSource_unmigratedNumericRoutesToPostgres() {
        when(orderJpaRepository.isMigrated(1)).thenReturn(false);

        assertEquals(DataSource.POSTGRES, resolver.resolveDataSource(EntityType.ORDER, "1"));
    }

    @Test
    void resolveDataSource_migratedNumericRoutesToMongo() {
        when(customerJpaRepository.isMigrated(2)).thenReturn(true);

        assertEquals(DataSource.MONGO, resolver.resolveDataSource(EntityType.CUSTOMER, "2"));
    }

    @Test
    void resolveDataSource_invalidIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveDataSource(EntityType.ORDER, "bad-id"));
    }
}
