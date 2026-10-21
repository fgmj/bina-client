package br.com.bomgas.bina;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ServerApiTest {
    @Test public void durableQueueKeepsHttpFailuresAndUsesTheSameEnvelopeOnRecovery() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger status = new java.util.concurrent.atomic.AtomicInteger(503);
        java.util.List<String> received = new java.util.ArrayList<>();
        server.createContext("/api/eventos", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Location", "/wrong-destination");
            exchange.sendResponseHeaders(status.get(), -1);
        });
        server.start();
        java.io.File directory = java.nio.file.Files.createTempDirectory("outbox-http").toFile();
        java.io.File file = new java.io.File(directory, "calls");
        try {
            EnvironmentConfig destination = EnvironmentConfig.local("127.0.0.1", String.valueOf(server.getAddress().getPort()));
            DurableCalls calls = new DurableCalls(file);
            calls.record("RINGING", 1, "", "line", 1000, 100, "boot", destination, "device", "{}");
            ServerApi api = new ServerApi();
            calls.deliver(1000, api::sendQueuedEvent);
            assertEquals(1, calls.pending().size());
            assertTrue(calls.pending().get(0).error.contains("503"));
            calls = new DurableCalls(file);
            status.set(302);
            calls.deliver(16000, api::sendQueuedEvent);
            assertEquals(1, calls.pending().size());
            assertTrue(calls.pending().get(0).error.contains("302"));
            status.set(200);
            calls.deliver(46000, api::sendQueuedEvent);
            assertTrue(new DurableCalls(file).pending().isEmpty());
            assertEquals(3, received.size());
            assertEquals(received.get(0), received.get(1));
            assertEquals(received.get(0), received.get(2));
            calls.deliver(100000, (url, json) -> fail("Evento entregue não pode ser reenviado"));
        } finally {
            server.stop(0);
            java.nio.file.Files.deleteIfExists(file.toPath());
            java.nio.file.Files.deleteIfExists(directory.toPath());
        }
    }

    // Small HTTP/1.1 stub using only APIs present on Android's test compile classpath.
    private static final class HttpServer {
        private final java.net.ServerSocket listener = new java.net.ServerSocket();
        private final java.util.Map<String, Handler> handlers = new java.util.HashMap<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private Thread worker;

        private HttpServer(InetSocketAddress address) throws java.io.IOException {
            try {
                listener.bind(address);
            } catch (java.io.IOException error) {
                listener.close();
                throw error;
            }
        }

        static HttpServer create(InetSocketAddress address, int backlog) throws java.io.IOException {
            return new HttpServer(address);
        }

        InetSocketAddress getAddress() {
            return (InetSocketAddress) listener.getLocalSocketAddress();
        }

        void createContext(String path, Handler handler) { handlers.put(path, handler); }

        void start() {
            worker = new Thread(() -> {
                while (!listener.isClosed()) {
                    try (java.net.Socket socket = listener.accept()) {
                        socket.setSoTimeout(5000);
                        java.io.InputStream input = new java.io.BufferedInputStream(socket.getInputStream());
                        String[] request = readLine(input).split(" ");
                        int length = 0;
                        String header;
                        while (!(header = readLine(input)).isEmpty()) {
                            int colon = header.indexOf(':');
                            if (colon > 0 && header.substring(0, colon).equalsIgnoreCase("Content-Length")) {
                                length = Integer.parseInt(header.substring(colon + 1).trim());
                            }
                        }
                        byte[] body = new byte[length];
                        new java.io.DataInputStream(input).readFully(body);
                        Exchange exchange = new Exchange(request[0], body);
                        Handler handler = handlers.get(request[1]);
                        if (handler == null) exchange.sendResponseHeaders(404, -1);
                        else handler.handle(exchange);
                        byte[] responseBody = exchange.response.toByteArray();
                        StringBuilder headers = new StringBuilder("HTTP/1.1 ")
                                .append(exchange.status).append(" Stub\r\nConnection: close\r\nContent-Length: ")
                                .append(responseBody.length).append("\r\n");
                        exchange.headers.forEach((name, value) -> headers.append(name).append(": ").append(value).append("\r\n"));
                        java.io.OutputStream output = socket.getOutputStream();
                        output.write(headers.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));
                        output.write(responseBody);
                        output.flush();
                    } catch (Throwable error) {
                        if (!(error instanceof java.net.SocketException && listener.isClosed())) failure.set(error);
                        break;
                    }
                }
            }, "server-api-test-stub");
            worker.setDaemon(true);
            worker.start();
        }

        void stop(int delay) throws Exception {
            listener.close();
            worker.join(6000);
            assertFalse("HTTP stub did not stop", worker.isAlive());
            if (failure.get() != null) throw new AssertionError("HTTP stub failed", failure.get());
        }

        private static String readLine(java.io.InputStream input) throws java.io.IOException {
            java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1) {
                if (value == '\n') return line.toString("US-ASCII").replace("\r", "");
                line.write(value);
            }
            throw new java.io.EOFException("Incomplete HTTP headers");
        }

        private interface Handler { void handle(Exchange exchange) throws java.io.IOException; }

        private static final class Headers extends java.util.LinkedHashMap<String, String> {
            void set(String name, String value) { put(name, value); }
        }

        private static final class Exchange {
            final String method;
            final java.io.ByteArrayInputStream request;
            final java.io.ByteArrayOutputStream response = new java.io.ByteArrayOutputStream();
            final Headers headers = new Headers();
            int status = 200;

            Exchange(String method, byte[] body) {
                this.method = method;
                request = new java.io.ByteArrayInputStream(body);
            }

            String getRequestMethod() { return method; }
            java.io.InputStream getRequestBody() { return request; }
            java.io.OutputStream getResponseBody() { return response; }
            Headers getResponseHeaders() { return headers; }
            void sendResponseHeaders(int status, long length) { this.status = status; }
            void close() { }
        }
    }

    @Test public void stubCannotBindAnOccupiedPort() throws Exception {
        try (java.net.ServerSocket occupied = new java.net.ServerSocket()) {
            occupied.bind(new InetSocketAddress("127.0.0.1", 0));
            assertThrows(java.net.BindException.class, () ->
                    HttpServer.create(new InetSocketAddress("127.0.0.1", occupied.getLocalPort()), 0));
        }
    }

    @Test public void eventsUseOnlyTheirCapturedEnvironmentAndKeepTheLegacyEnvelope() throws Exception {
        AtomicReference<String> firstBody = new AtomicReference<>();
        AtomicReference<String> secondBody = new AtomicReference<>();
        HttpServer first = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HttpServer second = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        first.createContext("/api/eventos", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            firstBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        second.createContext("/api/eventos", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            secondBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        first.start(); second.start();
        try {
            EnvironmentConfig selected = EnvironmentConfig.local("127.0.0.1", String.valueOf(first.getAddress().getPort()));
            String envelope = "{\"deviceId\":\"stable-id\",\"sourceId\":\"stable-id\",\"sourceType\":\"android\",\"eventType\":\"CALL_RECEIVED\",\"additionalData\":\"{\\\"numero\\\":\\\"1130047717\\\"}\"}";
            java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(2);
            AtomicReference<Exception> failure = new AtomicReference<>();
            okhttp3.Callback callback = new okhttp3.Callback() {
                public void onFailure(okhttp3.Call call, java.io.IOException error) { failure.set(error); completed.countDown(); }
                public void onResponse(okhttp3.Call call, okhttp3.Response response) { response.close(); completed.countDown(); }
            };
            ServerApi api = new ServerApi();
            api.sendEvent(selected, envelope, callback);
            selected = EnvironmentConfig.local("127.0.0.1", String.valueOf(second.getAddress().getPort()));
            api.sendEvent(selected, "{\"eventType\":\"CALL_MISSED\"}", callback);
            assertTrue(completed.await(15, java.util.concurrent.TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(envelope, firstBody.get());
            assertEquals("{\"eventType\":\"CALL_MISSED\"}", secondBody.get());
        } finally { first.stop(0); second.stop(0); }
    }
    @Test public void fractionalRevisionCannotBecomeAnEditableRegistration() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/origens", exchange -> {
            byte[] body = "{\"sourceType\":\"android\",\"sourceId\":\"stable-id\",\"name\":\"Recepção\",\"revision\":1.5}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            EnvironmentConfig destination = EnvironmentConfig.local("127.0.0.1", String.valueOf(server.getAddress().getPort()));
            assertThrows(java.io.IOException.class, () -> new ServerApi().register(destination, "stable-id", "Nome", null));
        } finally { server.stop(0); }
    }
    @Test public void nameMustBeNonemptyAndFitTheServerLimit() {
        assertEquals("Recepção", ServerApi.validateName("  Recepção  "));
        assertEquals(255, ServerApi.validateName("a".repeat(255)).length());
        for (String name : new String[]{"", "   ", "a".repeat(256), "Recepção\nFundos"}) {
            assertThrows(IllegalArgumentException.class, () -> ServerApi.validateName(name));
        }
    }

    @Test public void connectionTestUsesHttpWithoutCreatingAnEventOrFollowingRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger events = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger status = new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/api/eventos", exchange -> { events.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.createContext("/api/origens", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "/api/eventos");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            EnvironmentConfig destination = EnvironmentConfig.local("127.0.0.1", String.valueOf(server.getAddress().getPort()));
            ServerApi api = new ServerApi();
            api.testConnection(destination);
            status.set(503);
            assertThrows(java.io.IOException.class, () -> api.testConnection(destination));
            status.set(302);
            assertThrows(java.io.IOException.class, () -> api.testConnection(destination));
            assertEquals(0, events.get());
        } finally { server.stop(0); }
    }

    @Test public void unavailableLocalServerReportsAnIoFailure() throws Exception {
        int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        EnvironmentConfig destination = EnvironmentConfig.local("127.0.0.1", String.valueOf(port));
        assertThrows(java.io.IOException.class, () -> new ServerApi().testConnection(destination));
    }
    @Test public void staleRenameAdoptsTheServerNameWithoutRetrying() throws Exception {
        AtomicReference<JsonObject> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/origens", exchange -> {
            received.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            byte[] body = "{\"sourceType\":\"android\",\"sourceId\":\"stable-id\",\"name\":\"Nome do servidor\",\"revision\":3}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ServerApi api = new ServerApi();
            EnvironmentConfig destination = EnvironmentConfig.local("127.0.0.1", String.valueOf(server.getAddress().getPort()));
            ServerApi.Registration result = api.register(destination, "stable-id", "Nome antigo", 1L);
            assertEquals("Nome do servidor", result.name);
            assertEquals(3, result.revision);
            assertEquals(1, received.get().get("revision").getAsLong());
            api.register(destination, "stable-id", result.name, null);
            assertFalse(received.get().has("revision"));
            assertEquals("stable-id", received.get().get("sourceId").getAsString());
        } finally { server.stop(0); }
    }
}
