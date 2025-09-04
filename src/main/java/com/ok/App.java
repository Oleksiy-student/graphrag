package com.ok;

import java.util.logging.Level;
import com.ok.util.LoggerConfig;


public class App {

  public static void main(String[] args) {
    LoggerConfig.configurePackage("com.ok", Level.INFO);

    String query = args.length == 0 ? "What is GraphRAG?" : String.join(" ", args);

    // Workflow 1: full ingestion
    Workflow1.run("ch48.pdf", query);

    // Workflow 2: load existing graph & embeddings
    //Workflow2.run(query);
  }

}