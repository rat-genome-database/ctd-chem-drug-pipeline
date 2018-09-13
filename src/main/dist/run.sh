# run the pipeline
#
. /etc/profile
APPDIR=/home/rgddata/pipelines/CTDChemDrug
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
ELIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    ELIST="$ELIST,slaulederkind@mcw.edu"
fi

$APPDIR/bin/run.sh 2>&1

mailx -s "[$SERVER] CTD rejected annotations" $ELIST < $APPDIR/logs/rejectedAnnotsSummary.log
