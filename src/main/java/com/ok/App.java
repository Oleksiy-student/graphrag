package com.ok;

import java.util.logging.Logger;
import java.util.logging.Level;
import com.ok.util.Config;
import com.ok.util.LoggerConfig;


public class App {
  private static final Logger LOGGER = Logger.getLogger(App.class.getName());

  public static void main(String[] args) {
    // Automatically configure logging for your package
    LoggerConfig.configurePackage("com.ok", Level.INFO);

    String apiKey = Config.get("API_KEY");
    String dbPassword = Config.get("DB_PASSWORD");
    LOGGER.fine(() -> "Config loaded.\n API Key length: " + apiKey.length() + "\nDB Password length: " + dbPassword.length());
  
    String query = args.length == 0 ? "What is GraphRAG?" : String.join(" ", args);

    // Workflow 1: full ingestion
    //Workflow1.run("21-012 - ENG.pdf", query);

    // Workflow 2: load existing graph & embeddings
    Workflow2.run(query);
  }

}