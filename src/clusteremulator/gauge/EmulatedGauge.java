package clusteremulator.gauge;

import java.util.Observer;

public interface EmulatedGauge {

    /**
     * Adds an observer to this gauge.
     * 
     * @param o
     *                the observer
     */
    public void addObserver(Observer o);

    /**
     * Returns the list of cluster names per host.
     * 
     * @return the list of cluster names per host.
     */
    public String[] getClusterNames();

    /**
     * Returns the list of distinct cluster names
     */
    public String[] getDistinctClusterNames();

    /**
     * Returns the delay between two clusters
     * 
     * @param from
     *                rank of the source cluster
     * @param to
     *                rank of the destination cluster
     * 
     * @return the latency in seconds from the source to the destination cluster
     */
    public double getDelay(String from, String to);

    /**
     * Returns the bandwidth between two clusters
     * 
     * @param from
     *                rank of the source cluster
     * @param to
     *                rank of the destination cluster
     * @return the bandwidth in bytes per second from source to destination
     *         cluster
     */
    public double getBandwidth(String from, String to);

    /**
     * Returns the incoming local capacity of a cluster
     * 
     * @param rank
     *                the rank of the cluster
     * @return the incoming local capacity of a cluster in bytes per second
     */
    public double getIncomingClusterCapacity(String clusterName);

    /**
     * Returns the outgoing local capacity of a cluster
     * 
     * @param rank
     *                the rank of the cluster
     * @return the outgoing local capacity of a cluster in bytes per second
     */
    public double getOutgoingClusterCapacity(String clusterName);

    /**
     * Returns the incoming capacity of a host
     * 
     * @param rank
     *                the rank of the host
     * @return the incoming local capacity of a host in bytes per second
     */
    public double getIncomingHostCapacity(int rank);

    /**
     * Returns the outgoing capacity of a host
     * 
     * @param rank
     *                the rank of the cluster
     * @return the outgoing local capacity of a host in bytes per second
     */
    public double getOutgoingHostCapacity(int rank);

}
