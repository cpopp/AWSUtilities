# AWSUtilities

Provides a simple mechanism to estimate monthly costs based on current EC2 usage.  Takes into account price per hour for
instances (assuming only an on demand price) and EBS volume costs based on capacity, type, and whether provisioned IOPS
are in use.

## Usage
Make sure your AWS Access Keys are setup as described in the SDK Getting Started Guide (http://aws.amazon.com/developers/getting-started/java/)

File located at ~/.aws/credentials containing:

    [default]
    aws_access_key_id = YOUR_ACCESS_KEY_ID
    aws_secret_access_key = YOUR_SECRET_ACCESS_KEY

Run the main method found in EC2CostEstimator

## Output

A detailed list of EBS volumes will be logged starting with unattached volumes and then attached volumes.  The EBS volumes
are sorted with higest cost volumes first.

Next, a detailed listing of instances is logged starting with stopped instances followed by running instances.  Although
the stopped instances have no inherent per hour cost, each instance includes a total cost for all of the EBS volumes attached
in order to track stopped instances that may have high volume costs.  Instances are sorted with the highest cost instances
first.

Finally, a summary is given with per availability zone total costs and a final total of everything.

## Example

    -- EBS Costs --
    Idle Volumes
    EBSVolume [name=MY-SERVER-1, costPerMonth=$125.00, id=XXX, availabilityZone=us-east-1d, type=gp2, sizeInGB=1250, iops=3750, state=available, isInUse=false]
    EBSVolume [name=, costPerMonth=$4.00, id=XXX, availabilityZone=us-east-1e, type=gp2, sizeInGB=40, iops=120, state=available, isInUse=false]
    ...
    Attached volumes
    EBSVolume [name=MY-SERVER-2 (Data), costPerMonth=$672.50, id=XXX, availabilityZone=us-east-1d, type=io1, sizeInGB=700, iops=9000, state=in-use, isInUse=true]
    ...
    -- Instance Costs --
    Stopped Instances
    PricedInstance [name=MY-SERVER-3, owner=Doe, John, totalCostPerMonth=$10.00, instanceCostPerMonth=$0.00, volumeCostPerMonth=$10.00, availabilityZone=us-east-1d, instanceType=r3.xlarge, platform=windows, state=stopped, isRunning=false, volumes=[EBSVolume [name=MY-SERVER-3, costPerMonth=$10.00, id=XXX, availabilityZone=us-east-1d, type=gp2, sizeInGB=100, iops=300, state=in-use, isInUse=true]]]
    ...
    Running Instances
    PricedInstance [name=MY-SERVER-4, owner=Doe, Jane, totalCostPerMonth=$2,828.37, instanceCostPerMonth=$1,419.12, volumeCostPerMonth=$1,409.25, availabilityZone=us-east-1c, instanceType=r3.4xlarge, platform=windows, state=running, isRunning=true, volumes=[EBSVolume [name=MY-SERVER-4 (C), costPerMonth=$672.50, id=XXX, availabilityZone=us-east-1c, type=io1, sizeInGB=700, iops=9000, state=in-use, isInUse=true], EBSVolume [name=MY-SERVER-4 (D), costPerMonth=$282.50, id=XXX, availabilityZone=us-east-1c, type=io1, sizeInGB=700, iops=3000, state=in-use, isInUse=true], EBSVolume [name=MY-SERVER-4 (E), costPerMonth=$232.50, id=XXX, availabilityZone=us-east-1c, type=io1, sizeInGB=300, iops=3000, state=in-use, isInUse=true], EBSVolume [name=MY-SERVER-4 (F), costPerMonth=$213.75, id=XXX, availabilityZone=us-east-1c, type=io1, sizeInGB=150, iops=3000, state=in-use, isInUse=true], EBSVolume [name=MY-SERVER-4 (F), costPerMonth=$8.00, id=XXX, availabilityZone=us-east-1c, type=gp2, sizeInGB=80, iops=240, state=in-use, isInUse=true]]]
    
    Cost Summary
    Unattached EBS Volumes: $14.00
    Running instances in us-east-1c: $1,234.14
    Stopped instances in us-east-1c: $51.60
    Running instances in us-east-1d: $156.2
    Stopped instances in us-east-1d: $49.60
    Running instances in us-east-1e: $50.91
    Stopped instances in us-east-1e: $0.00
    Total Cost: $1,556.45

