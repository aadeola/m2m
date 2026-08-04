package com.migration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.migration.dto.BackfillDlqResponse;
import com.migration.model.jpa.BackfillDlqEntity;
import com.migration.repository.jpa.BackfillDlqRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

class BackfillDlqServiceTest {

    @Test
    void listFailures_defaultsToUnresolved() {
        BackfillDlqEntity entity = unresolvedEntity();
        BackfillDlqService service = new BackfillDlqService(new StubRepo(List.of(entity), List.of()));

        List<BackfillDlqResponse> responses = service.listFailures(null);

        assertEquals(1, responses.size());
        BackfillDlqResponse response = responses.get(0);
        assertEquals(7, response.getId());
        assertEquals("orders", response.getEntityName());
        assertEquals(10, response.getStartPk());
        assertEquals(19, response.getEndPk());
        assertEquals("com.migration.exception.MongoSchemaValidationException", response.getExceptionClass());
        assertEquals("schema boom", response.getMessage());
        assertEquals(false, response.isResolved());
    }

    @Test
    void listFailures_resolvedFalseUsesUnresolvedQuery() {
        BackfillDlqService service = new BackfillDlqService(new StubRepo(List.of(), List.of()));

        List<BackfillDlqResponse> responses = service.listFailures(false);

        assertEquals(0, responses.size());
    }

    @Test
    void listFailures_resolvedTrueUsesResolvedQuery() {
        BackfillDlqEntity entity = unresolvedEntity();
        entity.setResolved(true);
        BackfillDlqService service = new BackfillDlqService(new StubRepo(List.of(), List.of(entity)));

        List<BackfillDlqResponse> responses = service.listFailures(true);

        assertEquals(1, responses.size());
        assertEquals(true, responses.get(0).isResolved());
    }

    private static BackfillDlqEntity unresolvedEntity() {
        BackfillDlqEntity entity = new BackfillDlqEntity();
        entity.setId(7);
        entity.setEntityName("orders");
        entity.setStartPk(10);
        entity.setEndPk(19);
        entity.setExceptionClass("com.migration.exception.MongoSchemaValidationException");
        entity.setMessage("schema boom");
        entity.setOccurredAt(LocalDateTime.of(2026, 8, 3, 12, 0));
        entity.setResolved(false);
        return entity;
    }

    /** Minimal stub — avoids Mockito inline agent (blocked in some sandboxes). */
    private static final class StubRepo implements BackfillDlqRepository {
        private final List<BackfillDlqEntity> unresolved;
        private final List<BackfillDlqEntity> resolved;

        private StubRepo(List<BackfillDlqEntity> unresolved, List<BackfillDlqEntity> resolved) {
            this.unresolved = unresolved;
            this.resolved = resolved;
        }

        @Override
        public List<BackfillDlqEntity> findByResolvedFalse() {
            return unresolved;
        }

        @Override
        public List<BackfillDlqEntity> findByResolvedTrue() {
            return resolved;
        }

        @Override
        public void flush() {
        }

        @Override
        public <S extends BackfillDlqEntity> S saveAndFlush(S entity) {
            return entity;
        }

        @Override
        public <S extends BackfillDlqEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
            return List.of();
        }

        @Override
        public void deleteAllInBatch(Iterable<BackfillDlqEntity> entities) {
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<Integer> integers) {
        }

        @Override
        public void deleteAllInBatch() {
        }

        @Override
        public BackfillDlqEntity getOne(Integer integer) {
            return null;
        }

        @Override
        public BackfillDlqEntity getById(Integer integer) {
            return null;
        }

        @Override
        public BackfillDlqEntity getReferenceById(Integer integer) {
            return null;
        }

        @Override
        public <S extends BackfillDlqEntity> Optional<S> findOne(Example<S> example) {
            return Optional.empty();
        }

        @Override
        public <S extends BackfillDlqEntity> List<S> findAll(Example<S> example) {
            return List.of();
        }

        @Override
        public <S extends BackfillDlqEntity> List<S> findAll(Example<S> example, Sort sort) {
            return List.of();
        }

        @Override
        public <S extends BackfillDlqEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
            return Page.empty();
        }

        @Override
        public <S extends BackfillDlqEntity> long count(Example<S> example) {
            return 0;
        }

        @Override
        public <S extends BackfillDlqEntity> boolean exists(Example<S> example) {
            return false;
        }

        @Override
        public <S extends BackfillDlqEntity, R> R findBy(
                Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }

        @Override
        public <S extends BackfillDlqEntity> S save(S entity) {
            return entity;
        }

        @Override
        public <S extends BackfillDlqEntity> List<S> saveAll(Iterable<S> entities) {
            return List.of();
        }

        @Override
        public Optional<BackfillDlqEntity> findById(Integer integer) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(Integer integer) {
            return false;
        }

        @Override
        public List<BackfillDlqEntity> findAll() {
            return List.of();
        }

        @Override
        public List<BackfillDlqEntity> findAllById(Iterable<Integer> integers) {
            return List.of();
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public void deleteById(Integer integer) {
        }

        @Override
        public void delete(BackfillDlqEntity entity) {
        }

        @Override
        public void deleteAllById(Iterable<? extends Integer> integers) {
        }

        @Override
        public void deleteAll(Iterable<? extends BackfillDlqEntity> entities) {
        }

        @Override
        public void deleteAll() {
        }

        @Override
        public List<BackfillDlqEntity> findAll(Sort sort) {
            return List.of();
        }

        @Override
        public Page<BackfillDlqEntity> findAll(Pageable pageable) {
            return Page.empty();
        }
    }
}
