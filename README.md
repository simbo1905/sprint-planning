# Planning Poker

A lightweight implimentation of Planning Poker / Scrum Poker using JavaScript and Websockets with the serverside implimented with Akka/Socko. 

## Build Prerequisites

  - Java Platform (JDK 7+) http://www.oracle.com/technetwork/java/javase/downloads/index.html
  - SBT http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html#installing-sbt

## Building

```sh
git clone [git-repo-url] planning-poker
cd planning-poker
sbt run
```

## Running

```sh
sbt run
```
You should see the output 

```
Serving web content out of src/main/resources
Open a few browsers and navigate to http://localhost:8888. Start playing!
```

You can now go to that local url and play. 

Press ```Ctrl+c``` to kill the process which stops the server. 

## Creating A Skin

The site is effectively two pages under ```src/main/resources``` which are ```index.html``` and ```poker.html```. 

License
----

Apache 2.0 http://apache.org/licenses/LICENSE-2.0.html

