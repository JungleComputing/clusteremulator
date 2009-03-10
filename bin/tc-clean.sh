#!/bin/sh
#
# Removes all Linux Traffic Control settings.
#

if [ "$(hostname -f)" != "fs0.das3.cs.vu.nl" ]; then
    interfaces=$(/sbin/tc qdisc | awk '{print $5}')
    
    for if in $interfaces; do
        sudo /sbin/tc qdisc del dev $if root >/dev/null 2>&1
    done
fi
