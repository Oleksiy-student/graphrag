package com.ok.store;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Strategy pattern interface defining graph storage operations.
 * Allows pluggable implementations (TinkerGraph, Neo4j, etc.)
 */
public interface GraphStore {
  // Entity operations
  Vertex addEntity(String name, String type);
  Vertex addEntity(String name, String type, String wikidataId);
  
  // Chunk operations
  Vertex addChunk(String id, String text, float[] embedding);
  
  // Relationship operations
  Edge addEdge(Vertex from, Vertex to, String label, Map<String,Object> props);
  
  // Persistence operations
  void saveGraph(String filename) throws IOException;
  void loadGraph(String filename) throws IOException;
  void saveEmbeddings(String filePath) throws IOException;
  void loadEmbeddings(String filePath) throws IOException;
  void exportCytoscapeJson(String filePath) throws IOException;

  // Query operations
  List<Vertex> chunks();
  List<Vertex> entities();
  List<Vertex> chunksMentioning(Vertex entity);
  List<Vertex> entitiesMentionedIn(Vertex chunk);
}
