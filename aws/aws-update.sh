#!/bin/bash
test -d "$SPRINT_PLANNING_HOME" || mkdir "$SPRINT_PLANNING_HOME"
cd $SPRINT_PLANNING_HOME
git checkout -- ./src/main/resources/index.html
git pull origin master
DATETIME=`date +"%y%m%d%H%M"`
sed -i "s/<\/body>/Version: $DATETIME<\/body>/1" ./src/main/resources/index.html
