package com.migration.job;

import com.migration.service.BackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BackfillJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillJob.class);

    private final BackfillService backfillService;

    public BackfillJob(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("backfill")) {
            return;
        }
        log.info("BackfillJob starting");
        backfillService.runBackfill();
    }
}
