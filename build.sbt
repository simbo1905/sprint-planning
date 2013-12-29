name := """planning-poker"""

version := "0.1"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-contrib" % "2.2.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0",
  "org.mashupbots.socko" %% "socko-webserver" % "0.3.0",
  "io.argonaut" %% "argonaut" % "6.1-M1",
  "junit" % "junit" % "4.8" % "test->default",
  "org.mockito" % "mockito-core" % "1.9.5" % "test->default",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test")

