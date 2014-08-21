# Sprint Planning

A lightweight implementation of a Sprint Planning (aka Scrum Poker) web application. 

The browser code uses HTML5 [websockets](http://www.websocket.org/). The server logic is written in [Scala](http://www.scala-lang.org/) using the core [Akka](http://akka.io/) libraries for concurrency and [reactive programming](http://www.reactivemanifesto.org/). The networking layer is [Netty 4](https://github.com/netty/netty) with HTTP routing provided by the awesome [Socko](https://github.com/mashupbots/socko) server. 

When the browser does not support websockets or if a websocket cannot be opened due to proxy issues the browser code does a [graceful fallback](https://github.com/ffdead/jquery-graceful-websocket) to AJAX polling. 

The server is a single jar file which runs on a standard Java JVM. There is a version running at http://www.sprint-planning.info/

## Build Prerequisites

  - Java Platform (JDK 6+) http://www.oracle.com/technetwork/java/javase/downloads/index.html
  - SBT http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html#installing-sbt

## Building

```sh
git clone https://github.com/simbo1905/sprint-planning.git
cd sprint-planning
sbt test
```

## Running

For local testing you can run the code from the build folder with:

```sh
sbt run
```

Create then launch a runnable jar for a server deployment with:

```sh
sbt assembly
java -jar ./target/scala-2.10/sprint-planning-runnable.jar 127.0.0.1 80
```
N.B. You would need to use sudo to run the command as root to bind the server to port 80 on Mac OSX or Linux. 

You should see the output: 

```
Serving web content out of src/main/resources
Open a few browsers and navigate to http://localhost:80. Start playing!
```

You can now go to that local url and play. 

Press ```Ctrl+c``` to kill the process which stops the server. 

The process takes two mandatory and two optional arguments:

1. IP/interface to bind to
2. Port to serve static content
3. Websocket alternative port (defaults to static content port)
4. Graceful websocket polling port (defaults to static content port)

## Running Behind A Reverse Proxy

To run the socko server on port 8888 behind an nginx reverse proxy running 
on port 80 first start socko with: 

```
java -jar sprint-planning-runnable.jar 127.0.0.1 8888 80 80
``` 

That specifies that the websocket and polling fallback ports given to the browser will point 
to the reverse proxy running on port 80. The nginx reverse proxy configuration 
is then set to forward regular and websocket traffic to socko running on 8888: 

```
server {
        server_name www.sprint-planning.info;

        location / {
                proxy_set_header X-Real-IP  $remote_addr;
                proxy_set_header X-Forwarded-For $remote_addr;
                proxy_set_header Host $host;
                proxy_pass http://www.sprint-planning.info:8888;
        }

        location /websocket/ {
                proxy_pass http://www.sprint-planning.info:8888;
                proxy_http_version 1.1;
                proxy_set_header Upgrade $http_upgrade;
                proxy_set_header Connection "upgrade";
        }
}

```

## Creating A Skin

The site is effectively two pages under ```src/main/resources``` which are ```index.html``` and ```poker.html```. The messages sent and received from the server show up on the browsers javascript console (e.g. firefox / chrome web developers console) as ```out>``` and ```in>``` entries which mean *out* from the browser to the server else *in* from the server to the browser, e.g. 

```sh
out> {"mType":"Reveal"}
in>  {"cards":[{"player":"417007700350734336","card":13,"mType":"CardDrawn"},{"player":"417007962322767872","card":8,"mType":"CardDrawn"}],"mType":"CardSet"}"
```

shows that a player had hit the reveal cards button which sent a message of type "Reveal" to the server. The server responded with a message of type "CardSet" which contained two "CardDrawn" entries for two players which had values "8" and "13". 

The complete set of JSON messages are defined in the file ```sprint-planning/src/main/scala/scrumpoker/game/Messages.scala```

Sent from server to browser:

- RoomSize: Sent to all browsers when a player enters the room. Should be used to give a visual indication that more players have joined the room. Also sent to all browsers in response to a reset message. 
- DrawnSize: Sent to all browsers when a player selects a card. Should be used to indicate how many players in the room have selected a card. A message is also sent to all browsers in response to a reset message to show zero cards drawn. 
- CardSet: Sent to all browsers when the cards are revealed in response to a Reveal message. Contains the complete state of the game as the list of CardDrawn messages sent by all the players. 

Sent from browser to server: 

- CardDrawn: Sent from the browser when a player selects a card.
- CardUndrawn: Sent from the browser when a player unselects the card they had selected. 
- PlayerExit: Sent from the browser to the server when the browser window is closed. 
- Reveal: Sent from the browser to the server when a player presses the reveal button. Results in a CardSet being sent to all browsers. 
- Reset: Sent from the browser to the server when a player presses the reset button. Results in a Reset, RoomSize and DrawnSize zero messages all being sent to all browsers to cause the game state to be cleared. 

What's with all the ports? 
----

At the time of writing the app RedHat OpenShift required that you run with ports "8080 8000 80". Thats the port to bind to, the port websockets is running on and the port to fallback to polling if the browser is behind a proxy which does not support websockets.  

License
----

Apache 2.0 http://apache.org/licenses/LICENSE-2.0.html

TODO
----

[_] Port to Backbone.js and have it so that it remembers your card selection if you reload the page. 

End.
