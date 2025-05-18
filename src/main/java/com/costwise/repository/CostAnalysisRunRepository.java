package com.costwise.repository;

import com.costwise.model.CostAnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostAnalysisRunRepository extends JpaRepository<CostAnalysisRun, Long> {
    List<CostAnalysisRun> findByAwsAccountIdOrderByCreatedAtDesc(Long awsAccountId);
    List<CostAnalysisRun> findByStatus(String status);
} 