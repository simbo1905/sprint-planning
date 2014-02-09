#!/bin/bash
test -d "$HOME/sprint-planning" || mkdir "$HOME/sprint-planning"
cd $HOME/sprint-planning
git checkout -- ./src/main/resources/index.html
git pull origin master
DATETIME=`date +"%y%m%d%H%M"`
sed -i "s/<\/body>/Version: $DATETIME<\/body>/1" ./src/main/resources/index.html
