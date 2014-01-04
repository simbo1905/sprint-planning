# Planning Poker

A lightweight implementation of Planning Poker / Scrum Poker using JavaScript and Websockets with the serverside implimented with Akka/Socko. 

## Build Prerequisites

  - Java Platform (JDK 7+) http://www.oracle.com/technetwork/java/javase/downloads/index.html
  - SBT http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html#installing-sbt

## Building

```sh
git clone https://github.com/simbo1905/planning-poker.git planning-poker
cd planning-poker
sbt test
```

## Running

Running from a local build:

```sh
sbt run
```

Create a runnable jar to deploy and starting it on a server (see .openshift/action_hooks/*)

```sh
sbt assembly
java -jar ./target/scala-2.10/planning-poker-runnable.jar 127.0.0.1 8080 8000 80
```

You should see the output 

```
Serving web content out of src/main/resources
Open a few browsers and navigate to http://localhost:8080. Start playing!
```

You can now go to that local url and play. 

Press ```Ctrl+c``` to kill the process which stops the server. 

The three arguments are:

1. IP/interface to bind to
2. Port to serve static content on
3. Websocket alternative port
4. Graceful websocket polling port

In the example above Redhat Cloud has you bind to port 8080, browsers connect to get static content via port 80, browsers websockets are told to use port 8000 and if the browser cannot use websockets it is tool to poll port 80. The server only binds to the first port the others are sent to the browser as overrides based on the hosting port forwarding rules. A simple server installation could just specify "80 80 80" to run all services on the main web port.   

## Creating A Skin

The site is effectively two pages under ```src/main/resources``` which are ```index.html``` and ```poker.html```. The messages sent and received from the server show up on the browsers javascript console (e.g. firefox / chrome web developers console) as ```out>``` and ```in>``` entries which mean *out* from the browser to the server else *in* from the server to the browser, e.g. 

```sh
out> {"mType":"Reveal"}
in>  {"cards":[{"player":"417007700350734336","card":13,"mType":"CardDrawn"},{"player":"417007962322767872","card":8,"mType":"CardDrawn"}],"mType":"CardSet"}"
```

shows that a player had hit the reveal cards button which sent a message of type "Reveal" to the server. The server responded with a message of type "CardSet" which contained two "CardDrawn" entries for two players which had values "8" and "13". 

The complete set of messages are defined in the file ```/planning-poker/src/main/scala/scrumpoker/game/Messages.scala```

- RoomSize: Sent to all browsers when a player enters the room. Should be used to give a visual indicatation that more players have joined the room. Also sent to all browsers in response to a reset message. 
- DrawnSize: Sent to all browsers when a player selects a card. Should be used to indicte how many players in the room have selected a card. A message is also sent to all browsers in response to a reset eessage to show zero cards drawn. 
- CardSet: Sent to all browsers when the cards are revealed in response to a Reveal message. Contains the complete state of the game as the list of CardDrawn messages sent by all the players. 
- CardDrawn: Sent from the browser when a player selects a card.
- CardUndrawn: Sent from the browser when a player unselects the card they had selected. 
- PlayerExit: Sent from the browser to the server when the browser window is closed. 
- Reveal: Sent from the brower to the server when a player presses the reveal button. Results in a CardSet being sent to all browsers. 
- Reset: Sent from the browser to the server when a player presses the rest button. Results in a Reset, RoomSize message and DrawnSize zero message being sent to all browsers to cause the game state to be cleared. 

License
----

Apache 2.0 http://apache.org/licenses/LICENSE-2.0.html

TODO
----

[] Compress the static resources
	

