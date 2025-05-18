package com.costwise.service;

import com.costwise.model.AwsAccount;
import com.costwise.model.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsResourceAnalyzer {

    private static final double CPU_UTILIZATION_THRESHOLD = 20.0;
    private static final int LOOKBACK_DAYS = 7;

    public List<OptimizationResult> analyzeResources(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try {
            results.addAll(analyzeEC2Instances(account));
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

    private List<OptimizationResult> analyzeEC2Instances(AwsAccount account) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (Ec2Client ec2Client = Ec2Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build();
             CloudWatchClient cloudWatchClient = CloudWatchClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(account.getRegion()))
                .build()) {

            DescribeInstancesResponse response = ec2Client.describeInstances();
            
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    if (instance.state().name() == InstanceStateName.RUNNING) {
                        // Check CPU utilization
                        if (instance.monitoring().state() == MonitoringState.DISABLED) {
                            OptimizationResult result = new OptimizationResult();
                            result.setResourceType("EC2");
                            result.setResourceId(instance.instanceId());
                            result.setCurrentState("Instance running without detailed monitoring");
                            result.setSuggestedAction("Enable detailed monitoring to track CPU utilization");
                            result.setSeverity("MEDIUM");
                            results.add(result);
                        }

                        // Check instance type optimization
                        if (instance.instanceType().toString().startsWith("t2.")) {
                            OptimizationResult result = new OptimizationResult();
                            result.setResourceType("EC2");
                            result.setResourceId(instance.instanceId());
                            result.setCurrentState("Using burstable instance type");
                            result.setSuggestedAction("Consider upgrading to t3 instance type for better performance");
                            result.setSeverity("LOW");
                            results.add(result);
                        }

                        // Check CPU utilization for last 7 days
                        if (isInstanceUnderutilized(cloudWatchClient, instance.instanceId())) {
                            OptimizationResult result = new OptimizationResult();
                            result.setResourceType("EC2");
                            result.setResourceId(instance.instanceId());
                            result.setCurrentState("Low CPU utilization (< 20%) for the last 7 days");
                            result.setSuggestedAction("Consider downsizing the instance to save costs");
                            result.setSeverity("HIGH");
                            results.add(result);
                        }
                    }
                }
            }
        }
        
        return results;
    }

    private boolean isInstanceUnderutilized(CloudWatchClient cloudWatchClient, String instanceId) {
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace("AWS/EC2")
                .metricName("CPUUtilization")
                .dimensions(Dimension.builder()
                    .name("InstanceId")
                    .value(instanceId)
                    .build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600) // 1 hour intervals
                .statistics(Statistic.MAXIMUM)
                .build();

            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            
            // Check if we have data points
            if (response.datapoints().isEmpty()) {
                return false;
            }

            // Check if any data point exceeds the threshold
            return response.datapoints().stream()
                .noneMatch(point -> point.maximum() > CPU_UTILIZATION_THRESHOLD);

        } catch (Exception e) {
            log.error("Error checking CPU utilization for instance {}: {}", instanceId, e.getMessage());
            return false;
        }
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

                // Check for lifecycle policies
                GetBucketLifecycleConfigurationResponse lifecycleResponse = s3Client.getBucketLifecycleConfiguration(
                    GetBucketLifecycleConfigurationRequest.builder().bucket(bucket.name()).build());
                
                if (lifecycleResponse.rules().isEmpty()) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("S3");
                    result.setResourceId(bucket.name());
                    result.setCurrentState("No lifecycle policies");
                    result.setSuggestedAction("Configure lifecycle policies to optimize storage costs");
                    result.setSeverity("MEDIUM");
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
                // Check for multi-AZ using available methods
                if (cluster.engine().equals("redis") && !cluster.engineVersion().contains("cluster")) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("ElastiCache");
                    result.setResourceId(cluster.cacheClusterId());
                    result.setCurrentState("Single-node Redis deployment");
                    result.setSuggestedAction("Consider using Redis cluster mode for high availability");
                    result.setSeverity("HIGH");
                    results.add(result);
                }

                // Check for backup retention
                if (cluster.snapshotRetentionLimit() < 7) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("ElastiCache");
                    result.setResourceId(cluster.cacheClusterId());
                    result.setCurrentState("Low backup retention");
                    result.setSuggestedAction("Increase snapshot retention period");
                    result.setSeverity("MEDIUM");
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
                // Check for deletion protection using available methods
                if (lb.state().code() == LoadBalancerStateEnum.ACTIVE && !lb.scheme().equals("internal")) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("LoadBalancer");
                    result.setResourceId(lb.loadBalancerArn());
                    result.setCurrentState("Public load balancer without deletion protection");
                    result.setSuggestedAction("Enable deletion protection for public load balancers");
                    result.setSeverity("HIGH");
                    results.add(result);
                }

                // Check for idle load balancers
                if (lb.state().code() == LoadBalancerStateEnum.ACTIVE) {
                    DescribeTargetGroupsResponse targetGroupsResponse = elbClient.describeTargetGroups(
                        DescribeTargetGroupsRequest.builder()
                            .loadBalancerArn(lb.loadBalancerArn())
                            .build());

                    boolean hasHealthyTargets = false;
                    for (software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup targetGroup : targetGroupsResponse.targetGroups()) {
                        DescribeTargetHealthResponse healthResponse = elbClient.describeTargetHealth(
                            DescribeTargetHealthRequest.builder()
                                .targetGroupArn(targetGroup.targetGroupArn())
                                .build());

                        hasHealthyTargets = healthResponse.targetHealthDescriptions().stream()
                            .anyMatch(health -> health.targetHealth().state() == TargetHealthStateEnum.HEALTHY);

                        if (hasHealthyTargets) break;
                    }

                    if (!hasHealthyTargets) {
                        OptimizationResult result = new OptimizationResult();
                        result.setResourceType("LoadBalancer");
                        result.setResourceId(lb.loadBalancerArn());
                        result.setCurrentState("No healthy targets");
                        result.setSuggestedAction("Consider removing idle load balancer if no longer needed");
                        result.setSeverity("MEDIUM");
                        results.add(result);
                    }
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
                // Check for provisioned concurrency using available methods
                if (function.memorySize() < 256 && function.timeout() > 30) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("Lambda");
                    result.setResourceId(function.functionName());
                    result.setCurrentState("Low memory with high timeout");
                    result.setSuggestedAction("Consider increasing memory allocation for better performance");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            }
        }
        
        return results;
    }
} 