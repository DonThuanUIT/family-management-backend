package com.familyfund.core.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SupabaseStorageAdapterTest {
    HttpClient client;
    StorageProperties properties;
    SupabaseStorageAdapter adapter;
    String key="CLAN001/MEM001/7f69ddc3-781b-46cb-90a5-abc453b78f03.jpg";
    @BeforeEach void setup() {
        client=mock(HttpClient.class); properties=new StorageProperties();
        properties.setSupabaseUrl("https://storage.example.invalid");properties.setSupabaseServiceKey("unit-test-key");
        adapter=new SupabaseStorageAdapter(properties,client);
    }
    @SuppressWarnings("unchecked")
    private <T> HttpResponse<T> response(int status,T body) {
        HttpResponse<T> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);return response;
    }
    @Test void uploadsOnlyToPrivateRestEndpointWithServerCredentials() throws Exception {
        doReturn(response(200,null)).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        adapter.put("member-media",key,new byte[]{1},"image/jpeg");
        var request=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertEquals("POST",request.getValue().method());assertEquals("/storage/v1/object/member-media/"+key,request.getValue().uri().getPath());
        assertEquals("false",request.getValue().headers().firstValue("x-upsert").orElseThrow());
        assertEquals("Bearer unit-test-key",request.getValue().headers().firstValue("Authorization").orElseThrow());
    }
    @Test void returnsBytesAndHidesProviderErrorPayload() throws Exception {
        doReturn(response(200,new byte[]{1,2})).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        try(var input=adapter.open("member-media",key)) { assertArrayEquals(new byte[]{1,2},input.readAllBytes()); }
        doReturn(response(500,"sensitive provider text".getBytes(StandardCharsets.UTF_8))).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var error=assertThrows(MediaException.class,()->adapter.open("member-media",key));
        assertEquals(503,error.getStatus());assertFalse(error.getMessage().contains("sensitive"));
    }
    @Test void acceptsLegacyMissingObjectResponseAndConfirmsDelete() throws Exception {
        byte[] missing="{\"statusCode\":\"404\",\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8);
        doReturn(response(400,missing)).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertFalse(adapter.exists("member-media",key));assertDoesNotThrow(()->adapter.delete("member-media",key));
        assertEquals(404,assertThrows(MediaException.class,()->adapter.open("member-media",key)).getStatus());
    }
    @Test void deleteDoesNotSucceedWhenObjectStillExists() throws Exception {
        doReturn(response(200,new byte[0])).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertEquals(503,assertThrows(MediaException.class,()->adapter.delete("member-media",key)).getStatus());
    }
    @Test void rejectsUnsafeConfigurationAndRedirects() throws Exception {
        properties.setSupabaseUrl("http://example.invalid");
        assertThrows(MediaException.class,()->adapter.open("member-media",key));verifyNoInteractions(client);
        properties.setSupabaseUrl("https://storage.example.invalid");
        doReturn(response(302,new byte[0])).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertEquals(503,assertThrows(MediaException.class,()->adapter.open("member-media",key)).getStatus());
    }
    @Test void sendsNewSecretKeyAsApiKeyWithoutTreatingItAsJwt() throws Exception {
        properties.setSupabaseServiceKey("sb_secret_unit_test");
        doReturn(response(200,null)).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        adapter.put("member-media",key,new byte[]{1},"image/jpeg");
        var request=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertEquals("sb_secret_unit_test",request.getValue().headers().firstValue("apikey").orElseThrow());
        assertTrue(request.getValue().headers().firstValue("Authorization").isEmpty());
    }
    @Test void rejectsPublishableKeyBeforeSendingPrivateStorageRequest() {
        properties.setSupabaseServiceKey("sb_publishable_unit_test");
        assertEquals(503,assertThrows(MediaException.class,()->adapter.open("member-media",key)).getStatus());
        verifyNoInteractions(client);
    }
}
