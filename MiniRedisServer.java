import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
/**
* MiniRedis: educational TCP key-value server (Java 21+, uses
virtual threads).
* Protocol: one command per line, arguments separated by
spaces.
* Not RESP-compatible; use netcat or any plain TCP client.
*
* Commands: PING | SET key value | GET key | DEL key | EXISTS
key
* INCR key | DECR key | DBSIZE | QUIT
*/
public class MiniRedisServer {
private static final int MAX_LINE = 64 * 1024; //
reject absurdly long lines
private static final int MAX_CLIENTS = 1000;
private static final int IDLE_TIMEOUT_MS = 5 * 60 * 1000;
private final ConcurrentHashMap<String, String> store = new
ConcurrentHashMap<>();
private final AtomicInteger clients = new AtomicInteger();
private volatile ServerSocket server;
/** Blocks, accepting connections until stop() is called.
Binds to loopback only. */
public void serve(int port) throws IOException {
try (ServerSocket ss = new ServerSocket(port, 50,
InetAddress.getLoopbackAddress())) {
server = ss;
System.out.println("MiniRedis listening on
127.0.0.1:" + port);
while (!ss.isClosed()) {
Socket client;
try {
client = ss.accept();
} catch (SocketException e) {
break; // server closed by stop()
}
if (clients.incrementAndGet() > MAX_CLIENTS) {
clients.decrementAndGet();
}
reject(client);
continue;
Thread.startVirtualThread(() ->
handle(client));
}
}
}
public void stop() {
try {
ServerSocket ss = server;
if (ss != null) ss.close();
} catch (IOException ignored) { }
}
private static void reject(Socket socket) {
try (socket) {
socket.getOutputStream().write("ERR too many
clients\n".getBytes(StandardCharsets.UTF_8));
} catch (IOException ignored) { }
}
private void handle(Socket socket) {
try (socket;
Reader in = new BufferedReader(new
InputStreamReader(socket.getInputStream(),
StandardCharsets.UTF_8));
Writer out = new BufferedWriter(new
OutputStreamWriter(socket.getOutputStream(),
StandardCharsets.UTF_8))) {
socket.setSoTimeout(IDLE_TIMEOUT_MS);
String line;
while ((line = readLine(in)) != null) {
out.write(execute(line));
out.write('\n'); // explicit
\n: same on every OS
out.flush();
if (line.trim().equalsIgnoreCase("QUIT"))
break;
}
} catch (SocketTimeoutException e) {
// idle client: just drop it
} catch (IOException e) {
System.err.println("Client connection ended: " +
e.getMessage());
} finally {
clients.decrementAndGet();
}
}
/** Like BufferedReader.readLine but with a length cap, so
one client can't exhaust memory. */
private static String readLine(Reader in) throws
IOException {
StringBuilder sb = new StringBuilder();
int c;
while ((c = in.read()) != -1) {
if (c == '\n') return sb.toString();
if (sb.length() >= MAX_LINE) throw new
IOException("line too long");
sb.append((char) c);
}
return sb.length() == 0 ? null : sb.toString();
}
String execute(String line) {
if (line.isBlank()) return "ERR empty command";
String[] p = line.trim().split("\\s+", 3);
String cmd = p[0].toUpperCase();
try {
return switch (cmd) {
case "PING" -> "PONG";
case "SET" -> {
if (p.length < 3) yield "ERR usage: SET key
value";
store.put(p[1], p[2]);
yield "OK";
}
case "GET" -> {
if (p.length < 2) yield "ERR usage: GET
key";
String v = store.get(p[1]); lookup: no containsKey/get race
yield v == null ? "(nil)" : v;
// single
}
case "DEL" -> {
if (p.length < 2) yield "ERR usage: DEL
key";
yield store.remove(p[1]) == null ? "0" :
"1";
}
case "EXISTS" -> p.length < 2 ? "ERR usage:
EXISTS key"
: (store.containsKey(p[1]) ? "1" :
"0");
case "INCR" -> p.length < 2 ? "ERR usage: INCR
key" : addTo(p[1], 1);
case "DECR" -> p.length < 2 ? "ERR usage: DECR
key" : addTo(p[1], -1);
case "DBSIZE" ->
Integer.toString(store.size());
case "QUIT" -> "BYE";
default -> "ERR unknown command";
};
} catch (NumberFormatException | ArithmeticException e)
return "ERR value is not an integer or out of
{
range";
}
}
/** Atomic read-modify-write (the old getOrDefault + put
could lose concurrent increments). */
private String addTo(String key, long delta) {
return store.compute(key, (k, cur) -> {
long n = cur == null ? 0 : Long.parseLong(cur);
return Long.toString(Math.addExact(n, delta)); //
throws on overflow instead of wrapping
});
}
private static int parsePort(String s) {
if (s == null) return 6380;
try {
int port = Integer.parseInt(s.trim());
if (port < 1 || port > 65535) throw new
NumberFormatException();
return port;
} catch (NumberFormatException e) {
System.err.println("Invalid MINIREDIS_PORT '" + s +
"' (expected 1-65535)");
System.exit(1);
return -1;
}
}
public static void main(String[] args) throws IOException {
MiniRedisServer s = new MiniRedisServer();
Runtime.getRuntime().addShutdownHook(new
Thread(s::stop));
s.serve(parsePort(System.getenv("MINIREDIS_PORT")));
}
}