package gg.hydrus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

public class API {

    private final String token;
    private final Dispatcher dispatcher;
    private boolean invalidCredentials, alive;
    private final JsonParser jsonParser = new JsonParser();

    public API(String token, Dispatcher dispatcher) throws URISyntaxException {
        this.token = token;
        this.dispatcher = dispatcher;
    }

    private String createSession(int tries) {
        if (tries < 0) {
            Utils.println("API#createSession failed 3 times in a row, next try will occur within 5 minutes");
            Utils.sleep(300_000);
            return this.createSession(3);
        }

        HttpRequest request = HttpRequest.post("https://rtc.hydrus.gg/poll");
        request.header("x-scope", "plugin");
        request.authorization(this.token).acceptJson();
        JsonObject body;

        int statusCode;

        try {
            statusCode = request.code();
        } catch (Exception exception) {
            return this.createSession(tries - 1);
        }

        try {
            body = this.jsonParser.parse(request.body()).getAsJsonObject();
        } catch (Exception e) {
            body = new JsonObject();
        }

        if (statusCode == 403) {
            this.invalidCredentials = true;
            Utils.println("'%s'", this.token);
            Utils.println("Invalid credentials: %s", body.get("message").getAsString());
            return null;
        }
        if (statusCode != 200) {
            Utils.println("Unexpected HTTP Status: %d", statusCode);
            return createSession(tries - 1);
        }

        JsonObject store = body.getAsJsonObject("store");
        Utils.println("Connected as %s [%s]", store.get("domain").getAsString(), store.get("name").getAsString());

        JsonArray messages = body.getAsJsonArray("messages");
        handleMessages(messages);

        return body.get("token").getAsString();
    }

    public void work() {
        String sessionId = this.createSession(3);

        this.alive = !invalidCredentials;

        while (alive) {
            try {
                HttpRequest request = HttpRequest.get("https://rtc.hydrus.gg/poll");
                request.header("x-session-id", sessionId);

                if (request.code() == 410) {
                    Utils.println("Disconnected (410)");
                    sessionId = createSession(3);
                } else if (request.code() == 200) {
                    JsonObject body;
                    try {
                        body = this.jsonParser.parse(request.body()).getAsJsonObject();
                    } catch (Exception e) {
                        continue;
                    }
                    handleMessages(body.getAsJsonArray("messages"));
                }
            } catch (HttpRequest.HttpRequestException exception) {
                Utils.println("An IO error occurred, sleeping for 30 seconds: %s", exception.getMessage());
                Utils.sleep(30_000);
                sessionId = this.createSession(3);
            }
        }
    }

    public void kill() {
        alive = false;
    }

    private void handleMessages(JsonArray messages) {
        messages.forEach(element ->
            CompletableFuture.runAsync(() ->
                onMessage(element.getAsString())
            )
        );
    }

    private void onExecuteCommand(JsonObject payload) {
        this.dispatcher.dispatch(payload.get("command").getAsString());

        final int id = payload.get("id").getAsInt();
        final String url = "https://api.hydrus.gg/plugin/v1/commands/"+id;
        final String body = "{\"status\":\"done\",\"message\":\"OK\"}";

        while (true) {
            final HttpRequest req = new HttpRequest(url, "POST");
            req.header("X-HTTP-Method-Override", "PATCH");
            req.authorization("Bearer "+this.token);
            req.contentType("application/json");

            try {
                req.send(body);
            } catch (Exception exception) {
                Utils.println("Failed to update command #%d, error: %s", id, exception.getMessage());
                continue;
            }

            if (req.ok()) {
                break;
            } else {
                Utils.println("Failed to update command #%d, http status: %d (trying again in 10 seconds)", id, req.code());
                Utils.sleep(10_000);
            }
        }
    }

    public void onMessage(String raw) {
        final JsonObject object = this.jsonParser.parse(raw).getAsJsonObject();

        final String event = object.get("event").getAsString();
        final JsonElement payload = object.get("payload");

        switch (event) {
            case "EXECUTE_COMMAND": this.onExecuteCommand(payload.getAsJsonObject()); break;
            default: Utils.println("Unsupported event type: " + event);
        }
    }

}
