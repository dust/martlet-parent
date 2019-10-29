package com.kmfrog.martlet.feed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.Instrument;

/**
 * 
 * @author dust Oct 25, 2019
 *
 */
public abstract class BaseWebSocketHandler {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected Set<String> symbolNames;

    protected WebSocketClient client;
    protected WebSocketSession session;
    private ReentrantLock lock;
    private WebSocketDaemon websocket;

    private AtomicLong counter;

    public static final boolean DBG = true;
    protected final AtomicLong times = new AtomicLong(0L);
    protected final AtomicLong tt = new AtomicLong(0L);

    public BaseWebSocketHandler() {
        lock = new ReentrantLock();
        client = new WebSocketClient();

        counter = new AtomicLong(0L);
    }

    void setWebSocket(WebSocketDaemon websocket) {
        this.websocket = websocket;
    }

    public void onConnect(Session session) {
        lock.lock();
        try {
            this.session = (WebSocketSession) session;
        } finally {
            lock.unlock();
        }

        if (logger.isInfoEnabled()) {
            logger.info("连接创建,[{}],[{}],[{}]", getWebSocketUrl(), String.join("/", symbolNames),
                    session.getRemoteAddress());
        }
    }

    public abstract String getWebSocketUrl();

    /**
     * 文本消息。
     *
     * @param session
     * @param msg
     */
    protected abstract void onMessage(Session session, String msg);

    /**
     * 带有性能时间统计功能的onMessage方法。
     * 
     * @param session
     * @param msg
     */
    public void onMessageWithStats(Session session, String msg) {
        long b = System.currentTimeMillis();
        onMessage(session, msg);
        times.incrementAndGet();
        tt.addAndGet(System.currentTimeMillis() - b);
    }

    /**
     * 二进制消息。默认使用gzip解压二进制数据流为字符串，然后再调用onMessage
     *
     * @param session
     * @param is
     * @throws IOException
     */
    protected void onBinaryMessage(Session session, InputStream is) throws IOException {
        String msg = this.uncompressGzip(is);
        if (DBG) {
            onMessageWithStats(session, msg);
        } else {
            onMessage(session, msg);
        }
    }

    protected void onError(Throwable ex) {
        logger.error("{}.onError: {}", getClass(), ex.getMessage(), ex);
    }

    protected void onClose(int statusCode, String reason) {
        if (logger.isInfoEnabled()) {
            logger.info("连接关闭,[{}][{}],[{}]", statusCode, reason, session == null ? "" : session.getRemoteAddress());
        }
        lock.lock();
        try {
            this.session = null;
        } finally {
            lock.unlock();
        }
    }

    public void close(int status, String reason) {
        if (logger.isInfoEnabled()) {
            logger.info("[{}],[{}] voluntarily close the websocket, cause for [{}]", getWebSocketUrl(),
                    String.join(",", symbolNames), reason);
        }
        if (session != null) {
            session.close(status, reason);
        }
    }

    protected boolean clientOpened() {
        lock.lock();
        try {
            if (!client.isStarted()) {
                return false;
            }
            if (session == null) {
                return false;
            }
            return session.getConnection().isOpen();
        } finally {
            lock.unlock();
        }
    }

    protected final WebSocketSession connect() {
        String wsUriString = getWebSocketUrl();
        try {
            if (!client.isStarted()) {
                client.start();
            }
            return (WebSocketSession) client.connect(websocket, new URI(wsUriString), new ClientUpgradeRequest()).get();
        } catch (SocketTimeoutException e) {
            logger.error("WebSocket[{}]连接超时", wsUriString, e);
            // throw new FatalException("创建WebSocket连接失败[%s]", wsUriString, e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            logger.error("创建WebSocket连接失败[{}]", wsUriString, e);
            // throw new FatalException("创建WebSocket连接失败[%s]", wsUriString, e);
            throw new RuntimeException(e);
        }
    }

    public Session keepAlive() {
        try {
            if (!clientOpened()) {
                session = connect();
            }
        } catch (Exception ex) {
            logger.error("{}.keepAlive [{}], {}", getClass(), String.join("/", symbolNames), ex.getMessage());
        }
        return session;
    }

    protected void resubscribe(Instrument instrument, BaseInstrumentDepth depth) {

    }

    /**
     * 重新订阅某个交易对，或者重新建立连接。
     * 
     * @param instrument
     * @param depth
     * @param isSubscribe
     * @param isConnect
     */
    public void reset(Instrument instrument, BaseInstrumentDepth depth, boolean isSubscribe, boolean isConnect) {
        if (isSubscribe && !isConnect) {
            resubscribe(instrument, depth);
        }

        if (isConnect) {
            if (clientOpened()) {
                session.close(-1, "missing packet!");
            }

            session = connect();
        }
    }

    public Set<String> getSymbols() {
        return symbolNames;
    }

    protected String uncompressGzip(InputStream is) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // final Deflate64CompressorInputStream zin = new Deflate64CompressorInputStream(is);
        GZIPInputStream zin = new GZIPInputStream(is);
        final byte[] buffer = new byte[1024];
        int offset;
        while (-1 != (offset = zin.read(buffer))) {
            out.write(buffer, 0, offset);
        }
        return out.toString();
    }

    public String uncompress(InputStream is) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Deflate64CompressorInputStream zin = new Deflate64CompressorInputStream(is);
            final byte[] buffer = new byte[1024];
            int offset;
            while (-1 != (offset = zin.read(buffer))) {
                out.write(buffer, 0, offset);
            }
            String result = out.toString();
            zin.close();
            out.close();
            return result;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected long generateReqId() {
        return System.currentTimeMillis() + counter.incrementAndGet();
    }

    public void dumpStats(PrintStream ps) {
        if (times.get() > 0) {
            ps.println(String.format("\n%s: %d|%d|%d\n", getClass().getName(), tt.get(), times.get(),
                    tt.get() / times.get()));
        }
    }

}
