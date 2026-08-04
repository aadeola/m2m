package com.migration.dataquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.migration.MigrationShimApplication;
import com.migration.contract.support.DockerComposeSupport;
import com.migration.model.jpa.CustomerEntity;
import com.migration.model.mongo.CustomerDocument;
import com.migration.repository.jpa.CustomerJpaRepository;
import com.migration.repository.mongo.CustomerMongoRepository;
import com.migration.service.BackfillService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = MigrationShimApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerInvalidFieldMigrationIntegrationTest {

    @Autowired
    private BackfillService backfillService;

    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    @Autowired
    private CustomerMongoRepository customerMongoRepository;

    @BeforeAll
    void startInfrastructure() throws Exception {
        DockerComposeSupport.ensureRunning();
        runMongoInit();
    }

    @BeforeEach
    void resetMongoCustomers() {
        customerMongoRepository.deleteAll();
        customerJpaRepository.findAll().stream()
                .filter(entity -> entity.getCustomerId() > 5)
                .forEach(customerJpaRepository::delete);
        customerJpaRepository.findAll().forEach(entity -> {
            entity.setMigratedAt(null);
            customerJpaRepository.save(entity);
        });
    }

    @Test
    void backfillCopiesBadCustomerFaithfullyAndTagsInvalidFields() {
        CustomerEntity badCustomer = new CustomerEntity();
        badCustomer.setFirstName("Test");
        badCustomer.setLastName("User");
        badCustomer.setAccountNumber("CUS9998");
        badCustomer.setPhoneNumber("5559999998");
        badCustomer.setEmail("no-at-sign.example.com");
        badCustomer.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        badCustomer = customerJpaRepository.save(badCustomer);
        Integer customerId = badCustomer.getCustomerId();

        backfillService.runBackfill();

        CustomerDocument document = customerMongoRepository.findById(String.valueOf(customerId)).orElseThrow();
        assertEquals("no-at-sign.example.com", document.getEmail());
        assertEquals("Test", document.getFirstName());
        assertEquals("User", document.getLastName());
        assertEquals("CUS9998", document.getAccountNumber());
        assertEquals("5559999998", document.getPhoneNumber());
        assertEquals(List.of("email"), document.getInvalidFields());
    }

    @Test
    void validCustomersHaveNoInvalidFieldsAfterBackfill() {
        backfillService.runBackfill();

        customerMongoRepository.findAll().forEach(document -> {
            assertNull(document.getInvalidFields(), "customer " + document.getId() + " should have no invalidFields");
        });
    }

    @Test
    void multiFieldBadCustomerTagsBothFields() {
        CustomerEntity badCustomer = new CustomerEntity();
        badCustomer.setFirstName("Test");
        badCustomer.setLastName("User");
        badCustomer.setAccountNumber("AB12345");
        badCustomer.setPhoneNumber("123");
        badCustomer.setEmail("also-bad.example.com");
        badCustomer.setCreatedAt(LocalDateTime.of(2026, 1, 2, 12, 0));
        badCustomer = customerJpaRepository.save(badCustomer);
        Integer customerId = badCustomer.getCustomerId();

        backfillService.runBackfill();

        CustomerDocument document = customerMongoRepository.findById(String.valueOf(customerId)).orElseThrow();
        List<String> invalidFields = document.getInvalidFields();
        assertEquals(3, invalidFields.size());
        assertTrue(invalidFields.contains("email"));
        assertTrue(invalidFields.contains("phone_number"));
        assertTrue(invalidFields.contains("account_number"));
    }

    private void runMongoInit() throws IOException, InterruptedException {
        Path repoRoot = findRepoRoot();
        ProcessBuilder builder = new ProcessBuilder("docker", "compose", "run", "--rm", "mongo-init")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running mongo-init");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("mongo-init failed: " + output);
        }
    }

    private Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("docker-compose.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate docker-compose.yml");
    }
}
