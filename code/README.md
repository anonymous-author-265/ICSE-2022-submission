# Code of Lasso Tool

## Requirements

- Java Development Kit (JDK) 11.

- Ensure that the `JAVA_HOME` environment variable is set to the correct path for the JDK 11, for example `/usr/lib/jvm/java-11-openjdk-amd64`

## Quick Start

Follow these steps to replicate the results of the paper on an Ubuntu-based operating system.

1. Make sure that the current working directory is the one where this file is located (`code`)
2. `sudo apt-get install openjdk-11-jdk`
3. `./evaluate-lasso ../replication`

The results will be stored in the `replication` directory.

## Contents

1. `Lasso`: project containing the source code of the Lasso tool.
2. `java-callgraph` and `seers-base`: projects used as libraries.
3. `gradlew` and `gradlew.bat`: scripts used to build and run the tool on Linux and Windows systems, respectively.
4. `evaluate-lasso`: bash script to run the evaluation of the tool on Linux systems.

## Lasso Interface Description

The project is configured to use Gradle for building and running. The build is also configured to automatically extract the target system data from the compressed archives for ease of replication. This allows for a single-command replication of the results presented in the paper (using the `evaluate-lasso` script).

The main class responsible for executing the code of Lasso is `edu.utdallas.seers.lasso.experiment.ConstraintTracingEvaluator`. The Gradle build is already configured so that this class is executed by default when using the run task (`./gradlew run`). For help on the parameters that Lasso takes you can execute `./gradlew run --args="-h"`. They are also explained here:

- Positional parameters are required and tell Lasso where to find the data and where to write the results to: 

  - `constraints_file` is the CSV file containing the constraints to be traced.
  - `sources_dir` is the directory with the source code of each system to be traced on.
  - `output_path` is the directory where the results will be stored.
  
- `-c` (lowercase c) option allows for setting the cache path. Lasso uses indexes to speed up search and to provide fast results on subsequent runs. Indeed, most of the execution time the first time the tool is run is spent indexing the source code of the systems, and subsequent runs will take less than a tenth of the time, even with new constraints.

- `-C` option (uppercase C) tells Lasso to ignore and rebuild all indexes. Executing the evaluation will take longer, but this will fix any issues with the cache.

- `-k` option allows for setting a custom list of K values for the %HITS@K metric that will be used to present the results.

- `-i` enables printing the full results for each constraint. **WARNING**, will result in long files being written to the output directory.

## Interpreting results

Executing Lasso will result in 3 or 4 files as output:

- The cache will be saved to the directory specified by the `-c` option, or a default one in the system temp directory if none is provided. The files in this directory (including those with `.cfe`, `.cfs` and `.si` extensions) are not part of the results and are only used by Lasso to perform the search. These consist of Lucene indexes used internally by the tool.

- `results-all.csv` contains the summarized retrieval metrics for each constraint. This file contains the same columns as the [results-all.csv](../results/results-all.csv) provided with this replication package. See the [corresponding readme](../results/README.md) for details.

- `results-summary.csv` contains the summarized retrieval metrics for the evaluation of each technique. This file contains the same columns as the [results-summary.csv](../results/results-summary.csv) provided with this replication package. See the [corresponding readme](../results/README.md) for details.

- `individual-results.csv` will be produced if the `-i` option was provided. This file contains the individual results for each constraint run with each technique used in the evaluation. The columns are:

  - **Constraint, Technique**: Identification of the constraint and the technique that was used to run it.
  - **Score**: The final numeric score of the result.
  - **DecomposedScore**: The value for each Lasso scoring component in the case that technique is a Lasso variation.
  - **Is GT?**: True or false depending on whether the result is the ground truth.
  - **Rank**: 1-based rank of the result.
  - **Result**: The file and line of code of the result.

## Extending Lasso

The code of Lasso has been designed so that it is easy to add new detectors for newly identified CIPs. The code of the existing detectors can be found in the `edu.utdallas.seers.lasso.ast.matcher` package, and they are all descendants of the `edu.utdallas.seers.lasso.ast.matcher.PatternMatcher` class. The 13 detectors for the current implementation of Lasso can be used as reference to implement future ones.

To add new detectors, follow these two steps:

1. Implement the detector as a subclass of the `PatternMatcher` class. This class contains utility methods for creating ESCs (which are subclasses of the `edu.utdallas.seers.lasso.ast.matcher.PatternInstance` class) and default behavior to return no result for all AST nodes. The new implementation should override the methods corresponding to the AST node type that can contain the pattern to perform the checking and return a `PatternInstance` if there's a match. See the existing detectors for reference, and also the [JavaParser documentation](https://javaparser.org/getting-started.html) for details on the AST representation.

2. Add a reference to the new detector class to `edu.utdallas.seers.lasso.data.entity.PatternType` as a new Enum constant.

After following these steps, Lasso will automatically start accepting input constraints where the ground truth is an instance of the new pattern.

For more details on how to expand the data set, please see the corresponding [readme](../data/README.md).

## Troubleshooting

If you are getting execution errors, you can attempt running the tool with the ignore cache option (`-C`). This will solve any problems caused by incomplete runs, but will take longer to finish.
