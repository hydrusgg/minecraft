package gg.hydrus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class API {

    private final String token;
    private final Dispatcher dispatcher;
    private boolean alive;

    public API(String token, Dispatcher dispatcher) throws URISyntaxException {
        this.token = token;
        this.dispatcher = dispatcher;
    }

    private String getBearerToken() {
        return "Bearer "+this.token;
    }

    public void boot() {
        HttpRequest request = HttpRequest.get("https://api.hydrus.gg/plugin/v1/@me");
        request.authorization(this.getBearerToken());
        request.userAgent("Plugin/Minecraft");

        int code = request.code();

        if (code != 200) {
            Utils.println("Failed to authenticate. HTTP %d", code);
            return;
        }

        JSONObject store = new JSONObject(request.body());
        Utils.println("Authenticated as %s", store.getString("domain"));
        this.work();
    }

    public void work() {
        if (this.alive) {
            return;
        }
        this.alive = true;

        while (alive) {
            try {
                HttpRequest request = HttpRequest.get("https://api.hydrus.gg/plugin/v1/commands/plugin");
                request.authorization(this.getBearerToken());
                request.userAgent("Plugin/Minecraft");

                int code = request.code();

                if (code == 429) {
                    Utils.println("Too many requests (%d)", code);
                    Utils.println("The plugin will sleep for 120 seconds");
                    Utils.sleep(120_000);
                    continue;
                }

                if (code >= 400 && code < 429) {
                    Utils.println("Unauthorized (%d)", code);
                    this.kill();
                    continue;
                }

                if (code >= 500) {
                    Utils.println("Server Error (%d)", code);
                    Utils.sleep(10_000);
                    continue;
                }

                try {
                    JSONObject body = new JSONObject(request.body());
                    JSONArray commands = body.getJSONArray("data");

                    commands.forEach(command -> onExecuteCommand((JSONObject) command));
                } catch (Exception e) {
                    Utils.println("Failed to parse JSON Response: %s", e.getMessage());
                }

                Utils.sleep(60_000);
            } catch (HttpRequest.HttpRequestException exception) {
                Utils.println("An IO error occurred, sleeping for 30 seconds: %s", exception.getMessage());
                Utils.sleep(30_000);
            }
        }
    }

    public void kill() {
        alive = false;
    }

    private void onExecuteCommand(JSONObject payload) {
        this.dispatcher.dispatch(payload.getString("command"));

        final int id = payload.getInt("id");
        final String url = "https://api.hydrus.gg/plugin/v1/commands/"+id;
        final String body = "{\"status\":\"done\",\"message\":\"OK\"}";

        while (this.alive) {
            final HttpRequest req = new HttpRequest(url, "POST");
            req.header("X-HTTP-Method-Override", "PATCH");
            req.userAgent("Plugin/Minecraft");
            req.authorization(this.getBearerToken());
            req.contentType("application/json");

            try {
                req.send(body);
            } catch (Exception exception) {
                Utils.println("Failed to update command #%d, error: %s", id, exception.getMessage());
                continue;
            }

            if (req.ok()) {
                break;
            }
            Utils.println("Failed to update command #%d, http status: %d (trying again in 10 seconds)", id, req.code());
            Utils.println(req.body());
            Utils.sleep(10_000);
        }
    }

}
