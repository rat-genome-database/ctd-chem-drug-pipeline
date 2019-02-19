#!/usr/bin/env bash
# run the CTDChemDrug pipeline
#
. /etc/profile
APPNAME=CTDChemDrug

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar "$@"
