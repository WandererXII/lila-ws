#!/bin/sh

APP=lishogi-ws-2.0
REMOTE=$1
REMOTE_DIR="/home/lishogi-ws"
stage="target/universal/stage"

echo "Deploy $APP to server $REMOTE:$REMOTE_DIR"

rm -rf $stage
sbt stage

if [ $? != 0 ]; then
  echo "Deploy canceled"
  exit 1
fi

RSYNC_OPTIONS=" \
  --archive \
  --no-o --no-g \
  --force \
  --delete \
  --progress \
  --compress \
  --checksum \
  --verbose \
  --exclude RUNNING_PID \
  --exclude '.git/'"

include="$stage/bin $stage/lib"
rsync_command="rsync $RSYNC_OPTIONS $include $REMOTE:$REMOTE_DIR"
echo "$rsync_command"
$rsync_command
echo "rsync complete"

read -n 1 -p "Press [Enter] to continue."

echo "Restart lishogi-ws"
ssh $REMOTE "systemctl restart lishogi-ws"

echo "Deploy complete"

