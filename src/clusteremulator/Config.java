package clusteremulator;

public interface Config {

    static final String PREFIX_CE = "clusteremulator.";

    static final String s_emulate_delay = PREFIX_CE + "delay";
    static final String s_emulate_bandwidth = PREFIX_CE + "bandwidth";
    static final String s_emulate_on_hubs = PREFIX_CE + "emulate_on_hubs";
    static final String s_hub_port = PREFIX_CE + "hub_port";
    static final String s_hubrouted_buffer = PREFIX_CE + "hubrouted_buffer";
    static final String s_network_preference = PREFIX_CE + "network_preference";
    static final String s_fast_local_network = PREFIX_CE + "fast_local_network";
    static final String s_interface = PREFIX_CE + "interface";
    static final String s_tc_command = PREFIX_CE + "tc_command";
    static final String s_sudo_command = PREFIX_CE + "sudo_command";

    static ConfigProperties config = ConfigProperties.getInstance();

    // emulate latency with the cluster emulator
    static final boolean EMULATE_DELAY = 
        config.getBooleanProperty(s_emulate_delay, true);

    // emulate bandwidth with the cluster emulator
    static final boolean EMULATE_BANDWIDTH = 
        config.getBooleanProperty(s_emulate_bandwidth, true);

    // use emulation on the hub nodes of the cluster emulator
    static final boolean EMULATE_ON_HUBS = 
        config.getBooleanProperty(s_emulate_on_hubs, true);

    // port of the SmartSockets hubs
    static final int HUB_PORT = config.getIntProperty(s_hub_port, 17878);

    // size of the SmartSockets hubrouted buffers (in bytes)
    static final int HUBROUTED_BUFFER = 
        config.getIntProperty(s_hubrouted_buffer, 1024 * 1024 * 2);

    // network preference for SmartSockets
    static final String NETWORK_PREFERENCE = 
        config.getStringProperty(s_network_preference, "10.153.0.0/255.255.255.0,global");

    // whether the emulation lets all hosts in the same cluster communicate 
    // directly, without any traffic shaping
    static final boolean FAST_LOCAL_NETWORK = 
        config.getBooleanProperty(s_fast_local_network, true);

    // network interface to which the traffic shaping commands should be applied
    static final String INTERFACE = 
        config.getStringProperty(s_interface, "myri0");

    // Linux Traffic Shaping command
    static final String TC_COMMAND = 
        config.getStringProperty(s_tc_command, "/sbin/tc");

    // sudo command
    static final String SUDO_COMMAND = 
        config.getStringProperty(s_sudo_command, "sudo");

}
