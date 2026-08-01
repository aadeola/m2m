package com.migration.contract.support;

import com.migration.MigrationShimApplication;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = MigrationShimApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ContractTestBase {

    protected static final LegacyApiStub legacyApiStub = new LegacyApiStub();

    @LocalServerPort
    protected int shimPort;

    @Autowired
    private ContractFixtureSetup contractFixtureSetup;

    @BeforeAll
    static void startDockerCompose() throws Exception {
        DockerComposeSupport.ensureRunning();
    }

    @BeforeEach
    void setUpContractFixtures() throws Exception {
        legacyApiStub.start();
        contractFixtureSetup.prepareDatabaseAndLegacyStub(legacyApiStub);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    protected String callShimGet(String path) {
        return RestAssured.given()
                .port(shimPort)
                .accept(ContentType.JSON)
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    protected String callShimGetWithQuery(String path, String queryParam, String queryValue) {
        return RestAssured.given()
                .port(shimPort)
                .accept(ContentType.JSON)
                .queryParam(queryParam, queryValue)
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    protected String callShimPost(String path, Object body) {
        return RestAssured.given()
                .port(shimPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .post(path)
                .then()
                .statusCode(201)
                .extract()
                .asString();
    }

    protected void assertShimMatchesLegacyGet(String path) throws JSONException {
        String legacyJson = legacyApiStub.getLegacy(path);
        String shimJson = callShimGet(path);
        ContractAssertions.assertJsonEqual(legacyJson, shimJson);
    }

    protected void assertShimMatchesLegacyGetWithQuery(String path, String queryParam, String queryValue)
            throws JSONException {
        String legacyJson = legacyApiStub.getLegacy(path + "?" + queryParam + "=" + queryValue);
        String shimJson = callShimGetWithQuery(path, queryParam, queryValue);
        ContractAssertions.assertJsonEqualLenient(legacyJson, shimJson);
    }
}
