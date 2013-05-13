#!/bin/sh

export JAVA_HOME=/home/zsssmpp/SMPP/jre7
export PATH=$PATH:$JAVA_HOME/bin

classpath=".:../config/"
for jar in `ls ../lib/*`
do
        classpath=$classpath":"$jar
done

nohup $JAVA_HOME/bin/java -cp "$classpath" com.peoplecloud.smpp.SMPPApplication &

echo "Started SMPP Daemon"
