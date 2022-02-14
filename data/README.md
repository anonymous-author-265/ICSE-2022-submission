# Study data

## Contents

1. [constraints.csv](./constraints.csv): The set of 299 constraints used for the study, from both CDS and VDS.
   - Note that the 163 constraints in CDS come from the data set of Florez *et al.* [1]. We added these constraint to this replication package because the original data did not contain the "operands" field which was necessary for this study and did not exist in the aforementioned one.
 
2. [target-system-data](./target-system-data): Source code and textual artifacts for the 15 systems used in the empirical evaluation of Lasso-13.

### CSV Columns

1. **Project, ID, File/Section**: identification information.
2. **Context**: Constraint context as defined in the study, *i.e.* paragraph where the constraint is found.
3. **Text**: Actual text that describes the constraint.
4. **Interpreted Constraint**: Rewritten Text field for clarity. Not used in the study but provided to aid understanding of constraints.
5. **Operands**: Constraint operands as defined in the study, *i.e.* noun phrases from the constraint context referring to data that the constraint operates on. Manually extracted by one author.
6. **Enforcing statement**: Line(s) of code where the constraint is implemented. Extracted by tracers and verified by two authors.
7. **Enforcing Statement CIP**: CIP that the constraint implementation adheres to. Assigned by one author.
8. **Constraint Type**: Type of constraint as defined by the study. Assigned by one author.
9. **Data Set**: CDS or VDS as appropriate.

### Target System Data

Both source code artifacts (`doc-txt.zip`) and source code (`sources.zip`) of each system are found in each directory.

Each directory also contains an `exclude.txt` file, which signals to Lasso which directories contain test files ans should be ignored.

## Expanding the Data Set

It is possible to expand the current data set by providing the source code of the new target systems as well as the constraints extracted from their textual artifacts. It is also possible to extend the tool itself, see the corresponding [readme](../code/README.md) for instructions.

### Target System Source Code

In a directory of your choice (for example `new-data-set`), create one directory for each new system that needs to be added to the data set and name it accordingly. Inside each one of these directories there should be two elements:

- A directory named `sources` with the complete source code of the system. The code should be unmodified, *i.e.*, all files and structure should be exactly as they're present in the development repository.

- A file named `exclude.txt`. This file should contain the paths of the test directories of the system, one per line, which will be ignored by Lasso. The paths must start from the `sources` directory without including it. For example, if the path to a test directory is `system-1/sources/src/test`, the entry in the `exclude.txt` file should be: `src/test`.

        new-data-set/
        ├─ system-1/
        │  ├─ exclude.txt
        │  ├─ sources/
        ├─ system-2/
        │  ├─ exclude.txt
        │  ├─ sources/
        
### Constraints

New constraints must be separately extracted from textual artifacts of software systems and provided to Lasso with the fields it requires. Namely, the constraint *context* and *operands* must be derived from the constraint text, while the enforcing statement and constraint type must be inferred by the user.

In a directory of your choice, create a CSV file (for example, `new-constraints.csv`). The file must be UTF-8 encoded, comma delimited (`,`), and must use a standard quote character (`"`). This file will contain the constraint data. Constraints from multiple systems can appear in the same CSV file. Each constraint will be a row in the file, and the following columns are mandatory:

- **Project**: Name of the system that the constraint belongs to. Must be the same as the directory that contains the system code, as defined in the previous section.

- **ID**: A string ID for the constraint. Can be any valid string of characters as long as it is unique.

- **Enforcing statement**: For evaluation purposes, the ground truth enforcing statement must be provided. Exactly one enforcing statement must be provided for each constraint. The format is `path/to/JavaFile.java:<line number>`. The path must start from the `sources` directory without including it. For example, if the ground truth is the file `system-1/sources/src/java/my/package/JavaClass.java` and the line is 100, the entry for this column should be: `src/java/my/package/JavaClass.java:100`

- **Enforcing Statement CIP**: Also for evaluation purposes, the CIP of the ground truth must be provided. This should be a string of lower-case alphabetic characters and dashes, and must be one of the CIPs recognized by Lasso. For instructions on how to extend the CIPs recognized by Lasso, see the corresponding [readme](../code/README.md).

- **Constraint Type**: The constraint type as defined in the study. Must be a string of lower-case alphabetic characters and dashes, and must be one of the types recognized by Lasso.

- **Context**: The text of the paragraph where the constraint appears, exactly as it is in the original document.

- **Operands**: Comma-separated list of operands. The number of operands must be appropriate for each constraint type. See the study description for more details.

- **Data Set**: Optional data set identifier for each constraint. It will be output verbatim with the result of each constraint.

## References

[1] Florez, J.M., Moreno, L., Zhang, Z., Wei, S. and Marcus, A., 2021. An Empirical Study of Data Constraint Implementations in Java. arXiv preprint arXiv:2107.04720.
