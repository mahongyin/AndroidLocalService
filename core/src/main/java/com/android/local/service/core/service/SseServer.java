package com.android.local.service.core.service;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.nanohttpd.protocols.http.ClientHandler;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;
import org.nanohttpd.protocols.http.threading.IAsyncRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE（Server-Sent Events）服务，基于 NanoHTTPD 实现。
 * <p>
 * 用法：
 * <pre>
 *     SseServer server = new SseServer(9090);          // 默认路径 /sse
 *     server.setHeartbeatInterval(15000)
 *           .setMaxThreads(32)
 *           .setSseListener(listener);
 *     server.start();                                  // 或 start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
 *
 *     server.send(clientId, "chat", "{\"msg\":\"hi\"}");// event: chat / data: {"msg":"hi"}
 *     server.broadcast("hello");                       // data: hello
 *     server.broadcastDone();                          // data: [DONE]  （OpenAI 风格结束标记）
 * </pre>
 * <p>
 * 实现要点（也是容易踩坑的地方）：
 * <ol>
 *   <li>SSE 是长连接，一个连接会长期占用一个线程，所以用有上限的线程池替代 NanoHTTPD 默认的"一请求一线程"；</li>
 *   <li>必须关闭 gzip：NanoHTTPD 默认对 text/* 打开 gzip，响应体会被 GZIPOutputStream 缓冲，
 *       客户端收不到实时数据（这里显式 setUseGzip(false)）；</li>
 *   <li>响应体用阻塞队列驱动的 InputStream：write() 只入队不阻塞业务线程，read() 由连接线程消费，
 *       且永远不会返回 0（NanoHTTPD 把 read()&lt;=0 当作流结束）；</li>
 *   <li>心跳用 SSE 注释行（": ping"），既能保活，又能作为判断连接是否还活着的信号；</li>
 *   <li>客户端断开时 NanoHTTPD 写 Socket 会抛异常并 close 掉 InputStream，
 *       回调里统一做清理，避免 clients 泄漏。</li>
 * </ol>
 */
public class SseServer extends NanoHTTPD {
    /**
     * OpenAI 风格的结束标记，对应报文 {@code data: [DONE]}
     */
    public static final String DONE = "[DONE]";
    /**
     * SSE 标准 MIME，必须带 charset，否则部分客户端会把中文按 ISO-8859-1 解码
     */
    public static final String CONTENT_TYPE = "text/event-stream; charset=utf-8";
    private static final String TAG = "SseServer";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long DEFAULT_HEARTBEAT_MS = 0L;//15000
    private static final long DEFAULT_RETRY_MS = 0L;//3000
    private static final int DEFAULT_QUEUE_CAPACITY = 512;
    private static final int DEFAULT_MAX_THREADS = 64;
    private static final long READ_POLL_TIMEOUT_MS = 1000L;
    private static final long MIN_STALE_TIMEOUT_MS = 30000L;
    private static final String CORS_MAX_AGE = String.valueOf(42 * 60 * 60);

    /**
     * 所有活跃客户端，key 为客户端 ID
     */
    private final Map<String, SseClient> clients = new ConcurrentHashMap<>();
    private final AtomicInteger threadIndex = new AtomicInteger(1);
    private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_MS;
    private long retryMs = DEFAULT_RETRY_MS;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    /**
     * 0 表示不限制
     */
    private int maxClients = 0;
    private String allowOrigin = "*";
    private Map<String, SseListener> listeners;

    /**
     * 共享调度器：心跳 + 僵死连接清理，避免每个客户端起一个线程
     */
    private ScheduledExecutorService scheduler;
    private volatile boolean stopped = false;

    public SseServer(int port) {
        super(port);
        listeners = new ConcurrentHashMap<>();
        setAsyncRunner(new PooledAsyncRunner(DEFAULT_MAX_THREADS));
    }

    public SseServer(String hostname, int port) {
        super(hostname, port);
        listeners = new ConcurrentHashMap<>();
        setAsyncRunner(new PooledAsyncRunner(DEFAULT_MAX_THREADS));
    }

    // ------------------------------------------------------------------ 配置

    /**
     * 心跳间隔，单位毫秒。心跳是 SSE 注释行（": ping"），不会被浏览器派发成消息事件。
     * &lt;=0 表示关闭心跳（关闭后无法主动发现僵死连接，只能靠 Socket 异常回收）。
     */
    public SseServer setHeartbeatInterval(long millis) {
        this.heartbeatIntervalMs = millis;
        return this;
    }

    /**
     * 断线重连间隔，单位毫秒。连接建立时会下发 {@code retry: xxx}。&lt;=0 表示不下发。
     */
    public SseServer setRetry(long millis) {
        this.retryMs = millis;
        return this;
    }

    /**
     * 单个客户端待发送事件的队列上限，超出即判定连接已僵死并关闭，防止内存无限增长。
     */
    public SseServer setQueueCapacity(int capacity) {
        this.queueCapacity = Math.max(16, capacity);
        return this;
    }

    /**
     * 最大并发客户端数，0 表示不限制
     */
    public SseServer setMaxClients(int maxClients) {
        this.maxClients = Math.max(0, maxClients);
        return this;
    }

    /**
     * 跨域允许的 Origin，默认 "*"
     */
    public SseServer setAllowOrigin(String allowOrigin) {
        this.allowOrigin = allowOrigin == null ? "*" : allowOrigin;
        return this;
    }

    /**
     * 请求处理线程池上限，必须在 start() 之前调用。
     * SSE 是长连接，每个连接会一直占用一个线程，请根据实际并发量设置。
     */
    public SseServer setMaxThreads(int maxThreads) {
        setAsyncRunner(new PooledAsyncRunner(Math.max(4, maxThreads)));
        return this;
    }

    public SseServer addSseListener(String clientId, SseListener listener) {
        this.listeners.put(clientId, listener);
        return this;
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    public void start() throws IOException {
        prepare();
        super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    @Override
    public void start(int timeout) throws IOException {
        prepare();
        super.start(timeout, false);
    }

    @Override
    public void start(int timeout, boolean daemon) throws IOException {
        prepare();
        super.start(timeout, daemon);
    }

    @Override
    public void stop() {
        stopped = true;
        // 先结束所有流，让连接线程自然退出，再关监听
        closeAll();
        super.stop();
        shutdownScheduler();
        if(listeners != null) {
            listeners.clear();
        }
    }
    public void stop(String clientId) {
        SseClient client = clients.get(clientId);
        if (client != null) {
            client.close();
        }
        if(listeners != null) {
            listeners.remove(clientId);
        }
    }

    private synchronized void prepare() {
        stopped = false;
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "sse-maintenance-" + threadIndex.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });
            long interval = heartbeatIntervalMs > 0 ? heartbeatIntervalMs : DEFAULT_HEARTBEAT_MS;
            if (interval > 0) {
                scheduler.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        maintenance();
                    }
                }, interval, interval, TimeUnit.MILLISECONDS);
            }
        }
    }

    private synchronized void shutdownScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * 心跳 + 僵死连接回收
     */
    private void maintenance() {
        try {
            long now = System.currentTimeMillis();
            long staleTimeout = Math.max(heartbeatIntervalMs * 3, MIN_STALE_TIMEOUT_MS);
            for (SseClient client : new ArrayList<SseClient>(clients.values())) {
                if (heartbeatIntervalMs > 0) {
                    client.write(": ping " + now + "\n\n");
                }
                // 心跳会被持续消费，长时间没有任何读取说明连接线程已经没了（对端已断开）
                if (heartbeatIntervalMs > 0 && now - client.lastReadTime() > staleTimeout) {
                    Log.w(TAG, "SSE 客户端 " + client.getId() + " 超过 " + staleTimeout + "ms 未消费数据，关闭");
                    client.close();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "SSE 维护任务异常：" + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ 请求处理
    private String tmpClientId;

    public Response serve(IHTTPSession session, String clientId, SseListener sseListener) {
        addSseListener(clientId, sseListener);
        tmpClientId = clientId;
        return this.serve(session);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (!isSsePath(session.getUri())) {
            return notFound();
        }

        Method method = session.getMethod();
        if (Method.OPTIONS.equals(method)) {
            return corsPreflight();
        }
        if (!Method.GET.equals(method)) {
            Response response = Response.newFixedLengthResponse(
                    Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Only GET is supported");
            response.closeConnection(true);
            return withCors(response);
        }
        if (stopped) {
            Response response = Response.newFixedLengthResponse(
                    Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Server stopped");
            response.closeConnection(true);
            return withCors(response);
        }
        return openStream(session);
    }

    private Response openStream(IHTTPSession session) {
        if (maxClients > 0 && clients.size() >= maxClients) {
            Response response = Response.newFixedLengthResponse(
                    Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Too many SSE clients");
            response.closeConnection(true);
            return withCors(response);
        }

        String clientId = tmpClientId;
        if (TextUtils.isEmpty(tmpClientId)) {
            clientId = session.getCookies().read("Event-CLIENT-ID");
            if (TextUtils.isEmpty(clientId)) {
                clientId = UUID.randomUUID().toString();
            }
        }

        SseClient client = new SseClient(clientId);
        clients.put(clientId, client);

        if (retryMs > 0) {
            // 首帧：先下发重连间隔和一条注释，让浏览器立刻收到首字节并确认连接已建立
            StringBuilder first = new StringBuilder();
            first.append("retry: ").append(retryMs).append("\n\n");
            first.append(": open ").append(clientId).append("\n\n");
            client.write(first.toString());
        }

        Log.d(TAG, "SSE 客户端已连接：" + clientId + "  当前连接数：" + clients.size());

        if (listeners.get(clientId) != null) {
            try {
                listeners.get(clientId).onOpen(this, clientId, session);
            } catch (Throwable t) {
                Log.w(TAG, "SseListener.onOpen 异常：" + t.getMessage());
            }
        }

        Response response = Response.newChunkedResponse(Status.OK, CONTENT_TYPE, client.getInputStream());
        response.setChunkedTransfer(true);
        // 关键：SSE 绝不能压缩，否则数据会被 GZIPOutputStream 缓冲住，客户端收不到实时推送
        response.setUseGzip(false);
        response.setKeepAlive(true);
        response.addHeader("Cache-Control", "no-cache, no-store, no-transform, must-revalidate");
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Connection", "keep-alive");
        // 经 nginx 等反代时禁用响应缓冲
        response.addHeader("X-Accel-Buffering", "no");
// Set-Cookie 参数解释
// Expires / Max-Age ：控制Cookie生命周期。不设置则会话Cookie，关浏览器即清空；设置了则持久化到硬盘，过期前一直有效。
// Expires=Wed, 30 Dec 2026 23:59:59 GMT;  或 Max-Age=604800 秒=7天;  Expires=Wed, 30 Dec 2026 23:59:59 UGC+8000 即GMT+8;
// Domain / Path ：限定作用范围。Domain控制哪些域名可用，Path限制只发送到特定路径，避免不同系统Cookie互相干扰。
// Secure ：仅HTTPS协议下发送，防中间人攻击。
// HttpOnly ：禁止JavaScript通过document.cookie访问，防XSS跨站脚本窃取。
// SameSite ：限制跨站请求发送，可设为Strict、Lax或None，防CSRF跨站请求伪造。  
        response.addHeader("Set-Cookie", "Event-CLIENT-ID=" + clientId + "; HttpOnly; Max-Age=604800");
        return withCors(response);
    }

    private Response corsPreflight() {
        Response response = Response.newFixedLengthResponse(Status.NO_CONTENT, MIME_PLAINTEXT, "");
        response.setKeepAlive(true);
        return withCors(response);
    }

    private Response notFound() {
        Response response = Response.newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
        response.closeConnection(true);
        return withCors(response);
    }

    private Response withCors(Response response) {
        if (response == null) {
            return null;
        }
        response.addHeader("Access-Control-Allow-Origin", allowOrigin);
        response.addHeader("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Requested-With, Cookie, Cache-Control");
        response.addHeader("Access-Control-Max-Age", CORS_MAX_AGE);
        // 通配符 Origin 下带 credentials 会被浏览器直接拒绝，所以只在指定了具体 Origin 时才下发
        if (!"*".equals(allowOrigin)) {
            response.addHeader("Access-Control-Allow-Credentials", "true");
        }
        return response;
    }

    // ------------------------------------------------------------------ 发送消息

    /**
     * 向指定客户端发送默认消息（只有 data 字段）
     */
    public boolean send(String clientId, String data) {
        return send(clientId, null, data);
    }

    /**
     * 向指定客户端发送带事件名的消息
     *
     * @param event 事件名，为 null 时浏览器走默认的 message 事件
     * @param clientId    事件 ID，会写入 {@code id:} 字段，客户端重连时通过 Event-CLIENT-ID 带回
     * @return 是否投递成功（false 表示客户端已断开或不存在）
     */
    public boolean send(String clientId, String event, String data) {
        if (clientId == null) {
            return false;
        }
        SseClient client = clients.get(clientId);
        return client != null && client.write(frame(event, data, clientId));
    }
    private static String getSseMsg(String clientId, String word) {
        if (word.startsWith("{") || word.startsWith("[") || word.equals(DONE)){
            return word;
        }
        return "{\"code\":0,\"message\":\"Success\",\"sid\":\""+clientId+"\",\"id\":\""+clientId+"\",\"created\":"+(System.currentTimeMillis() / 1000)+",\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\""+word+"\"},\"index\":0}]}";
    }
    public int broadcast(String data) {
        return broadcast(null, data, tmpClientId);
    }

    public int broadcast(String event, String data) {
        return broadcast(event, data, tmpClientId);
    }

    /**
     * 广播给所有客户端，返回成功投递的数量
     */
    public int broadcast(String event, String data, String id) {
        String payload = frame(event, data, id);
        int count = 0;
        for (SseClient client : clients.values()) {
            if (client.write(payload)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 发送注释行，浏览器会忽略，可用于自定义心跳
     */
    public boolean sendComment(String clientId, String comment) {
        if (clientId == null) {
            return false;
        }
        SseClient client = clients.get(clientId);
        return client != null && client.write(commentFrame(comment));
    }

    public int broadcastComment(String comment) {
        String payload = commentFrame(comment);
        int count = 0;
        for (SseClient client : clients.values()) {
            if (client.write(payload)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 动态调整客户端的重连间隔
     */
    public boolean sendRetry(String clientId, long millis) {
        if (clientId == null) {
            return false;
        }
        SseClient client = clients.get(clientId);
        return client != null && client.write("retry: " + millis + "\n\n");
    }

    // ------------------------------------------------------------------ 结束 / 关闭

    /**
     * 发送结束标记 {@code data: [DONE]}（OpenAI 流式接口约定），连接保持。
     * 前端收到后即可自行 close EventSource。
     */
    public boolean sendDone(String clientId) {
        return sendDone(clientId, null);
    }

    /**
     * 发送带事件名的结束标记，如 {@code event: done / data: [DONE]}
     */
    public boolean sendDone(String clientId, String event) {
        if (clientId == null) {
            return false;
        }
        SseClient client = clients.get(clientId);
        return client != null && client.write(frame(event, DONE, null));
    }

    /**
     * 向所有客户端广播结束标记
     */
    public int broadcastDone() {
        return broadcastDone(null);
    }

    public int broadcastDone(String event) {
        String payload = frame(event, DONE, null);
        int count = 0;
        for (SseClient client : clients.values()) {
            if (client.write(payload)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 优雅结束指定客户端：先发 {@code data: [DONE]}，再关闭连接。
     * [DONE] 会先入队，close 只是让读取端在队列排空后结束，数据不会被丢掉。
     */
    public boolean complete(String clientId) {
        return complete(clientId, null);
    }

    public boolean complete(String clientId, String event) {
        if (clientId == null) {
            return false;
        }
        SseClient client = clients.get(clientId);
        if (client == null) {
            return false;
        }
        client.write(frame(event, DONE, null));
        client.close();
        return true;
    }

    /**
     * 结束所有客户端：广播 [DONE] 后关闭全部连接
     */
    public int broadcastComplete() {
        return broadcastComplete(null);
    }

    public int broadcastComplete(String event) {
        int count = broadcastDone(event);
        closeAll();
        return count;
    }

    /**
     * 立即关闭指定客户端（丢弃未发送的数据）
     */
    public boolean close(String clientId) {
        if (clientId == null) {
            return false;
        }
        SseClient client = clients.get(clientId);
        if (client == null) {
            return false;
        }
        client.close();
        return true;
    }

    /**
     * 关闭所有客户端连接
     */
    public void closeAll() {
        for (SseClient client : new ArrayList<SseClient>(clients.values())) {
            client.close();
        }
        clients.clear();
    }

    // ------------------------------------------------------------------ 状态查询

    public int getClientCount() {
        return clients.size();
    }

    public Set<String> getClientIds() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    public boolean isConnected(String clientId) {
        return clientId != null && clients.containsKey(clientId);
    }

    // ------------------------------------------------------------------ 报文组装

    /**
     * 组装一个 SSE 事件帧，以空行结束。
     * data 中的换行会被拆成多个 {@code data:} 行，符合 SSE 规范。
     */
    private static String frame(String event, String data, String id) {
        StringBuilder sb = new StringBuilder(64);
        if (id != null && id.length() > 0) {
            sb.append("id: ").append(field(id)).append('\n');
        }
        if (event != null && event.length() > 0) {
            sb.append("event: ").append(field(event)).append('\n');
        }
        for (String line : splitLines(data)) {
            sb.append("data: ").append(getSseMsg(id, line)).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String commentFrame(String comment) {
        StringBuilder sb = new StringBuilder(32);
        sb.append(": ");
        if (comment != null) {
            for (String line : splitLines(comment)) {
                sb.append(line).append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * SSE 字段值不允许出现 CR/LF
     */
    private static String field(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String[] splitLines(String text) {
        if (text == null || text.length() == 0) {
            return new String[]{""};
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    }

    private boolean isSsePath(String uri) {
        if (uri == null) {
            return false;
        }
        String path = uri;
        int idx = path.indexOf('?');
        if (idx >= 0) {
            path = path.substring(0, idx);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty() || "/".equals(path)) {
            return true;
        }
        return !TextUtils.isEmpty(path);
    }

    /**
     * NanoHTTPD 的 header 取值（忽略大小写）
     */
    public static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String value = headers.get(name);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
    /**
     * 从 Cookie 头中获取指定名称的 Cookie 值
     * @param cookieHeader Cookie 头值，格式为 "Event-CLIENT-ID=123456; Event-CLIENT-ID=789012"
     * @param name Cookie 名称
     */
    public static String getCookieValue(String cookieHeader, String name) {
        if (cookieHeader == null || name == null) {
            return null;
        }
        String[] cookies = cookieHeader.split("; ");
        for (String cookie : cookies) {
            String[] parts = cookie.split("=");
            if (parts.length == 2 && parts[0].equals(name)) {
                return parts[1];
            }
        }
        return null;
    }
    // ------------------------------------------------------------------ 客户端

    /**
     * 一个 SSE 连接对应一个客户端，写操作只是往队列里放数据，不会阻塞业务线程。
     */
    public final class SseClient {

        private final String id;
        private final BlockingQueue<byte[]> queue;
        private final SseInputStream inputStream;
        private volatile boolean closed = false;

        SseClient(String id) {
            this.id = id;
            this.queue = new LinkedBlockingQueue<byte[]>(Math.max(16, queueCapacity));
            this.inputStream = new SseInputStream(queue);
            this.inputStream.setOnClose(new Runnable() {
                @Override
                public void run() {
                    // 连接线程异常退出时（对端断开）NanoHTTPD 会 close 掉 InputStream，这里统一清理
                    SseClient.this.close();
                }
            });
        }

        String getId() {
            return id;
        }

        InputStream getInputStream() {
            return inputStream;
        }

        long lastReadTime() {
            return inputStream.lastReadTime();
        }

        boolean write(String payload) {
            return payload != null && write(payload.getBytes(UTF_8));
        }

        boolean write(byte[] chunk) {
            if (chunk == null || chunk.length == 0 || closed) {
                return false;
            }
            if (!queue.offer(chunk)) {
                // 队列长期堆积说明读取端（连接线程）已经不在了，直接回收，避免内存持续增长
                Log.w(TAG, "SSE 客户端 " + id + " 发送队列已满，判定为已断开");
                close();
                return false;
            }
            return true;
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            // 只打标记、不清队列，保证 close 之前写入的 [DONE] 还能被读走
            inputStream.markClosed();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clients.remove(id, this);
            } else {
                clients.remove(id);
            }
            Log.d(TAG, "SSE 客户端已断开：" + id + "  剩余连接数：" + clients.size());
            if (listeners.get(id) != null) {
                try {
                    listeners.get(id).onClose(SseServer.this, id);
                } catch (Throwable t) {
                    Log.w(TAG, "SseListener.onClose 异常：" + t.getMessage());
                }
            }
        }
    }

    /**
     * 由阻塞队列驱动的响应体。read() 要么返回正数，要么返回 -1，
     * 绝不返回 0（NanoHTTPD 的 sendBody 把 read()&lt;=0 当作流结束）。
     */
    private static final class SseInputStream extends InputStream {

        private final BlockingQueue<byte[]> queue;
        private volatile boolean closed = false;
        private volatile long lastReadTime = System.currentTimeMillis();
        /**
         * 只在连接线程里访问，无需同步
         */
        private byte[] current;
        private int position;
        private Runnable onClose;

        SseInputStream(BlockingQueue<byte[]> queue) {
            this.queue = queue;
        }

        void setOnClose(Runnable onClose) {
            this.onClose = onClose;
        }

        void markClosed() {
            closed = true;
        }

        long lastReadTime() {
            return lastReadTime;
        }

        @Override
        public int available() {
            return (closed && current == null && queue.isEmpty()) ? 0 : 1;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException("b == null");
            }
            if ((off | len) < 0 || len > b.length - off) {
                throw new ArrayIndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }
            while (true) {
                if (current == null) {
                    byte[] next = takeChunk();
                    if (next == null) {
                        return -1;
                    }
                    current = next;
                    position = 0;
                }
                int remaining = current.length - position;
                if (remaining <= 0) {
                    current = null;
                    continue;
                }
                int n = Math.min(remaining, len);
                System.arraycopy(current, position, b, off, n);
                position += n;
                if (position >= current.length) {
                    current = null;
                    position = 0;
                }
                lastReadTime = System.currentTimeMillis();
                return n;
            }
        }

        /**
         * 阻塞取下一块数据；已关闭且队列排空时返回 null
         */
        private byte[] takeChunk() throws IOException {
            while (true) {
                byte[] item;
                try {
                    item = queue.poll(READ_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("SSE read interrupted");
                }
                if (item != null) {
                    return item;
                }
                if (closed) {
                    return null;
                }
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (onClose != null) {
                onClose.run();
            }
        }
    }

    // ------------------------------------------------------------------ 线程池

    /**
     * 有上限的线程池。SSE 是长连接，默认的 DefaultAsyncRunner 每请求新建线程，
     * 连接一多就会把线程打满。
     */
    private final class PooledAsyncRunner implements IAsyncRunner {

        private final List<ClientHandler> running = Collections.synchronizedList(new ArrayList<ClientHandler>());
        private final ThreadPoolExecutor executor;

        PooledAsyncRunner(int maxThreads) {
            this.executor = new ThreadPoolExecutor(
                    4,
                    Math.max(4, maxThreads),
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "sse-worker-" + threadIndex.getAndIncrement());
                            t.setDaemon(true);
                            return t;
                        }
                    });
            this.executor.allowCoreThreadTimeOut(true);
        }

        @Override
        public void exec(final ClientHandler code) {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        running.add(code);
                        try {
                            code.run();
                        } finally {
                            running.remove(code);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                Log.w(TAG, "SSE 请求线程池已满，拒绝新连接");
                code.close();
            }
        }

        @Override
        public void closed(ClientHandler code) {
            running.remove(code);
        }

        @Override
        public void closeAll() {
            List<ClientHandler> snapshot;
            synchronized (running) {
                snapshot = new ArrayList<ClientHandler>(running);
            }
            for (ClientHandler handler : snapshot) {
                try {
                    handler.close();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            running.clear();
            executor.shutdownNow();
        }
    }

    /**
     * 连接生命周期回调
     */
    public interface SseListener {
        /**
         * 客户端建立连接（响应头还没下发，此时就可以推送首帧）
         */
        void onOpen(SseServer sseServer, String clientId, IHTTPSession session);

        /**
         * 客户端断开或连接被关闭
         */
        void onClose(SseServer sseServer, String clientId);
    }
}
