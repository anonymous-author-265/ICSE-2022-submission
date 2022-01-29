# Retrieving Data Constraint Implementations Using Fine-Grained Code Patterns - Replication Package

## Abstract

For software systems that are meant to support an organization, business rules are an important part of their requirements. Business rules describe the operations, definitions, and constraints that apply to the organization. Within the software system, business rules are often translated into constraints on the values that are required or allowed for data, called data constraints. Business rules are subject to frequent changes, which in turn require changes to the corresponding data constraints in the software. The ability to efficiently and precisely identify where data constraints are implemented in the source code is essential for performing such necessary changes.

In this paper, we introduce Lasso, the first technique that automatically retrieves the method and line of code where a given data constraint is enforced. Lasso is based on traceability link recovery approaches and leverages results from recent research that identified line-of-code level implementation patterns for data constraints. We implement two versions of Lasso that can retrieve data constraint implementations when they are implemented with any one of 13 frequently occurring patterns. We evaluate the two versions on a set of 299 data constraints from 15 real-world Java systems, and find that they improve method-level link recovery by 30% (and 163%, respectively) in terms of %HITS@10, compared to their text-retrieval-based baseline. More importantly, the Lasso variants correctly identify the line of code implementing the constraint inside the methods for 68% of the 299 constraints.

## Contents

1. [baseline-calibration](./baseline-calibration): Results of calibration tests for baselines. We evaluated the best constraint input for Lucene and LSI, and additionally the dimension parameter for LSI.

2. [data](./data): Source code, textual artifacts, and 299 constraints for the 15 target systems.

3. [results](./results): Full study results.

4. [stopwords.txt](./stopwords.txt): List of stopwords used by both Lasso-13 instances for preprocessing the textual fields of constraints and enforcing statement candidates.

5. [code](./code): Source code of Lasso.
