package org.infinispan.wfink.playground.security.ldap;

import java.io.IOException;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

public class HotRodLDAPClient {
  private static final Logger log = Logger.getLogger(HotRodLDAPClient.class.getName());
  private static final String CACHE_NAME = "futurama";
  private final String username;
  private final RemoteCacheManager remoteCacheManager;
  private final RemoteCache<String, String> remoteCache;

  public HotRodLDAPClient(String host, String port, String username, char[] passwd) throws IOException {
    setLogging(2229, true, null);

    this.username = username;

    Configuration configuration = new ConfigurationBuilder().connectionPool().security().authentication().username(username).password(passwd).saslMechanism("PLAIN").addServer().host(host).port(Integer.parseInt(port)).maxRetries(0).build();
    remoteCacheManager = new RemoteCacheManager(configuration);
    remoteCache = remoteCacheManager.getCache(CACHE_NAME);
  }

  private static final Level[] LEVELS = new Level[] { Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST, Level.ALL, Level.ALL, Level.ALL };

  /**
   * Initialize standard java util logging. For parameters see <a href= "https://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html#syntax">Formatter</a>
   *
   * @param logLevel
   * @param consoleLog
   * @param logFile
   * @throws IOException
   */
  private static void setLogging(int logLevel, boolean consoleLog, String logFile) throws IOException {
    SimpleFormatter sf = new SimpleFormatter() {
      @Override
      public synchronized String format(LogRecord lr) {
        return String.format("%1$tH:%1$tM:%1$tS.%1$tL %2$-4s [%3$s] (%5$s) %4$s%n", new Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getSourceClassName(), lr.getMessage(), lr.getThreadID());
      }
    };

    Logger root = Logger.getLogger("");

    if (consoleLog) {
      root.getHandlers()[0].setFormatter(sf);
      if (logFile == null) // set console logging to show ALL
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    } else {
      root.removeHandler(root.getHandlers()[0]);
    }
    if (logFile != null) {
      FileHandler fh = new FileHandler(logFile);
      fh.setLevel(Level.ALL);
      fh.setFormatter(sf);
      root.addHandler(fh);
    }

    // xxxx? example classes
    Logger.getLogger("org.infinispan.wfink").setLevel(LEVELS[logLevel % 10]);
    // xxx?x client messages
    Logger.getLogger("org.infinispan.client").setLevel(LEVELS[(logLevel % 100) / 10]);
    // xx?xx Infinispan classes
    Logger.getLogger("org.infinispan").setLevel(LEVELS[(logLevel % 1000) / 100]);
    // x?xxx JGroups
    Logger.getLogger("org.jgroups").setLevel(LEVELS[(logLevel % 10000) / 1000]);
  }

  private void put(String key, String value) {
    try {
      log.info("Inserting data into cache: " + key + " " + value);
      remoteCache.put(key, value);
    } catch (Exception s) {
      log.warning(s.getMessage());
      log.info("User '" + username + "' is not allowed to write entries!");
    }
  }

  private void getAndVerify(String key, String expectedValue) {
    log.info("Verifying data...");

    log.info("verify key " + key);
    try {
      String value = remoteCache.get(key);
      if (value == null) {
        log.info(" No value found!");
      } else if (!value.equals(expectedValue)) {
        log.info(" Value '" + value + "' differ from '" + expectedValue + "'");
      } else {
        log.info(" ok");
      }
    } catch (Exception s) {
      log.warning(s.getMessage());
      log.info("User '" + username + "' is not allowed to read entries!");
    }
  }

  private void stop() {
    remoteCacheManager.stop();
  }

  public static void main(String[] args) throws Exception {
    String host = "127.0.0.1";
    String port = "11222";
    String username = "professor";
    char[] password = "professor".toCharArray();

    if (args.length > 0) {
      username = args[0];
    }
    if (args.length > 1) {
      password = args[1].toCharArray();
    }
    if (args.length > 2) {
      port = args[2];
    }
    if (args.length > 3) {
      port = args[3];
    }
    HotRodLDAPClient client = new HotRodLDAPClient(host, port, username, password);

    client.put("test1", "value1");
    client.getAndVerify("test1", "value1");

    client.stop();
    client.log.info("\nDone !");
  }

}
