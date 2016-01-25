package org.cp.ec2;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.cp.ec2.EBSUtilities.EBSVolume;
import org.cp.ec2.InstanceUtilities.PricedInstance;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

/**
 * Estimates EC2 costs for the month based on current usage. (see main())
 * 
 * Calculation is based on cost per hour for instances and fixed
 * cost for EBS volumes.  All instances are assumed to use
 * on demand pricing with no extra costs included.
 */
public class EC2CostEstimator {
	
	public static void main(String[] args) throws Exception {
		AmazonEC2 ec2Client = new AmazonEC2Client();
		
		System.out.println("-- EBS Costs --");
		List<EBSVolume> volumes = EBSUtilities.getEbsVolumes();
		
		System.out.println("Idle Volumes");
		// print idle volumes
		volumes.stream()
			.filter(v -> !v.isInUse())
			.sorted(Comparator.comparing(EBSVolume::getCostPerMonth).reversed())
			.forEach(System.out::println);
		
		System.out.println();
		System.out.println("Attached volumes");
		// print attached volumes
		volumes.stream()
			.filter(v -> v.isInUse())
			.sorted(Comparator.comparing(EBSVolume::getCostPerMonth).reversed())
			.forEach(System.out::println);
		
		System.out.println();
		System.out.println("-- Instance Costs --");
		DescribeInstancesResult result = ec2Client.describeInstances();
		if(result.getNextToken() != null) {
			throw new RuntimeException("Need to implement paging for instances");
		}
		
		List<PricedInstance> instances = InstanceUtilities.getInstances(volumes);
		
		Map<String, List<PricedInstance>> instancesByAZ = instances.stream()
				.collect(Collectors.groupingBy(PricedInstance::getAvailabilityZone));
			
		System.out.println("Stopped Instances");
		instances.stream()
			.filter(instance -> !instance.isRunning())
			.sorted(Comparator.comparing(PricedInstance::getTotalCostPerMonth).reversed())
			.forEach(System.out::println);
		
		System.out.println();
		System.out.println("Running Instances");
		instances.stream()
			.filter(PricedInstance::isRunning)
			.sorted(Comparator.comparing(PricedInstance::getTotalCostPerMonth).reversed())
			.forEach(System.out::println);
		
		System.out.println();
		System.out.println("Cost Summary");
		
		// first is the cost for unattached EBS volumes
		double costForUnattachedVolumes = volumes.stream()
			.filter(v -> !v.isInUse())
			.map(EBSVolume::getCostPerMonth)
			.collect(Collectors.summingDouble(Double::doubleValue));
		
		System.out.println("Unattached EBS Volumes: " + NumberFormat.getCurrencyInstance().format(costForUnattachedVolumes));
		
		double totalCost = costForUnattachedVolumes;
		
		for(Entry<String, List<PricedInstance>> azWithInstances : instancesByAZ.entrySet()) {
			double azCostForRunningInstances = azWithInstances.getValue().stream()
					.filter(instance -> instance.isRunning())
					.map(PricedInstance::getTotalCostPerMonth)
					.collect(Collectors.summingDouble(Double::doubleValue));
			
			double azCostForStoppedInstances = azWithInstances.getValue().stream()
					.filter(instance -> !instance.isRunning())
					.map(PricedInstance::getTotalCostPerMonth)
					.collect(Collectors.summingDouble(Double::doubleValue));
			
			totalCost += azCostForRunningInstances;
			totalCost += azCostForStoppedInstances;
			
			System.out.println("Running instances in " + azWithInstances.getKey() + ": " + NumberFormat.getCurrencyInstance().format(azCostForRunningInstances));
			System.out.println("Stopped instances in " + azWithInstances.getKey() + ": " + NumberFormat.getCurrencyInstance().format(azCostForStoppedInstances));
		}
	
		System.out.println("Total Cost: " + NumberFormat.getCurrencyInstance().format(totalCost));
	}
}
