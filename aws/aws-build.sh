#!/bin/bash

SBT_PATH=$HOME/sbt
SBT_DIR=$HOME/.sbt
IVY_DIR=$HOME/.ivy

cd $HOME/planning-poker

SBT_OPTS="-XX:MaxPermSize=256m -Xmx768m"

$SBT_PATH/bin/sbt -sbt-dir $SBT_DIR -ivy $IVY_DIR assembly

