name := "lila-ws"

version := "2.0"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)

val akkaVersion          = "2.6.6"
val kamonVersion         = "2.1.3"
val nettyVersion         = "4.1.50.Final"
val reactivemongoVersion = "1.0.3"

scalaVersion := "2.13.3"

libraryDependencies += "org.reactivemongo"          %% "reactivemongo"                % reactivemongoVersion
libraryDependencies += "org.reactivemongo"          %% "reactivemongo-bson-api"       % reactivemongoVersion
libraryDependencies += "org.reactivemongo"           % "reactivemongo-shaded-native"  % s"$reactivemongoVersion-linux-x86-64"
libraryDependencies += "io.lettuce"                  % "lettuce-core"                 % "5.3.1.RELEASE"
libraryDependencies += "io.netty"                    % "netty-handler"                % nettyVersion
libraryDependencies += "io.netty"                    % "netty-codec-http"             % nettyVersion
libraryDependencies += "io.netty"                    % "netty-transport-native-epoll" % nettyVersion classifier "linux-x86_64"
libraryDependencies += "com.typesafe.akka"          %% "akka-actor-typed"             % akkaVersion
libraryDependencies += "com.typesafe.akka"          %% "akka-slf4j"                   % akkaVersion
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging"                % "3.9.2"
libraryDependencies += "joda-time"                   % "joda-time"                    % "2.10.6"
libraryDependencies += "com.github.blemale"         %% "scaffeine"                    % "4.0.1" % "compile"
libraryDependencies += "ch.qos.logback"              % "logback-classic"              % "1.2.3"
libraryDependencies += "com.typesafe.play"          %% "play-json"                    % "2.9.0"
libraryDependencies += "io.kamon"                   %% "kamon-core"                   % kamonVersion
libraryDependencies += "io.kamon"                   %% "kamon-influxdb"               % kamonVersion
libraryDependencies += "io.kamon"                   %% "kamon-system-metrics"         % kamonVersion
libraryDependencies += "com.softwaremill.macwire"   %% "macros"                       % "2.3.7" % "provided"
libraryDependencies += "com.roundeights"            %% "hasher"                       % "1.2.1"

libraryDependencies ++= List(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
  "org.scalaz"             %% "scalaz-core"              % "7.2.30",
  "org.specs2"             %% "specs2-core"              % "4.7.0" % "test",
  "org.specs2"             %% "specs2-scalaz"            % "4.7.0" % "test",
  "com.github.ornicar"     %% "scalalib"                 % "6.8",
  "joda-time"              % "joda-time"                 % "2.10.6"
)

resolvers ++= Seq(
  "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master",
  "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
)
resolvers += Resolver.sonatypeRepo("snapshots")

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
    "-Wdead-code",
    "-Wextra-implicit",
    //"-Wnumeric-widen",
    "-Wunused:imports",
    "-Wunused:locals",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:implicits",
    "-Wunused:params",
    /* "-Wvalue-discard" */
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

javaOptions in reStart += "-Xmx128m"

/* scalafmtOnCompile := true */
