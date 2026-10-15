package com.lifememory.app;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class ApiSettingsTest {
  @Test public void newlyTypedKeyTakesPrecedenceOverOldProvider() throws Exception {
    ApiSettings.checkKeyReuse("api.openai.com", "https://api.deepseek.com", true, "test-only-key");
    assertEquals("test-only-key", ApiSettings.key(" Bearer test-only-key "));
  }
  @Test public void sameProviderAndVersionPathCanKeepSavedKey() throws Exception {
    ApiSettings.checkKeyReuse("api.deepseek.com", "https://API.DEEPSEEK.COM:443/v1", true, "");
    assertEquals("api.deepseek.com", ApiSettings.provider("https://api.deepseek.com/models"));
  }
  @Test public void differentProviderOrPortRequiresNewKey() throws Exception {
    for (String base : new String[] {"https://api.openai.com/v1", "https://api.deepseek.com:8443"}) {
      try { ApiSettings.checkKeyReuse("api.deepseek.com", base, true, ""); fail(); }
      catch (IOException expected) { }
    }
  }
  @Test public void missingOrMalformedKeyFailsWithoutLeakingIt() throws Exception {
    try { ApiSettings.checkKeyReuse("api.deepseek.com", "https://api.deepseek.com", false, ""); fail(); }
    catch (IOException expected) { }
    try { ApiSettings.key("test secret with spaces"); fail(); }
    catch (IOException expected) { assertFalse(expected.getMessage().contains("test secret")); }
  }
  @Test public void unsafeUrlsAreRejectedWithoutLeakingCredentials() throws Exception {
    for (String base : new String[] {"http://api.deepseek.com", "https://user:password@api.deepseek.com", "https://api.deepseek.com?token=x"}) {
      try { ApiSettings.base(base); fail(); }
      catch (IOException expected) { assertFalse(expected.getMessage().contains("password")); }
    }
  }
}
