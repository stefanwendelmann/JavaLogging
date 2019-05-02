

import java.io.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.layout.*;
import org.junit.*;

/**
 *
 * @author swendelmann
 */
public class LoggerTest
{

  private static final org.apache.logging.log4j.Logger logg = LogManager.getLogger();
  private static final LoggerContext ctx = (LoggerContext) LogManager.getContext();

  @Test
  public void testProfiling() throws InterruptedException
  {
    logg.traceEntry();
    int runs = 1_000;
    int logs = 2;
    int threadPoolSize = 8;
    logg.debug("Start Test mit " + runs + " durchläufen & " + logs + " logs & " + threadPoolSize + " ThreadPool");
    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);

    for (int i = 0; i < runs; i++)
    {
      String laufid = "LD0" + i;
      Task t = new Task(laufid, logs);
      executor.execute(t);
    }
    executor.shutdown();
    while (!executor.awaitTermination(100L, TimeUnit.MINUTES))
    {
//      logg.info("Not Ready yet ");
    }
    logg.traceExit();
  }

  public class Task implements Runnable
  {

    String allSicherungsVerzeichnis = "target/sicherungsverzeichnis/";

    public String laufId;
    public int logs;

    public Task(String laufId, int logs)
    {
      this.laufId = laufId;
      this.logs = logs;
    }

    @Override
    public void run()
    {
      Configuration config = ctx.getConfiguration();

      //File Appender aufbauen <laufid>_file
      File laufLogFile = new File(allSicherungsVerzeichnis + laufId + "_log4j.log");

      Layout layout = PatternLayout.newBuilder()
        .withConfiguration(config)
        .withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN)
        .withCharset(Charset.forName("UTF-8"))
        .build();

      Appender fullAppender = FileAppender.newBuilder()
        .setConfiguration(config)
        .setName(laufId + "_FILE")
        .withFileName(laufLogFile.getAbsolutePath())
        .withAppend(true)
        .withImmediateFlush(true)
        .setIgnoreExceptions(false)
        .setLayout(layout)
        .build();

      fullAppender.start();
      config.addAppender(fullAppender);

      // Logger Referenzen aufbauen
      AppenderRef refDB = AppenderRef.createAppenderRef("Lauflog", Level.ALL, null); // HIER LOGLEVEL DB EINSTELLEN
      AppenderRef refFile = AppenderRef.createAppenderRef(laufId + "_FILE", Level.ALL, null); // HIER LOGLEVEL Datei einstellen
      AppenderRef[] refs = new AppenderRef[]
      {
        refDB, refFile
      };

      // Logger Aufbauen
      LoggerConfig loggerConfig = LoggerConfig
        .createLogger(false, Level.ALL, laufId, "true", refs, null, config, null);
      loggerConfig.addAppender(fullAppender, Level.ALL, null); // HIER LOGLEVEL Datei einstellen
      loggerConfig.addAppender(config.getAppender("Lauflog"), Level.ALL, null); // HIER LOGLEVEL Datei einstellen
      config.addLogger(laufId, loggerConfig);

      ctx.updateLoggers();

      org.apache.logging.log4j.Logger log = LogManager.getLogger(laufId);

      ThreadContext.put("schnittstelle", "LISA4711");
      ThreadContext.put("version", "L01");
      ThreadContext.put("laufid", laufId);
      ThreadContext.put("user", "SPADMIN");

      for (int j = 0; j < logs; j++)
      {
        log.info("Ich bin nur eine Info und soll nur in das FullFile logging!");
        log.warn("Ich bin ein böser warning und soll in das FullFile und in die DB");
      }

      // Logger säubern
      fullAppender.stop();
      loggerConfig.stop();
      ctx.updateLoggers();
      ThreadContext.clearAll();
    }

  }

}
