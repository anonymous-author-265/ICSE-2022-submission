# Baseline Calibration

## All Results

[lsi-input-dimension-calibration-all.csv](./lsi-input-dimension-calibration-all.csv) and [lucene-input-calibration-all.csv](./lucene-input-calibration-all.csv) contain the results for each constraint in the data set for the calibration tests.

### Columns

1. **Configuration**: The input (or input + dimension in the case of LSI) that was tested.

2. **Project, Constraint ID, Data Set**: Constraint identification.

3. **Method Rank**: position of the ground truth method in the result list, or empty if it was not retrieved.

4. **HIT@N?**: 1 if the constraint's *method rank* was <= N, otherwise 0.

5. **Recall**: 1 if the ground truth method was retrieved, 0 otherwise.

6. **Number Method Results**: size of the method result list.

7. **Reciprocal Rank**: 1 divided by method rank if ground truth was retrieved, 0 otherwise.

## Summary

[lsi-input-dimension-calibration-summary.csv](./lsi-input-dimension-calibration-summary.csv) and [lucene-input-calibration-summary.csv](./lucene-input-calibration-summary.csv) contain the aggregated results per scenario of the calibration tests.

### Columns

1. **Configuration**: The input (or input + dimension in the case of LSI) that was tested.

2. **Average Method Rank**: average *method rank* only for constraint where the ground truth was retrieved.

3. **%HIT@N, HIT@N**: percentage and number of constraints where the ground truth was retrieved in position <= N.

4. **Average Recall**: recall averaged over all constraints.

5. **MRR**: *reciprocal rank* averaged over all constraints.
