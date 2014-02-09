name := """sprint-planning"""

version := "0.4"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "org.mashupbots.socko" %% "socko-webserver" % "0.4.0",
  "io.argonaut" %% "argonaut" % "6.1-M2",
  "junit" % "junit" % "4.8" % "test->default",
  "org.mockito" % "mockito-core" % "1.9.5" % "test->default",
  "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"
  )

net.virtualvoid.sbt.graph.Plugin.graphSettings
