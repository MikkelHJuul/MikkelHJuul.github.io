import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


class Client {

    static final class DummyRequest implements WebFramework.Request {
        private final String path;
        private final String body;

        DummyRequest(String path, String body) {
            this.path = path;
            this.body = body;
        }
        @Override
        public String path() {
            return path;
        }

        @Override
        public String body() {
            return body;
        }
    }

    static final class DummyResponse implements WebFramework.Response {
        private final Map<String, List<String>> headers = new HashMap<>();
        private String body;
        @Override
        public void body(String body) {
            this.body = body;
        }

        @Override
        public void addHeader(String header, String value) {
            var heads = this.headers.computeIfAbsent(header, s -> new ArrayList<>());
            heads.add(value);
        }
    }

    public static void main(String[] args) {

        // actual handler
        WebFramework.HttpHandler importantBusinessLogicHandler = (req, res) -> res.body("200: {\"Hello\":\"" + req.body() + "\"}" );

        // our HTTPServer is an HttpHandler itself
        WebFramework.HttpHandler server = WebFramework.MiddleWares.logBefore(WebFramework.MiddleWares.logAfter(
                WebFramework.routeWithDefaultNotFound(
                    Map.of(
                            "/endpoint/resource",
                                    WebFramework.MiddleWares.authenticate(
                                            WebFramework.MiddleWares.applicationJson(importantBusinessLogicHandler))))
                            // more endpoints here
                )
        );

        var resp = new DummyResponse();
        server.serveHttp(new DummyRequest("/endpoint/resource", "World"), resp);

        var log = Logger.getLogger("output");
        log.log(Level.SEVERE, resp.body);
        log.log(Level.SEVERE, "ContentType: {0}", resp.headers.get("ContentType"));


        var resp2 = new DummyResponse();
        server.serveHttp(new DummyRequest("unknown", "hell"), resp2);

        log.log(Level.WARNING, resp2.body);
        log.log(Level.WARNING, resp2.headers.isEmpty() ? "No headers added" : "header was added");
    }
}