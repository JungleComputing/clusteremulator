#!/bin/sh

# Example script to run the clusteremulator.test.PingPongTest application
# WITHOUT the cluster emulator

nodes=2
server_addr=fs0.das3.cs.vu.nl
ibis_server_port=44661
pool_name=PingPongTest

bindir=$(dirname $0)

prun -no-panda -1 -t 15:00 \
    java $nodes \
    -cp $IPL_HOME/lib/'*':$bindir/../lib/'*' \
    -Dlog4j.configuration=file:$bindir/../log4j.properties \
    -Dibis.server.address=$server_addr \
    -Dibis.server.port=$ibis_server_port \
    -Dibis.pool.size=$nodes \
    -Dibis.pool.name=$pool_name \
    clusteremulator.test.PingPongTest
