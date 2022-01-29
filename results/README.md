# Results

## All Results

[results-all.csv](./results-all.csv) contains the results for each constraint in the data set evaluated with each technique.

### Columns

1. **Technique**: The Lasso-13 variation (Lasso-13Luc, Lasso-13TF-IDF, or Lasso13LSI) or baseline (Lucene, TF-IDF, LSI) that produced the result.

2. **Project, Constraint ID, Data Set**: Constraint identification.

3. **Method Rank**: position of the ground truth method in the result list, or empty if it was not retrieved.

4. **HIT@N?**: 1 if the constraint's *method rank* was <= N, otherwise 0.

5. **GT ESC Rank**: *ESC rank* of the ground truth inside its method, or "None" if the ground truth ESC was not retrieved. "N/A" if the ground truth method was not retrieved.

6. **GT ESC Results**: number of ESCs in the list of the ground truth method, or "N/A" if the method was not retrieved.

7. **Recall**: 1 if the ground truth method was retrieved, 0 otherwise.

8. **Number Method Results**: size of the method result list.

9. **Reciprocal Rank**: 1 divided by method rank if ground truth was retrieved, 0 otherwise.

## Summary

[results-summary.csv](./results-summary.csv) contains the aggregated results for each technique evaluated on each data set.

### Columns

1. **Technique**: Technique tested.

2. **Average Method Rank**: average *method rank* only for constraint where the ground truth was retrieved.

3. **%HIT@N, HIT@N**: percentage and number of constraints where the ground truth was retrieved in position <= N.

4. **Average Recall**: recall averaged over all constraints.

5. **MRR**: *reciprocal rank* averaged over all constraints.
