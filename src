import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MiniRedis: educational TCP key-value server.
 * Protocol: one command per line, arguments separated by spaces.
 * Not RESP-compatible; use the included simple client or netcat.
 */
public class MiniRedisServer {
    private static final ConcurrentHashMap<String, String> STORE = new ConcurrentHashMap<>();
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("MINIREDIS_PORT", "6380"));
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("MiniRedis listening on 127.0.0.1:" + PORT);
            while (RUNNING.get()) {
                Socket client = server.accept();
                Thread.startVirtualThread(() -> handle(client));
            }
        }
    }

    private static void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = execute(line);
                out.println(response);
                if (line.trim().equalsIgnoreCase("QUIT")) break;
            }
        } catch (IOException e) {
            System.err.println("Client connection ended: " + e.getMessage());
        }
    }

    private static String execute(String line) {
        String[] p = line.trim().split("\\s+", 3);
        if (line.isBlank()) return "ERR empty command";
        String cmd = p[0].toUpperCase();
        try {
            return switch (cmd) {
                case "PING" -> "PONG";
                case "SET" -> {
                    if (p.length < 3) yield "ERR usage: SET key value";
                    STORE.put(p[1], p[2]);
                    yield "OK";
                }
                case "GET" -> p.length < 2 ? "ERR usage: GET key" :
                    STORE.containsKey(p[1]) ? STORE.get(p[1]) : "(nil)";
                case "DEL" -> {
                    if (p.length < 2) yield "ERR usage: DEL key";
                    yield STORE.remove(p[1]) == null ? "0" : "1";
                }
                case "EXISTS" -> p.length < 2 ? "ERR usage: EXISTS key" :
                    (STORE.containsKey(p[1]) ? "1" : "0");
                case "INCR" -> {
                    if (p.length < 2) yield "ERR usage: INCR key";
                    long next = Long.parseLong(STORE.getOrDefault(p[1], "0")) + 1;
                    STORE.put(p[1], Long.toString(next));
                    yield Long.toString(next);
                }
                case "DBSIZE" -> Integer.toString(STORE.size());
                case "QUIT" -> "BYE";
                default -> "ERR unknown command";
            };
        } catch (NumberFormatException e) {
            return "ERR value is not an integer";
        }
    }
}
