package com.resolveiq.backend.api;

import com.resolveiq.backend.evaluation.domain.EvaluationRunEntity;
import com.resolveiq.backend.evaluation.dto.EvaluationRegressionDto;
import com.resolveiq.backend.evaluation.dto.EvaluationReportDto;
import com.resolveiq.backend.evaluation.dto.EvaluationRunRequest;
import com.resolveiq.backend.evaluation.service.AiEvaluationService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for AI Evaluation Framework & Benchmarking (PRD §§30, 56, 58).
 */
@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final AiEvaluationService evaluationService;

    public EvaluationController(AiEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<EvaluationReportDto>> runEvaluation(
            @RequestBody(required = false) EvaluationRunRequest request) {
        requirePermission("trigger AI evaluation benchmark");
        EvaluationRunRequest req = request != null ? request : new EvaluationRunRequest("all", null, null);
        EvaluationReportDto report = evaluationService.runEvaluation(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(report));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EvaluationRunEntity>>> listRuns() {
        List<EvaluationRunEntity> runs = evaluationService.listRuns();
        return ResponseEntity.ok(ApiResponse.ok(runs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EvaluationReportDto>> getReport(@PathVariable("id") UUID id) {
        EvaluationReportDto report = evaluationService.getReport(id);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/{id}/compare/{baselineId}")
    public ResponseEntity<ApiResponse<EvaluationRegressionDto>> compareRuns(
            @PathVariable("id") UUID id,
            @PathVariable("baselineId") UUID baselineId) {
        EvaluationRegressionDto comparison = evaluationService.compareRuns(id, baselineId);
        return ResponseEntity.ok(ApiResponse.ok(comparison));
    }

    private void requirePermission(String action) {
        Role role = TenantContextHolder.getContext().map(TenantContext::role).orElse(Role.VIEWER);
        if (!role.canTriggerInvestigation()) {
            throw new ForbiddenException("Role " + role + " is not authorized to " + action);
        }
    }
}
