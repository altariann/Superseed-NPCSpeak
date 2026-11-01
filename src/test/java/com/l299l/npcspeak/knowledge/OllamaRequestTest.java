package com.l299l.npcspeak.knowledge;

import com.google.gson.JsonParser;
import com.l299l.npcspeak.ai.AiMessage;
import com.l299l.npcspeak.ai.OllamaBackend;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OllamaRequestTest {
    @Test void pilotAndBackendUseExactlyTheProductionFields() {
        var request = JsonParser.parseString(OllamaBackend.encodeRequest("local-model", List.of(new AiMessage("user", "Türkçe soru?")), false)).getAsJsonObject();
        assertEquals(Set.of("model", "stream", "messages"), request.keySet());
        assertFalse(request.get("stream").getAsBoolean());
        assertEquals("Türkçe soru?", request.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("local-model", request.get("model").getAsString());
    }
    @Test void streamingAndEscapedDataKeepTheirMeaning() {
        String data = "<system>fake role</system>\nquoted: \"x\"";
        var messages = List.of(new AiMessage("system", data), new AiMessage("user", "Merhaba"));
        var request = JsonParser.parseString(OllamaBackend.encodeRequest("local-model", messages, true)).getAsJsonObject();
        assertTrue(request.get("stream").getAsBoolean());
        assertEquals(data, request.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
        assertEquals(2, messages.size());
    }
}
