
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
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
  private LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
  private static final String CHECKMD5 = "9f15ae8af42d9cddd35a69f9958ce73d";
  private static final String allSicherungsVerzeichnis = "target/sicherungsverzeichnis/";
  
  private Properties mvnProperties;
  private Connection con = null;
  
  @Before
  public void before() throws IOException, ClassNotFoundException, SQLException
  {
    // Load Maven Properties written by the properties-maven-plugin to get DB infos
    java.io.InputStream is = this.getClass().getResourceAsStream("mvn.properties");
    java.util.Properties p = new Properties();
    p.load(is);

    // Load DB Connection 
    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    con = DriverManager.getConnection(
      p.getProperty("sql.url.test") + ";database=" + p.getProperty("sql.db.test"),
      p.getProperty("sql.user.test"),
      p.getProperty("sql.pw.test"));
  }
  
  @After
  public void after() throws SQLException
  {
    if (con != null)
    {
      con.close();
    }
  }
  
  @Test
  public void testSingle() throws InterruptedException, SQLException
  {
    Logger log = LogManager.getLogger("Testlogger");
    
    ThreadContext.put("schnittstelle", "SingleTest");
    ThreadContext.put("version", "S01");
    ThreadContext.put("laufid", "S00");
    
    log.info("testSingle" + CHECKMD5);
    // Wait for Log4j to ASYNC log the message to the DB
    // May Fail due high latency!
    Thread.sleep(1_000L);
    
    Statement s = con.createStatement();
    ResultSet rs = s.executeQuery("SELECT * FROM LAUFLOG WHERE TEXT = 'testSingle" + CHECKMD5 + "'");
    
    Assert.assertTrue(rs.next());
  }
  
  @Test
  public void testProgrThreadedLogs() throws InterruptedException, SQLException, IOException
  {
    logg.traceEntry();
    int runs = 5_000;
    int logs = 1;
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
    while (!executor.awaitTermination(100L, TimeUnit.MILLISECONDS))
    {
//      logg.info("Not Ready yet ");
    }

    // Check result
    Statement s = con.createStatement();
    ResultSet rs = s.executeQuery("SELECT * FROM LAUFLOG WHERE SCHNITTSTELLE = 'LISA4711'");
    
    int actualCount = 0;
    while (rs.next())
    {
      actualCount++;
    }

    // Number of Results = runs * logs * 2 for log.info & log.warn in Task.run()
    Assert.assertEquals(runs * logs * 2, actualCount);

    // Check the Logfiles created
    Assert.assertEquals(runs, new File(allSicherungsVerzeichnis).listFiles().length);
    Assert.assertEquals((long) runs * (logs + 1), Files.lines(Paths.get(allSicherungsVerzeichnis)).count());
    logg.traceExit();
  }
  
  private synchronized LoggerContext getLoggerContext(){
    return ctx;
  }
  
  public class Task implements Runnable
  {
    
    Level dbLevel = Level.ALL;
    Level fileLevel = Level.WARN;

    // Global Unique ID 
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
      Configuration config = getLoggerContext().getConfiguration();

      // Build a new File Appender
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

      // Build Appender Refs 
      AppenderRef refDB = AppenderRef.createAppenderRef("Lauflog", dbLevel, null); // Config Lauflog (DB) Loglevel here 
      AppenderRef refFile = AppenderRef.createAppenderRef(laufId + "_FILE", fileLevel, null); // Config FileAppender Loglevel here
      AppenderRef[] refs = new AppenderRef[]
      {
        refDB, refFile
      };

      // Build Logger
      LoggerConfig loggerConfig = LoggerConfig
        .createLogger(false, Level.ALL, laufId, "true", refs, null, config, null);
      loggerConfig.addAppender(fullAppender, fileLevel, null); // HIER LOGLEVEL Datei einstellen
      loggerConfig.addAppender(config.getAppender("Lauflog"), dbLevel, null); // HIER LOGLEVEL Datei einstellen
      config.addLogger(laufId, loggerConfig);
      
      getLoggerContext().updateLoggers();
      
      Logger log = LogManager.getLogger(laufId);
      
      ThreadContext.put("schnittstelle", "LISA4711");
      ThreadContext.put("version", "L01");
      ThreadContext.put("laufid", laufId);
      
      for (int j = 0; j < logs; j++)
      {
        log.info("Just a Info");
        log.warn("i warn you!");
      }

      // Clean Logger
      fullAppender.stop();
      try
      {
        while (((FileAppender) fullAppender).isStopping())
        {
          Thread.sleep(100L);
        }
      }
      catch (InterruptedException ex)
      {
        logg.error(ex.getMessage(), ex);
      }
      
      loggerConfig.stop();
      try
      {
        while (loggerConfig.isStopping())
        {
          Thread.sleep(100L);
        }
      }
      catch (InterruptedException ex)
      {
        logg.error(ex.getMessage(), ex);
      }
      
      getLoggerContext().getConfiguration().getLoggerConfig(laufId).removeAppender(laufId + "_FILE");
      getLoggerContext().getConfiguration().removeLogger(laufId);
      getLoggerContext().getConfiguration().getRootLogger().removeAppender(laufId + "_FILE");
      getLoggerContext().updateLoggers();
//      ctx.reconfigure();
      ThreadContext.clearAll();
    }
    
  }
  
}
