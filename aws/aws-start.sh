#!/bin/bash
# You need to sudo to start the server on port 80
cd $HOME/sprint-planning
nohup java -jar $HOME/sprint-planning/target/scala-2.10/sprint-planning-runnable.jar 0.0.0.0 80 < /dev/null > $HOME/sprint-planning/server.log &

