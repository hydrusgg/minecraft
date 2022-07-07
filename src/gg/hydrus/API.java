package gg.hydrus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

public class API extends WebSocketClient {

    private final String token;
    private final Dispatcher dispatcher;
    private final JsonParser jsonParser = new JsonParser();

    // Websockets
    private boolean connected,invalidCredentials,alive;

    public API(String token, Dispatcher dispatcher) throws URISyntaxException {
        super(new URI("wss://rtc.hydrus.gg/"+token+"/plugin"));

        this.token = token;
        this.dispatcher = dispatcher;
        try {
            this.connect();

            CompletableFuture.runAsync(() -> {
                while (alive) {
                    Utils.sleep(60_000);
                    if (connected) {
                        this.sendPing();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void kill() {
        this.close();
        alive = false;
    }

    private void onHandshake(JsonObject payload) {
        if (payload.has("error")) {
            Utils.println("Unable to connect to the RTC: "+payload.get("error").getAsString());
            this.invalidCredentials = true;
        } else {
            this.connected = true;
            Utils.println("Connected to the RTC");
        }
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
            req.userAgent("Hydrus/MinecraftPlugin");
            req.contentType("application/json");

            try {
                req.send(body);
            } catch (Exception exception) {
                Utils.println("Failed to update command #"+id+", error: "+exception.getMessage());
                continue;
            }

            if (req.ok()) {
                break;
            } else {
                Utils.println("Failed to update command #"+id+", http status: "+req.code()+" (trying again in 10 seconds)");
                Utils.sleep(10_000);
            }
        }
    }

    private void onExecuteCommands(JsonArray payload) {
        payload.forEach(it -> this.onExecuteCommand(it.getAsJsonObject()));
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {}

    @Override
    public void onMessage(String raw) {
        final JsonObject parsed = this.jsonParser.parse(raw).getAsJsonObject();

        final String event = parsed.get("event").getAsString();
        final JsonElement payload = parsed.get("payload");

        switch (event) {
            case "HANDSHAKE": this.onHandshake(payload.getAsJsonObject()); break;
            case "EXECUTE_COMMAND": this.onExecuteCommand(payload.getAsJsonObject()); break;
            case "EXECUTE_COMMANDS": this.onExecuteCommands(payload.getAsJsonArray()); break;
            default: Utils.println("Unsupported event type: " + event);
        }
    }

    @Override
    public void onClose(int i, String s, boolean b) {
        if (!this.invalidCredentials) {
            if (this.connected) {
                Utils.println("Connection dropped, trying again in 5 seconds");
            } else {
                Utils.println("Failed to connect, trying again in 5 seconds");
            }
            CompletableFuture.runAsync(() -> {
                Utils.sleep(5000);
                this.reconnect();
            });
        }
        this.connected = false;
    }

    @Override
    public void onError(Exception e) {
        e.printStackTrace();
        if (!connected && !invalidCredentials) {
            Utils.println("Failed to connect for the first time, trying again in 5 seconds");
            CompletableFuture.runAsync(() -> {
                Utils.sleep(5000);
                this.reconnect();
            });
        }
    }
}
