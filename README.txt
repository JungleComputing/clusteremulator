=======================
Cluster Emulator README
=======================

--------
Contents
--------

1. What is it?
2. Requirements
3. Emulation scripts
4. Running your application in the cluster emulator
5. Ibis locations
6. Cleaning up
7. Testing the emulator with the PingPongTest application
8. SmartSockets visualization
9. Configuration properties


--------------
1. What is it?
--------------

The cluster emulator lets applications experience wide-area network performance 
within a single cluster. It groups application nodes into 'virtual' clusters. 
Nodes in the same virtual cluster can communicate at normal speed, but traffic 
between nodes in different virtual clusters is slowed down using various forms 
of traffic shaping. This way, the cluster emulator can emulate:

- Delay between virtual clusters

- Bandwidth between virtual clusters that is shared by all traffic between
  these clusters

- Local capacity per virtual cluster, i.e. the bandwidth of the link between 
  a cluster and the WAN that is shared by all traffic from and to that cluster.

- Local capacity' per node, i.e. the bandwidth of the wide-area network
  interface of cluster nodes.
  
All delay and bandwidth values can be different in both directions.


---------------
2. Requirements
---------------

SmartSockets
    The cluster emulator uses the SmartSockets library for custom routing of
    network traffic. The emulator can therefore be used with any program that 
    communicates via SmartSockets. More information about SmartSockets can
    be found at http://www.cs.vu.nl/ibis/smartsockets.html.
    
Linux Traffic Control
    The actual emulation of delay and bandwidth is done using Linux Traffic 
    Control (LTC). The cluster in which the emulator is run should therefore run
    a Linux version with LTC enabled. You must be able to execute the command
    "sudo /sbin/tc". Contact your local system administrator to arrange 
    sufficient rights, if necessary. More information about Linux Traffic 
    Control can be found at http://lartc.org/.


--------------------
3. Emulation scripts
--------------------

The network environment to emulate is specified in a script file. 
See 'SCRIPTS.txt' for more information about the structure and syntax of
these script files.

The 'examples/' directory contains several example script files that demonstrate
all commands. The easiest way of writing your own script file is to copy and 
modify one of the example scripts.


---------------------------------------------------
4. Running your application in the cluster emulator
---------------------------------------------------

The class 'clusteremulator.ApplicationRunner' is used to first start the cluster 
emulator, and then your application. Its syntax is:

    java clusteremulator.ApplicationRunner <emulation script> <main class> ...

The total number of nodes you have to reserve depends on your emulation script 
file. You will have to reserve a little more nodes than usual, since the cluster
emulator also requires one node per virtual cluster to run a SmartSockets hubs 
and emulate inter-cluster network characteristics. The total number of required 
nodes therefore depends on the total number of nodes and the total number of 
virtual clusters. With N nodes in C virtual clusters, the total amount of 
required nodes is N + C.  

Each node needs to know what its purpose is (running part of the application,
or running a SmartSockets hub). This is done via a central PoolInfo server that 
gives every node a zero-based during startup. The first N nodes become regular
application nodes, while the last C nodes run the SmartSockets hubs. The 
PoolInfo server is also used to synchronize all nodes at runtime. This server 
has to be started before starting your application, using the script 
'bin/poolinfo-server.sh'.

The following steps are therefore required to run your application in the
cluster emulator:

1. Write an emulation script file. Note the number of nodes N and the 
   number of virtual clusters C the script specifies.
2. Start the PoolInfo server
3. Start your application using the ApplicationStarter class on N + C nodes. 

When your application is finished, the cluster emulator will automatically 
finish too.

The ApplicationRunner accepts two optional parameters:
  -tc     (start the emulation script, also done by default)
  -no-tc  (does not start the emulation script, thereby effectively disabling
           all traffic shaping)


-----------------
5. Ibis locations
-----------------

For convenience, the ApplicationRunner sets the property 'ibis.location' on each 
application node. This property is used in the Ibis system to identify the 
location of each Ibis instance. The syntax used in the location property is:

   node<rank>@<virtual cluster>
  
For example, if the emulation script contained

   defineCluster 0 1 2 3  cluster_a
   
then the ibis.location property on the node that gets rank 0 will be set to
"node0@cluster_a". Each node in an Ibis application can therefore inspect the
Location object to discover which cluster it is part of. 

 
--------------
6. Cleaning up
--------------

The cluster emulator adds various LTC settings to the Myrinet interfaces of the
nodes used in a run. When the application is finished, the cluster emulator
deletes all LTC settings in a JVM shutdown hook (see 
java.lang.Runtime.addShutdownHook). However, this mechanism fails if the 
application is forcibly killed.

Checking whether LTC settings have already been set can be done with the
script bin/tc-info.sh. When no special LTC settings have been set, the output
on DAS-3 nodes will look like:

   $ ./tc-info.sh 
   qdisc pfifo_fast 0: dev eth0 bands 3 priomap  1 2 2 2 1 2 0 0 1 1 1 1 1 1 1 1
   qdisc pfifo_fast 0: dev myri0 bands 3 priomap  1 2 2 2 1 2 0 0 1 1 1 1 1 1 1 1
   
When something else is printed, some interface cards have special LTC settings.
Cleaning all settings can be done with the script 'bin/tc-clean.sh'.

On the DAS-3, you can and should let the SGE scheduler remove all LTC settings 
automatically when your jobs terminate. This is done by creating an executable 
file '~/.sge_epilog' that performs the same commands as tc-clean.sh does:

   $ cp bin/tc-clean.sh ~/.sge_epilog
   $ chmod +x ~/.sge_epilog

Please perform these steps before you use the cluster emulator on the DAS-3.


---------------------------------------------------------
7. Testing the emulator with the PingPongTest application
---------------------------------------------------------

The example application 'PingPongTest' measures the round-trip time and 
bandwidth between all application nodes. It uses Ibis to communicate. 

Take the following steps to run the PingPongTest on fs0.das3.cs.vu.nl:

1. Follow the instructions in INSTALL.txt to compile the cluster emulator code
   and arrange/install thel dependencies (Linux Traffic Control and the 
   Ibis Portability Layer) 

2. Start three shells (shell 1, 2, and 3) and 'cd' to the cluster emulator 
   directory.
   
   In shell 1, start the PoolInfo server:
 
      $ bin/poolinfo-server.sh
   
   In shell 2, start the Ibis server:
   
      $ bin/ipl-server.sh --port 44661 --events
      
   This script is a slight modification of the ipl-server.sh script in the IPL
   distribution, and sets an additional SmartSockets property that is needed for
   the cluster emulator.
            
   In shell 3, first start the PingPongTest program without the cluster emulator.
      
      $ bin/run-pingpong-normal.sh
      
   The output will be in the files "node.[01]", showing the low RTT and 
   high bandwidth measured between two normal cluster nodes.
   
   Now in shell 3, start the PingPongTest program with the cluster emulator.
      
      $ bin/run-pingpong-emulator.sh
      
   The application will print the measured RTT and bandwidth between all 
   application nodes,  will now reflect the values that are specified
   in the emulation script file (by default, '2x1.script'). 
   
   Please customize bin/run-pingpong-emulator.sh to match your own needs. 


-----------------------------
8. SmartSockets visualization
-----------------------------

The SmartSockets library can visualize the current network of hub and 
application nodes. The visualization gives you an idea of what is actually 
going on, and is also fun to watch :).

There are two obstacles when using the visualization with the cluster emulator:

- The cluster emulator limits the connectivity between hubs, so an additional 
  property has to be set when starting the visualization.
   
- The address of the hub node to connect the visualization to is only known at
  runtime, and has to been manually located in the logging output.
   
Take the following steps to use the SmartSockets visualization with the example 
PingPongTest application:

1. Add to the log4j.properties file:

   log4j.logger.clusteremulator.ClusterEmulator=INFO
   
   The hub node to which the visualization should connect will now print its
   address.
   
2. Start the PingPongTest application in the cluster emulator. 

3. Look for the following line in the logging output:

   Visualization hook node address: 130.37.197.194-17878/10.153.0.194/10.141.0.66-17878 / smartsockets.networks.name=viz
   
4. Start the visualization in another shell:

   $ cd $IPL_HOME
   $ java -cp lib/'*' -Dsmartsockets.networks.name=viz \
       ibis.smartsockets.viz.SmartsocketsViz \
       130.37.197.194-17878/10.153.0.194/10.141.0.66-17878

   The address at the end must be the same as the one seen in the output of the
   cluster emulator.

 
---------------------------   
9. Configuration properties
---------------------------

Certain more exotic functionality of the cluster emulator can be configured 
with Java properties. The following is possible:

- Disable the emulation of delay (default: true)
    clusteremulator.delay = false
    
- Disable all emulation (local capacity, bandwidth, and delay, default: true)
    clusteremulator.delay = false
    clusteremulator.bandwidth = false
    
  (only disabling bandwidth is not supported, but can be mimicked by setting
   bandwidth and local capacity to arbitrary high values) 
  
- Disable emulation of delay and bandwidth on the hub nodes (default: true)
  The application nodes will still emulate local capacity.
    clusteremulator.emulate_on_hubs = false
   
- Let the SmartSockets hubs use a different port (default: 17878)
    clusteremulator.hub_port = <number>
    
- Change the buffer size (in bytes) on the SmartSockets hubs (default: 2097152)
  Very large streams may need larger buffers to SmartSockets to keep up.
     clusteremulator.hubrouted_buffer = <size>

- Change the network preference used by SmartSockets. 
  Default: 10.153.0.0/255.255.255.0,global (selects Myrinet on the DAS-3)
     clusteremulator.network_preference = <string>

- Do NOT let nodes in the same virtual cluster connect to each other directly,
  but use a hubrouted connection via their cluster hub. (default: "true", i.e. 
  DO connect directly)     
     clusteremulator.fast_local_network = false
     
- Change the 'sudo' command in each LTC command line (default: "sudo")
     clusteremulator.sudo_command = <command>
     
- Change the 'tc' command in each LTC command line (default: "/sbin/tc")
     clusteremulator.tc_command = <command>

- Change the network interface used in each LTC command line (default: "myri0")
     clusteremulator.interface = <interface>
     
  (note that you will probably also have to change the SmartSockets network
   preference to actually use the new network interface)
