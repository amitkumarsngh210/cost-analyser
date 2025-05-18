package com.costwise.service;

import com.costwise.model.OptimizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.*;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ec2CostOptimizer {
    private static final double CPU_UTILIZATION_THRESHOLD = 10.0;
    private static final double NETWORK_IO_THRESHOLD = 1000000; // 1 MB
    private static final int LOOKBACK_DAYS = 30;
    private static final Map<String, String> OLD_TO_NEW_INSTANCE_TYPES = Map.of(
        "t2", "t3",
        "m3", "m6i",
        "c4", "c7g"
    );

    public List<OptimizationResult> analyzeEc2Instances(String region) {
        List<OptimizationResult> results = new ArrayList<>();
        
        try (Ec2Client ec2Client = Ec2Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();
             CloudWatchClient cloudWatchClient = CloudWatchClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();
             PricingClient pricingClient = PricingClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1) // Pricing API is only available in us-east-1
                .build();
             AutoScalingClient autoScalingClient = AutoScalingClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build()) {

            DescribeInstancesResponse response = ec2Client.describeInstances();
            
            for (Reservation reservation : response.reservations()) {
                for (software.amazon.awssdk.services.ec2.model.Instance instance : reservation.instances()) {
                    // 1. Check for idle/underutilized instances
                    checkIdleInstances(cloudWatchClient, instance, results);
                    
                    // 2. Check for overprovisioned instances
                    checkOverprovisionedInstances(cloudWatchClient, instance, results);
                    
                    // 3. Check for old generation instances
                    checkOldGenerationInstances(instance, results);
                    
                    // 4. Check for On-Demand instances running 24/7
                    checkOnDemandInstances(cloudWatchClient, instance, results);
                    
                    // 5. Check for instances in high-cost regions
                    checkRegionPricing(pricingClient, instance, region, results);
                    
                    // 6. Check for stopped instances with EBS volumes
                    checkStoppedInstancesWithEbs(ec2Client, instance, results);
                    
                    // 7. Check for unused Elastic IPs
                    checkUnusedElasticIps(ec2Client, instance, results);
                    
                    // 8. Check for missing Auto Scaling
                    checkMissingAutoScaling(autoScalingClient, instance, results);
                    
                    // 9. Check for Spot Instance opportunities
                    checkSpotInstanceOpportunities(instance, results);
                    
                    // 10. Check for unused reservations
                    checkUnusedReservations(ec2Client, instance, results);
                    
                    // 11. Check for missing lifecycle policies
                    checkMissingLifecyclePolicies(instance, results);
                    
                    // 12. Check for high network transfer costs
                    checkNetworkTransferCosts(cloudWatchClient, instance, results);
                }
            }
        } catch (Exception e) {
            log.error("Error analyzing EC2 instances: {}", e.getMessage());
        }
        
        return results;
    }

    private void checkIdleInstances(CloudWatchClient cloudWatchClient, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

            // Get CPU utilization
            GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/EC2")
                .metricName("CPUUtilization")
                .dimensions(Dimension.builder().name("InstanceId").value(instance.instanceId()).build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600)
                .statistics(Statistic.AVERAGE)
                .build();

            // Get network I/O
            GetMetricStatisticsRequest networkRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/EC2")
                .metricName("NetworkIn")
                .dimensions(Dimension.builder().name("InstanceId").value(instance.instanceId()).build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600)
                .statistics(Statistic.SUM)
                .build();

            GetMetricStatisticsResponse cpuResponse = cloudWatchClient.getMetricStatistics(cpuRequest);
            GetMetricStatisticsResponse networkResponse = cloudWatchClient.getMetricStatistics(networkRequest);

            if (!cpuResponse.datapoints().isEmpty() && !networkResponse.datapoints().isEmpty()) {
                double avgCpu = cpuResponse.datapoints().stream()
                    .mapToDouble(Datapoint::average)
                    .average()
                    .orElse(0.0);

                double avgNetwork = networkResponse.datapoints().stream()
                    .mapToDouble(Datapoint::sum)
                    .average()
                    .orElse(0.0);

                if (avgCpu < CPU_UTILIZATION_THRESHOLD && avgNetwork < NETWORK_IO_THRESHOLD) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("EC2");
                    result.setResourceId(instance.instanceId());
                    result.setCurrentState("Idle instance (CPU < 10%, low network I/O)");
                    result.setSuggestedAction("Consider stopping or terminating the instance");
                    result.setSeverity("HIGH");
                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.error("Error checking idle instances: {}", e.getMessage());
        }
    }

    private void checkOverprovisionedInstances(CloudWatchClient cloudWatchClient, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

            // Get CPU and memory utilization
            GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/EC2")
                .metricName("CPUUtilization")
                .dimensions(Dimension.builder().name("InstanceId").value(instance.instanceId()).build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600)
                .statistics(Statistic.MAXIMUM)
                .build();

            GetMetricStatisticsRequest memoryRequest = GetMetricStatisticsRequest.builder()
                .namespace("System/Linux")
                .metricName("MemoryUtilization")
                .dimensions(Dimension.builder().name("InstanceId").value(instance.instanceId()).build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600)
                .statistics(Statistic.MAXIMUM)
                .build();

            GetMetricStatisticsResponse cpuResponse = cloudWatchClient.getMetricStatistics(cpuRequest);
            GetMetricStatisticsResponse memoryResponse = cloudWatchClient.getMetricStatistics(memoryRequest);

            if (!cpuResponse.datapoints().isEmpty() && !memoryResponse.datapoints().isEmpty()) {
                double maxCpu = cpuResponse.datapoints().stream()
                    .mapToDouble(Datapoint::maximum)
                    .max()
                    .orElse(0.0);

                double maxMemory = memoryResponse.datapoints().stream()
                    .mapToDouble(Datapoint::maximum)
                    .max()
                    .orElse(0.0);

                if (maxCpu < 40.0 && maxMemory < 40.0) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("EC2");
                    result.setResourceId(instance.instanceId());
                    result.setCurrentState("Overprovisioned instance (low resource utilization)");
                    result.setSuggestedAction("Consider downsizing to a smaller instance type");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.error("Error checking overprovisioned instances: {}", e.getMessage());
        }
    }

    private void checkOldGenerationInstances(software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        String instanceType = instance.instanceType().toString();
        String instanceFamily = instanceType.split("\\.")[0];

        if (OLD_TO_NEW_INSTANCE_TYPES.containsKey(instanceFamily)) {
            OptimizationResult result = new OptimizationResult();
            result.setResourceType("EC2");
            result.setResourceId(instance.instanceId());
            result.setCurrentState("Using older generation instance type: " + instanceType);
            result.setSuggestedAction("Consider migrating to " + OLD_TO_NEW_INSTANCE_TYPES.get(instanceFamily) + " family");
            result.setSeverity("MEDIUM");
            results.add(result);
        }
    }

    private void checkOnDemandInstances(CloudWatchClient cloudWatchClient, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        if (instance.instanceLifecycle() == null) { // On-Demand instance
            try {
                Instant endTime = Instant.now();
                Instant startTime = endTime.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

                GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace("AWS/EC2")
                    .metricName("CPUUtilization")
                    .dimensions(Dimension.builder().name("InstanceId").value(instance.instanceId()).build())
                    .startTime(startTime)
                    .endTime(endTime)
                    .period(3600)
                    .statistics(Statistic.AVERAGE)
                    .build();

                GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
                
                if (!response.datapoints().isEmpty()) {
                    double avgCpu = response.datapoints().stream()
                        .mapToDouble(Datapoint::average)
                        .average()
                        .orElse(0.0);

                    if (avgCpu > 0) { // Instance is running
                        OptimizationResult result = new OptimizationResult();
                        result.setResourceType("EC2");
                        result.setResourceId(instance.instanceId());
                        result.setCurrentState("On-Demand instance running 24/7");
                        result.setSuggestedAction("Consider using Reserved Instances or Savings Plans");
                        result.setSeverity("HIGH");
                        results.add(result);
                    }
                }
            } catch (Exception e) {
                log.error("Error checking On-Demand instances: {}", e.getMessage());
            }
        }
    }

    private void checkRegionPricing(PricingClient pricingClient, software.amazon.awssdk.services.ec2.model.Instance instance, String currentRegion, List<OptimizationResult> results) {
        try {
            GetProductsRequest request = GetProductsRequest.builder()
                .serviceCode("AmazonEC2")
                .filters(
                    software.amazon.awssdk.services.pricing.model.Filter.builder()
                        .type("TERM_MATCH")
                        .field("instanceType")
                        .value(instance.instanceType().toString())
                        .build()
                )
                .build();

            GetProductsResponse response = pricingClient.getProducts(request);
            
            // Compare prices across regions and suggest cheaper alternatives
            // Implementation depends on the pricing API response format
            // This is a simplified example
            if (!response.priceList().isEmpty()) {
                OptimizationResult result = new OptimizationResult();
                result.setResourceType("EC2");
                result.setResourceId(instance.instanceId());
                result.setCurrentState("Instance running in " + currentRegion);
                result.setSuggestedAction("Consider moving to a lower-cost region");
                result.setSeverity("MEDIUM");
                results.add(result);
            }
        } catch (Exception e) {
            log.error("Error checking region pricing: {}", e.getMessage());
        }
    }

    private void checkStoppedInstancesWithEbs(Ec2Client ec2Client, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        if (instance.state().name() == InstanceStateName.STOPPED) {
            try {
                DescribeVolumesRequest request = DescribeVolumesRequest.builder()
                    .filters(software.amazon.awssdk.services.ec2.model.Filter.builder()
                        .name("attachment.instance-id")
                        .values(instance.instanceId())
                        .build())
                    .build();

                DescribeVolumesResponse response = ec2Client.describeVolumes(request);
                
                if (!response.volumes().isEmpty()) {
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("EC2");
                    result.setResourceId(instance.instanceId());
                    result.setCurrentState("Stopped instance with attached EBS volumes");
                    result.setSuggestedAction("Consider creating snapshots and removing unused volumes");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            } catch (Exception e) {
                log.error("Error checking stopped instances with EBS: {}", e.getMessage());
            }
        }
    }

    private void checkUnusedElasticIps(Ec2Client ec2Client, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        try {
            DescribeAddressesRequest request = DescribeAddressesRequest.builder()
                .filters(software.amazon.awssdk.services.ec2.model.Filter.builder()
                    .name("instance-id")
                    .values(instance.instanceId())
                    .build())
                .build();

            DescribeAddressesResponse response = ec2Client.describeAddresses(request);
            
            if (!response.addresses().isEmpty() && instance.state().name() == InstanceStateName.STOPPED) {
                OptimizationResult result = new OptimizationResult();
                result.setResourceType("EC2");
                result.setResourceId(instance.instanceId());
                result.setCurrentState("Stopped instance with associated Elastic IP");
                result.setSuggestedAction("Consider releasing the Elastic IP");
                result.setSeverity("MEDIUM");
                results.add(result);
            }
        } catch (Exception e) {
            log.error("Error checking unused Elastic IPs: {}", e.getMessage());
        }
    }

    private void checkMissingAutoScaling(AutoScalingClient autoScalingClient, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        try {
            DescribeAutoScalingInstancesRequest request = DescribeAutoScalingInstancesRequest.builder()
                .instanceIds(instance.instanceId())
                .build();

            DescribeAutoScalingInstancesResponse response = autoScalingClient.describeAutoScalingInstances(request);
            
            if (response.autoScalingInstances().isEmpty()) {
                OptimizationResult result = new OptimizationResult();
                result.setResourceType("EC2");
                result.setResourceId(instance.instanceId());
                result.setCurrentState("Instance not part of an Auto Scaling Group");
                result.setSuggestedAction("Consider adding to an Auto Scaling Group for better scalability");
                result.setSeverity("MEDIUM");
                results.add(result);
            }
        } catch (Exception e) {
            log.error("Error checking Auto Scaling: {}", e.getMessage());
        }
    }

    private void checkSpotInstanceOpportunities(software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        if (instance.instanceLifecycle() == null) { // On-Demand instance
            OptimizationResult result = new OptimizationResult();
            result.setResourceType("EC2");
            result.setResourceId(instance.instanceId());
            result.setCurrentState("Using On-Demand instance");
            result.setSuggestedAction("Consider using Spot Instances for non-critical workloads");
            result.setSeverity("MEDIUM");
            results.add(result);
        }
    }

    private void checkUnusedReservations(Ec2Client ec2Client, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        try {
            DescribeReservedInstancesRequest request = DescribeReservedInstancesRequest.builder()
                .filters(software.amazon.awssdk.services.ec2.model.Filter.builder()
                    .name("instance-type")
                    .values(instance.instanceType().toString())
                    .build())
                .build();

            DescribeReservedInstancesResponse response = ec2Client.describeReservedInstances(request);
            
            if (!response.reservedInstances().isEmpty()) {
                OptimizationResult result = new OptimizationResult();
                result.setResourceType("EC2");
                result.setResourceId(instance.instanceId());
                result.setCurrentState("Instance type has available Reserved Instance capacity");
                result.setSuggestedAction("Consider purchasing Reserved Instances for long-term cost savings");
                result.setSeverity("MEDIUM");
                results.add(result);
            }
        } catch (Exception e) {
            log.error("Error checking unused reservations: {}", e.getMessage());
        }
    }

    private void checkMissingLifecyclePolicies(software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        // Check for tags indicating non-production environment
        boolean isNonProd = instance.tags().stream()
            .anyMatch(tag -> tag.key().equals("Environment") && 
                           (tag.value().equalsIgnoreCase("dev") || 
                            tag.value().equalsIgnoreCase("test") || 
                            tag.value().equalsIgnoreCase("staging")));

        if (isNonProd) {
            OptimizationResult result = new OptimizationResult();
            result.setResourceType("EC2");
            result.setResourceId(instance.instanceId());
            result.setCurrentState("Non-production instance without lifecycle policies");
            result.setSuggestedAction("Implement automated shutdown/start schedules");
            result.setSeverity("MEDIUM");
            results.add(result);
        }
    }

    private void checkNetworkTransferCosts(CloudWatchClient cloudWatchClient, software.amazon.awssdk.services.ec2.model.Instance instance, List<OptimizationResult> results) {
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace("AWS/EC2")
                .metricName("NetworkOut")
                .dimensions(Dimension.builder().name("InstanceId").value(instance.instanceId()).build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600)
                .statistics(Statistic.SUM)
                .build();

            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            
            if (!response.datapoints().isEmpty()) {
                double totalNetworkOut = response.datapoints().stream()
                    .mapToDouble(Datapoint::sum)
                    .sum();

                if (totalNetworkOut > 1000000000) { // 1 GB
                    OptimizationResult result = new OptimizationResult();
                    result.setResourceType("EC2");
                    result.setResourceId(instance.instanceId());
                    result.setCurrentState("High network transfer costs");
                    result.setSuggestedAction("Consider using S3 Transfer Acceleration or CDN");
                    result.setSeverity("MEDIUM");
                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.error("Error checking network transfer costs: {}", e.getMessage());
        }
    }
} 