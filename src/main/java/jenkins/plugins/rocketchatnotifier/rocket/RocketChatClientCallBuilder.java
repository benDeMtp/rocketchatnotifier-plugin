package jenkins.plugins.rocketchatnotifier.rocket;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import jenkins.plugins.rocketchatnotifier.model.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map.Entry;

/**
 * The call builder for the {@link RocketChatClient} and is only supposed to be used internally.
 *
 * @author Bradley Hilton (graywolf336)
 * @version 0.0.1
 * @since 0.1.0
 */
public class RocketChatClientCallBuilder {
  private final ObjectMapper objectMapper;
  private final String serverUrl;
  private final String user;
  private final String password;
  private String authToken;
  private String userId;

  protected RocketChatClientCallBuilder(String serverUrl, String user, String password) {
    if (!serverUrl.endsWith("api/")) {
      this.serverUrl = serverUrl + (serverUrl.endsWith("/") ? "" : "/") + "api/";
    } else {
      this.serverUrl = serverUrl;
    }

    this.user = user;
    this.password = password;
    this.authToken = "";
    this.userId = "";
    this.objectMapper = new ObjectMapper();
    this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  protected Response buildCall(RocketChatRestApiV1 call) throws IOException {
    return this.buildCall(call, null, null);
  }

  protected Response buildCall(RocketChatRestApiV1 call, RocketChatQueryParams queryParams) throws IOException {
    return this.buildCall(call, queryParams, null);
  }

  protected Response buildCall(RocketChatRestApiV1 call, RocketChatQueryParams queryParams, Object body) throws IOException {
    if (call.requiresAuth() && (authToken.isEmpty() || userId.isEmpty())) {
      login();
    }

    switch (call.getHttpMethod()) {
      case GET:
        return this.buildGetCall(call, queryParams);
      case POST:
        return this.buildPostCall(call, queryParams, body);
      default:
        throw new IOException("Http Method " + call.getHttpMethod().toString() + " is not supported.");
    }
  }

  private void login() throws IOException {
    HttpResponse<JsonNode> loginResult;

    try {
      loginResult = Unirest.post(serverUrl + "v1/login").field("user", user).field("password", password).asJson();
    } catch (UnirestException e) {
      throw new IOException(e);
    } catch (Exception e) {
      throw new IOException(e);
    }

    if (loginResult.getStatus() == 401)
      throw new IOException("The username and password provided are incorrect.");

    if (loginResult.getStatus() != 200)
      throw new IOException("The login failed with a result of: " + loginResult.getStatus());

    JSONObject data = loginResult.getBody().getObject().getJSONObject("data");
    this.authToken = data.getString("authToken");
    this.userId = data.getString("userId");
  }

  private Response buildGetCall(RocketChatRestApiV1 call, RocketChatQueryParams queryParams) throws IOException {
    GetRequest req = Unirest.get(serverUrl + call.getMethodName());

    if (call.requiresAuth()) {
      req.header("X-Auth-Token", authToken);
      req.header("X-User-Id", userId);
    }

    if (queryParams != null && !queryParams.isEmpty()) {
      for (Entry<? extends String, ? extends String> e : queryParams.get().entrySet()) {
        req.queryString(e.getKey(), e.getValue());
      }
    }

    try {
      HttpResponse<String> res = req.asString();

      return objectMapper.readValue(res.getBody(), Response.class);
    } catch (UnirestException e) {
      throw new IOException(e);
    }
  }

  private Response buildPostCall(RocketChatRestApiV1 call, RocketChatQueryParams queryParams, Object body) throws IOException {
    HttpRequestWithBody req = Unirest.post(serverUrl + call.getMethodName()).header("Content-Type", "application/json");

    if (call.requiresAuth()) {
      req.header("X-Auth-Token", authToken);
      req.header("X-User-Id", userId);
    }

    if (queryParams != null && !queryParams.isEmpty()) {
      for (Entry<? extends String, ? extends String> e : queryParams.get().entrySet()) {
        req.queryString(e.getKey(), e.getValue());
      }
    }

    if (body != null) {
      req.body(objectMapper.writeValueAsString(body));
    }

    try {
      HttpResponse<String> res = req.asString();

      return objectMapper.readValue(res.getBody(), Response.class);
    } catch (UnirestException e) {
      throw new IOException(e);
    }
  }
}
