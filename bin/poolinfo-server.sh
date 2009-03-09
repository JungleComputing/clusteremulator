#!/bin/sh

# Starts modified version of the PoolInfoServer included in Ibis.
# This one also provides the full SmartSocket address sets of each host.

port=44662

function usage() {
	echo "usage: $0 [-port <port>]"
	exit 0
}

# read command line parameters
while true; do
    case $1 in
		-port)
			port=$1
			shift 2
			;;
		-*)	
			echo "Unknown parameter"
			usage
			;;
		*)
			break
			;;
	esac
done

bindir=$(dirname $0)

java -cp $bindir/../lib/'*' -Dlog4j.configuration=$bindir/../log4j.properties \
	clusteremulator.poolinfo.PoolInfoServer -port $port
