#!/bin/sh

# This script is a modified version of the convenience script included in the 
# ipl distribution. It starts the ipl registry server from the IPL installation, 
# specified in the $IPL_HOME environment variable.
#
# Changes to the original script are:
# - set the property smartsockets.networks.name to 'ns', so all nodes in the
#   cluster emulator can connect to the Ibis registry server

# Check IPL_HOME.
if [ -z "$IPL_HOME" ];  then
    echo "please set IPL_HOME to the location of your Ibis installation" 1>&2
    exit 1
fi

exec "$IPL_HOME"/bin/ipl-run -Xmx256M -Dsmartsockets.networks.name=ns ibis.server.Server "$@"
