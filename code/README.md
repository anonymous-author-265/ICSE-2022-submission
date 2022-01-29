# Code of Lasso Tool

## Requirements

Java Development Kit (JDK) 11.

## Quick Start

Follow these steps to replicate the results of the paper on an Ubuntu-based operating system.

1. Make sure that the current working directory is the one where this file is located (`code`)
2. `sudo apt-get install openjdk-11-jdk`
3. `./evaluate-lasso ../replication`

The results will be stored in the `replication` directory.

## Contents

1. `Lasso`: project containing the source code of the Lasso tool.
2. `java-callgraph` and `seers-base`: projects used as libraries.
3. `gradlew` and `gradlew.bat`: used to build and run the tool on Linux and Windows systems, respectively.
4. `evaluate-lasso`: bash script to run the evaluation of the tool on Linux systems.

## Description

The project is configured to use Gradle for building and running. Use `./gradlew tasks` to see the available tasks and `./gradlew run --args="-h"` to see the options that Lasso accepts.
