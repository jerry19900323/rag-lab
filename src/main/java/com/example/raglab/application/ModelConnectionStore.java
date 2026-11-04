package com.example.raglab.application;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for model connections and purpose bindings. */
public interface ModelConnectionStore {
    List<ModelConnection> listConnections();
    Optional<ModelConnection> findConnection(String id);
    void saveConnection(ModelConnection connection);
    void deleteConnection(String id);
    List<ModelAssignment> listAssignments();
    void saveAssignment(ModelAssignment assignment);
}
