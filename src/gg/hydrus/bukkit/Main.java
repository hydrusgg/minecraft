package gg.hydrus.bukkit;

import gg.hydrus.API;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URISyntaxException;

public class Main extends JavaPlugin {

    private API api;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        final String token = this.getConfig().getString("token");

        try {
            this.api = new API(token, command -> {
                Bukkit.getScheduler().runTask(Main.this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                });
            });

            Bukkit.getScheduler().runTaskAsynchronously(Main.this, api::work);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        this.api.kill();
    }
}
