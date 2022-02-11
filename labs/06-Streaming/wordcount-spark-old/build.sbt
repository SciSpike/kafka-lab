name := "spark-kafka"

version := "1.0"

scalaVersion := "2.11.8"

val sparkVersion = "2.0.2"

resolvers += "Spark Packages Repo" at "https://dl.bintray.com/spark-packages/maven"

libraryDependencies ++= Seq(
  "com.github.benfradet" %% "spark-kafka-0-10-writer" % "0.2.0",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  ("org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion ) exclude ("org.spark-project.spark", "unused")
)

assemblyJarName in assembly := name.value + ".jar"
        