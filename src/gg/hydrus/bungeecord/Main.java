package gg.hydrus.bungeecord;

import gg.hydrus.API;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;

public class Main extends Plugin {

    private API api;
    private Configuration config;

    @Override
    public void onLoad() {
        final File folder = getDataFolder();

        folder.mkdirs();

        final File file = new File(folder, "config.yml");

        if (!file.exists()) {
            try {
                final InputStream stream = getResourceAsStream("config.yml");
                Files.copy(stream, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        final String token = this.config.getString("token");

        try {
            this.api = new API(token, command -> {
                final BungeeCord bungee = BungeeCord.getInstance();
                bungee.pluginManager.dispatchCommand(bungee.getConsole(), command);
            });
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        this.api.kill();
    }
}
