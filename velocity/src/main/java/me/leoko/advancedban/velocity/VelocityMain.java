package me.leoko.advancedban.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.velocity.listener.ChatListenerVelocity;
import me.leoko.advancedban.velocity.listener.ConnectionListenerVelocity;
import me.leoko.advancedban.velocity.listener.InternalListenerVelocity;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Velocity plugin entry point. The {@code @Plugin} annotation processor in {@code velocity-api}
 * generates {@code velocity-plugin.json} at compile time so no manual descriptor file is needed.
 */
@Plugin(
        id = "advancedban",
        name = "AdvancedBan",
        version = "2.4.0",
        description = "All-in-one punishment system. Fork maintained by Leon (Grafkox_LP), based on the original AdvancedBan by Leoko.",
        url = "https://github.com/Grafkox-LP/AdvancedBan-Velocity",
        authors = {"Leoko (original)", "Leon (Grafkox_LP) - maintainer"},
        dependencies = {
                @Dependency(id = "luckperms", optional = true)
        }
)
public class VelocityMain {

    private static VelocityMain instance;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer container;
    private final Metrics.Factory metricsFactory;

    @Inject
    public VelocityMain(ProxyServer server,
                        Logger logger,
                        @DataDirectory Path dataDirectory,
                        PluginContainer container,
                        Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.container = container;
        this.metricsFactory = metricsFactory;
        instance = this;
    }

    public static VelocityMain get() {
        return instance;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        Universal.get().setup(new VelocityMethods(this));

        InternalListenerVelocity internalListener = new InternalListenerVelocity(server);
        server.getEventManager().register(this, new ConnectionListenerVelocity());
        server.getEventManager().register(this, new ChatListenerVelocity());
        server.getEventManager().register(this, internalListener);
        server.getChannelRegistrar().register(InternalListenerVelocity.CHANNEL);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Universal.get().shutdown();
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public PluginContainer getContainer() {
        return container;
    }

    public Metrics.Factory getMetricsFactory() {
        return metricsFactory;
    }
}
