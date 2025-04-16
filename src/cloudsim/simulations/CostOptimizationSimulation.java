package cloudsim.simulations;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;

public class CostOptimizationSimulation {
    // VM Types with different cost/performance ratios
    private static final VMType[] VM_TYPES = {
        new VMType("Small", 500, 1, 512, 0.05),  // 0.05$/hour
        new VMType("Medium", 1000, 2, 1024, 0.10),
        new VMType("Large", 2000, 4, 2048, 0.20)
    };

    public static void main(String[] args) {
        Log.printLine("Starting Cost Optimization Simulation...");

        try {
            // 1. Initialize CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // 2. Create Datacenter with pricing
            Datacenter datacenter = createDatacenter("CostAware-DC");

            // 3. Create Broker
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // 4. Create workloads
            List<Cloudlet> cloudletList = createWorkloads(brokerId, 50);

            // 5. Test different allocation strategies
            testStrategy(broker, "Cheapest-First", this::cheapestFirst);
            testStrategy(broker, "Performance-First", this::performanceFirst);
            testStrategy(broker, "Balanced", this::balancedApproach);

            CloudSim.stopSimulation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testStrategy(DatacenterBroker broker, String strategyName, 
                                   AllocationStrategy strategy) throws Exception {
        Log.printLine("\n=== Testing Strategy: " + strategyName + " ===");
        
        // Reset simulation
        CloudSim.init(1, Calendar.getInstance(), false);
        broker = createBroker();
        int brokerId = broker.getId();
        List<Cloudlet> cloudletList = createWorkloads(brokerId, 50);

        // Allocate VMs using the strategy
        List<Vm> vmList = strategy.allocateVMs(brokerId);
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // Run simulation
        CloudSim.startSimulation();

        // Calculate metrics
        List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
        double totalCost = calculateTotalCost(vmList, finishedCloudlets);
        double avgCompletionTime = calculateAvgCompletionTime(finishedCloudlets);

        Log.printLine(String.format(
            "Results - Cost: $%.2f | Avg Time: %.2f sec | VMs Used: %d",
            totalCost, avgCompletionTime, vmList.size()));
    }

    // Allocation Strategies
    private interface AllocationStrategy {
        List<Vm> allocateVMs(int brokerId);
    }

    private static List<Vm> cheapestFirst(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        // Allocate only small VMs
        for (int i = 0; i < 10; i++) {
            VMType type = VM_TYPES[0]; // Small
            vms.add(createVM(brokerId, i, type));
        }
        return vms;
    }

    private static List<Vm> performanceFirst(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        // Allocate only large VMs
        for (int i = 0; i < 3; i++) {
            VMType type = VM_TYPES[2]; // Large
            vms.add(createVM(brokerId, i, type));
        }
        return vms;
    }

    private static List<Vm> balancedApproach(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        // Mix of medium and small VMs
        for (int i = 0; i < 5; i++) {
            VMType type = i % 2 == 0 ? VM_TYPES[1] : VM_TYPES[0]; // Medium or Small
            vms.add(createVM(brokerId, i, type));
        }
        return vms;
    }

    // Helper methods
    private static Vm createVM(int brokerId, int id, VMType type) {
        return new Vm(
            id, brokerId, 
            type.mips, type.pes, type.ram, 
            1000, 10000, "Xen",
            new CloudletSchedulerTimeShared());
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        
        // Create high-capacity host
        for (int i = 0; i < 8; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(2000)));
        }

        hostList.add(new Host(
            0,
            new RamProvisionerSimple(16384),
            new BwProvisionerSimple(10000),
            1000000,
            peList,
            new VmSchedulerTimeShared(peList))
        );

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            "x86", "Linux", "Xen", 
            hostList, 10.0, 3.0, 0.05, 0.001, 0.0);

        try {
            return new Datacenter(
                name, characteristics, 
                new VmAllocationPolicySimple(hostList), 
                new ArrayList<>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static DatacenterBroker createBroker() {
        try {
            return new DatacenterBroker("CostAware-Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<Cloudlet> createWorkloads(int brokerId, int count) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();
        
        // Create mixed workload (small, medium, large tasks)
        for (int i = 0; i < count; i++) {
            long length = 1000 + (i % 3) * 2000; // Varies by task type
            cloudlets.add(new Cloudlet(
                i, length, 1, 300, 300,
                utilizationModel, utilizationModel, utilizationModel));
            cloudlets.get(i).setUserId(brokerId);
        }
        return cloudlets;
    }

    private static double calculateTotalCost(List<Vm> vms, List<Cloudlet> cloudlets) {
        double totalCost = 0;
        for (Vm vm : vms) {
            // Find VM type
            for (VMType type : VM_TYPES) {
                if (vm.getMips() == type.mips && vm.getRam() == type.ram) {
                    // Cost = hourly rate * (max completion time / 3600)
                    double maxTime = cloudlets.stream()
                        .filter(c -> c.getVmId() == vm.getId())
                        .mapToDouble(Cloudlet::getFinishTime)
                        .max().orElse(0);
                    totalCost += type.hourlyCost * (maxTime / 3600);
                    break;
                }
            }
        }
        return totalCost;
    }

    private static double calculateAvgCompletionTime(List<Cloudlet> cloudlets) {
        return cloudlets.stream()
            .mapToDouble(Cloudlet::getActualCPUTime)
            .average()
            .orElse(0);
    }

    // VM Type definition
    private static class VMType {
        String name;
        int mips;
        int pes;
        int ram;
        double hourlyCost;

        public VMType(String name, int mips, int pes, int ram, double hourlyCost) {
            this.name = name;
            this.mips = mips;
            this.pes = pes;
            this.ram = ram;
            this.hourlyCost = hourlyCost;
        }
    }
}
