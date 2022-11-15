package ok.dht.test.gerasimov.service;

import ok.dht.test.gerasimov.ChunkedHttpSession;
import ok.dht.test.gerasimov.exception.EntitiesServiceException;
import ok.dht.test.gerasimov.exception.EntityServiceException;
import ok.dht.test.gerasimov.model.DaoEntry;
import ok.dht.test.gerasimov.utils.ObjectMapper;
import ok.dht.test.gerasimov.utils.ResponseEntity;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class EntitiesService implements HandleService {
    private static final Set<Integer> ALLOWED_METHODS = Set.of(Request.METHOD_GET);
    private static final String ENDPOINT = "/v0/entities";

    private final DB dao;

    public EntitiesService(DB dao) {
        this.dao = dao;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        Parameters parameters = isValidRequest(request, session);

        if (parameters == null) {
            return;
        }

        ((ChunkedHttpSession) session).sendChunkedResponse(
                new Response(Response.OK),
                new Range(dao.iterator(), parameters)::get
        );
    }

    @Override
    public String getEndpoint() {
        return ENDPOINT;
    }

    private Parameters isValidRequest(Request request, HttpSession session) {
        try {
            if (!ALLOWED_METHODS.contains(request.getMethod())) {
                session.sendResponse(ResponseEntity.methodNotAllowed());
                return null;
            }

            String start = request.getParameter("start=");
            String end = request.getParameter("end=");
            if (start == null || start.isEmpty()) {
                session.sendResponse(ResponseEntity.badRequest(
                                "The required <start> parameter was not passed"
                        )
                );
                return null;
            }

            try {
                return new Parameters(
                        start,
                        end
                );
            } catch (NumberFormatException e) {
                session.sendResponse(ResponseEntity.internalError("Invalid timestamp header"));
                return null;
            }
        } catch (IOException e) {
            throw new EntityServiceException("Can not send response", e);
        }
    }

    private static class Parameters {
        private final String start;
        private final String end;

        public Parameters(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class Range {
        public static final byte[] NEW_LINE = "\n".getBytes(StandardCharsets.UTF_8);

        private final DBIterator iterator;
        private final Parameters parameters;

        public Range(DBIterator iterator, Parameters parameters) {
            this.iterator = iterator;
            this.parameters = parameters;
            iterator.seek(parameters.start.getBytes(StandardCharsets.UTF_8));
        }

        public byte[] get() {
            try {
                if (iterator.hasNext()) {
                    Map.Entry<byte[], byte[]> entry = iterator.next();

                    if (Arrays.equals(entry.getKey(), parameters.end.getBytes(StandardCharsets.UTF_8))) {
                        iterator.close();
                        return null;
                    }

                    return createData(entry);
                }

                return null;
            } catch (IOException e) {
                throw new EntitiesServiceException("Can not close iterator", e);
            }
        }

        private static byte[] createData(Map.Entry<byte[], byte[]> entry) {
            try {
                final byte[] key = entry.getKey();
                final byte[] value = ((DaoEntry) ObjectMapper.deserialize(entry.getValue())).getValue();

                return ByteBuffer.allocate(key.length + NEW_LINE.length + value.length)
                        .put(key)
                        .put(NEW_LINE)
                        .put(value)
                        .array();
            } catch (IOException | ClassNotFoundException e) {
                throw new EntitiesServiceException("Can not deserialize DaoEntry", e);
            }
        }
    }
}
