package org.cp.ec2;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.cp.ec2.EBSUtilities.EBSVolume;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.EbsInstanceBlockDevice;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;

/**
 * Basic utility class providing instance retrieval mechanisms that add cost estimates and provide
 * additional linkage to the instance's EBS volumes.
 */
public class InstanceUtilities {
	
	private static Map<String, Double> priceByLinuxInstanceType = getLinuxInstancePriceMap();
	private static Map<String, Double> priceByWindowsInstanceType = getWindowsInstancePriceMap();
	
	/**
	 * Gets price by instance type and platform.  Platform ignored for now
	 */
	public static double getPricePerHour(String instanceType, String platform) {
		Double price = null;
		
		if(platform == null) {
			price = priceByLinuxInstanceType.get(instanceType);
		} else if(platform.equalsIgnoreCase("windows")) {
			price = priceByWindowsInstanceType.get(instanceType); 
		}
		
		if(price == null) {
			throw new RuntimeException("No price found for instance type " + instanceType + " and platform " + platform);
		} else {
			return price;
		}
	}
	
	public static class PricedInstance {
		private final String availabilityZone;
		private final String instanceType;
		private final String platform;
		private final String state;
		private final boolean isRunning;
		private final String name;
		private final String owner;
		private final List<EBSVolume> volumes;
	
		public PricedInstance(Instance instance, Map<String, EBSVolume> ebsVolumes) {
			this.availabilityZone = instance.getPlacement().getAvailabilityZone();
			this.instanceType = instance.getInstanceType();
			this.platform = instance.getPlatform();
			this.state = instance.getState().getName();
			this.isRunning = (instance.getState().getCode() & 0xFF) == 16;
			
			this.name = instance.getTags().stream()
					.filter(t -> t.getKey().equalsIgnoreCase("name"))
					.findFirst()
					.map(t -> t.getValue())
					.orElse("");
			
			this.owner = instance.getTags().stream()
					.filter(t -> t.getKey().equalsIgnoreCase("owner"))
					.findFirst()
					.map(t -> t.getValue())
					.orElse("");
			
			this.volumes = instance.getBlockDeviceMappings().stream()
					.map(InstanceBlockDeviceMapping::getEbs)
					.map(EbsInstanceBlockDevice::getVolumeId)
					.map(ebsVolumes::get)
					.sorted(Comparator.comparing(EBSVolume::getCostPerMonth).reversed())
					.collect(Collectors.toList());
		}

		public String getAvailabilityZone() {
			return availabilityZone;
		}

		public String getInstanceType() {
			return instanceType;
		}

		public String getPlatform() {
			return platform;
		}

		public String getState() {
			return state;
		}

		public boolean isRunning() {
			return isRunning;
		}

		public String getName() {
			return name;
		}
		
		public String getOwner() {
			return owner;
		}

		public List<EBSVolume> getVolumes() {
			return volumes;
		}
		
		public double getInstanceCostPerMonth() {
			if(isRunning()) {
				// 730 hours per month
				return 730 * InstanceUtilities.getPricePerHour(getInstanceType(), getPlatform());
			} else {
				// stopped instances don't cost anything
				return 0;
			}
		}
		
		public double getVolumeCostPerMonth() {
			return getVolumes().stream()
					.map(EBSVolume::getCostPerMonth)
					.collect(Collectors.summingDouble(Double::doubleValue));
		}
		
		public double getTotalCostPerMonth() {
			return getVolumeCostPerMonth() + getInstanceCostPerMonth();
		}

		@Override
		public String toString() {
			return "PricedInstance [name=" + name + ", owner=" + owner
					+ ", totalCostPerMonth=" + NumberFormat.getCurrencyInstance().format(getTotalCostPerMonth())
					+ ", instanceCostPerMonth=" + NumberFormat.getCurrencyInstance().format(getInstanceCostPerMonth())
					+ ", volumeCostPerMonth=" + NumberFormat.getCurrencyInstance().format(getVolumeCostPerMonth()) 
					+ ", availabilityZone=" + availabilityZone + ", instanceType=" + instanceType
					+ ", platform=" + platform + ", state=" + state + ", isRunning=" + isRunning 
					+ ", volumes=" + volumes + "]";
		}
		
		
	}
	
	public static List<PricedInstance> getInstances(List<EBSVolume> volumes) {
		AmazonEC2 ec2Client = new AmazonEC2Client();
		
		DescribeInstancesResult result = ec2Client.describeInstances();
		if(result.getNextToken() != null) {
			throw new RuntimeException("Need to implement paging for instances");
		}
		
		Map<String, EBSVolume> ebsVolumesById
			= volumes.stream().collect(Collectors.toMap(EBSVolume::getVolumeId, Function.identity()));
		
		return result.getReservations().stream()
			.flatMap(r -> r.getInstances().stream())
			.map(i -> new PricedInstance(i, ebsVolumesById))
			.collect(Collectors.toList());
	}
	
	/**
	 * On-Demand Linux Instance Prices pulled from 
	 * https://aws.amazon.com/ec2/pricing/ on 1/24/2016
	 */
	private static Map<String, Double> getLinuxInstancePriceMap() {
		Map<String, Double> prices = new HashMap<String, Double>();
		prices.put("t2.nano",0.0065);
		prices.put("t2.micro",0.013);
		prices.put("t2.small",0.026);
		prices.put("t2.medium",0.052);
		prices.put("t2.large",0.104);
		prices.put("m4.large",0.12);
		prices.put("m4.xlarge",0.239);
		prices.put("m4.2xlarge",0.479);
		prices.put("m4.4xlarge",0.958);
		prices.put("m4.10xlarge",2.394);
		prices.put("m3.medium",0.067);
		prices.put("m3.large",0.133);
		prices.put("m3.xlarge",0.266);
		prices.put("m3.2xlarge",0.532);
		prices.put("c4.large",0.105);
		prices.put("c4.xlarge",0.209);
		prices.put("c4.2xlarge",0.419);
		prices.put("c4.4xlarge",0.838);
		prices.put("c4.8xlarge",1.675);
		prices.put("c3.large",0.105);
		prices.put("c3.xlarge",0.21);
		prices.put("c3.2xlarge",0.42);
		prices.put("c3.4xlarge",0.84);
		prices.put("c3.8xlarge",1.68);
		prices.put("g2.2xlarge",0.65);
		prices.put("g2.8xlarge",2.6);
		prices.put("r3.large",0.166);
		prices.put("r3.xlarge",0.333);
		prices.put("r3.2xlarge",0.665);
		prices.put("r3.4xlarge",1.33);
		prices.put("r3.8xlarge",2.66);
		prices.put("i2.xlarge",0.853);
		prices.put("i2.2xlarge",1.705);
		prices.put("i2.4xlarge",3.41);
		prices.put("i2.8xlarge",6.82);
		prices.put("d2.xlarge",0.69);
		prices.put("d2.2xlarge",1.38);
		prices.put("d2.4xlarge",2.76);
		prices.put("d2.8xlarge",5.52);
		
		return prices;
	}
	
	/**
	 * On-Demand Windows Instance Prices pulled from 
	 * https://aws.amazon.com/ec2/pricing/ on 1/24/2016
	 */
	private static Map<String, Double> getWindowsInstancePriceMap() {
		Map<String, Double> prices = new HashMap<String, Double>();
		prices.put("t2.nano", 0.0088);
		prices.put("t2.micro", 0.018);
		prices.put("t2.small", 0.036);
		prices.put("t2.medium", 0.072);
		prices.put("t2.large", 0.134);
		prices.put("m4.large", 0.246);
		prices.put("m4.xlarge", 0.491);
		prices.put("m4.2xlarge", 0.983);
		prices.put("m4.4xlarge", 1.966);
		prices.put("m4.10xlarge", 4.914);
		prices.put("m3.medium", 0.13);
		prices.put("m3.large", 0.259);
		prices.put("m3.xlarge", 0.518);
		prices.put("m3.2xlarge", 1.036);
		prices.put("c4.large", 0.193);
		prices.put("c4.xlarge", 0.386);
		prices.put("c4.2xlarge", 0.773);
		prices.put("c4.4xlarge", 1.546);
		prices.put("c4.8xlarge", 3.091);
		prices.put("c3.large", 0.188);
		prices.put("c3.xlarge", 0.376);
		prices.put("c3.2xlarge", 0.752);
		prices.put("c3.4xlarge", 1.504);
		prices.put("c3.8xlarge", 3.008);
		prices.put("g2.2xlarge", 0.767);
		prices.put("g2.8xlarge", 2.878);
		prices.put("r3.large", 0.291);
		prices.put("r3.xlarge", 0.583);
		prices.put("r3.2xlarge", 1.045);
		prices.put("r3.4xlarge", 1.944);
		prices.put("r3.8xlarge", 3.5);
		prices.put("i2.xlarge", 0.973);
		prices.put("i2.2xlarge", 1.946);
		prices.put("i2.4xlarge", 3.891);
		prices.put("i2.8xlarge", 7.782);
		prices.put("d2.xlarge", 0.821);
		prices.put("d2.2xlarge", 1.601);
		prices.put("d2.4xlarge", 3.062);
		prices.put("d2.8xlarge", 6.198);
		
		return prices;
	}
}
