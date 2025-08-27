package com.ok.store;

import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;

public class TinkerGraphStore implements GraphStore {
  private final TinkerGraph graph;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public TinkerGraphStore() {
      this.graph = TinkerGraph.open();
  }

  @Override
  public Vertex addEntity(String name, String type) {
    return graph.addVertex(T.label, "entity", "name", name, "type", type);
  }

  @Override
  public Vertex addChunk(String id, String text, float[] embedding) {
    Vertex v = graph.addVertex(T.label, "chunk", T.id, id, "text", text);
    if (embedding != null && embedding.length > 0) {
        v.property("embedding", embedding);
    }
    return v;
  }

  @Override
  public Edge addEdge(Vertex from, Vertex to, String label, Map<String, Object> props) {
    Edge e = from.addEdge(label, to);
    if (props != null) {
      for (Map.Entry<String, Object> entry : props.entrySet()) {
        e.property(entry.getKey(), entry.getValue());
      }
    }
    return e;
  }

  @Override
  public List<Vertex> chunks() {
    List<Vertex> out = new ArrayList<>();
    Iterator<Vertex> it = graph.vertices();
    while (it.hasNext()) {
      Vertex v = it.next();
      if ("chunk".equals(v.label())) out.add(v);
    }
    return out;
  }

  @Override
  public List<Vertex> entities() {
    List<Vertex> out = new ArrayList<>();
    Iterator<Vertex> it = graph.vertices();
    while (it.hasNext()) {
      Vertex v = it.next();
      if ("entity".equals(v.label())) out.add(v);
    }
    return out;
  }

  @Override
  public List<Vertex> chunksMentioning(Vertex entity) {
    List<Vertex> out = new ArrayList<>();
    Iterator<Edge> it = entity.edges(Direction.IN, "MENTIONS");
    while (it.hasNext()) {
      out.add(it.next().outVertex());
    }
    return out;
  }

  @Override
  public List<Vertex> entitiesMentionedIn(Vertex chunk) {
    List<Vertex> out = new ArrayList<>();
    Iterator<Edge> it = chunk.edges(Direction.OUT, "MENTIONS");
    while (it.hasNext()) {
      out.add(it.next().inVertex());
    }
    return out;
  }

  @Override
  public void saveGraph(String filename) throws IOException {
    File dir = new File("data");
    if (!dir.exists()) dir.mkdirs();
    try (var out = new FileOutputStream(new File(dir, filename))) {
      org.apache.tinkerpop.gremlin.structure.io.graphml.GraphMLWriter.build().create().writeGraph(out, graph);
    }
  }

  @Override
  public void loadGraph(String filename) throws IOException {
    File file = new File("data", filename);
    if (!file.exists()) return;
    try (var in = new FileInputStream(file)) {
      org.apache.tinkerpop.gremlin.structure.io.graphml.GraphMLReader.build().create().readGraph(in, graph);
    }
  }

  @Override
  public void saveEmbeddings(String filename) throws IOException {
    File f = new File("data", filename);

    try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
      bw.write("{\n");
      boolean firstVertex = true;
      Iterator<Vertex> it = graph.vertices();
      while (it.hasNext()) {
        Vertex v = it.next();
        if (!v.property("embedding").isPresent()) continue;
        float[] emb = (float[]) v.property("embedding").value();
        if (emb == null || emb.length == 0) continue;

        if (!firstVertex) bw.write(",\n");
        firstVertex = false;

        bw.write("  \"" + v.id().toString() + "\": [");
        for (int i = 0; i < emb.length; i++) {
          if (i > 0) bw.write(",");
          bw.write(Float.toString(emb[i]));
        }
        bw.write("]");
      }
      bw.write("\n}\n");
    }
  }

  @Override
  public void loadEmbeddings(String filename) throws IOException {
    File f = new File("data", filename);
    if (!f.exists()) return;

    Map<String, List<Double>> data = MAPPER.readValue(
            f,
            new TypeReference<Map<String, List<Double>>>() {}
    );

    for (Map.Entry<String, List<Double>> e : data.entrySet()) {
      String vid = e.getKey();
      List<Double> vals = e.getValue();
      if (vals == null || vals.isEmpty()) continue;

      float[] emb = new float[vals.size()];
      for (int i = 0; i < vals.size(); i++) emb[i] = vals.get(i).floatValue();

      Iterator<Vertex> it = graph.vertices(vid);
      if (it.hasNext()) {
        Vertex v = it.next();
        v.property("embedding", emb);
      }
    }
  }

  public TinkerGraph getGraph() {
    return graph;
  }
}
