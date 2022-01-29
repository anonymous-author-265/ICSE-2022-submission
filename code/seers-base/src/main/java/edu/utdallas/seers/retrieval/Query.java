package edu.utdallas.seers.retrieval;

import java.util.List;

public interface Query {
    List<String> getGroundTruthIDs();

    String getID();

    String getField(String name);
}
