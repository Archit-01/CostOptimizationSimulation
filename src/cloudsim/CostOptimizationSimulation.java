package cloudsim.simulations;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;
import java.util.*;

public class WebAppSimulation {
    private static final int BUSINESS_HOUR_START = 8;
    private static final int BUSINESS_HOUR_END = 17;
    private static final double SCALE_UP_THRESHOLD = 80.0;
    private static final double SCALE_DOWN_THRESHOLD = 30.0;
    private static final int INITIAL_VMS = 2;
    private static final int SIMULATION_HOURS = 24;

    public static void main(String[] args) {
        try {
            Log.printLine("Starting Web Application Simulation...");

            // 1. Initialize CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // 2. Create Datacenter
            Datacenter datacenter = createDatacenter("WebApp-Datacenter");

            // 3. Create Broker
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // 4. Create initial VMs
            List<Vm> vmList = createInitialVMs(brokerId);
            broker.submitVmList(vmList);

            // 5. Simulate traffic pattern
            List<Cloudlet> cloudletList = new ArrayList<>();
            for (int hour = 0; hour < SIMULATION_HOURS; hour++) {
                int requests = isBusinessHour(hour) ? 150 : 50;
                List<Cloudlet> hourCloudlets = createRequests(brokerId, requests, hour);
                cloudletList.addAll(hourCloudlets);

                // Auto-scale based on load
                double utilization = calculateUtilization(vmList, hour);
                autoScale(broker, vmList, utilization, hour);
            }

            broker.submitCloudletList(cloudletList);

            // 6. Start simulation
            CloudSim.startSimulation();

            // 7. Print results
            printResults(broker, vmList);

            CloudSim.stopSimulation();
            Log.printLine("Simulation completed!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        int mips = 1000;
        
        // Create PEs
        for (int i = 0; i < 4; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        // Create host
        hostList.add(new Host(
            0,
            new RamProvisionerSimple(16384), // 16GB RAM
            new BwProvisionerSimple(10000), // 10Gbps
            1000000, // 1TB Storage
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
            return new DatacenterBroker("WebApp-Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<Vm> createInitialVMs(int brokerId) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < INITIAL_VMS; i++) {
            vmList.add(new Vm(
                i, brokerId, 1000, 2, 2048, 1000, 10000, "Xen",
                new CloudletSchedulerTimeShared()));
        }
        return vmList;
    }

    private static List<Cloudlet> createRequests(int brokerId, int count, int hour) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();
        
        for (int i = 0; i < count; i++) {
            Cloudlet cloudlet = new Cloudlet(
                (hour * 1000) + i, // Unique ID
                2000, // length
                1, // pesNumber
                300, 300, // fileSize, outputSize
                utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    private static boolean isBusinessHour(int hour) {
        return hour >= BUSINESS_HOUR_START && hour <= BUSINESS_HOUR_END;
    }

    private static double calculateUtilization(List<Vm> vms, int hour) {
        // Simulate higher utilization during business hours
        return isBusinessHour(hour) ? 85.0 : 25.0;
    }

    private static void autoScale(DatacenterBroker broker, List<Vm> vms, 
                                double utilization, int hour) {
        Log.printLine(String.format("\nHour %d - Utilization: %.2f%%", hour, utilization));
        
        if (utilization > SCALE_UP_THRESHOLD) {
            int newVmId = vms.size();
            Vm newVm = new Vm(
                newVmId, broker.getId(), 1000, 2, 2048, 1000, 10000, "Xen",
                new CloudletSchedulerTimeShared());
            
            vms.add(newVm);
            broker.submitVmList(List.of(newVm));
            Log.printLine("Scaling UP - Added VM " + newVmId);
        } 
        else if (utilization < SCALE_DOWN_THRESHOLD && vms.size() > INITIAL_VMS) {
            Vm removedVm = vms.remove(vms.size() - 1);
            broker.destroyVm(removedVm);
            Log.printLine("Scaling DOWN - Removed VM " + removedVm.getId());
        }
    }

    private static void printResults(DatacenterBroker broker, List<Vm> vms) {
        List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
        
        System.out.println("\n========== FINAL RESULTS ==========");
        System.out.println("Total VMs created: " + vms.size());
        System.out.println("Total requests processed: " + finishedCloudlets.size());
        
        double totalTime = 0;
        for (Cloudlet cloudlet : finishedCloudlets) {
            totalTime += cloudlet.getActualCPUTime();
        }
        System.out.printf("Average response time: %.2f seconds\n", 
                         totalTime / finishedCloudlets.size());
    }
}
