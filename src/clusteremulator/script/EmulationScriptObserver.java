package clusteremulator.script;

public interface EmulationScriptObserver {

    public void updateDelay(String fromCluster, String toCluster, double value);

    public void updateBandwidth(String fromCluster, String toCluster, double value);

    public void updateIncomingClusterCapacity(String cluster, double value);

    public void updateOutgoingClusterCapacity(String cluster, double value);

    public void updateIncomingHostCapacity(int hostRank, double value);

    public void updateOutgoingHostCapacity(int hostRank, double value);

    public void emulationSleeping();

    public void emulationFixated();

    public void emulationListening();

}
