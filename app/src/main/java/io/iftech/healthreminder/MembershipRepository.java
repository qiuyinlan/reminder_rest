package io.iftech.healthreminder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

final class MembershipRepository {
    interface CredentialStore {
        String token();
        void save(String token, String email);
        String email();
        void clear();
    }

    static final class Result {
        final boolean success;
        final boolean unauthorized;
        final boolean active;
        final long validUntil;
        final String email;
        final String message;

        Result(boolean success, boolean unauthorized, boolean active, long validUntil, String email, String message) {
            this.success = success;
            this.unauthorized = unauthorized;
            this.active = active;
            this.validUntil = validUntil;
            this.email = email;
            this.message = message;
        }

        static Result ok(String message) { return new Result(true, false, false, 0, null, message); }
        static Result fail(String message, boolean unauthorized) {
            return new Result(false, unauthorized, false, 0, null, message);
        }
    }

    private final MembershipApiClient api;
    private final CredentialStore credentials;

    MembershipRepository(MembershipApiClient api, CredentialStore credentials) {
        this.api = api;
        this.credentials = credentials;
    }

    boolean isLoggedIn() { return credentials.token() != null && !credentials.token().isEmpty(); }
    String cachedEmail() { return credentials.email(); }

    Result sendCode(String email) {
        JSONObject body = new JSONObject();
        try { body.put("email", email); }
        catch (JSONException e) { return Result.fail("邮箱信息无效", false); }
        return execute("POST", "/v1/auth/email/send-code", body, null, "验证码已发送", false);
    }

    Result verifyCode(String email, String code) {
        JSONObject body = new JSONObject();
        try { body.put("email", email); body.put("code", code); }
        catch (JSONException e) { return Result.fail("登录信息无效", false); }
        MembershipApiClient.Response response;
        try { response = api.request("POST", "/v1/auth/email/verify-code", body, null); }
        catch (IOException e) { return networkFailure(e); }
        if (!isSuccess(response.status)) return failure(response, false);
        try {
            JSONObject json = new JSONObject(response.body);
            String token = json.optString("access_token", "");
            if (token.isEmpty()) return Result.fail("服务端未返回登录凭据，请重试", false);
            JSONObject user = json.optJSONObject("user");
            String serverEmail = user == null ? email : user.optString("email", email);
            credentials.save(token, serverEmail);
            return Result.ok("登录成功");
        } catch (JSONException e) { return Result.fail("服务端响应无法读取，请重试", false); }
    }

    Result refreshAccountAndEntitlement() {
        String token = credentials.token();
        if (token == null || token.isEmpty()) return Result.fail("请先登录", false);
        MembershipApiClient.Response accountResponse;
        try { accountResponse = api.request("GET", "/v1/me", null, token); }
        catch (IOException e) { return networkFailure(e); }
        if (!isSuccess(accountResponse.status)) return failure(accountResponse, true);
        String email;
        try { email = new JSONObject(accountResponse.body).optString("email", ""); }
        catch (JSONException e) { return Result.fail("账号信息读取失败，请重试", false); }

        MembershipApiClient.Response entitlementResponse;
        try { entitlementResponse = api.request("GET", "/v1/entitlements", null, token); }
        catch (IOException e) { return networkFailure(e); }
        if (!isSuccess(entitlementResponse.status)) return failure(entitlementResponse, true);
        try {
            JSONObject entitlement = new JSONObject(entitlementResponse.body).optJSONObject("entitlement");
            boolean active = entitlement != null && entitlement.optBoolean("active", false);
            boolean hasValidUntil = entitlement != null && !entitlement.isNull("valid_until");
            long validUntil = hasValidUntil ? entitlement.optLong("valid_until", 0) : 0;
            if (active && hasValidUntil && validUntil <= System.currentTimeMillis() / 1000L) active = false;
            credentials.save(token, email);
            return new Result(true, false, active, validUntil, email, active ? "会员有效" : "暂未开通或已过期");
        } catch (JSONException e) { return Result.fail("会员权益读取失败，请重试", false); }
    }

    Result redeem(String code) {
        String token = credentials.token();
        if (token == null || token.isEmpty()) return Result.fail("请先登录后再兑换", false);
        JSONObject body = new JSONObject();
        try { body.put("code", code); }
        catch (JSONException e) { return Result.fail("兑换码无效", false); }
        MembershipApiClient.Response response;
        try { response = api.request("POST", "/v1/membership/redeem", body, token); }
        catch (IOException e) { return networkFailure(e); }
        if (!isSuccess(response.status)) return failure(response, true);
        Result refreshed = refreshAccountAndEntitlement();
        if (!refreshed.success) return refreshed;
        return new Result(true, false, refreshed.active, refreshed.validUntil, refreshed.email, "兑换成功");
    }

    Result logout() {
        String token = credentials.token();
        Result result = Result.ok("已退出登录");
        if (token != null && !token.isEmpty()) {
            try {
                MembershipApiClient.Response response = api.request("POST", "/v1/auth/logout", new JSONObject(), token);
                if (!isSuccess(response.status)) result = failure(response, false);
            } catch (IOException e) { result = networkFailure(e); }
        }
        credentials.clear();
        return result;
    }

    private Result execute(String method, String path, JSONObject body, String token, String successMessage, boolean auth) {
        try {
            MembershipApiClient.Response response = api.request(method, path, body, token);
            return isSuccess(response.status) ? Result.ok(successMessage) : failure(response, auth);
        } catch (IOException e) { return networkFailure(e); }
    }

    private static boolean isSuccess(int status) { return status >= 200 && status < 300; }

    private Result failure(MembershipApiClient.Response response, boolean clearUnauthorized) {
        if (response.status == 401 && clearUnauthorized) {
            credentials.clear();
            return Result.fail("登录已失效，请重新登录", true);
        }
        if (response.status == 429) return Result.fail("操作太频繁，请稍后再试", false);
        try {
            JSONObject error = new JSONObject(response.body).optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (!message.isEmpty()) return Result.fail(message, response.status == 401);
        } catch (JSONException ignored) { }
        return Result.fail("请求失败（" + response.status + "），请重试", response.status == 401);
    }

    private static Result networkFailure(IOException error) {
        String message = error.getMessage();
        if (message != null && message.contains("尚未配置")) return Result.fail(message, false);
        return Result.fail("网络连接失败，请检查网络后重试", false);
    }
}
