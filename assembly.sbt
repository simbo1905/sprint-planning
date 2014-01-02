import AssemblyKeys._ 

assemblySettings

jarName in assembly := "planning-poker-runnable.jar"

test in assembly := {}

mainClass in assembly := Some("scrumpoker.server.ScrumGameApp")
