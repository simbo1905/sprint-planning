#!/bin/bash
test -d "$HOME/planning-poker" || mkdir "$HOME/planning-poker"
cd $HOME/planning-poker
git checkout -- ./src/main/resources/index.html
git pull origin master
DATETIME=`date +"%y%m%d%H%M"`
sed -i "s/<\/body>/Version: $DATETIME<\/body>/1" ./src/main/resources/index.html
