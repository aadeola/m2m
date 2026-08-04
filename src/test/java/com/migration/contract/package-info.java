/**
 * Contract tests verify legacy API JSON parity between the shim and the legacy oracle.
 * Style: Spring Boot integration tests with RestAssured and WireMock. Does not assert
 * internal Mongo document shape — see {@code com.migration.transform} and
 * {@code com.migration.migration}.
 */
package com.migration.contract;
