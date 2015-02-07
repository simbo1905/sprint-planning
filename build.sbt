name := """sprint-planning"""

version := "0.5"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "org.mashupbots.socko" %% "socko-webserver" % "0.6.0",
  "io.argonaut" % "argonaut_2.11" % "6.1-M5",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "junit" % "junit" % "4.8" % "test->default",
  "org.mockito" % "mockito-core" % "1.9.5" % "test->default",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test"
  )

net.virtualvoid.sbt.graph.Plugin.graphSettings
