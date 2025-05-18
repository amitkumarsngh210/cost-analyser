package com.costwise.service;

import com.costwise.model.AwsAccount;
import com.costwise.model.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.elasticache.ElastiCacheClient;
import software.amazon.awssdk.services.elasticache.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsResourceAnalyzer {
    private final Ec2CostOptimizer ec2CostOptimizer;

    public List<OptimizationResult> analyzeResources(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try {
            // Use the new Ec2CostOptimizer for EC2 analysis
            results.addAll(ec2CostOptimizer.analyzeEc2Instances(account.getRegion()));
            
            // Continue with other resource analysis
            results.addAll(analyzeRDSInstances(account));
            results.addAll(analyzeS3Buckets(account));
            results.addAll(analyzeElastiCacheClusters(account));
            results.addAll(analyzeLoadBalancers(account));
            results.addAll(analyzeLambdaFunctions(account));
        } catch (Exception e) {
            log.error("Error analyzing AWS resources: {}", e.getMessage());
        }
        
        return results;
    }

    private List<OptimizationResult> analyzeRDSInstances(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (RdsClient rdsClient = RdsClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build()) {

            DescribeDbInstancesResponse response = rdsClient.describeDBInstances();
            
            for (DBInstance instance : response.dbInstances()) {
                // Check for multi-AZ deployment
                if (!instance.multiAZ()) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("RDS");
                    result.setResourceId(instance.dbInstanceIdentifier());
                    result.setCurrentState("Single-AZ deployment");
                    result.setSuggestedAction("Consider enabling Multi-AZ for high availability");
                    result.setSeverity("HIGH");
                    results.add(result);
                }

                // Check for auto minor version upgrade
                if (!instance.autoMinorVersionUpgrade()) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("RDS");
                    result.setResourceId(instance.dbInstanceIdentifier());
                    result.setCurrentState("Auto minor version upgrade disabled");
                    result.setSuggestedAction("Enable auto minor version upgrade for better maintenance");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            }
        }
        
        return results;
    }

    private List<OptimizationResult> analyzeS3Buckets(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (S3Client s3Client = S3Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build()) {

            ListBucketsResponse response = s3Client.listBuckets();
            
            for (Bucket bucket : response.buckets()) {
                // Check for versioning
                GetBucketVersioningResponse versioningResponse = s3Client.getBucketVersioning(
                    GetBucketVersioningRequest.builder().bucket(bucket.name()).build());
                
                if (versioningResponse.status() != BucketVersioningStatus.ENABLED) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("S3");
                    result.setResourceId(bucket.name());
                    result.setCurrentState("Versioning disabled");
                    result.setSuggestedAction("Enable versioning for data protection");
                    result.setSeverity("HIGH");
                    results.add(result);
                }
            }
        }
        
        return results;
    }

    private List<OptimizationResult> analyzeElastiCacheClusters(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (ElastiCacheClient elasticacheClient = ElastiCacheClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build()) {

            DescribeCacheClustersResponse response = elasticacheClient.describeCacheClusters();
            
            for (CacheCluster cluster : response.cacheClusters()) {
                // Check for Redis cluster mode
                if (cluster.engine().equals("redis") && !cluster.engineVersion().contains("cluster")) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("ElastiCache");
                    result.setResourceId(cluster.cacheClusterId());
                    result.setCurrentState("Single-node Redis deployment");
                    result.setSuggestedAction("Consider using Redis cluster mode for high availability");
                    result.setSeverity("HIGH");
                    results.add(result);
                }
            }
        }
        
        return results;
    }

    private List<OptimizationResult> analyzeLoadBalancers(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (ElasticLoadBalancingV2Client elbClient = ElasticLoadBalancingV2Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build()) {

            DescribeLoadBalancersResponse response = elbClient.describeLoadBalancers();
            
            for (LoadBalancer lb : response.loadBalancers()) {
                // Check for public load balancers
                if (lb.state().code() == LoadBalancerStateEnum.ACTIVE && !lb.scheme().equals("internal")) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("LoadBalancer");
                    result.setResourceId(lb.loadBalancerArn());
                    result.setCurrentState("Public load balancer");
                    result.setSuggestedAction("Consider using internal load balancer if external access is not needed");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            }
        }
        
        return results;
    }

    private List<OptimizationResult> analyzeLambdaFunctions(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (LambdaClient lambdaClient = LambdaClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build()) {

            ListFunctionsResponse response = lambdaClient.listFunctions();
            
            for (FunctionConfiguration function : response.functions()) {
                // Check for memory allocation
                if (function.memorySize() < 256) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("Lambda");
                    result.setResourceId(function.functionName());
                    result.setCurrentState("Low memory allocation");
                    result.setSuggestedAction("Consider increasing memory for better performance");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            }
        }
        
        return results;
    }
} 