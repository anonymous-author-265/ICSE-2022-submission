# Study data

## Contents

1. [constraints.csv](./constraints.csv): The set of 299 constraints used for the study, from both CDS and VDS.
   - Note that the 163 constraints in CDS come from the data set of Florez *et al.* [1]. We added these constraint to this replication package because the "operands" field was used only for this study and did not exist in the aforementioned study.
 
2. [target-system-data](./target-system-data): Source code and textual artifacts for the 15 systems used in the empirical evaluation of Lasso-13.

## CSV Columns

1. **Project, ID, File/Section**: identification information.
2. **Context**: Constraint context as defined in the study, *i.e.* paragraph where the constraint is found.
3. **Text**: Actual text that describes the constraint.
4. **Interpreted Constraint**: Rewritten Text field for clarity. Not used in the study but provided to aid understanding of constraints.
5. **Operands**: Constraint operands as defined in the study, *i.e.* noun phrases from the constraint context referring to data that the constraint operates on. Manually extracted by one author.
6. **Enforcing statement**: Line(s) of code where the constraint is implemented. Extracted by tracers and verified by two authors.
7. **Enforcing Statement CIP**: CIP that the constraint implementation adheres to. Assigned by one author.
8. **Constraint Type**: Type of constraint as defined by the study. Assigned by one author.
9. **Data Set**: CDS or VDS as appropriate.

## Target System Data

Both source code artifacts (`doc-txt.zip`) and source code (`sources.zip`) of each system are found in each directory.

Each directory also contains an `exclude.txt` file, which signals to Lasso which directories contain test files ans hsould be ignored.

## References

[1] Florez, J.M., Moreno, L., Zhang, Z., Wei, S. and Marcus, A., 2021. An Empirical Study of Data Constraint Implementations in Java. arXiv preprint arXiv:2107.04720.
