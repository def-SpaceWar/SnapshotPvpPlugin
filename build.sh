#!/bin/sh

./gradlew build
scp -i ~/Desktop/SnapshotPvp.key build/libs/*.jar opc@129.213.91.103:/home/opc/papertest/plugins/SnapshotPvpPlugin.jar
rm -rf build/libs/*.jar
