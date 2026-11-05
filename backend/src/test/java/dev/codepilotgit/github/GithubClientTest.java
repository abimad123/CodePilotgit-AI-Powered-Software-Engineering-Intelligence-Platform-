package dev.codepilotgit.github;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import dev.codepilotgit.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class GithubClientTest {
    HttpServer server;
    GithubClient client;
    KeyPair keys;
    AtomicBoolean slow = new AtomicBoolean();
    AtomicBoolean rateLimited = new AtomicBoolean();
    @BeforeEach void setUp() throws Exception {
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login/oauth/access_token", exchange -> respond(exchange, 200, "{\"access_token\":\"user-token\"}"));
        server.createContext("/user/installations", exchange -> {
            if (slow.get()) { try { Thread.sleep(900); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
            if (rateLimited.get()) {
                exchange.getResponseHeaders().set("X-RateLimit-Remaining", "0");
                respond(exchange, 403, "{\"message\":\"rate limit\"}");
            } else respond(exchange, 200, "{\"installations\":[{\"id\":42}]}");
        });
        server.createContext("/app/installations/42/access_tokens", exchange -> respond(exchange, 200, "{\"token\":\"installation-token\"}"));
        server.createContext("/app/installations/42", exchange -> respond(exchange, 200, "{\"id\":42,\"account\":{\"login\":\"team\",\"type\":\"Organization\"}}"));
        server.createContext("/installation/repositories", exchange -> respond(exchange, 200,
                "{\"repositories\":[{\"id\":7,\"full_name\":\"team/repo\",\"description\":\"example\",\"default_branch\":\"main\",\"private\":true,\"html_url\":\"https://github.com/team/repo\"}]}"));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String pem = "-----BEGIN PRIVATE KEY-----\\n" + Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()) + "\\n-----END PRIVATE KEY-----";
        client = new GithubClient(base, base, "123", "client", "secret", pem, "http://localhost/callback", 500, 500);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void signsAppJwtAndFetchesInstallationAndRepositoryMetadata() throws Exception {
        SignedJWT jwt = SignedJWT.parse(client.appJwt());
        assertTrue(jwt.verify(new RSASSAVerifier((java.security.interfaces.RSAPublicKey) keys.getPublic())));
        assertEquals("123", jwt.getJWTClaimsSet().getIssuer());
        assertEquals("user-token", client.userToken("code"));
        assertTrue(client.userCanAccessInstallation("user-token", 42));
        assertEquals("team", client.installation(42).accountLogin());
        var repo = client.repository(42, 7);
        assertEquals("team/repo", repo.full_name());
        assertTrue(repo.isPrivate());
        assertThrows(ApiException.class, () -> client.repository(42, 999));
    }
    @Test void mapsRateLimitAndTimeoutWithoutLeakingUpstreamBody() {
        rateLimited.set(true);
        ApiException rate = assertThrows(ApiException.class, () -> client.userCanAccessInstallation("user-token", 42));
        assertEquals("GITHUB_RATE_LIMITED", rate.code);
        rateLimited.set(false); slow.set(true);
        ApiException timeout = assertThrows(ApiException.class, () -> client.userCanAccessInstallation("user-token", 42));
        assertEquals("UPSTREAM_TIMEOUT", timeout.code);
    }
    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
