package com.ok;

import java.util.logging.Level;
import com.ok.util.LoggerConfig;

/**
 * Main application class demonstrating the Strategy pattern for workflow selection.
 * Provides two processing strategies: full ingestion vs. loading existing data.
 */
public class App {

  public static void main(String[] args) {
    LoggerConfig.configurePackage("com.ok", Level.INFO);

    String query = args.length == 0 ? "What is GraphRAG?" : String.join(" ", args);

    // Full ingestion workflow: process PDF, extract entities, build graph
    Workflow1.run("ch48.pdf", query);

    // Alternative: query existing graph and embeddings
    //Workflow2.run(query);
  }

}