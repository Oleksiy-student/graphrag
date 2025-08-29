package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.tinkerpop.gremlin.structure.Vertex;

public class Workflow1 {
  private static final Logger LOGGER = Logger.getLogger(Workflow1.class.getName());

  public static void run(String pdfPath, String query) {
    try {
      // Extract PDF text
      PdfExtractor pdfExtractor = new PdfExtractor();
      String docText = pdfExtractor.extract(new File(pdfPath));

      // Chunk document
      DocumentChunker chunker = new DocumentChunker(500); // 500 tokens per chunk
      List<String> chunks = chunker.chunk(docText);
      LOGGER.fine(() -> "Total chunks: " + chunks.size());

      // Initialize embedding model and graph store
      EmbeddingModel model = new Qwen3EmbeddingModel();
      GraphStore store = new TinkerGraphStore();
      GraphBuilder builder = new GraphBuilder(store, model);
      EntityExtractor ner = new EntityExtractor();

      // Ingest chunks
      builder.ingest("doc", chunks, ner);

      LOGGER.fine("--- Chunk embeddings ---");
      for (Vertex c : store.chunks()) {
        String cid = c.property("id").isPresent() ? c.property("id").value().toString() : c.id().toString();
        if (c.property("embedding").isPresent()) {
          float[] emb = (float[]) c.property("embedding").value();
          double norm = 0;
          for (float v : emb) norm += v * v;
          double finalNorm = Math.sqrt(norm);
          int length = emb.length;
          LOGGER.fine(() -> String.format("Chunk %s: length=%d, norm=%.4f", cid, length, finalNorm));
        } else {
          LOGGER.fine(() -> "Chunk " + cid + ": no embedding!");
        }
      }
      LOGGER.fine("--- End of embeddings ---");

      // Save graph and embeddings
      store.saveGraph("graph.xml");
      store.saveEmbeddings("embeddings.json");

      // Retrieve
      Retriever retriever = new Retriever(store, model);
      List<Retriever.Hit> hits = retriever.retrieve(query, 5, 2);

      // Compose answer
      AnswerComposer composer = new AnswerComposer();
      String answer = composer.compose(query, hits, 1200);

      LOGGER.info(answer);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
