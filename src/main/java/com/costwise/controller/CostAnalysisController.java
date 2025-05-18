package com.costwise.controller;

import com.costwise.model.AwsAccount;
import com.costwise.model.CostAnalysisRun;
import com.costwise.repository.AwsAccountRepository;
import com.costwise.repository.CostAnalysisRunRepository;
import com.costwise.service.AwsCostAnalysisService;
import com.costwise.service.AwsResourceAnalyzer;
import com.costwise.service.ExcelReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/analyze")
@RequiredArgsConstructor
public class CostAnalysisController {
    private final AwsCostAnalysisService costAnalysisService;
    private final AwsResourceAnalyzer resourceAnalyzer;
    private final ExcelReportService excelReportService;
    private final AwsAccountRepository awsAccountRepository;
    private final CostAnalysisRunRepository costAnalysisRunRepository;

    @PostMapping("/{accountId}")
    public ResponseEntity<CostAnalysisRun> analyzeCosts(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        AwsAccount account = awsAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("AWS Account not found"));
        
        CostAnalysisRun analysisRun = costAnalysisService.analyzeCosts(account, startDate, endDate);
        return ResponseEntity.ok(analysisRun);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<CostAnalysisRun> getAnalysisRun(@PathVariable Long runId) {
        CostAnalysisRun analysisRun = costAnalysisRunRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("Analysis run not found"));
        return ResponseEntity.ok(analysisRun);
    }

    @GetMapping("/{runId}/report")
    public ResponseEntity<byte[]> generateReport(@PathVariable Long runId) {
        CostAnalysisRun analysisRun = costAnalysisRunRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("Analysis run not found"));
        
        byte[] reportBytes = excelReportService.generateReport(analysisRun);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cost-analysis-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reportBytes);
    }

    @PostMapping("/{accountId}/resources")
    public ResponseEntity<CostAnalysisRun> analyzeResources(@PathVariable Long accountId) {
        AwsAccount account = awsAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("AWS Account not found"));
        
        CostAnalysisRun analysisRun = new CostAnalysisRun();
        analysisRun.setAwsAccount(account);
        analysisRun.setStartDate(LocalDateTime.now());
        analysisRun.setEndDate(LocalDateTime.now());
        analysisRun.setStatus("COMPLETED");
        
        // Add resource analysis results
        analysisRun.setOptimizationResults(resourceAnalyzer.analyzeResources(account));
        
        return ResponseEntity.ok(analysisRun);
    }
} 