package fr.a21assistant;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal loopback-only diagnostic API.
 *
 * It deliberately exposes no device controls or personal data. A future tunnel
 * integration must target this loopback listener and retain bearer-token checks.
 */
public final class A21LocalApiServer {
    private final String token;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public A21LocalApiServer(String token) {
        if (token == null || token.length() < 32) {
            throw new IllegalArgumentException("A local API token of at least 32 characters is required.");
        }
        this.token = token;
    }

    public synchronized void start() throws Exception {
        if (running) return;
        serverSocket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    clients.execute(() -> handle(socket));
                } catch (Exception e) {
                    if (running) stop();
                }
            }
        }, "A21-local-api");
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) { }
        serverSocket = null;
    }

    public boolean isRunning() { return running; }

    public int getPort() {
        ServerSocket current = serverSocket;
        return current == null ? -1 : current.getLocalPort();
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.US_ASCII))) {
            client.setSoTimeout(5000);
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.length() > 2048) {
                write(client, 400, "{\"error\":\"bad_request\"}");
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                write(client, 400, "{\"error\":\"bad_request\"}");
                return;
            }
            String authorization = null;
            String line;
            int headerBytes = requestLine.length();
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                headerBytes += line.length();
                if (headerBytes > 8192) {
                    write(client, 431, "{\"error\":\"headers_too_large\"}");
                    return;
                }
                int colon = line.indexOf(':');
                if (colon > 0 && "authorization".equalsIgnoreCase(line.substring(0, colon).trim())) {
                    authorization = line.substring(colon + 1).trim();
                }
            }
            if (!constantTimeEquals("Bearer " + token, authorization)) {
                write(client, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if ("GET".equals(parts[0]) && "/health".equals(parts[1])) {
                write(client, 200, "{\"service\":\"a21-local-api\",\"status\":\"ready\"}");
            } else if ("GET".equals(parts[0]) && "/capabilities".equals(parts[1])) {
                write(client, 200, "{\"capabilities\":[\"health\",\"capabilities\"]}");
            } else {
                write(client, 404, "{\"error\":\"not_found\"}");
            }
        } catch (Exception ignored) {
            // A malformed or disconnected client must not stop the listener.
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Socket socket, int status, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized"
                : status == 404 ? "Not Found" : status == 431 ? "Request Header Fields Too Large"
                : "Bad Request";
        OutputStream out = socket.getOutputStream();
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }
}
