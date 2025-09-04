package com.ok.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerConfig {

  /**
   * Configures all loggers under the given package to the specified level,
   * including console handlers, so FINE messages are visible.
   *
   * @param packageName the package to configure (e.g., "com.ok")
   * @param level     logging level (Level.FINE, Level.INFO, etc.)
   */
  public static void configurePackage(String packageName, Level level) {
    Logger logger = Logger.getLogger(packageName);
    logger.setLevel(level);

    // Configure root logger handlers to show messages
    Logger rootLogger = Logger.getLogger("");
    rootLogger.setLevel(level);
    for (Handler handler : rootLogger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        handler.setLevel(level);
      }
    }
  }
}
