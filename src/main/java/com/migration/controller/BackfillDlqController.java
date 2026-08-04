package com.migration.controller;

import com.migration.dto.BackfillDlqResponse;
import com.migration.service.BackfillDlqService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/backfill/dlq")
public class BackfillDlqController {

    private final BackfillDlqService backfillDlqService;

    public BackfillDlqController(BackfillDlqService backfillDlqService) {
        this.backfillDlqService = backfillDlqService;
    }

    @GetMapping
    public List<BackfillDlqResponse> listDlq(
            @RequestParam(value = "resolved", required = false) Boolean resolved) {
        return backfillDlqService.listFailures(resolved);
    }
}
