package com.costwise.service;

import com.costwise.model.CostAnalysisRun;
import com.costwise.model.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generateReport(CostAnalysisRun analysisRun) {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create summary sheet
            createSummarySheet(workbook, analysisRun);

            // Create detailed sheets for each resource type
            Map<String, List<OptimizationResult>> resultsByType = analysisRun.getOptimizationResults().stream()
                    .collect(Collectors.groupingBy(OptimizationResult::getResourceType));

            resultsByType.forEach((resourceType, results) -> 
                createResourceTypeSheet(workbook, resourceType, results));

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Error generating Excel report: {}", e.getMessage());
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    private void createSummarySheet(Workbook workbook, CostAnalysisRun analysisRun) {
        Sheet sheet = workbook.createSheet("Summary");
        AtomicInteger rowNum = new AtomicInteger(0);

        // Create header style
        CellStyle headerStyle = createHeaderStyle(workbook);

        // Add summary information
        createHeaderRow(sheet, rowNum.getAndIncrement(), headerStyle, "Analysis Summary");
        createDataRow(sheet, rowNum.getAndIncrement(), "Account ID", analysisRun.getAwsAccount().getAccountId());
        createDataRow(sheet, rowNum.getAndIncrement(), "Start Date", analysisRun.getStartDate().format(DATE_FORMATTER));
        createDataRow(sheet, rowNum.getAndIncrement(), "End Date", analysisRun.getEndDate().format(DATE_FORMATTER));
        createDataRow(sheet, rowNum.getAndIncrement(), "Total Cost", String.format("$%.2f", analysisRun.getTotalCost()));
        createDataRow(sheet, rowNum.getAndIncrement(), "Potential Savings", String.format("$%.2f", analysisRun.getPotentialSavings()));
        createDataRow(sheet, rowNum.getAndIncrement(), "Status", analysisRun.getStatus());

        // Add optimization summary
        rowNum.incrementAndGet();
        createHeaderRow(sheet, rowNum.getAndIncrement(), headerStyle, "Optimization Summary by Resource Type");
        
        Map<String, List<OptimizationResult>> resultsByType = analysisRun.getOptimizationResults().stream()
                .collect(Collectors.groupingBy(OptimizationResult::getResourceType));

        createHeaderRow(sheet, rowNum.getAndIncrement(), headerStyle, "Resource Type", "Count", "Total Cost", "Potential Savings");
        
        resultsByType.forEach((type, results) -> {
            double totalCost = results.stream().mapToDouble(OptimizationResult::getCurrentCost).sum();
            double totalSavings = results.stream().mapToDouble(OptimizationResult::getPotentialSavings).sum();
            createDataRow(sheet, rowNum.getAndIncrement(), type, String.valueOf(results.size()),
                    String.format("$%.2f", totalCost),
                    String.format("$%.2f", totalSavings));
        });

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createResourceTypeSheet(Workbook workbook, String resourceType, List<OptimizationResult> results) {
        Sheet sheet = workbook.createSheet(resourceType);
        AtomicInteger rowNum = new AtomicInteger(0);

        // Create header style
        CellStyle headerStyle = createHeaderStyle(workbook);

        // Add headers
        createHeaderRow(sheet, rowNum.getAndIncrement(), headerStyle,
                "Resource ID", "Current State", "Suggested Action", "Current Cost", "Potential Savings", "Severity");

        // Add data rows
        for (OptimizationResult result : results) {
            createDataRow(sheet, rowNum.getAndIncrement(),
                    result.getResourceId(),
                    result.getCurrentState(),
                    result.getSuggestedAction(),
                    String.format("$%.2f", result.getCurrentCost()),
                    String.format("$%.2f", result.getPotentialSavings()),
                    result.getSeverity());
        }

        // Auto-size columns
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createHeaderRow(Sheet sheet, int rowNum, CellStyle style, String... headers) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void createDataRow(Sheet sheet, int rowNum, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
        }
    }
} 