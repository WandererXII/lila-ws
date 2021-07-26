Handle incoming websocket traffic for [lishogi.org](https://lishogi.org).

```
lishogi <-> redis <-> lishogi-ws <-> websocket <-> client
```

Start:
```
sbt
~reStart
```

Start with custom config file:
```
sbt -Dconfig.file=/path/to/my.conf
```

Custom config file example:
```
include "application"
http.port = 8080
netty.useEpoll = true
mongo.uri = "mongodb://localhost:27017/lishogi"
redis.uri = "redis://127.0.0.1"
```

Code formatting
###

This repository uses [scalafmt](https://scalameta.org/scalafmt/).

Please [install it for your code editor](https://scalameta.org/scalafmt/docs/installation.html)
if you're going to contribute to this project.

If you don't install it, please run `scalafmtAll` in the sbt console before committing.
