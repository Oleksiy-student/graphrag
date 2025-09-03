package com.ok.store;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public interface GraphStore {
  Vertex addEntity(String name, String type);
  Vertex addEntity(String name, String type, String wikidataId);
  Vertex addChunk(String id, String text, float[] embedding);
  Edge addEdge(Vertex from, Vertex to, String label, Map<String,Object> props);
  
  void saveGraph(String filename) throws IOException;
  void loadGraph(String filename) throws IOException;
  void saveEmbeddings(String filePath) throws IOException;
  void loadEmbeddings(String filePath) throws IOException;

  List<Vertex> chunks();
  List<Vertex> entities();

  List<Vertex> chunksMentioning(Vertex entity);
  List<Vertex> entitiesMentionedIn(Vertex chunk);
}
