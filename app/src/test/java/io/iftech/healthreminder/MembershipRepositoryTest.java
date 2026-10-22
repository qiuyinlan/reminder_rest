package io.iftech.healthreminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public final class MembershipRepositoryTest {
    @Test public void freeUserCanRemainLoggedOutAndThirdItemIsFree() {
        MemoryCredentials credentials = new MemoryCredentials();
        MembershipRepository repository = repository(credentials, new FakeTransport());
        assertFalse(repository.isLoggedIn());
        assertFalse(MembershipPolicy.requiresMembershipForNewItem(2));
        assertTrue(MembershipPolicy.requiresMembershipForNewItem(3));
        assertTrue(MembershipPolicy.requiresMembershipForArtwork(3));
        assertFalse(MembershipPolicy.requiresMembershipForArtwork(2));
        assertTrue(MembershipPolicy.requiresMembershipForFocusMode(true));
        assertFalse(MembershipPolicy.requiresMembershipForFocusMode(false));
    }

    @Test public void loginSavesServerTokenAndAccount() {
        MemoryCredentials credentials = new MemoryCredentials();
        FakeTransport transport = new FakeTransport();
        transport.reply("/v1/auth/email/verify-code", 200,
                "{\"access_token\":\"server-token\",\"token_type\":\"Bearer\",\"user\":{\"id\":\"u1\",\"email\":\"person@example.com\"}}");
        MembershipRepository.Result result = repository(credentials, transport)
                .verifyCode("person@example.com", "123456");
        assertTrue(result.success);
        assertEquals("server-token", credentials.token());
        assertEquals("person@example.com", credentials.email());
        assertFalse(transport.lastBody.contains("server-token"));
    }

    @Test public void sendCodeAccepts202AndSurfacesRateLimit() {
        FakeTransport transport = new FakeTransport();
        transport.reply("/v1/auth/email/send-code", 202, "{\"message\":\"验证码已发送\"}");
        MembershipRepository repository = repository(new MemoryCredentials(), transport);
        assertTrue(repository.sendCode("person@example.com").success);
        assertTrue(transport.requestBodies.get("/v1/auth/email/send-code").contains("person@example.com"));

        transport = new FakeTransport();
        transport.reply("/v1/auth/email/send-code", 429, "{}");
        MembershipRepository.Result limited = repository(new MemoryCredentials(), transport)
                .sendCode("person@example.com");
        assertFalse(limited.success);
        assertEquals("操作太频繁，请稍后再试", limited.message);
    }

    @Test public void entitlementUsesServerActiveValueAndExpiry() {
        MemoryCredentials credentials = loggedIn();
        FakeTransport transport = validEntitlement(true, "null");
        MembershipRepository.Result active = repository(credentials, transport).refreshAccountAndEntitlement();
        assertTrue(active.success);
        assertTrue(active.active);
        assertEquals("server-token", transport.lastToken);

        transport = validEntitlement(false, "null");
        MembershipRepository.Result inactive = repository(credentials, transport).refreshAccountAndEntitlement();
        assertTrue(inactive.success);
        assertFalse(inactive.active);

        transport = validEntitlement(true, "1");
        MembershipRepository.Result expired = repository(credentials, transport).refreshAccountAndEntitlement();
        assertFalse(expired.active);
    }

    @Test public void redeemRefreshesServerEntitlementAndRejectsFailure() {
        MemoryCredentials credentials = loggedIn();
        FakeTransport transport = new FakeTransport();
        transport.reply("/v1/membership/redeem", 200,
                "{\"message\":\"兑换成功\",\"entitlement\":{\"active\":true,\"status\":\"active\",\"valid_until\":null}}");
        transport.reply("/v1/me", 200, "{\"id\":\"u1\",\"email\":\"person@example.com\"}");
        transport.reply("/v1/entitlements", 200, "{\"entitlement\":{\"active\":true,\"valid_until\":null}}");
        MembershipRepository.Result success = repository(credentials, transport).redeem("MANUAL-CODE");
        assertTrue(success.success);
        assertTrue(success.active);
        assertTrue(transport.requestBodies.get("/v1/membership/redeem").contains("MANUAL-CODE"));

        transport = new FakeTransport();
        transport.reply("/v1/membership/redeem", 400,
                "{\"error\":{\"code\":\"invalid_code\",\"message\":\"兑换码无效\"}}");
        MembershipRepository.Result failure = repository(credentials, transport).redeem("BAD");
        assertFalse(failure.success);
        assertEquals("兑换码无效", failure.message);
    }

    @Test public void logoutClearsCredentialsEvenWhenNetworkFails() {
        MemoryCredentials credentials = loggedIn();
        FakeTransport transport = new FakeTransport();
        transport.fail("/v1/auth/logout");
        MembershipRepository.Result result = repository(credentials, transport).logout();
        assertFalse(result.success);
        assertFalse(repository(credentials, transport).isLoggedIn());
        assertNull(credentials.email());
    }

    @Test public void unauthorizedAndNetworkFailureDoNotGrantMembership() {
        MemoryCredentials credentials = loggedIn();
        FakeTransport unauthorized = new FakeTransport();
        unauthorized.reply("/v1/me", 401, "{\"error\":{\"message\":\"expired\"}}");
        MembershipRepository.Result denied = repository(credentials, unauthorized).refreshAccountAndEntitlement();
        assertFalse(denied.success);
        assertTrue(denied.unauthorized);
        assertFalse(repository(credentials, unauthorized).isLoggedIn());

        MemoryCredentials networkCredentials = loggedIn();
        FakeTransport network = new FakeTransport();
        network.fail("/v1/me");
        MembershipRepository.Result unavailable = repository(networkCredentials, network).refreshAccountAndEntitlement();
        assertFalse(unavailable.success);
        assertFalse(unavailable.active);
        assertEquals("网络连接失败，请检查网络后重试", unavailable.message);
        assertTrue(repository(networkCredentials, network).isLoggedIn());
    }

    private static FakeTransport validEntitlement(boolean active, String validUntil) {
        FakeTransport transport = new FakeTransport();
        transport.reply("/v1/me", 200, "{\"id\":\"u1\",\"email\":\"person@example.com\"}");
        transport.reply("/v1/entitlements", 200,
                "{\"entitlement\":{\"active\":" + active + ",\"valid_until\":" + validUntil + "}}");
        return transport;
    }

    private static MemoryCredentials loggedIn() {
        MemoryCredentials credentials = new MemoryCredentials();
        credentials.save("server-token", "person@example.com");
        return credentials;
    }

    private static MembershipRepository repository(MemoryCredentials credentials, FakeTransport transport) {
        return new MembershipRepository(new MembershipApiClient("https://membership.example", transport), credentials);
    }

    private static final class MemoryCredentials implements MembershipRepository.CredentialStore {
        private String token;
        private String email;
        @Override public String token() { return token; }
        @Override public void save(String token, String email) { this.token = token; this.email = email; }
        @Override public String email() { return email; }
        @Override public void clear() { token = null; email = null; }
    }

    private static final class FakeTransport implements MembershipApiClient.Transport {
        private final Map<String, Queue<MembershipApiClient.Response>> responses = new HashMap<>();
        private final Map<String, String> requestBodies = new HashMap<>();
        private final Map<String, Boolean> failures = new HashMap<>();
        private String lastBody = "";
        private String lastToken;

        void reply(String path, int status, String body) {
            responses.computeIfAbsent(path, ignored -> new ArrayDeque<>())
                    .add(new MembershipApiClient.Response(status, body));
        }

        void fail(String path) { failures.put(path, true); }

        @Override public MembershipApiClient.Response execute(String method, String url, String json, String token)
                throws IOException {
            String path = url.substring("https://membership.example".length());
            lastBody = json == null ? "" : json;
            lastToken = token;
            requestBodies.put(path, lastBody);
            if (failures.containsKey(path)) throw new IOException("offline");
            Queue<MembershipApiClient.Response> queue = responses.get(path);
            if (queue == null || queue.isEmpty()) return new MembershipApiClient.Response(500, "");
            return queue.remove();
        }
    }
}
