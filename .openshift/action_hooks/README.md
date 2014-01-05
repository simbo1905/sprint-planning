
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

To see the url of the running app (also a link in the openshift web console)

```sh
rhc app show --app planningpoker
```

## Dude, what's with all the ports? 

The start specifies ports "8080 8000 80". Since you need to be root to bind to port 80 for security on openshift you must bind to port 8080. All browser traffic to the default port of 80 which is routed to your server on port 8080. All good so far. This explains the 8080 and the 80 as the server is told to bind to port 8080 but it tells browsers which don't have websockets to poll on port 80 which will hit the server on 8080. Everyone with me so far? Good. Next up is the port 8000 which is [explained here](https://www.openshift.com/blogs/paas-websockets). Basically websocket support on openshift required that they rewrite their routing layer and that has not yet been pushed out on to the production port 80 it is being parallel run on port 8000. This is a pain because corporate users behind proxies wont be able to connect out on ports other than 80 or 443. 

End. 
