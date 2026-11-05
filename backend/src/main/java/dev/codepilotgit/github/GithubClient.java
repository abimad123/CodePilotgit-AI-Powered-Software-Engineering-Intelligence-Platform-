package dev.codepilotgit.github;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.codepilotgit.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.util.LinkedMultiValueMap;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Component
public class GithubClient {
    public record InstallationInfo(long id, String accountLogin, String accountType) {}
    public record RepositoryInfo(long id, String full_name, String description, String default_branch, boolean isPrivate, String html_url) {}
    private final RestClient api;
    private final RestClient oauth;
    private final String appId, clientId, clientSecret, privateKey, redirectUri;
    public GithubClient(@Value("${app.github.api-base-url}") String apiBase,
                        @Value("${app.github.oauth-base-url:https://github.com}") String oauthBase,
                        @Value("${app.github.app-id}") String appId,
                        @Value("${app.github.client-id:}") String clientId,
                        @Value("${app.github.client-secret:}") String clientSecret,
                        @Value("${app.github.private-key}") String privateKey,
                        @Value("${app.github.redirect-uri:}") String redirectUri,
                        @Value("${app.github.connect-timeout-ms:5000}") int connectTimeoutMs,
                        @Value("${app.github.read-timeout-ms:10000}") int readTimeoutMs) {
        if (connectTimeoutMs < 1 || readTimeoutMs < 1) throw new IllegalArgumentException("Invalid GitHub HTTP timeout");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs); factory.setReadTimeout(readTimeoutMs);
        this.api = RestClient.builder().baseUrl(apiBase).requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28").build();
        this.oauth = RestClient.builder().baseUrl(oauthBase).requestFactory(factory).defaultHeader("Accept", "application/json").build();
        this.appId = appId; this.clientId = clientId; this.clientSecret = clientSecret; this.privateKey = privateKey; this.redirectUri = redirectUri;
    }
    public boolean configured() { return !appId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank() && !privateKey.isBlank(); }
    public String appJwt() {
        requireConfig();
        try {
            String pem = privateKey.replace("\\n", "\n").replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(appId)
                    .issueTime(Date.from(now.minusSeconds(60))).expirationTime(Date.from(now.plusSeconds(540))).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(key)); return jwt.serialize();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_CONFIGURATION_ERROR", "GitHub App key is invalid or unavailable.");
        }
    }
    public String userToken(String code) {
        requireConfig();
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId); form.add("client_secret", clientSecret);
            form.add("code", code); form.add("redirect_uri", redirectUri);
            Map<?, ?> data = oauth.post().uri("/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(Map.class);
            if (data == null || !(data.get("access_token") instanceof String token) || token.isBlank()) throw upstream();
            return token;
        } catch (RestClientResponseException | ResourceAccessException ex) { throw translate(ex); }
    }
    public boolean userCanAccessInstallation(String userToken, long installationId) {
        for (int page = 1; page <= 10; page++) {
            Map<?, ?> data = getMap("/user/installations?per_page=100&page=" + page, userToken);
            List<?> items = list(data.get("installations"));
            for (Object item : items) if (number(map(item).get("id")) == installationId) return true;
            if (items.size() < 100) return false;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_PAGINATION_LIMIT", "Too many GitHub installations to verify.");
    }
    public InstallationInfo installation(long id) {
        Map<?, ?> data = getMap("/app/installations/" + id, appJwt());
        Map<?, ?> account = map(data.get("account"));
        return new InstallationInfo(number(data.get("id")), string(account.get("login")), string(account.get("type")));
    }
    public List<RepositoryInfo> repositories(long installationId, int page) {
        if (page < 1 || page > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid page.");
        String token = installationToken(installationId);
        Map<?, ?> data = getMap("/installation/repositories?per_page=100&page=" + page, token);
        return list(data.get("repositories")).stream().map(this::repository).toList();
    }
    public RepositoryInfo repository(long installationId, long repositoryId) {
        for (int page = 1; page <= 10; page++) {
            List<RepositoryInfo> items = repositories(installationId, page);
            for (RepositoryInfo item : items) if (item.id() == repositoryId) return item;
            if (items.size() < 100) break;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Repository is not accessible to this installation.");
    }
    private String installationToken(long id) {
        try {
            Map<?, ?> data = api.post().uri("/app/installations/{id}/access_tokens", id)
                    .header("Authorization", "Bearer " + appJwt()).retrieve().body(Map.class);
            if (data == null || !(data.get("token") instanceof String token)) throw upstream();
            return token;
        } catch (RestClientResponseException | ResourceAccessException ex) { throw translate(ex); }
    }
    private Map<?, ?> getMap(String path, String token) {
        try {
            Map<?, ?> data = api.get().uri(path).header("Authorization", "Bearer " + token).retrieve().body(Map.class);
            if (data == null) throw upstream(); return data;
        } catch (RestClientResponseException | ResourceAccessException ex) { throw translate(ex); }
    }
    private RepositoryInfo repository(Object value) {
        Map<?, ?> data = map(value);
        return new RepositoryInfo(number(data.get("id")), string(data.get("full_name")),
                data.get("description") == null ? null : string(data.get("description")),
                string(data.get("default_branch")), Boolean.TRUE.equals(data.get("private")), string(data.get("html_url")));
    }
    private void requireConfig() { if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED", "GitHub App is not configured."); }
    private ApiException translate(Exception ex) {
        if (ex instanceof ResourceAccessException) return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "GitHub did not respond in time.");
        int status = ((RestClientResponseException) ex).getStatusCode().value();
        if (status == 404) return new ApiException(HttpStatus.NOT_FOUND, "GITHUB_NOT_FOUND", "GitHub resource not found.");
        if ((status == 403 || status == 429) && "0".equals(((RestClientResponseException) ex).getResponseHeaders() == null ? null : ((RestClientResponseException) ex).getResponseHeaders().getFirst("X-RateLimit-Remaining")))
            return new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_RATE_LIMITED", "GitHub rate limit reached.");
        if (status == 401 || status == 403) return new ApiException(status == 403 ? HttpStatus.FORBIDDEN : HttpStatus.BAD_GATEWAY,
                "GITHUB_ACCESS_DENIED", "GitHub denied access or permissions are missing.");
        if (status == 429) return new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_RATE_LIMITED", "GitHub rate limit reached.");
        return upstream();
    }
    private ApiException upstream() { return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_FAILURE", "GitHub request failed."); }
    private Map<?, ?> map(Object x) { return (Map<?, ?>) x; }
    private List<?> list(Object x) { return (List<?>) x; }
    private long number(Object x) { return ((Number) x).longValue(); }
    private String string(Object x) { return (String) x; }
}
