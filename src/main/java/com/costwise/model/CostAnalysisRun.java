package com.costwise.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "cost_analysis_runs")
public class CostAnalysisRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aws_account_id", nullable = false)
    private AwsAccount awsAccount;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED

    @Column
    private String errorMessage;

    @Column(nullable = false)
    private double totalCost;

    @Column(nullable = false)
    private double potentialSavings;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OptimizationResult> optimizationResults = new ArrayList<>();
} 