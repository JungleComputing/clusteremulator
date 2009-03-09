#!/bin/sh

# Example script to run the clusteremulator.test.PingPongTest application
# using the cluster emulator.

bindir=$(dirname $0)

# The emulation script to use
script=$bindir/../examples/2x1.script

# The total number of nodes: <# virtual-cluster-nodes> + <# virtual clusters>
# In the default 2x1.script, we have a total of 2 nodes in 2 virtual clusters.
nodes=4

# Bootstrapping info for the PoolInfo server and Ibis server
pool_name=PingPongTest
server_addr=fs0.das3.cs.vu.nl
ibis_server_port=44661
poolinfo_server_port=44662

prun -no-panda -1 -t 15:00 \
    java $nodes \
    -cp $IPL_HOME/lib/'*':$bindir/../lib/'*' \
    -Dlog4j.configuration=file:$bindir/../log4j.properties \
    -Dibis.server.address=$server_addr \
    -Dibis.server.port=$ibis_server_port \
    -Dibis.pool.size=$nodes \
    -Dibis.pool.name=$pool_name \
    -Dibis.pool.server.host=$server_addr \
    -Dibis.pool.server.port=$poolinfo_server_port \
    clusteremulator.ApplicationRunner $script clusteremulator.test.PingPongTest
