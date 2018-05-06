#!/usr/bin/env bash
mvn clean
mvn package -U
scp target/wordvectodocvec-1.0-SNAPSHOT.jar vec:/data/wangyuqin/bin