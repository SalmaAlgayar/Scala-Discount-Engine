ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "OrdersDiscount",
    // Add this to make sure macros/newtypes work correctly in 2.13
    scalacOptions ++= Seq("-Ymacro-annotations")
  )

val DoobieVersion = "1.0.0-RC12"

libraryDependencies ++= Seq(
  // Core Libraries
  "org.typelevel" %% "cats-effect"    % "3.5.4", // Added explicitly
  "org.tpolecat"  %% "doobie-core"     % DoobieVersion,
  "org.tpolecat"  %% "doobie-postgres" % DoobieVersion,
  "org.tpolecat"  %% "doobie-hikari"   % DoobieVersion,
  "org.postgresql" % "postgresql"      % "42.7.3",

  // Utilities & Logging
  "io.estatico"   %% "newtype"         % "0.4.4",
  "org.typelevel"  %% "log4cats-slf4j"  % "2.6.0",
  "ch.qos.logback" %  "logback-classic" % "1.2.13",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
)