package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import ok.dht.test.slastin.node.Node;
import ok.dht.test.slastin.replication.ReplicasDeleteRequestHandler;
import ok.dht.test.slastin.replication.ReplicasGetRequestHandler;
import ok.dht.test.slastin.replication.ReplicasPutRequestHandler;
import ok.dht.test.slastin.sharding.ShardingManager;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.net.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static ok.dht.test.slastin.Utils.badMethod;
import static ok.dht.test.slastin.Utils.badRequest;
import static ok.dht.test.slastin.Utils.getResponseCodeByStatusCode;
import static ok.dht.test.slastin.Utils.serviceUnavailable;

public class SladkiiServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(SladkiiServer.class);
    private static final Duration PROXY_REQUEST_DURATION = Duration.ofMillis(20);

    private final int selfNodeIndex;
    private final List<Node> nodes;
    private final SladkiiComponent component;
    private final ExecutorService processors;
    private final ShardingManager shardingManager;
    private final HttpClient client;

    public SladkiiServer(
            HttpServerConfig httpServerConfig,
            ServiceConfig serviceConfig,
            List<Node> nodes,
            SladkiiComponent component,
            ExecutorService processors,
            ShardingManager shardingManager
    ) throws IOException {
        super(httpServerConfig);
        this.selfNodeIndex = serviceConfig.clusterUrls().indexOf(serviceConfig.selfUrl());
        this.nodes = nodes;
        this.component = component;
        this.processors = processors;
        this.shardingManager = shardingManager;
        client = HttpClient.newHttpClient();
    }

    @Path("/v0/internal/entity")
    public void handleInternalRequest(Request request, HttpSession session) {
        // supposes that previous node checked client's id
        String id = request.getParameter("id=");

        // log.info("internal handling {} for id {}", request.getMethodName(), id);

        boolean wasTaskAdded = tryAddNodeTask(session, selfNodeIndex, () ->
                sendResponse(session, processRequestSelf(id, request))
        );

        if (!wasTaskAdded) {
            sendResponse(session, serviceUnavailable());
        }
    }

    @Path("/v0/entity")
    public void handleClientRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (id == null || id.isBlank()) {
            sendResponse(session, badRequest());
            return;
        }

        int ack;
        int from;
        try {
            String ackParameter = request.getParameter("ack=");
            if (ackParameter == null) {
                from = nodes.size();
                ack = from / 2 + 1;
            } else {
                from = Integer.parseInt(request.getParameter("from="));
                ack = Integer.parseInt(ackParameter);
            }
        } catch (NumberFormatException e) {
            sendResponse(session, badRequest());
            return;
        }

        if (!validateAckFrom(ack, from)) {
            sendResponse(session, badRequest());
            return;
        }

        // log.info("handling {} for id {}", request.getMethodName(), id);

        RequestHandler replicasRequestHandler = switch (request.getMethod()) {
            case Request.METHOD_GET -> new ReplicasGetRequestHandler(id, ack, from, this);
            case Request.METHOD_PUT -> new ReplicasPutRequestHandler(id, ack, from, this);
            case Request.METHOD_DELETE -> new ReplicasDeleteRequestHandler(id, ack, from, this);
            default -> {
                sendResponse(session, badMethod());
                yield null;
            }
        };

        if (replicasRequestHandler != null) {
            replicasRequestHandler.handleRequest(request, session);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) {
        sendResponse(session, badRequest());
    }

    private boolean validateAckFrom(int ack, int from) {
        return 0 < ack && ack <= from && from <= nodes.size();
    }

    public ShardingManager getShardingManager() {
        return shardingManager;
    }

    public boolean tryAddNodeTask(HttpSession session, int nodeIndex, Runnable task) {
        boolean wasTaskAdded = nodes.get(nodeIndex).offerTask(task);
        if (wasTaskAdded) {
            try {
                processors.submit(() -> {
                    int currentNodeIndex = nodeIndex;
                    while (true) {
                        var currentNode = nodes.get(currentNodeIndex);
                        var currentTask = currentNode.pollTask();
                        if (currentTask != null) {
                            try {
                                currentTask.run();
                            } finally {
                                currentNode.finishTask();
                            }
                            return;
                        }
                        currentNodeIndex = (currentNodeIndex + 1) % nodes.size();
                    }
                });
            } catch (RejectedExecutionException e) {
                log.error("Can not schedule task for execution", e);
                sendResponse(session, serviceUnavailable());
            }
        }
        return wasTaskAdded;
    }

    public Response processRequest(int nodeIndex, String id, Request request) {
        return nodeIndex == selfNodeIndex
                ? processRequestSelf(id, request)
                : processRequestProxy(shardingManager.getNodeUrlByNodeIndex(nodeIndex), id, request);
    }

    private Response processRequestSelf(String id, Request request) {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> component.get(id);
            case Request.METHOD_PUT -> component.put(id, extractTimestamp(request), request);
            case Request.METHOD_DELETE -> component.delete(id, extractTimestamp(request));
            default -> {
                log.error("unsupported method={}", request.getMethod());
                yield badMethod();
            }
        };
    }

    private static Long extractTimestamp(Request request) {
        return Long.parseLong(request.getHeader("Timestamp:"));
    }

    private Response processRequestProxy(String nodeUrl, String id, Request request) {
        var builder = HttpRequest.newBuilder(URI.create(nodeUrl + "/v0/internal/entity?id=" + id));
        builder.timeout(PROXY_REQUEST_DURATION);

        var bodyPublishers = request.getBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.getBody());
        builder.method(request.getMethodName(), bodyPublishers);

        String timestamp = request.getHeader("Timestamp:");
        if (timestamp != null) {
            builder.setHeader("Timestamp", timestamp);
        }

        try {
            var httpResponse = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(getResponseCodeByStatusCode(httpResponse.statusCode()), httpResponse.body());
        } catch (IOException e) {
            log.error("can not reach {}", nodeUrl, e);
            return serviceUnavailable();
        } catch (InterruptedException e) {
            log.error("error occurred while handling http response", e);
            return serviceUnavailable();
        }
    }

    public void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            log.error("failed to send response", e);
            closeSession(session);
        }
    }

    public synchronized void stop() {
        closeAllSessions();
        super.stop();
    }

    private void closeAllSessions() {
        for (var selectorThread : selectors) {
            selectorThread.selector.forEach(this::closeSession);
        }
    }

    private void closeSession(Session session) {
        try {
            session.close();
        } catch (Exception e) {
            log.error("failed to close session", e);
        }
    }
}
