
# Running the code on Redhat OpenShift cloud

```sh
rhc app create -a planningpoker -t diy 
cd planningpoker/
git remote add upstream https://github.com/simbo1905/planning-poker.git
git pull -s recursive -X theirs upstream master
sbt test
git push
```

Then ssh to the server and tail the logs else use the rhc-tail-files command

```sh
tail -1000f ${OPENSHIFT_DIY_LOG_DIR}server.log &
```

To see the url of the running app

```sh
rhc app show --app planningpoker
```

End. 