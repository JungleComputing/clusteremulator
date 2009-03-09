package clusteremulator.script;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.text.ParseException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clusteremulator.Config;
import clusteremulator.util.Convert;

/**
 * @author mathijs
 */
public class EmulationScript implements Config, Runnable {

    private static enum Opcode {
        DEFINE_CLUSTER, DEFINE_MAX_TCP_BUFFERSIZE, SET_DELAY, SET_BANDWIDTH, SET_CAPACITY_IN,
        SET_CAPACITY_OUT, SET_ALL_CAPACITY_IN, SET_ALL_CAPACITY_OUT, SLEEP, FOREVER, HEAR,
        INIT_RANDOM
    }

    private static enum State {
        INITIALISED, RUNNING, SLEEPING, FINISHED
    }

    private static Set<State> pausedStates = new HashSet<State>();
    static {
        pausedStates.add(State.SLEEPING);
        pausedStates.add(State.FINISHED);
    }

    private Logger logger = LoggerFactory.getLogger(EmulationScript.class);

    private String[] clusterNames;
    private List<EmulationScriptObserver> observers;
    private int[] tcpBufferSize;

    private ArrayList<Command> commands;
    private volatile int current, forever;
    private volatile StateStack<State> state;
    private Set<String> told;
    private Random random;
    private volatile boolean done;

    public EmulationScript(String fileName) throws IOException, ParseException {
        FileReader r = new FileReader(fileName);
        init(r);
        r.close();
    }

    public EmulationScript(File file) throws IOException, ParseException {
        FileReader r = new FileReader(file);
        init(r);
        r.close();
    }

    public EmulationScript(Reader r) throws IOException, ParseException {
        init(r);
    }

    private void init(Reader r) throws IOException, ParseException {
        clusterNames = new String[0];
        tcpBufferSize = new int[0];
        observers = new LinkedList<EmulationScriptObserver>();
        told = new HashSet<String>();

        // put all commands in the command array

        forever = -1;
        commands = readScript(r);
        current = 0;
        random = new Random();
        state = new StateStack<State>(State.INITIALISED);

        done = false;
    }

    public void addObserver(EmulationScriptObserver o) {
        observers.add(o);
    }

    private ArrayList<Command> readScript(Reader r) 
    throws IOException, ParseException {
        StreamTokenizer s = new StreamTokenizer(r);
        s.whitespaceChars(0, 32);
        s.wordChars(33, 255);
        s.parseNumbers();
        s.eolIsSignificant(false);
        s.commentChar('#');

        ArrayList<Command> commands = new ArrayList<Command>();

        String cmd = null;
        while ((cmd = readCommand(s)) != null) {
            if (cmd.equalsIgnoreCase("defineCluster")) {
                readDefineCluster(s);
            } else if (cmd.equalsIgnoreCase("defineMaxTCPBuffer")) {
                readDefineMaxTCPBufferSize(s);
            } else if (cmd.equalsIgnoreCase("setDelay")) {
                commands.add(readSetDelay(s));
            } else if (cmd.equalsIgnoreCase("setBandwidth")) {
                commands.add(readSetBandwidth(s));
            } else if (cmd.equalsIgnoreCase("setCapacityIn")) {
                commands.add(readSetCapacity(s, Opcode.SET_CAPACITY_IN));
            } else if (cmd.equalsIgnoreCase("setCapacityOut")) {
                commands.add(readSetCapacity(s, Opcode.SET_CAPACITY_OUT));
            } else if (cmd.equalsIgnoreCase("setAllCapacityIn")) {
                commands.add(readSetAllCapacity(s, Opcode.SET_ALL_CAPACITY_IN));
            } else if (cmd.equalsIgnoreCase("setAllCapacityOut")) {
                commands
                .add(readSetAllCapacity(s, Opcode.SET_ALL_CAPACITY_OUT));
            } else if (cmd.equalsIgnoreCase("sleep")) {
                commands.add(readSleep(s));
            } else if (cmd.equalsIgnoreCase("forever")) {
                commands.add(readForever());
                forever = commands.size() - 1;
            } else if (cmd.equalsIgnoreCase("hear")) {
                commands.add(readHear(s));
            } else if (cmd.equalsIgnoreCase("initRandom")) {
                commands.add(readInitRandom(s));
            } else {
                throw new ParseException("unknown script command: " + cmd, s
                        .lineno());
            }
        }

        // check if all hosts are part of a cluster
        for (int i = 0; i < clusterNames.length; i++) {
            if (clusterNames[i] == null) {
                throw new ParseException("host " + i
                        + " is not a member of any cluster", s.lineno());
            }
        }

        return commands;
    }

    /**
     * Returns the name of the cluster of each host
     * 
     * @return
     */
    public String[] getClusterNames() {
        return clusterNames;
    }

    public int getHostCount() {
        return clusterNames.length;
    }

    public int getMaxTCPBufferSize(int hostRank) {
        if (hostRank < 0 || hostRank > tcpBufferSize.length - 1) {
            return 0;
        } else {
            return tcpBufferSize[hostRank];
        }
    }

    public int getClusterCount() {
        HashSet<String> clusters = new HashSet<String>();

        for (String name : clusterNames) {
            clusters.add(name);
        }

        return clusters.size();
    }

    private String readCommand(StreamTokenizer s)
    throws IOException, ParseException {
        int next = s.nextToken();

        if (next == StreamTokenizer.TT_EOF) {
            return null;
        } else if (next == StreamTokenizer.TT_WORD) {
            return s.sval;
        } else {
            s.pushBack();
            throw new ParseException("expected a command", s.lineno());
        }
    }

    private boolean isDefinedCluster(String name) {
        for (int i = 0; i < clusterNames.length; i++) {
            if (clusterNames[i] != null && clusterNames[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void readDefineCluster(StreamTokenizer s)
    throws IOException, ParseException {
        SortedSet<Integer> ranks = new TreeSet<Integer>();

        try {
            while (true) {
                ranks.add(readInteger(s, "node rank"));
            }
        } catch (ParseException e) {
            // we've reached the cluster name
        }

        if (ranks.isEmpty()) {
            throw new ParseException("defineCluster: missing host rank(s)", s
                    .lineno());
        }

        String name = readClusterName(s);

        logger.debug("defining cluster '" + name + "': " + ranks);

        if (!isDefinedCluster(name)) {
            // enlarge hostClusterNames to hold the largest rank
            int largest = Math.max(clusterNames.length - 1, ranks.last());

            if (clusterNames.length < largest + 1) {
                String[] newNames = new String[largest + 1];
                System.arraycopy(clusterNames, 0, newNames, 0,
                        clusterNames.length);
                clusterNames = newNames;
            }

            for (int rank : ranks) {
                if (clusterNames[rank] == null) {
                    clusterNames[rank] = name;
                } else {
                    throw new ParseException("defineCluster: rank " + rank
                            + " is already part of cluster " + clusterNames[rank],
                            s.lineno());
                }
            }
        } else {
            throw new ParseException("cluster " + name
                    + " has already been defined", s.lineno());
        }
    }

    private Command readSetDelay(StreamTokenizer s)
    throws IOException, ParseException {
        Command setDelay = null;

        setDelay = new Command(Opcode.SET_DELAY, 0, 2);
        setDelay.clusterNames[0] = readCluster(s, "source cluster name");
        setDelay.clusterNames[1] = readCluster(s, "destination cluster name");

        if (peekString(s)) {
            // value is random
            readString(s, "random");

            double min = readSeconds(s, "minimum random delay");   
            double max = readSeconds(s, "maximum random delay");    

            setDelay.setRandomValue(min, max);
        } else {
            double value = readSeconds(s, "delay");
            setDelay.setValue(value);
        }

        return setDelay;
    }

    private Command readSetBandwidth(StreamTokenizer s)
    throws IOException, ParseException {
        Command setBandwidth = null;

        setBandwidth = new Command(Opcode.SET_BANDWIDTH, 0, 2);
        setBandwidth.clusterNames[0] = readCluster(s, "source cluster name");
        setBandwidth.clusterNames[1] = readCluster(s,
        "destination cluster name");

        if (peekString(s)) {
            // value is random
            readString(s, "random");

            double min = readBytesPerSecond(s, "minimum random bandwidth");	    
            double max = readBytesPerSecond(s, "maximum random bandwidth");    

            setBandwidth.setRandomValue(min, max);
        } else {
            double value = readBytesPerSecond(s, "bandwidth");
            setBandwidth.setValue(value);
        }

        return setBandwidth;
    }

    private Command readSetCapacity(StreamTokenizer s, Opcode opcode)
    throws IOException, ParseException {
        Command setCapacity = null;

        if (peekString(s)) {
            setCapacity = new Command(opcode, 0, 1);
            setCapacity.clusterNames[0] = readCluster(s, "cluster name");
        } else {
            setCapacity = new Command(opcode, 1, 0);
            setCapacity.hostRanks[0] = readHost(s, "node rank");
        }

        double value = readBytesPerSecond(s, "capacity");
        setCapacity.setValue(value);

        return setCapacity;
    }

    private Command readSetAllCapacity(StreamTokenizer s, Opcode opcode)
    throws IOException, ParseException {
        ArrayList<Double> ranks = new ArrayList<Double>();
        try {
            while (true) {
                ranks.add(new Double(readDouble(s, "node rank")));
            }
        } catch (ParseException e) {
            // we've reached the unit
        }

        Command setAllCapacity;

        if (ranks.size() > 1) {
            // use specific ranks given
            setAllCapacity = new Command(opcode, ranks.size() - 1, 0);
            for (int i = 0; i < ranks.size() - 1; i++) {
                Double rank = (Double) ranks.get(i);
                setAllCapacity.hostRanks[i] = rank.intValue();
            }
        } else {
            // no specific ranks given; use all nodes
            int hostCount = clusterNames.length;
            setAllCapacity = new Command(opcode, hostCount, 0);
            for (int i = 0; i < hostCount; i++) {
                setAllCapacity.hostRanks[i] = i;
            }
        }

        Double value = (Double) ranks.get(ranks.size() - 1);

        setAllCapacity.setValue(readBytesPerSecond(s, "all capacity", value));

        return setAllCapacity;
    }

    private void readDefineMaxTCPBufferSize(StreamTokenizer s) 
    throws IOException, ParseException {
        int hostRank = readHost(s, "node rank");
        int value = (int)readBytes(s, "maximum TCP buffer size");

        logger.debug("set maximum TCP buffer size of " + hostRank + ": " + value);

        // increase bufferSize array if needed
        if (tcpBufferSize.length < hostRank + 1) {
            int[] newBufferSize = new int[hostRank + 1];
            System.arraycopy(tcpBufferSize, 0, newBufferSize, 0, tcpBufferSize.length);
            tcpBufferSize = newBufferSize;
        }

        tcpBufferSize[hostRank] = value;
    }

    private Command readSleep(StreamTokenizer s) 
    throws IOException, ParseException {
        Command sleep = new Command(Opcode.SLEEP, 0, 0);

        double seconds = readSeconds(s, "sleep time");
        sleep.setValue(seconds);

        return sleep;
    }

    private Command readHear(StreamTokenizer s)
    throws IOException, ParseException {
        String word = readString(s, "word to hear");

        Command hearCommand = new Command(Opcode.HEAR, 0, 1);
        hearCommand.clusterNames[0] = word;

        return hearCommand;
    }

    private Command readInitRandom(StreamTokenizer s)
    throws IOException, ParseException {
        double seed = readDouble(s, "random seed");

        Command initRandomCommand = new Command(Opcode.INIT_RANDOM, 0, 0);
        initRandomCommand.setValue(seed);

        return initRandomCommand;
    }

    private double readSeconds(StreamTokenizer s, String name)
    throws IOException, ParseException {
        double value = readDouble(s, name);
        return readSeconds(s, name, value);
    }

    private double readSeconds(StreamTokenizer s, String name, double value)
    throws IOException, ParseException {
        String unit = readUnit(s, name + " unit");
        try {
            return Convert.toSec(value, unit);
        } catch (IllegalArgumentException e) {
            throw new ParseException("cannot convert " + name + " unit '"
                    + unit + "' to seconds", s.lineno());
        }
    }

    private double readBytes(StreamTokenizer s, String name)
    throws IOException, ParseException {
        double value = readDouble(s, name);

        return readBytes(s, name, value);
    }

    private double readBytes(StreamTokenizer s, String name, double value) 
    throws IOException, ParseException {
        String unit = readUnit(s, name + " unit");
        try {
            return Convert.toBytes(value, unit);
        } catch (IllegalArgumentException e) {
            throw new ParseException("cannot convert " + name + " unit '"
                    + unit + "' to bytes", s.lineno());
        }
    }

    private double readBytesPerSecond(StreamTokenizer s, String name)
    throws IOException, ParseException {
        double value = readDouble(s, name);

        return readBytesPerSecond(s, name, value);
    }

    private double readBytesPerSecond(StreamTokenizer s, String name, double value) 
    throws IOException, ParseException {
        String unit = readUnit(s, name + " unit");
        try {
            return Convert.toBytesPerSec(value, unit);
        } catch (IllegalArgumentException e) {
            throw new ParseException("cannot convert " + name + " unit '"
                    + unit + "' to bytes per second", s.lineno());
        }
    }

    private Command readForever() 
    throws IOException, ParseException {
        return new Command(Opcode.FOREVER, 0, 0);
    }

    private String readCluster(StreamTokenizer s, String name)
    throws IOException, ParseException {
        return readString(s, name);
    }

    private int readHost(StreamTokenizer s, String name)
    throws IOException, ParseException {
        int next = s.nextToken();
        if (next == StreamTokenizer.TT_NUMBER) {
            int rank = (int) s.nval;
            if (rank != s.nval) {
                s.pushBack();
                throw new ParseException("expected a integer rank for " + name
                        + ", not " + s.nval, s.lineno());
            } else if (rank < 0) {
                s.pushBack();
                throw new ParseException("negative rank: " + rank, s.lineno());
            } else {
                return rank;
            }
        } else {
            s.pushBack();
            throw new ParseException("expected a rank for " + name, s.lineno());
        }
    }

    private int readInteger(StreamTokenizer s, String name)
    throws IOException, ParseException {
        if (s.nextToken() != StreamTokenizer.TT_NUMBER) {
            s.pushBack();
            throw new ParseException("expected an integer for " + name, s
                    .lineno());
        }

        int result = (int) s.nval;

        if (result != s.nval) {
            s.pushBack();
            throw new ParseException("expected an integer for " + name, s
                    .lineno());
        }

        return result;
    }

    private double readDouble(StreamTokenizer s, String name)
    throws IOException, ParseException {
        if (s.nextToken() != StreamTokenizer.TT_NUMBER) {
            s.pushBack();
            throw new ParseException("expected a number for " + name, s
                    .lineno());
        }
        return s.nval;
    }

    private String readUnit(StreamTokenizer s, String name)
    throws IOException, ParseException {
        return readString(s, "a unit for " + name);
    }

    private String readClusterName(StreamTokenizer s)
    throws IOException, ParseException {
        return readString(s, "a cluster name");
    }

    private String readString(StreamTokenizer s, String expected)
    throws IOException, ParseException {

        if (s.nextToken() != StreamTokenizer.TT_WORD) {
            s.pushBack();
            throw new ParseException("expected " + expected, s.lineno());
        }
        return s.sval;
    }

    private boolean peekString(StreamTokenizer s) throws IOException {
        try {
            readString(s, "string");
            s.pushBack();
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    public void run() {
        if (current >= commands.size()) {
            state.set(State.FINISHED);
            return;
        }

        state.set(State.RUNNING);

        while (!done) {
            Command cmd = (Command) commands.get(current);

            switch (cmd.opcode) {
            case SET_DELAY:
            case SET_BANDWIDTH:
            case SET_CAPACITY_IN:
            case SET_ALL_CAPACITY_IN:
            case SET_CAPACITY_OUT:
            case SET_ALL_CAPACITY_OUT:
                if (cmd.clusterNames == null) {
                    executeSetHostCharacteristic(cmd);
                } else {
                    executeSetClusterCharacteristic(cmd);
                }
                break;
            case DEFINE_CLUSTER:
                logger.warn("ignoring cluster definition in already running " +
                "emulation script");
                break;
            case SLEEP:
                executeSleep(cmd);
                break;
            case FOREVER:
                // do nothing
                break;
            case HEAR:
                executeHear(cmd);
                break;
            case INIT_RANDOM:
                executeInitRandom(cmd);
                break;
            default:
                System.err.println("WARNING: unknown opcode " + cmd.opcode
                        + "!?");
            }
            current++;
            if (current >= commands.size()) {
                // end of script reached; what to do?

                if (forever < 0) {
                    // the script does not contain a loop
                    done = true;
                } else {
                    // loop back
                    current = forever;
                }
            }
        }

        for (EmulationScriptObserver o : observers) {
            o.emulationFixated();
        }

        state.set(State.FINISHED);
    }

    public void end() {
        done = true;

        synchronized(told) {
            // wake up threads that are waiting for or listening to a 'tell' command 
            told.notifyAll();
        }

        waitUntilFinished();
    }

    private void executeSetClusterCharacteristic(Command set) {
        logger.debug("setting cluster characteristic " + set);

        String[] src = getClusterNames(set.clusterNames[0]);

        switch (set.opcode) {
        case SET_DELAY:
        case SET_BANDWIDTH:
            String[] dst = getClusterNames(set.clusterNames[1]);

            for (int from = 0; from < src.length; from++) {
                for (int to = 0; to < dst.length; to++) {
                    if (!src[from].equals(dst[to])) {
                        double value = set.getValue();

                        if (set.opcode == Opcode.SET_DELAY) {
                            logger.debug("set delay " + src[from] + "->"
                                    + dst[to] + ": " + value + " sec.");
                            for (EmulationScriptObserver o : observers) {
                                o.updateDelay(src[from], dst[to], value);
                            }
                        } else {
                            logger.debug("set bandwidth " + src[from] + "->"
                                    + dst[to] + ": " + value + " bytes/sec.");
                            for (EmulationScriptObserver o : observers) {
                                o.updateBandwidth(src[from], dst[to], value);
                            }
                        }
                    }
                }
            }

            break;
        case SET_CAPACITY_IN:
            for (int i = 0; i < src.length; i++) {
                double value = set.getValue();

                logger.debug("set incoming capacity of " + src[i] + ": "
                        + value + " bytes/sec.");

                for (EmulationScriptObserver o : observers) {
                    o.updateIncomingClusterCapacity(src[i], value);
                }
            }
            break;
        case SET_CAPACITY_OUT:
            for (int i = 0; i < src.length; i++) {
                double value = set.getValue();

                logger.debug("set outgoing capacity of " + src[i] + ": "
                        + value + " bytes/sec.");

                for (EmulationScriptObserver o : observers) {
                    o.updateOutgoingClusterCapacity(src[i], value);
                }
            }
            break;
        }
    }

    private String[] getClusterNames(String pattern) {
        HashSet<String> matched = new HashSet<String>();

        for (String name : clusterNames) {
            boolean match = name.matches(pattern);

            if (match) {
                matched.add(name);
            }
        }

        logger.debug("cluster name " + pattern + " matched " + matched);

        return (String[]) matched.toArray(new String[0]);
    }

    private void executeSetHostCharacteristic(Command set) {
        int srcEnd;

        logger.debug("setting node characteristic " + set + ", hostRanks="
                + Arrays.toString(set.hostRanks));

        switch (set.opcode) {
        case SET_ALL_CAPACITY_IN:
        case SET_ALL_CAPACITY_OUT:
            srcEnd = set.hostRanks.length;
            break;
        case SET_CAPACITY_IN:
        case SET_CAPACITY_OUT:
            srcEnd = 1;
            break;
        default:
            srcEnd = 1;
        break;
        }

        int[] srcRanks = new int[srcEnd];

        System.arraycopy(set.hostRanks, 0, srcRanks, 0, srcRanks.length);

        executeSetHostCharacteristic(set.opcode, set.getValue(), srcRanks);
    }

    private void executeSetHostCharacteristic(Opcode opcode, double value,
            int[] srcRanks) {
        for (int i = 0; i < srcRanks.length; i++) {
            int rank = srcRanks[i];

            switch (opcode) {
            case SET_CAPACITY_IN:
            case SET_ALL_CAPACITY_IN:
                logger.debug("set incoming capacity of " + rank + ": " + value
                        + " bytes/sec.");
                for (EmulationScriptObserver o : observers) {
                    o.updateIncomingHostCapacity(rank, value);
                }
                break;
            case SET_CAPACITY_OUT:
            case SET_ALL_CAPACITY_OUT:
                logger.debug("set outgoing capacity of " + rank + ": " + value
                        + " bytes/sec.");
                for (EmulationScriptObserver o : observers) {
                    o.updateOutgoingHostCapacity(rank, value);
                }
                break;
            }
        }
    }

    private void executeSleep(Command sleep) {
        state.push(State.SLEEPING);

        for (EmulationScriptObserver o : observers) {
            o.emulationSleeping();
        }

        long sleepMillis = (long) (sleep.getValue() * 1000);
        logger.debug("sleeping " + sleepMillis + " ms...");
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            logger.warn("interrupted while sleeping " + sleepMillis + " ms.");
        }

        state.pop();
    }

    private void executeHear(Command hear) {
        // notify observers before listening 
        for (EmulationScriptObserver o : observers) {
            o.emulationListening();
        }

        synchronized (told) {
            String word = hear.clusterNames[0];

            while (!done && !told.contains(word)) {
                logger.info("waiting until I heared '" + word + "'...");

                try {
                    told.wait();
                } catch (InterruptedException e) {
                    logger.warn("interrupted while waiting until I heared '" + 
                            word + "'");
                }
            }

            if (told.contains(word)) {
                logger.info("I heard '" + word + "', notifying waiting threads");
            } else {
                logger.info("I did not hear '" + word + "', but the script has been ended");
            }

            told.remove(word);
            told.notifyAll();
        }
    }

    private void executeInitRandom(Command initRandom) {
        long seed = (long)Math.floor(initRandom.getValue());
        logger.info("initializing random seed: " + seed);
        random.setSeed(seed);
    }

    public void tell(String word) {
        if (state.is(State.FINISHED)) {
            logger.warn("not telling '" + word + "' to emulation script: " +
            "it is already finished");
            return;
        }

        synchronized(told) {
            logger.info("telling " + word);

            told.add(word);
            told.notifyAll();

            while (!done && told.contains(word)) {
                logger.info("waiting until emulation script heard '" + word + "'...");

                try {
                    told.wait();
                } catch (InterruptedException e) {
                    logger.warn("interrupted while waiting until emulation " +
                            "script heard '" + word + "'");
                }
            }
        }
    }

    public void waitUntilPaused() {
        state.waitForAny(pausedStates);
    }

    public void waitUntilFinished() {
        state.waitForAny(Collections.singleton(State.FINISHED));
    }

    public String toString() {
        String result = "";
        String separator = "";
        for (int i = 0; i < commands.size(); i++) {
            Command c = (Command) commands.get(i);
            result += separator + c.toString();
            separator = ", ";
        }
        return result;
    }

    // INNER CLASSES

    private class Command {

        Opcode opcode;
        int[] hostRanks;
        String[] clusterNames;
        double minRandomValue;
        double maxRandomValue;

        Command(Opcode opcode, int noHostRanks, int noClusterNames) {
            this.opcode = opcode;
            if (noHostRanks > 0)
                hostRanks = new int[noHostRanks];
            if (noClusterNames > 0)
                clusterNames = new String[noClusterNames];
            minRandomValue = 0;
            maxRandomValue = 0;
        }

        public void setValue(double value) {
            minRandomValue = maxRandomValue = value;
        }

        public void setRandomValue(double minValue, double maxValue) {
            if (minValue > maxValue) {
                throw new IllegalArgumentException("minValue (" + minValue + 
                        ") > maxValue (" + maxValue + ")");
            }

            minRandomValue = minValue;
            maxRandomValue = maxValue;
        }

        public double getValue() {
            if (minRandomValue == maxRandomValue) {
                return minRandomValue;
            }

            // use a random value between minRandom and maxRandom
            double r = random.nextDouble();

            return ((maxRandomValue - minRandomValue) * r) + minRandomValue;
        }

        private String nodeString() {
            String result = "";
            if (hostRanks.length == 0) {
                result = "*";
            } else {
                String concat = "";
                for (int i = 0; i < hostRanks.length; i++) {
                    result += concat + hostRanks[i];
                    concat = ",";
                }
            }
            return result;
        }

        private String clusterString() {
            String result = "";
            String concat = "";
            for (int i = 0; i < clusterNames.length; i++) {
                result += concat + clusterNames[i];
                concat = ",";
            }
            return result;
        }

        public String toString() {
            String src = "";
            String dst = "";
            String list = "";

            switch (opcode) {
            case SET_DELAY:
            case SET_BANDWIDTH:
                dst = clusterNames[1];
                // fall through
            case SET_CAPACITY_IN:
            case SET_CAPACITY_OUT:
                if (hostRanks != null && hostRanks.length > 0) {
                    src += hostRanks[0];
                } else {
                    src = clusterNames[0];
                }
                break;

            case SET_ALL_CAPACITY_IN:
            case SET_ALL_CAPACITY_OUT:
                if (hostRanks != null) {
                    list = nodeString();
                } else {
                    list = clusterString();
                }
                break;
            }

            switch (opcode) {
            case DEFINE_CLUSTER:
                return "define cluster of " + nodeString() + " to "
                + clusterNames[0];
            case SET_DELAY:
                return "delay " + src + "->" + dst + ": " + valueToString() + 
                " sec.";
            case SET_BANDWIDTH:
                return "bandwidth " + src + "->" + dst + ": " + valueToString()
                + " bytes/sec.";
            case SET_CAPACITY_IN:
                return "capacity in of " + src + ": " + valueToString() + 
                " bytes/sec.";
            case SET_ALL_CAPACITY_IN:
                return "capacity in of " + list + ": " + valueToString() + 
                " bytes/sec.";
            case SET_CAPACITY_OUT:
                return "capacity out of " + src + ": " + valueToString() + 
                " bytes/sec.";
            case SET_ALL_CAPACITY_OUT:
                return "capacity out of " + list + ": " + valueToString() + 
                " bytes/sec.";
            case SLEEP:
                return "sleep " + valueToString() + " sec.";
            case FOREVER:
                return "forever";
            default:
                return "unknown command";
            }
        }

        private String valueToString() {
            if (minRandomValue == maxRandomValue) {
                return Double.toString(minRandomValue);
            } else {
                return "random between " + minRandomValue + " and " + 
                maxRandomValue;
            }
        }

    }

}
