package com.migration.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.migration.job.BackfillJob;
import com.migration.service.BackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class BackfillJobTest {

    @Test
    void skipsBackfillWhenFlagMissing() {
        RecordingBackfillService service = new RecordingBackfillService();
        BackfillJob job = new BackfillJob(service);

        job.run(new DefaultApplicationArguments(new String[] {}));

        assertFalse(service.called);
    }

    @Test
    void runsBackfillWhenFlagPresent() {
        RecordingBackfillService service = new RecordingBackfillService();
        BackfillJob job = new BackfillJob(service);

        job.run(new DefaultApplicationArguments(new String[] {"--backfill"}));

        assertTrue(service.called);
    }

    private static final class RecordingBackfillService extends BackfillService {

        private boolean called;

        private RecordingBackfillService() {
            super(
                    10,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        @Override
        public void runBackfill() {
            called = true;
        }
    }
}
