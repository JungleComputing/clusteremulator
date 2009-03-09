package clusteremulator.gauge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Observable;

import clusteremulator.script.EmulationScriptObserver;

public class EmulationGauge extends Observable
implements EmulatedGauge, EmulationScriptObserver
{

    private String[] clusterNames;
    private String[] distinctClusterNames;
    private double[][] delay;
    private double[][] bandwidth;
    private double[] incomingClusterCapacity;
    private double[] outgoingClusterCapacity;
    private double[] incomingHostCapacity;
    private double[] outgoingHostCapacity;

    public EmulationGauge(String[] clusterNames) {
        if (clusterNames == null) {
            throw new IllegalArgumentException("cluster names cannot be null");
        }

        this.clusterNames = clusterNames;

        HashSet<String> set = new HashSet<String>();
        set.addAll(Arrays.asList(clusterNames));
        distinctClusterNames = set.toArray(new String[0]);

        delay = new double[distinctClusterNames.length][distinctClusterNames.length];
        bandwidth = new double[distinctClusterNames.length][distinctClusterNames.length];
        incomingClusterCapacity = new double[distinctClusterNames.length];
        outgoingClusterCapacity = new double[distinctClusterNames.length];
        incomingHostCapacity = new double[clusterNames.length];
        outgoingHostCapacity = new double[clusterNames.length];
    }

    public String[] getClusterNames() {
        return clusterNames;
    }

    public String[] getDistinctClusterNames() {
        return distinctClusterNames;
    }

    public int clusterRank(String clusterName) {
        for (int i = 0; i < distinctClusterNames.length; i++) {
            if (distinctClusterNames[i].equals(clusterName)) {
                return i;
            }
        }

        throw new NoSuchElementException("unknown cluster: " + clusterName);
    }

    public void updateBandwidth(String from, String to, double value) {
        int fromRank = clusterRank(from);
        int toRank = clusterRank(to);

        if (bandwidth[fromRank][toRank] != value) {
            bandwidth[fromRank][toRank] = value;
            setChanged();
        }
    }

    public double getBandwidth(String from, String to) {
        return bandwidth[clusterRank(from)][clusterRank(to)];
    }

    public void updateDelay(String from, String to, double value) {
        int fromRank = clusterRank(from);
        int toRank = clusterRank(to);

        if (delay[fromRank][toRank] != value) {
            delay[fromRank][toRank] = value;
            setChanged();
        }
    }

    public double getDelay(String from, String to) {
        return delay[clusterRank(from)][clusterRank(to)];
    }

    public void updateIncomingClusterCapacity(String name, double value) {
        int rank = clusterRank(name);

        if (incomingClusterCapacity[rank] != value) {
            incomingClusterCapacity[rank] = value;
            setChanged();
        }
    }

    public double getIncomingClusterCapacity(String name) {
        return incomingClusterCapacity[clusterRank(name)];
    }

    public void updateIncomingHostCapacity(int rank, double value) {
        if (incomingHostCapacity[rank] != value) {
            incomingHostCapacity[rank] = value;
            setChanged();
        }
    }

    public double getIncomingHostCapacity(int rank) {
        return incomingHostCapacity[rank];
    }

    public void updateOutgoingClusterCapacity(String name, double value) {
        int rank = clusterRank(name);

        if (outgoingClusterCapacity[rank] != value) {
            outgoingClusterCapacity[rank] = value;
            setChanged();
        }
    }

    public double getOutgoingClusterCapacity(String name) {
        return outgoingClusterCapacity[clusterRank(name)];
    }

    public void updateOutgoingHostCapacity(int rank, double value) {
        if (outgoingHostCapacity[rank] != value) {
            outgoingHostCapacity[rank] = value;
            setChanged();
        }
    }

    public double getOutgoingHostCapacity(int rank) {
        return outgoingHostCapacity[rank];
    }

    public void emulationSleeping() {
        notifyObservers();
    }

    public void emulationListening() {
        notifyObservers();
    }

    public void emulationFixated() {
        notifyObservers();
    }

}
