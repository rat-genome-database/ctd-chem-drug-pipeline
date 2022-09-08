# run the pipeline
#
. /etc/profile
APPDIR=/home/rgddata/pipelines/ctd-chem-drug-pipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
ELIST1=mtutaj@mcw.edu
ELIST2=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    ELIST1="$ELIST1,rgd.pipelines@mcw.edu"
    ELIST2="$ELIST2,slaulederkind@mcw.edu"
fi

$APPDIR/_run.sh 2>&1 > run.log

mailx -s "[$SERVER] CTD Chemical Interaction Pipeline ok" $ELIST1 < $APPDIR/logs/summary.log
mailx -s "[$SERVER] CTD rejected annotations" $ELIST2 < $APPDIR/logs/rejectedAnnotsSummary.log
