package de.gsi.acc.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.servlet.ServletOutputStream;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

import io.javalin.Javalin;
import io.javalin.core.compression.CompressionStrategy;
import io.javalin.core.compression.Gzip;
import io.javalin.core.security.Role;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;

public class RestServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestServer.class);
    private static final String ENDPOINT_HELLO = "/hello";
    private static final String ENDPOINT_HELLO_NAME_PARAM = "/hello/:name";
    private static final int SERVER_JAVALIN = 8080;
    public static final String ENDPOINT_BYTE_BUFFER = "/byteBuffer";

    private static final int N_DATA = 100;
    static final byte[] BYTE_BUFFER = new byte[N_DATA];

    private static Javalin instance;

    private static final ConcurrentMap<String, Queue<SseClient>> eventListener = new ConcurrentHashMap<>();
    private static final ObservableList<String> endpoints = FXCollections.observableArrayList();

    public RestServer() {
    }

    public static ObservableList<String> getEndpoints() {
        return endpoints;
    }

    public static Queue<SseClient> getEventClients(final String endpointName) {
        if (endpointName == null || endpointName.isEmpty()) {
            throw new IllegalArgumentException("invalid endpointName '" + endpointName + "'");
        }

        final String fullEndPointName = '/' == endpointName.charAt(0) ? endpointName : "/" + endpointName;
        final Queue<SseClient> ret = eventListener.get(fullEndPointName);
        if (ret == null) {
            throw new IllegalArgumentException("endpointName '" + fullEndPointName + "' not registered");
        }
        return ret;
    }

    public static Javalin getInstance() {
        if (instance == null) {
        }
        return instance;
    }

    public static void registerEndpoint(final String endpointName, Handler userHandler) {
        if (endpointName == null || endpointName.isEmpty()) {
            throw new IllegalArgumentException("invalid endpointName '" + endpointName + "'");
        }
        if (userHandler == null) {
            throw new IllegalArgumentException("user-provided handler for  endpointName '" + endpointName + "' is null");
        }

        final String fullEndPointName = '/' == endpointName.charAt(0) ? endpointName : "/" + endpointName;
        final HashSet<Role> permittedRoles = new HashSet<>();
        endpoints.add(fullEndPointName);
        eventListener.computeIfAbsent(fullEndPointName, key -> new ConcurrentLinkedQueue<>());

        SseHandler sseHandler = new SseHandler(client -> {
            System.err.println("sse interface invoked for '" + fullEndPointName + "' and client: " + client.ctx.req.getRemoteHost());
            getEventClients(fullEndPointName).add(client);
            client.sendEvent("connected", "Hello, SSE " + client.ctx.req.getRemoteHost());

            client.onClose(() -> {
                System.err.println("removed client: " + client.ctx.req.getRemoteHost());
                getEventClients(fullEndPointName).remove(client);
            });
        });

        final Handler localHandler = ctx -> {
            if (MimeType.EVENT_STREAM.toString().equals(ctx.header(Header.ACCEPT))) {
                sseHandler.handle(ctx);
                return;
            }
            userHandler.handle(ctx);
        };
        instance.addHandler(HandlerType.GET, endpointName, localHandler, permittedRoles);
    }

    public static void startRestServer() {
        instance = Javalin.create(config -> {
                              config.enableCorsForAllOrigins();
                              // config.defaultContentType = MimeType.BINARY.toString();
                              config.compressionStrategy(null, new Gzip(0));
                              config.inner.compressionStrategy = CompressionStrategy.NONE;
                          })
                           .start(SERVER_JAVALIN);

        // some default routes
        registerEndpoint("/", ctx -> ctx.result("available end points" + endpoints.stream().collect(Collectors.joining(", ", "[", "]"))));
        registerEndpoint(ENDPOINT_HELLO, ctx -> ctx.result("Hello World"));
        registerEndpoint(ENDPOINT_HELLO_NAME_PARAM, ctx -> ctx.result("Hello: " + ctx.pathParam("name") + "!"));

        initDefaultRoutes();
    }

    public static void writeBytesToContext(@NotNull final Context ctx, final byte[] bytes, final int nSize) {
        // based on the suggestion at https://github.com/tipsy/javalin/issues/910
        try (ServletOutputStream outputStream = ctx.res.getOutputStream()) {
            outputStream.write(bytes, 0, nSize);
        } catch (IOException e) {
            LOGGER.atError().setCause(e);
        }
    }

    protected static void initDefaultRoutes() {
        registerEndpoint(ENDPOINT_BYTE_BUFFER, ctx -> {
            String type = ctx.header(Header.ACCEPT);
            System.err.println("started bytebuffer endpoint - accept header = " + type);

            if (type == null || type.equalsIgnoreCase(MimeType.JSON.toString())) {
                // ctx.contentType(MimeType.JSON.toString()).result(JSON.toJSONString(new
                // MyBinaryData(BYTE_BUFFER)));
                // alt 1:
                final String returnString = JSON.toJSONString(BYTE_BUFFER);
                final byte[] bytes = returnString.getBytes(StandardCharsets.UTF_8);
                // alt 2:
                // final byte[] bytes = JSON.toJSONBytes(new MyBinaryData(BYTE_BUFFER));
                writeBytesToContext(ctx, bytes, bytes.length);
                return;
            } else if (type.equalsIgnoreCase(MimeType.BINARY.toString())) {
                writeBytesToContext(ctx, BYTE_BUFFER, BYTE_BUFFER.length);
                return;
            }
            // default return type for unspecified mime type
            writeBytesToContext(ctx, BYTE_BUFFER, BYTE_BUFFER.length);
        });
    }
}
