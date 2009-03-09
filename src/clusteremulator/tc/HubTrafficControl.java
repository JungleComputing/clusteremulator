package clusteremulator.tc;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clusteremulator.Config;
import clusteremulator.gauge.EmulatedGauge;
import clusteremulator.gauge.EmulationGauge;

public class HubTrafficControl extends AbstractTrafficControl implements Config {

    private Logger logger = LoggerFactory.getLogger(HubTrafficControl.class);

    private boolean emulateDelay;
    private boolean emulateBandwidth;
    private String myClusterName;
    private Map<String, InetAddress[]> clusterHubMap;
    private InetAddress[][] hostAddrs;
    private EmulationGauge previous; 

    public HubTrafficControl(EmulatedGauge gauge, boolean emulateDelay, 
            boolean emulateBandwidth, String myClusterName,
            Map<String, InetAddress[]> clusterHubMap, InetAddress[][] hostAddrs)
    throws IOException {
        super(gauge);

        if (emulateDelay && !emulateBandwidth) {
            throw new IllegalArgumentException(
            "only emulating delay has not been implemented");
        }

        this.emulateDelay = emulateDelay;
        this.emulateBandwidth = emulateBandwidth;
        this.myClusterName = myClusterName;
        this.clusterHubMap = clusterHubMap;
        this.hostAddrs = hostAddrs;

        previous = new EmulationGauge(gauge.getClusterNames());
    }

    /**
     * Sets up local traffic control to emulate a certain outgoing capacity to a
     * number of IP addresses.
     */
    protected void updateEmulation(EmulatedGauge gauge, boolean firstTime)
    throws IOException
    {
        if (!emulateBandwidth || !EMULATE_ON_HUBS) {
            return;
        }

        /*
         * DON'T PANIC
         * 
         * TC hierarchy used in this method:
         * 
         * 1: root qdisc
         * |
         * +-- 1:1 HTB class for outgoing cluster capacity
         * |   |
         * |   +-- 2: HTB root qdisc for link bandwidth  
         * |       |
         * |       +-- 2:1 HTB class for link to first other cluster
         * |       |   |
         * |       |   +-- 4: NetEm qdisc for delay to first other cluster
         * |       |
         * |       +-- 2:2 HTB class for link to second other cluster
         * |       |   |
         * |       |   +-- 5: NetEm qdisc for delay to first other cluster
         * |       |
         * |       +-- etc. for links to all other hubs
         * | 
         * +-- 1:2 HTB class for incoming cluster capacity
         *     |
         *     +-- 3: HTB root qdisc for incoming host capacity
         *         |
         *         +-- 3:1 HTB class for incoming capacity of first host in 
         *         |       my cluster
         *         | 
         *         +-- 3:2 HTB class for incoming capacity of second host in 
         *         |       my cluster
         *         | 
         *         +-- etc. for all other hosts in my cluster
         */
        int majorHandleDelay = 4; // first major handle for the NetEm qdiscs
        int minorHandleLinks = 1; // first minor handle for the HTB classes for link bandwidth
        int minorHandleHostInCap = 1; // first minor handle for the HTB classes for incoming host capacity

        // get the incoming and outgoing capacity values
        double outCap = gauge.getOutgoingClusterCapacity(myClusterName);
        double prevOutCap = previous.getOutgoingClusterCapacity(myClusterName); 

        if (outCap <= 0) {
            throw new IOException(
                    "cannot emulate the outgoing capacity of cluster "
                    + myClusterName + ": it is set to " + outCap + " bytes/sec");
        }

        double inCap = gauge.getIncomingClusterCapacity(myClusterName);
        double prevInCap = previous.getIncomingClusterCapacity(myClusterName); 

        if (inCap <= 0) {
            throw new IOException(
                    "cannot emulate the incoming capacity of cluster "
                    + myClusterName + ": it is set to " + inCap + " bytes/sec");
        }

        String editCommand = firstTime ? "add" : "change";

        // add the cluster capacity constraints
        if (firstTime) {
            execute(true, SUDO_COMMAND, TC_COMMAND, "qdisc", "add", "dev", INTERFACE,
                    "root", "handle", "1:", "htb", "default", "3"); 
        }

        if (firstTime || prevOutCap != outCap) {
            logger.info("Emulating outgoing capacity of " + myClusterName + 
                    ": " + outCap + " bytes/sec");

            execute(true, SUDO_COMMAND, TC_COMMAND, "class", editCommand, 
                    "dev", INTERFACE, "parent", "1:", "classid", "1:1", "htb", 
                    "rate", bandwidthValue(outCap), "mtu", mtuValue(),
                    "burst", burstValue(outCap), "cburst", cburstValue(outCap));

            previous.updateOutgoingClusterCapacity(myClusterName, outCap);
        }

        if (firstTime || prevInCap != inCap) {
            logger.info("Emulating incoming capacity of " + myClusterName + ": "
                    + inCap + " bytes/sec");

            execute(true, SUDO_COMMAND, TC_COMMAND, "class", editCommand, "dev",
                    INTERFACE, "parent", "1:", "classid", "1:2", "htb", 
                    "rate", bandwidthValue(inCap), "mtu", mtuValue(),
                    "burst", burstValue(inCap), "cburst", cburstValue(inCap));

            previous.updateIncomingClusterCapacity(myClusterName, inCap);
        }

        // add the root qdisc of the link constraints
        if (firstTime) {
            execute(true, SUDO_COMMAND, TC_COMMAND, "qdisc", "add", "dev", 
                    INTERFACE, "parent", "1:1", "handle", "2:", "htb"); 
        }

        // add the bandwidth and delay constaints to all destination clusters
        for (String destClusterName : gauge.getDistinctClusterNames()) {
            if (!destClusterName.equals(myClusterName)) {

                double bandwidth = gauge.getBandwidth(myClusterName, destClusterName);
                double prevBandwidth = previous.getBandwidth(myClusterName, 
                        destClusterName);

                if (bandwidth <= 0) {
                    throw new IOException("cannot emulate the bandwidth from cluster "
                            + myClusterName + " to cluster " + destClusterName
                            + ": it is set to " + bandwidth + " bytes/sec");
                }

                if (firstTime || bandwidth != prevBandwidth) {
                    logger.info("Emulating bandwidth from " + myClusterName + 
                            " to " + destClusterName + ": " + bandwidth + 
                            " bytes/sec");

                    // add the bandwidth emulation
                    execute(true, SUDO_COMMAND, TC_COMMAND, "class", editCommand, 
                            "dev", INTERFACE, "parent", "2:", "classid", 
                            "2:" + minorHandleLinks, "htb", "rate", 
                            bandwidthValue(bandwidth), "mtu", mtuValue(), 
                            "burst", burstValue(bandwidth), "cburst", 
                            cburstValue(bandwidth));

                    previous.updateBandwidth(myClusterName, destClusterName, bandwidth);
                }

                // add the latency emulation
                if (emulateDelay) {
                    double delay = gauge.getDelay(myClusterName, destClusterName);
                    double prevDelay = gauge.getDelay(myClusterName, destClusterName);

                    if (delay < 0) {
                        throw new IOException(
                                "cannot emulate the delay from cluster "
                                + myClusterName + ": it is set to " + delay
                                + " seconds");
                    }

                    if (firstTime || delay != prevDelay) {
                        logger.info("Emulating delay from " + myClusterName
                                + " to " + destClusterName + ": " + delay + " sec");

                        execute(true, SUDO_COMMAND, TC_COMMAND, "qdisc", editCommand,
                                "dev", INTERFACE, "parent", "2:" + minorHandleLinks,
                                "handle", majorHandleDelay + ":", "netem", 
                                "delay", delayValue(delay));

                        previous.updateDelay(myClusterName, destClusterName, delay);
                    }
                }

                if (firstTime) {
                    // add the filters, so packets to the destination clusters
                    // get the right traffic control
                    InetAddress[] hubAddrs = clusterHubMap.get(destClusterName);

                    for (int i = 0; i < hubAddrs.length; i++) {
                        String ip = hubAddrs[i].getHostAddress();

                        // all packets to a destination cluster should go to the
                        // outgoing capacity qdisc
                        execute(true, SUDO_COMMAND, TC_COMMAND, "filter", "add", 
                                "dev", INTERFACE, "protocol", "ip", "parent", 
                                "1:0", "u32", "match", "ip", "dst", ip, "flowid", 
                                "1:1");

                        // all packets to a destination cluster should go their
                        // link's HTB and Netem qdisc
                        execute(true, SUDO_COMMAND, TC_COMMAND, "filter", "add", 
                                "dev", INTERFACE, "protocol", "ip", "parent", 
                                "2:0", "u32", "match", "ip", "dst", ip, "flowid", 
                                "2:" + minorHandleLinks);
                    }
                }

                minorHandleLinks++;
                majorHandleDelay++;
            }
        }

        // add the root qdisc of the incoming host capacity constraints
        if (firstTime) {
            execute(true, SUDO_COMMAND, TC_COMMAND, "qdisc", "add", "dev", 
                    INTERFACE, "parent", "1:2", "handle", "3:", "htb");
        }

        // add the individual incoming capacity constraints of all nodes in my
        // cluster
        String[] clusterNames = gauge.getClusterNames();

        for (int rank = 0; rank < clusterNames.length; rank++) {
            if (clusterNames[rank].equals(myClusterName)) {
                // found a host in my cluster; add it's incoming capacity to our
                // incoming HTB qdisc

                double hostInCap = gauge.getIncomingHostCapacity(rank);
                double prevHostInCap = previous.getIncomingHostCapacity(rank);

                if (firstTime || hostInCap != prevHostInCap) {
                    logger.info("Emulating incoming capacity of " + rank + ": "
                            + hostInCap + " bytes/sec");

                    // add the incoming capacity emulation
                    execute(true, SUDO_COMMAND, TC_COMMAND, "class", editCommand,
                            "dev", INTERFACE, "parent", "3:", "classid", 
                            "3:" + minorHandleHostInCap, "htb", 
                            "rate", bandwidthValue(hostInCap), 
                            "mtu", mtuValue(), "burst", burstValue(hostInCap), 
                            "cburst", cburstValue(hostInCap)); 

                    previous.updateIncomingHostCapacity(rank, hostInCap);
                }

                if (firstTime) {
                    // add the filters, so packets to the host get the right
                    // incoming capacity traffic control
                    for (int i = 0; i < hostAddrs[rank].length; i++) {
                        String ip = hostAddrs[rank][i].getHostAddress();

                        // all packets to a host should go to the incoming
                        // capacity qdisc
                        execute(true, SUDO_COMMAND, TC_COMMAND, "filter", "add", 
                                "dev", INTERFACE, "protocol", "ip", "parent", 
                                "1:0", "u32", "match", "ip", "dst", ip, 
                                "flowid", "1:2");

                        // all packets to a host should go to its own HTB class
                        execute(true, SUDO_COMMAND, TC_COMMAND, "filter", "add", 
                                "dev", INTERFACE, "protocol", "ip", "parent", 
                                "3:0", "u32", "match", "ip", "dst", ip, "flowid", 
                                "3:" + minorHandleHostInCap);
                    }
                }

                minorHandleHostInCap++;
            }
        }
    }

}
