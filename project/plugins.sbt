addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.8")
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
