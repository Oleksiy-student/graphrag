package com.ok.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ok.embeddings.EmbeddingModel;
import java.sql.*;
import java.util.*;

public class SupabaseVectorStore {
  private final Connection conn;
  private final ObjectMapper mapper = new ObjectMapper();

  public SupabaseVectorStore(String url, String user, String password) throws SQLException {
    this.conn = DriverManager.getConnection(url, user, password);
  }

  public void saveChunk(String docId, String text, Map<String, Object> metadata, float[] embedding) throws SQLException {
    // Convert float[] to Postgres array string for pgvector
    String vectorLiteral = arrayToPgVector(embedding);

    String sql = "INSERT INTO document_chunks (doc_id, chunk_text, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, docId);
      ps.setString(2, text);
      ps.setString(3, mapper.writeValueAsString(metadata));
      ps.setString(4, vectorLiteral);
      ps.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public List<Map<String, Object>> query(String query, EmbeddingModel model, int k) throws SQLException {
    float[] qEmb = model.embed(query);
    String vectorLiteral = arrayToPgVector(qEmb);

    String sql = """
      SELECT id, doc_id, chunk_text, metadata,
              1 - (embedding <=> ?::vector) AS score
      FROM document_chunks
      ORDER BY embedding <=> ?::vector
      LIMIT ?
    """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, vectorLiteral);
      ps.setString(2, vectorLiteral);
      ps.setInt(3, k);

      ResultSet rs = ps.executeQuery();
      List<Map<String, Object>> results = new ArrayList<>();

      while (rs.next()) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("doc_id", rs.getString("doc_id"));
        row.put("chunk_text", rs.getString("chunk_text"));
        row.put("metadata", rs.getString("metadata"));
        row.put("score", rs.getDouble("score"));
        results.add(row);
      }
      return results;
    }
  }

  private String arrayToPgVector(float[] arr) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < arr.length; i++) {
      sb.append(arr[i]);
      if (i < arr.length - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }
}
