/**
 * Migration tests verify the backfill workflow: entity sequencing, checkpoint updates,
 * {@code migrated_at} stamping, and Mongo upserts. Style: integration tests against Docker-backed
 * Postgres and Mongo. Does not assert client API shape — see {@code com.migration.contract}.
 */
package com.migration.migration;
