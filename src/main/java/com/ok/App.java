package com.ok;
import java.util.logging.Logger;
import java.util.logging.Level;


public class App {
    public static void main(String[] args) {
      Logger.getLogger("com.ok").setLevel(Level.FINE);
      String query = args.length == 0 ? "What is GraphRAG?" : String.join(" ", args);

      // Workflow 1: full ingestion
      //Workflow1.run("data/21-012 - ENG.pdf", query);

      // Workflow 2: load existing graph & embeddings
      Workflow2.run(query);
    }
}
