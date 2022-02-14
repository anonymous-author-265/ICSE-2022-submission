# Retrieving Data Constraint Implementations Using Fine-Grained Code Patterns - Replication Package

## Abstract

Business rules are an important part of the requirements of software systems that are meant to support an organization. These rules describe the operations, definitions, and constraints that apply to the organization. Within the software system, business rules are often translated into constraints on the values that are required or allowed for data, called data constraints. Business rules are subject to frequent changes, which in turn require changes to the corresponding data constraints in the software. The ability to efficiently and precisely identify where data constraints are implemented in the source code is essential for performing such necessary changes.

In this paper, we introduce Lasso, the first technique that automatically retrieves the method and line of code where a given data constraint is enforced. Lasso is based on traceability link recovery approaches and leverages results from recent research that identified line-of-code level implementation patterns for data constraints. We implement three versions of Lasso that can retrieve data constraint implementations when they are implemented with any one of 13 frequently occurring patterns. We evaluate the three versions on a set of 299 data constraints from 15 real-world Java systems, and find that they improve method-level link recovery by 30%, 70%, and 163%, in terms of true positives within the first 10 results, compared to their text-retrieval-based baseline. More importantly, the Lasso variants correctly identify the line of code implementing the constraint inside the methods for 68% of the 299 constraints.

## Contents

1. [baseline-calibration](./baseline-calibration): Results of calibration tests for baselines. We evaluated the best constraint input for Lucene and LSI, and additionally the dimension parameter for LSI.

2. [code](./code): Extensible source code of the Lasso tool.

3. [data](./data): Source code, textual artifacts, and 299 constraints for the 15 target systems used as objects of the study in the paper.

4. [misc](./misc): Contains the list of stopwords used by both Lasso-13 instances for preprocessing the textual fields of constraints and enforcing statement candidates. These stop words can also be found in the source code of Lasso.

4. [results](./results): Full study results.

## Extending Lasso and Expanding the Data Set

We designed the code of our tool to be flexible, so that it can both be extended to provide additional functionality, and also can be seamlessly used with new data. Please see the README files for the [code](./code/README.md) and [data](./data/README.md) for instructions on how to extend Lasso and how to expand the data set, respectively.
