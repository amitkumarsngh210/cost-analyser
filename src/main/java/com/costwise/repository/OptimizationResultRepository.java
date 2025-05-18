package com.costwise.repository;

import com.costwise.model.OptimizationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptimizationResultRepository extends JpaRepository<OptimizationResult, Long> {
    List<OptimizationResult> findByAnalysisRunId(Long analysisRunId);
    List<OptimizationResult> findByAnalysisRunIdAndSeverity(Long analysisRunId, String severity);
    List<OptimizationResult> findByAnalysisRunIdAndResourceType(Long analysisRunId, String resourceType);
} 