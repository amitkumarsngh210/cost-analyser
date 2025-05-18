package com.costwise.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "optimization_results")
public class OptimizationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private CostAnalysisRun analysisRun;

    @Column(nullable = false)
    private String resourceType; // EC2, RDS, EBS, etc.

    @Column(nullable = false)
    private String resourceId;

    @Column(nullable = false)
    private String currentState;

    @Column(nullable = false)
    private String suggestedAction;

    @Column(nullable = false)
    private double currentCost;

    @Column(nullable = false)
    private double potentialSavings;

    @Column(nullable = false)
    private String severity; // HIGH, MEDIUM, LOW

    @Column
    private String additionalDetails;

    @CreationTimestamp
    private LocalDateTime createdAt;
} 