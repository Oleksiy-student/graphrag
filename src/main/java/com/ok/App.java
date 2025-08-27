package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class App {
  public static void main(String[] args) {
    // Handle multi-word queries from command line
    String query;
    if (args.length == 0) {
      // Default query if none provided
      query = "What is GraphRAG?";
    } else {
      StringBuilder sb = new StringBuilder();
      for (String a : args) {
        sb.append(a).append(" ");
      }
    query = sb.toString().trim();
}

    // Sample document corpus
    String doc = "GraphRAG augments retrieval with a knowledge graph. " +
             "It is designed to improve the quality of answers by linking entities and their relationships. " +
             "Entities like OpenAI, Neo4j, and TinkerPop are extracted and linked automatically. " +
             "Neo4j is a popular graph database used to store connected data efficiently. " +
             "OpenAI provides state-of-the-art language models capable of understanding context and generating text. " +
             "TinkerPop is a graph computing framework that allows querying and traversing graphs easily. " +
             "GraphRAG retrieves relevant chunks from documents and summarizes them based on entity relationships. " +
             "This approach allows answering complex questions that require connecting multiple pieces of information. " +
             "By combining chunk-level embeddings and graph structure, GraphRAG can surface the most relevant evidence. " +
             "The retrieved evidence is then synthesized into a concise draft answer suitable for downstream tasks.";

    PdfExtractor pdfExtractor = new PdfExtractor();
    String pdfPath = "data/21-012 - ENG.pdf";
    String docText = "";
    try {
      docText = pdfExtractor.extract(new File(pdfPath));
    } catch (IOException e) {
      System.err.println("Failed to extract PDF: " + e.getMessage());
      e.printStackTrace();
    }

    // Chunk documents
    DocumentChunker chunker = new DocumentChunker(1000);
    //List<String> chunks = chunker.chunk(doc);
    List<String> chunks = chunker.chunk(docText);
    System.out.println("Total chunks: " + chunks.size());

    // Embedding model
    //TfIdfEmbeddingModel model = new TfIdfEmbeddingModel();
    //model.fit(chunks);
    EmbeddingModel model = new Qwen3EmbeddingModel();

    // Build the graph
    GraphStore store = new TinkerGraphStore();
    EntityExtractor ner = new EntityExtractor();
    GraphBuilder builder = new GraphBuilder(store, model);
    builder.ingest("doc", chunks, ner);

    // Save graph and embeddings
    try {
      store.saveGraph("graph.xml");
      store.saveEmbeddings("embeddings.json");
    } catch (IOException e) {
      System.err.println("Failed to save graph/embeddings: " + e.getMessage());
      e.printStackTrace();
    }

    // Retrieve relevant chunks
    Retriever retriever = new Retriever(store, model);
    List<Retriever.Hit> hits = retriever.retrieve(query, 5, 2);
    if (hits.isEmpty()) {
      System.out.println("No hits found. Check embeddings and chunk content!");
    } else {
      System.out.println("Retrieved evidence:");
      for (Retriever.Hit h : hits) {
        System.out.println(h.chunkId + " -> score=" + h.score);
        System.out.println(h.text);
      }
    }

    // Compose the answer
    AnswerComposer composer = new AnswerComposer();
    String answer = composer.compose(query, hits, 1800);

    // Print output
    System.out.println("\n=== DRAFT ANSWER ===\n" + answer);
  }
}