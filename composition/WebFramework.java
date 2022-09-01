import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("java:S125")
public class WebFramework {
    // Simplified for clarity

    interface Request {
        String path();
        String body(); // InputStream simplified
        // omitted: request type, headers, trailers, maybe context etc.
    }

    interface Response {
        void body(String body);
        void addHeader(String header, String value);
        // omitted trailers, etc. and a lot more
    }

    // Core server / http handler
    // stolen from Go, but this interface (or similar) is used extensively across many languages
    interface HttpHandler {
        void serveHttp(Request req, Response res);
    }

    /**
     * ┌─────────────────┐
     * │     Routing     │
     * │          ┌──┐   │
     * │       ┌─►│  │   │
     * │       │  ├──┤   │
     * │  path ├─►│  │   │
     * │  ┌──┐ │  ├──┤   │
     * │  │  ├─┼─►│  │   │
     * │  └──┘ │  ├──┤   │
     * │       ├─►│  │   │
     * │       │  ├──┤   │
     * │       └─►│  │   │
     * │          └──┘   │
     * │       Handlers  │
     * └─────────────────┘
     */
    private static final class RoutingHttpHandler implements HttpHandler {
        // ignore concurrency
        private final Map<String, HttpHandler> handlers;
        private final HttpHandler notFoundHandler;

        RoutingHttpHandler(Map<String, HttpHandler> handlers, HttpHandler notFoundHandler) {
            this.handlers = handlers;
            this.notFoundHandler = notFoundHandler;
        }

        @Override
        public void serveHttp(Request req, Response res) {
            handlers.getOrDefault(req.path(), notFoundHandler).serveHttp(req, res);
        }
    }

    public static final HttpHandler defaultNotFound = (req, res) -> res.body("404: not found!");

    public static HttpHandler route(Map<String, HttpHandler> routes, HttpHandler notFound) {
        return new RoutingHttpHandler(routes, notFound);
    }

    public static HttpHandler routeWithDefaultNotFound(Map<String, HttpHandler> routes) {
        return route(routes, defaultNotFound);
    }

    // our framework needs middlewares
    class MiddleWares {
        private MiddleWares() {} // uninstantiable

        /**
         * Simple MiddleWare: it short circuits on not authenticated
         */
        @SuppressWarnings({"java:S2589", "java:S1172"})
        private static final class AuthenticationMiddleWare implements HttpHandler {

            private final HttpHandler unAuthHandler;
            private final HttpHandler next;

            AuthenticationMiddleWare(HttpHandler unAuthHandler, HttpHandler next) {
                this.unAuthHandler = unAuthHandler;
                this.next = next;
            }

            @Override
            public void serveHttp(Request req, Response res) {
                String[] authHeaders = null; // req.header("Authorization")
                //if (authHeaders == null || authHeader.length == 0) {
                //  res.body("401: Unauthorized");
                //  res.addHeader("WWW-Authenticate", ...);
                //  return;
                //}
                if (isAuthenticated(authHeaders)) {
                    next.serveHttp(req, res);
                    return;
                }
                this.unAuthHandler.serveHttp(req, res);
            }

            private boolean isAuthenticated(String[] auth) {
                // check stuff with auth header
                return true;
            }

        }

        private final static HttpHandler notAuthenticated = (req, res) -> res.body("403: forbidden, go away!");

        public static HttpHandler authenticate(HttpHandler unAuthHandler, HttpHandler handler) {
            return new AuthenticationMiddleWare(unAuthHandler, handler);
        }

        public static HttpHandler authenticate(HttpHandler handler) {
            return authenticate(notAuthenticated, handler);
        }





        private static final class BinaryHttpHandler implements HttpHandler {
            private final HttpHandler first;
            private final HttpHandler second;

            BinaryHttpHandler(HttpHandler first, HttpHandler second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public void serveHttp(Request req, Response res) {
                first.serveHttp(req, res);
                second.serveHttp(req, res);
            }
        }


        public static HttpHandler ordered(HttpHandler doFirst, HttpHandler doSecond) {
            return new BinaryHttpHandler(doFirst, doSecond);
        }

        private final static Logger logger = Logger.getLogger("middlewares");

        public static HttpHandler logBefore(HttpHandler handler) {
            return ordered((request, response) -> logger.log(Level.INFO, request.path() + ": hello!"), handler);
        }

        public static HttpHandler logAfter(HttpHandler handler) {
            return ordered(handler, (request, response) -> logger.log(Level.INFO,  "Goodbye"));
        }

        public static HttpHandler addHeader(String key, String value, HttpHandler handler) {
            return new BinaryHttpHandler(handler, (request, response) -> response.addHeader(key, value));
        }

        public static HttpHandler applicationJson(HttpHandler handler) {
            return addHeader("ContentType", "application/json", handler);
        }


        // TODO Middlewares for: metrics etc.
    }

}
