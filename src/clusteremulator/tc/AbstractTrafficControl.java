package clusteremulator.tc;

import ibis.util.RunProcess;

import java.io.IOException;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clusteremulator.Config;
import clusteremulator.gauge.EmulatedGauge;
import clusteremulator.util.Convert;
import clusteremulator.util.Defense;

public abstract class AbstractTrafficControl implements Observer, Config {

    private static final int MTU = 9000;
    private static final int KERNEL_HZ = 250;
    
    private Logger logger = LoggerFactory.getLogger(AbstractTrafficControl.class);

    private EmulatedGauge gauge;
    private boolean firstUpdate;

    public AbstractTrafficControl(EmulatedGauge gauge) 
    throws IOException {
        Defense.checkNotNull(gauge, "gauge");

        this.gauge = gauge;

        firstUpdate = true;

        gauge.addObserver(this);

        // clean current settings
        clean();

        // register shutdown thread to clean settings afterwards
        Cleaner cleaner = new Cleaner();
        Thread cleanThread = new Thread(cleaner, "TrafficControlCleaner");
        Runtime.getRuntime().addShutdownHook(cleanThread);
    }

    public void clean() throws IOException {
        logger.info("Cleaning traffic control settings");

        // Do not fail on error, since cleaning the settings without
        // them being set (e.g. in the constructor) results in an error
        // message and exit code 2. We want to ignore that.
        execute(false, SUDO_COMMAND, TC_COMMAND, "qdisc", "del", "dev", 
                INTERFACE, "root");
    }

    public void update(Observable o, Object arg) {
        if (o == gauge) {
            try {
                updateEmulation(gauge, firstUpdate);
                firstUpdate = false;
            } catch (IOException e) {
                throw new RuntimeException("failed to update traffic control", 
                        e);
            }
        }
    }

    protected abstract void updateEmulation(EmulatedGauge gauge, 
            boolean firstTime) throws IOException;

    /**
     * The black magic calculation of the HTB burst size to achieve a certain
     * throughput, which uses some info from the HTB website. Quote: "when you
     * operate with high rates on computer with low resolution timer you need
     * some minimal burst and cburst to be set for all classes. Timer resolution
     * on i386 systems is 10ms and 1ms on Alphas. The minimal burst can be
     * computed as max_rate*timer_resolution. So that for 10Mbit on plain i386
     * you needs burst 12kb." The DAS3 kernels are compiled with a CONFIG_HZ
     * value of 250, which gives a timer resolution of 4ms. So, the minimum
     * burst size must be desired_throughput_in_MB_per_sec * 1024 * 1024 / 250
     * We add 9000 to this, which is the Myrinet MTU. This prevents the burst
     * size from being less than the MTU, which hurts throughput. And yes, all
     * this is very black magic... :)
     * 
     * @param bytesPerSec
     *                the desired throughput (in bytes per second)
     * 
     * @return the HTB 'burst' value
     */
    protected String burstValue(double bytesPerSec) {
        return Integer.toString((int)((bytesPerSec / (double) KERNEL_HZ) + MTU));
    }

    protected String cburstValue(double bytesPerSec) {
        return burstValue(bytesPerSec);
    }

    protected String mtuValue() {
        return Integer.toString(MTU);
    }

    protected String bandwidthValue(double bytesPerSec) {
        if (bytesPerSec < 1024 * 1024) {
            // less then 1 MB/s, express it in kilobytes/sec
            double kbytesPerSecond = Convert.bytesPerSecToKBytesPerSec(bytesPerSec);
            return String.format("%1$.3fkbps", kbytesPerSecond);
        } else {
            // at least 1 MB/s, express in megabytes/sec
            double mbytesPerSecond = Convert.bytesPerSecToMBytesPerSec(bytesPerSec);
            return String.format("%1$.3fmbps", mbytesPerSecond);
        }
    }

    /**
     * The black magic calculation for the Netem 'delay' value to achieve a
     * certain end-to-end one-way delay in the cluster emulation. The old
     * formula was: delay_in_milliseconds = (8/7 *
     * desired_oneway_delay_in_milliseconds) - 17 1/7. The new (current) one is:
     * 3 ms. less than desired (when applied both ways, this results in 6 ms.
     * less RTT, which roughly compensates for the wonky Netem delay and some
     * SmartSockets overhead)
     * 
     * @param millisec
     *                the desired end-to-end one-way delay (in seconds)
     * 
     * @return the value to use for the Netem 'delay' parameter on a hub node.
     */
    protected String delayValue(double sec) {
        //return Math.max(0, ((8.0/7.0) * millisec) - 17 - (1.0/7.0));
        double ms = Convert.secToMillisec(sec);

        double corrected = Math.max(ms - 3, 0);

        return corrected + "ms";
    }

    private String buildString(String[] arr) {
        StringBuilder b = new StringBuilder();
        for (String s: arr) {
            b.append(s);
            b.append(' ');
        }
        return b.toString();
    }

    protected void execute(boolean failOnError, String... command) 
    throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Executing " + buildString(command));
        }

        RunProcess p = new RunProcess(command);
        p.redirectErrorStream(true);
        p.run();

        int exitValue = p.getExitStatus();

        if (failOnError && exitValue != 0) {
            logger.error("Error executing " + buildString(command) + 
                    ", output:\n" + new String(p.getStdout()));
            throw new IOException("Executing '" + Arrays.toString(command) + 
                    "' returned " + exitValue);
        }   
    }

    // INNER CLASSES

    private class Cleaner implements Runnable {

        Cleaner() {
            // do nothing
        }

        public void run() {
            try {
                clean();
            } catch (IOException e) {
                logger.warn("Problem while cleaning traffic control settings", e);
            }
        }
    }

}
