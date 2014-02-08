#!/bin/bash

# The logic to stop your application should be put in this script.
if [ -z "$(ps -ef | grep 'planning-poker-runnable.jar' | grep -v grep)" ]
then
    echo "Application is already stopped"
else
    echo "Stopping application"
    kill `ps -ef | grep 'planning-poker-runnable.jar' | grep -v grep | awk '{ print $2 }'` > /dev/null 2>&1
fi
