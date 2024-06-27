name := "lila-ws"

version := "2.0"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)

val akkaVersion          = "2.6.21"
val kamonVersion         = "2.4.8"
val nettyVersion         = "4.1.111.Final"
val reactivemongoVersion = "1.0.10"

scalaVersion := "2.13.14"

libraryDependencies += "org.reactivemongo"          %% "reactivemongo"                % reactivemongoVersion
libraryDependencies += "org.reactivemongo"          %% "reactivemongo-bson-api"       % reactivemongoVersion
libraryDependencies += "org.reactivemongo"           % "reactivemongo-shaded-native"  % s"$reactivemongoVersion-linux-x86-64"
libraryDependencies += "io.lettuce"                  % "lettuce-core"                 % "6.3.2.RELEASE"
libraryDependencies += "io.netty"                    % "netty-handler"                % nettyVersion
libraryDependencies += "io.netty"                    % "netty-codec-http"             % nettyVersion
libraryDependencies += "io.netty"                    % "netty-transport-native-epoll" % nettyVersion classifier "linux-x86_64"
libraryDependencies += "com.typesafe.akka"          %% "akka-actor-typed"             % akkaVersion
libraryDependencies += "com.typesafe.akka"          %% "akka-slf4j"                   % akkaVersion
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging"                % "3.9.5"
libraryDependencies += "joda-time"                   % "joda-time"                    % "2.12.7"
libraryDependencies += "com.github.blemale"         %% "scaffeine"                    % "5.2.1" % "compile"
libraryDependencies += "ch.qos.logback"              % "logback-classic"              % "1.4.14"
libraryDependencies += "com.typesafe.play"          %% "play-json"                    % "2.10.6"
libraryDependencies += "io.kamon"                   %% "kamon-core"                   % kamonVersion
libraryDependencies += "io.kamon"                   %% "kamon-influxdb"               % kamonVersion
libraryDependencies += "io.kamon"                   %% "kamon-system-metrics"         % kamonVersion
libraryDependencies += "com.softwaremill.macwire"   %% "macros"                       % "2.5.9" % "provided"
libraryDependencies += "com.roundeights"            %% "hasher"                       % "1.2.1"
libraryDependencies += "com.github.ornicar"         %% "scalalib"                     % "7.0.2"
libraryDependencies += "io.github.WandererXII"      %% "scalashogi"                   % "12.1.1"

resolvers ++= Seq(
  "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"
)
resolvers ++= Resolver.sonatypeOssRepos("snapshots")

scalacOptions ++= Seq(
    "-explaintypes",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Ymacro-annotations",
    // Warnings as errors!
    //"-Xfatal-warnings",
    // Linting options
    "-unchecked",
    "-Xcheckinit",
    "-Xlint:adapted-args",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:deprecation",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:nullary-unit",
    "-Xlint:option-implicit",
    "-Xlint:package-object-classes",
    "-Xlint:poly-implicit-overload",
    "-Xlint:private-shadow",
    "-Xlint:stars-align",
    "-Xlint:type-parameter-shadow",
    "-Wconf:cat=other-implicit-type:s",
    "-Wdead-code",
    "-Wextra-implicit",
    //"-Wnumeric-widen",
    "-Wunused:imports",
    "-Wunused:locals",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:implicits",
    "-Wunused:explicits",
    /* "-Wvalue-discard" */
)

Compile / doc / sources := Seq.empty
Compile / packageDoc / publishArtifact := false

reStart / javaOptions += "-Xmx128m"

/* scalafmtOnCompile := true */
