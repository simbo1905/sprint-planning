import AssemblyKeys._ 

assemblySettings

jarName in assembly := "sprint-planning-runnable.jar"

test in assembly := {}

mainClass in assembly := Some("scrumpoker.server.ScrumGameApp")
