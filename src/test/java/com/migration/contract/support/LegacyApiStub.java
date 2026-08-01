package com.migration.contract.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;

/**
 * Mock legacy REST API used as the contract oracle during Phase 3 tests.
 */
public final class LegacyApiStub {

    private WireMockServer server;

    public void start() {
        if (server != null && server.isRunning()) {
            return;
        }
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public void reset() {
        if (server != null) {
            server.resetAll();
        }
    }

    public int port() {
        return server.port();
    }

    public void registerGet(String path, String jsonBody) {
        server.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody)));
    }

    public void registerGetWithQuery(String path, String queryParam, String queryValue, String jsonBody) {
        server.stubFor(get(urlPathEqualTo(path))
                .withQueryParam(queryParam, equalTo(queryValue))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody)));
    }

    public void registerPost(String path, String jsonBody) {
        server.stubFor(post(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody)));
    }

    public String getLegacy(String path) {
        return RestAssured.given()
                .baseUri("http://localhost:" + port())
                .get(path)
                .asString();
    }

    public String postLegacy(String path, Object body) {
        return RestAssured.given()
                .baseUri("http://localhost:" + port())
                .contentType("application/json")
                .body(body)
                .post(path)
                .asString();
    }
}
