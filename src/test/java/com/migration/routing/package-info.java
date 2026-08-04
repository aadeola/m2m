/**
 * Routing tests verify {@link com.migration.routing.DataSourceResolver} and service-level
 * routing/merge behavior directly. Style: unit tests with mocks; focused integration where merge
 * logic is subtle. Does not assert legacy JSON shape — see {@code com.migration.contract}.
 */
package com.migration.routing;
