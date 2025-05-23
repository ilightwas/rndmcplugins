package io.github.ilightwas.proxysrv;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;

@Plugin(id = ProxySRV.PLUGIN_ID, name = ProxySRV.PLUGIN_NAME, version = "1.0.0-SNAPSHOT", url = "https://github.com/ilightwas/mcrndplugins", description = "Player proxy join/quit messages for discordSRV", authors = {
        "ilightwas" })

public class ProxySRV {

    public static final String PLUGIN_ID = "proxysrv";
    public static final String PLUGIN_NAME = "proxySRV";
    public static final String CHANNEL = "proxysrv:main";
    public static final MinecraftChannelIdentifier CHANNEL_ID = MinecraftChannelIdentifier.from(CHANNEL);

    private static final Base64 base64 = new Base64();

    private final ProxyServer proxy;
    private final Logger logger;
    private static YamlDocument config;

    private final List<RegisteredServer> filteredServers = new ArrayList<>();

    public enum ProxyEvent {
        JOIN((byte) 1),
        QUIT((byte) 2);

        private final byte opcode;

        ProxyEvent(byte opcode) {
            this.opcode = opcode;
        }

        byte opcode() {
            return opcode;
        }

        public static ProxyEvent fromOpcode(byte opcode) {
            for (ProxyEvent p : values()) {
                if (p.opcode == opcode)
                    return p;
            }
            throw new IllegalArgumentException("Unknown ProxyEvent with opcode: " + opcode);
        }
    }

    public static final class EventData {
        public ProxyEvent proxyEvent;
        public String userName;
        public long uuidMSB;
        public long uuidLSB;
        public byte[] texture;

        public EventData() {
        }
    }

    @Inject
    public ProxySRV(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;

        try {
            config = YamlDocument.create(new File(dataDirectory.toFile(), "config.yml"),
                    getClass().getResourceAsStream("/config.yml"), GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version"))
                            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS).build());
        } catch (IOException ex) {
            logger.error("Could not load config file", ex);
            proxy.getPluginManager().getPlugin(PLUGIN_ID).ifPresent(container -> {
                container.getExecutorService().shutdown();
            });
        }

        if (config.getBoolean("debug")) {
            try {
                // Some reflection shenanigans
                Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
                Method setLevel = Class.forName("org.apache.logging.log4j.core.config.Configurator").getMethod(
                        "setLevel",
                        String.class, levelClass);

                setLevel.invoke(null, PLUGIN_ID, levelClass.getField("DEBUG").get(null));
            } catch (ReflectiveOperationException e) {
                logger.warn("while changing log level", e);
            }
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        final Set<String> disabledServers = new HashSet<>(config.getStringList("disabled-servers"));

        // I know Venkat, no side effects but just this time..
        proxy.getAllServers().stream().filter(s -> !disabledServers.contains(s.getServerInfo().getName()))
                .forEach(server -> filteredServers.add(server));

        logger.info("disabled-servers: {} ", disabledServers.toString());
        proxy.getChannelRegistrar().register(CHANNEL_ID);
        logger.info("proxySRV enabled");
    }

    @Subscribe
    public void onJoin(final ServerConnectedEvent event) {
        if (event.getPreviousServer().isPresent())
            return;

        messageDiscordSRV(prepareData(event.getPlayer()), ProxyEvent.JOIN);
    }

    @Subscribe
    public void onQuit(final DisconnectEvent event) {
        messageDiscordSRV(prepareData(event.getPlayer()), ProxyEvent.QUIT);
    }

    private static EventData prepareData(final Player player) {
        final EventData data = new EventData();
        final UUID uuid = player.getUniqueId();
        data.userName = player.getUsername();
        data.uuidMSB = uuid.getMostSignificantBits();
        data.uuidLSB = uuid.getLeastSignificantBits();
        data.texture = getTexture(player);
        return data;
    }

    public void messageDiscordSRV(final EventData data, ProxyEvent proxyEvent) {
        final ByteArrayDataOutput byteArr = ByteStreams.newDataOutput();
        byteArr.writeByte(proxyEvent.opcode());
        byteArr.writeUTF(data.userName);
        byteArr.writeLong(data.uuidMSB);
        byteArr.writeLong(data.uuidLSB);
        byteArr.writeInt(data.texture.length);
        byteArr.write(data.texture);

        proxy.getScheduler().buildTask(this, () -> {
            for (RegisteredServer server : filteredServers) {
                logger.debug("Trying to send message to server \"{}\"", server.getServerInfo().getName());
                if (server.sendPluginMessage(CHANNEL_ID, byteArr.toByteArray())) {
                    return; // sent to one of the servers
                }
                logger.debug("Could not send plugin message to server \"{}\"", server.getServerInfo().getName());
            }
            logger.debug("No server to receive a message with opcode: {}", proxyEvent.opcode());
        }).schedule();
    }

    private static byte[] getTexture(Player player) {
        return player.getGameProfile().getProperties().stream()
                .filter(prop -> prop.getName().equals("textures"))
                .map(prop -> base64.decode(prop.getValue()))
                .findFirst().orElse(null);
    }
}
