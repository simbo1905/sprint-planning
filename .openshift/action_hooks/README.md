
# Running the code on Redhat OpenShift cloud

```sh
rhc app create -a planningpoker -t diy 
cd planningpoker/
git remote add upstream https://github.com/simbo1905/planning-poker.git
git pull -s recursive -X theirs upstream master
sbt test
git push
rhc tail planningpoker -f ./diy/logs/server.log -o '-n100'
```

To see the url of the running app

```sh
rhc app show --app planningpoker
```

If you need to ssh onto the box you can tail the files with: 

```sh
tail -1000f ${OPENSHIFT_DIY_LOG_DIR}server.log &
```

End. 
