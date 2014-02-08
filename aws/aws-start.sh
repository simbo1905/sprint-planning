#!/bin/bash
# You need to sudo to start the server on port 80
cd $HOME/planning-poker
nohup java -jar $HOME/planning-poker/target/scala-2.10/planning-poker-runnable.jar 0.0.0.0 80 < /dev/null > $HOME/planning-poker/server.log &

