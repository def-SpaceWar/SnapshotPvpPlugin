#!/bin/sh

rm -rf ./build/libs/*.jar
./gradlew build
mv ./build/libs/*.jar /home/aryan/Desktop/SnapshotPvp/plugins/SnapshotPvpPlugin.jar
