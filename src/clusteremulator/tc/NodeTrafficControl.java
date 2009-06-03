package clusteremulator.tc;

import java.io.IOException;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clusteremulator.gauge.EmulatedGauge;

public class NodeTrafficControl extends AbstractTrafficControl {

    private static Logger logger = LoggerFactory.getLogger(NodeTrafficControl.class);

    private boolean emulateBandwidth;
    private InetAddress[] hubAddrs;
    private int myRank;
    private double prevOutCap;

    public NodeTrafficControl(EmulatedGauge gauge, boolean emulateBandwidth, 
            InetAddress[] hubAddrs, int myRank) throws IOException {
        super(gauge);

        this.emulateBandwidth = emulateBandwidth;
        this.hubAddrs = hubAddrs;
        this.myRank = myRank;

        prevOutCap = -1;
    }

    /**
     * Sets up local traffic control to emulate a certain outgoing capacity to a
     * number of IP addresses.
     */
    @Override
    protected void updateEmulation(EmulatedGauge gauge, boolean firstTime) 
    throws IOException {
        if (!emulateBandwidth) {
            return;
        }

        double outCap = gauge.getOutgoingHostCapacity(myRank);

        if (outCap <= 0) {
            throw new IOException("cannot emulate the outgoing capacity of host " + 
                    myRank + ": it is set to " + outCap + " bytes/sec");
        }

        if (firstTime || outCap != prevOutCap) {
            String f = "Emulating outgoing capacity of %1$d: %2$.2f bytes/sec";
            logger.info(String.format(f, myRank, outCap)); 

            if (firstTime) {
                // add the root HTB qdisc
                execute(true, SUDO_COMMAND, TC_COMMAND, "qdisc", "add", "dev", 
                        INTERFACE, "root", "handle", "1:", "htb", "default", "2"); 
            }

            // add or change the outgoing cluster capacity
            execute(true, SUDO_COMMAND, TC_COMMAND, "class", 
                    (firstTime ? "add" : "change"), "dev", INTERFACE, "parent", 
                    "1:", "classid", "1:1", "htb", 
                    "rate", bandwidthValue(outCap), "mtu", mtuValue(), 
                    "burst", burstValue(outCap), "cburst", cburstValue(outCap));

            prevOutCap = outCap;

            if (firstTime) {
                // add rules so only the traffic going to certain addresses is
                // emulated
                for (int i = 0; i < hubAddrs.length; i++) {
                    String ip = hubAddrs[i].getHostAddress();
                    execute(true, SUDO_COMMAND, TC_COMMAND, "filter", "add", 
                            "dev", INTERFACE, "protocol", "ip", "parent", "1:0", 
                            "u32", "match", "ip", "dst", ip, "flowid", "1:1");
                }
            }
        }
    }

}
