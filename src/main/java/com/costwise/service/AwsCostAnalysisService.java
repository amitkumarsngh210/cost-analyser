package com.costwise.service;

import com.costwise.model.AwsAccount;
import com.costwise.model.CostAnalysisRun;
import com.costwise.model.OptimizationResult;
import com.costwise.repository.CostAnalysisRunRepository;
import com.costwise.repository.OptimizationResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsCostAnalysisService {
    private final CostAnalysisRunRepository costAnalysisRunRepository;
    private final OptimizationResultRepository optimizationResultRepository;

    @Transactional
    public CostAnalysisRun analyzeCosts(AwsAccount awsAccount, LocalDateTime startDate, LocalDateTime endDate) {
        CostAnalysisRun analysisRun = new CostAnalysisRun();
        analysisRun.setAwsAccount(awsAccount);
        analysisRun.setStartDate(startDate);
        analysisRun.setEndDate(endDate);
        analysisRun.setStatus("RUNNING");
        analysisRun.setTotalCost(0.0);
        analysisRun.setPotentialSavings(0.0);

        try {
            // Save initial run
            analysisRun = costAnalysisRunRepository.save(analysisRun);

            // Get cost data
            List<OptimizationResult> results = fetchAndAnalyzeCosts(awsAccount, startDate, endDate);
            
            // Calculate totals
            double totalCost = results.stream().mapToDouble(OptimizationResult::getCurrentCost).sum();
            double potentialSavings = results.stream().mapToDouble(OptimizationResult::getPotentialSavings).sum();

            // Update run with results
            analysisRun.setTotalCost(totalCost);
            analysisRun.setPotentialSavings(potentialSavings);
            analysisRun.setStatus("COMPLETED");
            analysisRun.setOptimizationResults(results);

            return costAnalysisRunRepository.save(analysisRun);
        } catch (Exception e) {
            log.error("Error analyzing costs for account {}: {}", awsAccount.getAccountId(), e.getMessage());
            analysisRun.setStatus("FAILED");
            analysisRun.setErrorMessage(e.getMessage());
            return costAnalysisRunRepository.save(analysisRun);
        }
    }

    private List<OptimizationResult> fetchAndAnalyzeCosts(AwsAccount account, LocalDateTime startDate, LocalDateTime endDate) {
        List<OptimizationResult> results = new ArrayList<>();
        
        AWSCostExplorer ceClient = AWSCostExplorerClientBuilder.standard()
                .withRegion(account.getRegion())
                .build();

        // Get cost and usage data
        GetCostAndUsageRequest request = new GetCostAndUsageRequest()
                .withTimePeriod(new DateInterval()
                        .withStart(startDate.toLocalDate().toString())
                        .withEnd(endDate.toLocalDate().toString()))
                .withGranularity("DAILY")
                .withMetrics("UnblendedCost")
                .withGroupBy(new GroupDefinition()
                        .withType("DIMENSION")
                        .withKey("SERVICE"));

        GetCostAndUsageResult response = ceClient.getCostAndUsage(request);
        
        // Process results and create optimization suggestions
        for (ResultByTime result : response.getResultsByTime()) {
            for (com.amazonaws.services.costexplorer.model.Group group : result.getGroups()) {
                String service = group.getKeys().get(0);
                double cost = Double.parseDouble(group.getMetrics().get("UnblendedCost").getAmount());
                OptimizationResult optimizationResult = createOptimizationResult(service, cost);
                if (optimizationResult != null) {
                    results.add(optimizationResult);
                }
            }
        }
        ceClient.shutdown();
        return results;
    }

    private OptimizationResult createOptimizationResult(String service, double cost) {
        // This is a simplified example. In a real implementation, you would have more sophisticated
        // logic to determine optimization suggestions based on service type and cost patterns.
        if (cost > 1000) {
            OptimizationResult result = new OptimizationResult();
            result.setResourceType(service);
            result.setCurrentCost(cost);
            result.setPotentialSavings(cost * 0.2); // Example: 20% potential savings
            result.setSeverity("HIGH");
            result.setSuggestedAction("Review usage patterns and consider reserved instances");
            result.setCurrentState("High cost detected");
            return result;
        }
        return null;
    }
} 