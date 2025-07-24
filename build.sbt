name := "lila-ws"

version := "2.0"

scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)

val akkaVersion          = "2.6.21"
val kamonVersion         = "2.7.5"
val nettyVersion         = "4.1.116.Final"
val reactivemongoVersion = "1.0.10"

resolvers ++= Resolver.sonatypeOssRepos("snapshots")
// for com.roundheights hasher
resolvers += "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo"                % reactivemongoVersion,
  "org.reactivemongo" %% "reactivemongo-bson-api"       % reactivemongoVersion,
  "org.reactivemongo"  % "reactivemongo-shaded-native"  % s"$reactivemongoVersion-linux-x86-64",
  "io.lettuce"         % "lettuce-core"                 % "6.5.2.RELEASE",
  "io.netty"           % "netty-handler"                % nettyVersion,
  "io.netty"           % "netty-codec-http"             % nettyVersion,
  "io.netty"           % "netty-transport-native-epoll" % nettyVersion classifier "linux-x86_64",
  "com.typesafe.akka" %% "akka-actor-typed"             % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"                   % akkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging"        % "3.9.5",
  "joda-time"                   % "joda-time"            % "2.13.1",
  "com.github.blemale"         %% "scaffeine"            % "5.3.0" % "compile",
  "ch.qos.logback"              % "logback-classic"      % "1.5.16",
  "com.typesafe.play"          %% "play-json"            % "2.10.6",
  "io.kamon"                   %% "kamon-core"           % kamonVersion,
  "io.kamon"                   %% "kamon-influxdb"       % kamonVersion,
  "io.kamon"                   %% "kamon-system-metrics" % kamonVersion,
  "com.softwaremill.macwire"   %% "macros"               % "2.6.5" % "provided",
  "com.roundeights"            %% "hasher"               % "1.2.1",
  "io.github.WandererXII"      %% "scalashogi"           % "12.2.0",
)

Compile / doc / sources                := Seq.empty
Compile / packageDoc / publishArtifact := false

reStart / javaOptions += "-Xmx128m"

scalacOptions ++= Seq(
  "-explaintypes",
  "-feature",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ymacro-annotations",
  "-unchecked",
  "-Xcheckinit",
  // Linting options
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
  // Warning options
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wunused:imports",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wunused:locals",
  "-Wunused:explicits",
  "-Wunused:implicits",
  "-Wmacros:after",
  // "-Wvalue-discard",
  "-Werror",
)
