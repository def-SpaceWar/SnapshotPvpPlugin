#!/bin/bash

rm -rf ./build/libs/*.jar
./gradlew build $@
mv ./build/libs/*.jar /home/aryan/Games/Minecraft/SnapshotPvp/plugins/SnapshotPvpPlugin.jar
