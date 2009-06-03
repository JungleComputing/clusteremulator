package clusteremulator.test;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clusteremulator.util.Convert;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisCreationFailedException;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.Registry;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class PingPongTest {

    private static Logger logger = LoggerFactory.getLogger(PingPongTest.class);

    private static final int RTT_WARMUP_TIMES = 10;
    private static final int RTT_PROBES = 1;
    private static final byte[] RTT_PROBE = new byte[1];
    
    private static final int BANDWIDTH_WARMUP_TIMES = 5;
    private static final byte[] BANDWIDTH_PROBE = new byte[32 * 1024];
    private static final byte[] BANDWIDTH_ACK = new byte[1];
    private static final int BANDWIDTH_MEASUREMENT_TIME = 5000; // ms
    
    private IbisCapabilities capabilities = new IbisCapabilities(
            IbisCapabilities.CLOSED_WORLD,
            IbisCapabilities.ELECTIONS_STRICT,
            IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED);

    private PortType portType = new PortType(    
            PortType.SERIALIZATION_BYTE,
            PortType.CONNECTION_ONE_TO_ONE,
            PortType.COMMUNICATION_RELIABLE,
            PortType.RECEIVE_EXPLICIT);

    private Ibis ibis;

    PingPongTest() 
    throws IbisCreationFailedException, IOException 
    {
        ibis = IbisFactory.createIbis(capabilities, null, portType);
        ibis.registry().waitUntilPoolClosed();
    }

    private static void send(SendPort sport, byte[] probe, int times) 
    throws Exception 
    {
        WriteMessage m = sport.newMessage();
        for (int i = 0; i < times; i++) {
            m.writeArray(probe);
        }
        m.finish();
    }

    private static void receive(ReceivePort rport, byte[] probe, int times) 
    throws Exception 
    {
        ReadMessage m = rport.receive();
        for (int i = 0; i < times; i++) {
            m.readArray(probe);
        }
        m.finish();
    }

    public void run() throws Exception {
        IbisIdentifier me = ibis.identifier();
        Registry registry = ibis.registry();

        IbisIdentifier[] ibises = registry.joinedIbises();

        logger.info("Measuring RTT and bandwidth between all " + ibises.length 
                + " ibises");

        for (int src = 0; src < ibises.length; src++) {
            for (int dst = 0; dst < ibises.length; dst++) {
                if (src != dst) {
                    boolean meSrc = me.equals(ibises[src]);
                    boolean meDst = me.equals(ibises[dst]);
                    String srcName = ibises[src].location().getLevel(0);
                    String dstName = ibises[dst].location().getLevel(0);
                    String round = src + "->" + dst; 

                    if (meSrc || meDst) {
                        ReceivePort rport = ibis.createReceivePort(portType, round);
                        rport.enableConnections();

                        IbisIdentifier peer = meSrc ? ibises[dst] : ibises[src];
                        
                        SendPort sport = ibis.createSendPort(portType);
                        sport.connect(peer, round);

                        logger.debug("Measuring delay " + srcName + " -> " + dstName);

                        if (meSrc) {
                            // warmup
                            for (int i = 0; i < RTT_WARMUP_TIMES; i++) {
                                send(sport, RTT_PROBE, RTT_PROBES); 
                                receive(rport, RTT_PROBE, RTT_PROBES);
                            }

                            // measure RTT
                            long time = System.currentTimeMillis();
                            send(sport, RTT_PROBE, RTT_PROBES); 
                            receive(rport, RTT_PROBE, RTT_PROBES);
                            time = System.currentTimeMillis() - time;
                            logger.info("RTT       " + srcName + " -> " + 
                                    dstName + " = " + time + " ms");
                        } else if (meDst) {
                            // warmup + measure
                            for (int i = 0; i < RTT_WARMUP_TIMES + 1; i++) {
                                receive(rport, RTT_PROBE, RTT_PROBES);
                                send(sport, RTT_PROBE, RTT_PROBES); 
                            }
                        }
                        
                        logger.debug("Measuring bandwidth " + srcName + " -> " + 
                                dstName);

                        if (meSrc) {
                            // warmup
                            send(sport, BANDWIDTH_PROBE, BANDWIDTH_WARMUP_TIMES);
                            receive(rport, BANDWIDTH_ACK, 1);

                            WriteMessage m = sport.newMessage();

                            // measure bandwidth
                            long start = System.currentTimeMillis();
                            long probesSent = 0;
                            
                            do {
                                m.writeByte((byte)0);
                                m.writeArray(BANDWIDTH_PROBE);
                                m.flush();
                                probesSent++;
                            } while (System.currentTimeMillis() - start < BANDWIDTH_MEASUREMENT_TIME);
                               
                            m.writeByte((byte)1);
                            m.finish();
                                
                            receive(rport, BANDWIDTH_ACK, 1);
                            
                            long time = System.currentTimeMillis() - start;

                            long bytes = ((BANDWIDTH_PROBE.length + 1) * probesSent) + 1;
                            double kb = Convert.bytesToKBytes(bytes);
                            double sec = Convert.millisecToSec(time);
                            
                            String f = "Bandwidth %1$s -> %2$s = %3$.2f KB/s";
                            logger.info(String.format(f, srcName, dstName, kb / sec));
                        } else if (meDst) {
                            // warmup
                            receive(rport, BANDWIDTH_PROBE, BANDWIDTH_WARMUP_TIMES);
                            send(sport, BANDWIDTH_ACK, 1);
                            
                            // measure
                            ReadMessage m = rport.receive();
                            while (m.readByte() == 0) {
                                m.readArray(BANDWIDTH_PROBE);
                            }
                            m.finish();
                            
                            send(sport, BANDWIDTH_ACK, 1);
                        }

                        sport.close();
                        rport.close();

                        // notify the end of this round
                        registry.elect(round);
                    } else {
                        // wait for this round to end
                        registry.getElectionResult(round);
                    }
                }
            }
        }

        logger.debug("Done, ending Ibis");
        ibis.end();
    }

    public static void main(String[] args) {
        try {
            new PingPongTest().run();
        } catch (Exception e) {
            System.err.println("Caught exception " + e);
            System.err.println("StackTrace:");
            e.printStackTrace();            
        }
    }

    
    
}
