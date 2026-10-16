package com.lifememory.app;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class SocialAddressTest {
  @Test public void remoteServersRequireHttpsOrigin() throws Exception {
    assertEquals("https://circle.example.com", SocialAddress.normalize(" https://circle.example.com/ "));
    for (String value : new String[] {"http://circle.example.com", "http://192.168.1.2:8787", "https://user:password@circle.example.com",
        "https://circle.example.com/path", "https://circle.example.com?token=secret", "https://circle.example.com#fragment"}) {
      try { SocialAddress.normalize(value); fail("Unsafe server accepted"); }
      catch (IOException expected) { assertFalse(expected.getMessage().contains("password")); }
    }
  }
  @Test public void localDevelopmentExceptionsAreExact() throws Exception {
    assertEquals("http://127.0.0.1:8787", SocialAddress.normalize("http://127.0.0.1:8787"));
    assertEquals("http://10.0.2.2:8787", SocialAddress.normalize("http://10.0.2.2:8787"));
    try { SocialAddress.normalize("http://localhost.example.com:8787"); fail(); }
    catch (IOException expected) { }
  }
}
