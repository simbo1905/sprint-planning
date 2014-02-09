#!/bin/bash
# You need to sudo to start the server on port 80
cd $SPRINT_PLANNING_HOME
nohup java -jar $SPRINT_PLANNING_HOME/target/scala-2.10/sprint-planning-runnable.jar 0.0.0.0 80 < /dev/null > $SPRINT_PLANNING_HOME/server.log &
