package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.net.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ApiConfigTest {
  @Test
  public void baseAndHeaderAreNormalizedWithoutLeakingKeys() throws Exception {
    assertEquals(
        "https://api.deepseek.com",
        Ai.normalizeBase(" https://api.deepseek.com/chat/completions/ "));
    assertEquals("https://api.deepseek.com/v1", Ai.normalizeBase("https://api.deepseek.com/v1/"));
    assertEquals("test-only", Ai.normalizeKey(" Bearer test-only "));
    for (String bad : new String[] {"", "abc def", "abc\nx\ny"}) {
      try {
        Ai.normalizeKey(bad);
        fail("Invalid key accepted");
      } catch (IOException expected) {
      }
    }
    for (String bad :
        new String[] {
          "http://api.deepseek.com",
          "https://user:pass@api.deepseek.com",
          "https://api.deepseek.com?key=x"
        }) {
      try {
        Ai.normalizeBase(bad);
        fail("Invalid URL accepted");
      } catch (IOException expected) {
        assertFalse(expected.getMessage().contains("user:pass"));
      }
    }
  }

  @Test
  public void responseDistinguishesAuthenticationAndBalanceAndDisconnects() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    class Reply extends HttpURLConnection {
      int status;
      boolean closed = false;

      Reply(int code) throws Exception {
        super(new URL("https://api.deepseek.com/models"));
        status = code;
      }

      public int getResponseCode() {
        return status;
      }

      public void disconnect() {
        closed = true;
      }

      public boolean usingProxy() {
        return false;
      }

      public void connect() {}
    }
    for (int code : new int[] {401, 402, 403, 404, 429, 503}) {
      Reply reply = new Reply(code);
      try {
        new Ai(c).response(reply);
        fail("Expected failure");
      } catch (ApiFailure error) {
        assertEquals(code, error.status);
        assertEquals(code == 429 || code == 503, error.retryable());
        assertTrue(error.getMessage().contains("api.deepseek.com/models"));
      }
      assertTrue(reply.closed);
    }
  }

  @Test
  public void interruptedJobRecoversWithoutDeletingTranscriptOrAudioPath() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (Store db = new Store(c)) {
      long id =
          db.add(
              System.currentTimeMillis(), "audio", "saved transcript", "test-path", "processing");
      try {
        db.metadata(id, new org.json.JSONObject().put("segments", new org.json.JSONArray()));
        SpeechJobs.recoverInterrupted(c);
        assertEquals("ready", db.find(id).status);
        assertEquals("saved transcript", db.find(id).text);
        assertEquals("test-path", db.find(id).path);
      } finally {
        db.delete(db.find(id));
      }
    }
  }
}
