package org.infinispan.wfink.playground.security.ldap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.testng.annotations.Test;

public class AuthorizationTest {
  private final String host = "localhost";
  private final int port = 11222;
  private static final String CACHE_NAME = "futurama";

  private RemoteCacheManager remoteCacheManager = null;

  private RemoteCacheManager startCacheManager(String username, String password) {
    Configuration configuration = new ConfigurationBuilder().connectionPool().security().authentication() // .enable()
        .username(username).password(password.toCharArray()).saslMechanism("PLAIN").addServer().host(host).port(port).maxRetries(0).build();
    this.remoteCacheManager = new RemoteCacheManager(configuration);
    return this.remoteCacheManager;
  }

  private RemoteCache<String, String> getCache() {
    return remoteCacheManager.getCache(CACHE_NAME);
  }

  private void stopCacheManager() {
    if (this.remoteCacheManager != null) {
      this.remoteCacheManager.stop();
      this.remoteCacheManager = null;
    }
  }

  @Test
  public void createEntryLackingPermission() {
    startCacheManager("fry", "fry");
    RemoteCache<String, String> cache = getCache();

    try {
      cache.put("1", "fail1");
      fail("Unexpected to succeed put(..)");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Unauthorized access"), "Error message does not contain 'Unauthorized access' -> " + e.getMessage());
    }
  }

  @Test
  public void createEntry() {
    startCacheManager("professor", "professor");
    RemoteCache<String, String> cache = getCache();
    try {
      cache.put("1", "test1");
    } catch (Exception e) {
      fail("Unexpected failure for put() message is -> " + e.getMessage());
    }
  }

  @Test
  public void readEntry() {
    startCacheManager("professor", "professor");
    getCache().put("readtest", "readtest");
    stopCacheManager();

    startCacheManager("fry", "fry");
    String v = getCache().get("readtest");
    assertEquals(v, "readtest");
    stopCacheManager();
  }

}
