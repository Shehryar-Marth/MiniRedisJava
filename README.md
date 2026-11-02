 #MiniRedisServer
A small in-memory key-value server in Java that you talk to over TCP, in the spirit of
Redis. It is a single file with no dependencies, built to explore concurrent network
servers.
It is **not** Redis and does not speak the Redis protocol (RESP), so `redis-cli` and
Redis client libraries will not work with it.
## Requirements
- Java 21 or newer (uses virtual threads)
Tested on OpenJDK 21.
## Quick start
```bash
# run directly from source
java MiniRedisServer.java
# or compile first
javac MiniRedisServer.java
java MiniRedisServer
```
The server prints `MiniRedis listening on 127.0.0.1:6380`. In another terminal:
```bash
nc 127.0.0.1 6380
```
### Configuration
| Setting | How | Default |
|---|---|---|
| Port | `MINIREDIS_PORT` environment variable (1-65535) | `6380` |
```bash
MINIREDIS
_PORT=7000 java MiniRedisServer.java
```
An invalid port value prints an error and exits.
## Protocol
Plain text, one command per line, arguments separated by spaces. Commands are
case-insensitive. Each command gets exactly one line in response. Keys cannot
contain spaces; values can.
| Command | Response |
|---|---|
| `PING` | `PONG` |
| `SET key value` | `OK` |
| `GET key` | The value, or `(nil)` if the key does not exist |
| `DEL key` | `1` if deleted, `0` if the key did not exist |
| `EXISTS key` | `1` or `0` |
| `INCR key` | The new value after adding 1. A missing key counts as `0`. |
| `DECR key` | The new value after subtracting 1. A missing key counts as `0`. |
| `DBSIZE` | Number of keys stored |
| `QUIT` | `BYE`, then the server closes the connection |
### Errors
Errors start with `ERR`:
| Response | Cause |
|---|---|
| `ERR empty command` | Blank line |
| `ERR unknown command` | Unrecognized command name |
| `ERR usage: SET key value` | Missing arguments (similar messages for other
commands) |
| `ERR value is not an integer or out of range` | `INCR`/`DECR` on a non-numeric
value, or on a result that would overflow a 64-bit integer |
### Example session
```
$ nc 127.0.0.1 6380
PING
PONG
SET greeting hello world
OK
GET greeting
hello world
GET missing
(nil)
INCR visits
1
INCR visits
2
DECR visits
1
SET name abc
OK
INCR name
ERR value is not an integer or out of range
DBSIZE
3
QUIT
BYE
```
### Using it from code
Any plain TCP client works. For example, in Python:
```python
import socket
s = socket.create
_connection(("127.0.0.1", 6380))
f = s.makefile("rw", newline="\n", encoding="utf-8")
def cmd(line):
f.write(line + "\n")
f.flush()
return f.readline().rstrip("\n")
print(cmd("SET a 1")) # OK
print(cmd("INCR a")) # 2
print(cmd("GET a")) # 2
cmd("QUIT")
```
## How it works
- **One virtual thread per client.** Each connection is handled by a lightweight virtual
thread, so many idle clients are cheap. All clients share one `ConcurrentHashMap`.
- **Atomic counters.** `INCR` and `DECR` use `ConcurrentHashMap.compute()`, so
two clients incrementing the same key at once cannot lose an update.
`Math.addExact` turns overflow into an error instead of silently wrapping around.
- **Single-lookup reads.** `GET` does one `get()` and checks for null, rather than
`containsKey` followed by `get`, which could race with a concurrent `DEL`.
- **UTF-8 everywhere,** and every response ends in `\n` regardless of operating
system.
### Safety limits
| Limit | Value | Why |
|---|---|---|
| Bind address | `127.0.0.1` only | Not reachable from other machines, since there is
no authentication |
| Maximum line length | 64 KB | One client cannot exhaust memory with an endless
line |
| Maximum clients | 1,000 | Extra connections get `ERR too many clients` and are
closed |
| Idle timeout | 5 minutes | Abandoned connections are dropped |
A shutdown hook closes the server socket cleanly on SIGTERM or Ctrl+C.
## Limitations
- **In-memory only.** All data is lost when the server stops.
- **Strings only.** No lists, hashes, sets, or other data types.
- **No expiry** (`EXPIRE`/`TTL`), no pub/sub, no transactions.
- **No authentication or TLS.** This is why it binds to loopback only. Do not expose it
to a network.
- **Not RESP-compatible.**
- **No automated test suite yet.** It has been checked by hand and with a scripted
client against a running server.
## Ideas for extending
- Persist to disk with an append-only log or atomic snapshots.
- Add `EXPIRE`, `TTL`, and `KEYS`.
- Implement RESP so that real Redis clients can connect.
- Add JUnit tests. `execute(String)` is a plain method, so command handling can be
tested without opening sockets.