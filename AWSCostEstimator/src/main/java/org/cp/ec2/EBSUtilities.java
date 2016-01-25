package org.cp.ec2;

import java.text.NumberFormat;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Volume;

/**
 * Basic utility class to wrap the Amazon APIs around EBS volumes to simplify them slightly
 * and attach cost estimates to the returned volumes.
 */
public class EBSUtilities {
	/**
	 * Get a simplified list of EBS volumes including support for retrieving
	 * estimate cost for the volume.
	 */
	public static List<EBSVolume> getEbsVolumes() {
		AmazonEC2 ec2Client = new AmazonEC2Client();
		
		DescribeVolumesResult volumeResult = ec2Client.describeVolumes();
		if(volumeResult.getNextToken() != null) {
			throw new RuntimeException("Paging not implemented");
		}
		
		return volumeResult.getVolumes().stream()
				.map(EBSVolume::new)
				.collect(Collectors.toList());
	}
	
	public static class EBSVolume {
		private final String volumeId;
		private final String availabilityZone;
		private final String state;
		private boolean isInUse;
		private final String type;
		private final Integer sizeInGB;
		private final Integer iops;
		private final String name;
		
		public EBSVolume(Volume volume) {
			this.volumeId = volume.getVolumeId();
			this.availabilityZone = volume.getAvailabilityZone();
			this.state = volume.getState();
			this.isInUse = volume.getState().equalsIgnoreCase("in-use");
			this.type = volume.getVolumeType();
			this.sizeInGB = volume.getSize();
			this.iops = volume.getIops();
			
			this.name = volume.getTags().stream()
				.filter(t -> t.getKey().equalsIgnoreCase("name"))
				.findFirst()
				.map(t -> t.getValue())
				.orElse("");
		}
		
		public String getVolumeId() {
			return volumeId;
		}

		public String getAvailabilityZone() {
			return availabilityZone;
		}

		public String getState() {
			return state;
		}
		
		public boolean isInUse() {
			return isInUse;
		}

		public String getType() {
			return type;
		}

		public Integer getSizeInGB() {
			return sizeInGB;
		}

		public Integer getIops() {
			return iops;
		}

		public String getName() {
			return name;
		}

		public double getCostPerMonth() {
			double costPerMonth = 0;
			
			if(type.equals("standard")) {
				// magnetic disk, also has per 1 million I/O requests not accounted for
				costPerMonth = 0.05 * sizeInGB; 
			} else if(type.equals("gp2")) {
				// standard SSD without provisioned IOPS
				costPerMonth = 0.10 * sizeInGB;
			} else if(type.equals("io1")) {
				// SSD with provisioned iops
				costPerMonth = 0.125 * sizeInGB;
				costPerMonth += 0.065 * iops;
			} else {
				throw new RuntimeException("Unknown volume type: " + type);
			}
			
			return costPerMonth;
		}

		@Override
		public String toString() {
			return "EBSVolume [name=" + name + ", costPerMonth=" + NumberFormat.getCurrencyInstance().format(getCostPerMonth())
					+ ", id=" + volumeId + ", availabilityZone=" + availabilityZone  + ", type=" + type + ", sizeInGB=" + sizeInGB 
					+ ", iops=" + iops  + ", state=" + state + ", isInUse=" + isInUse + "]";
		}
	}
}
