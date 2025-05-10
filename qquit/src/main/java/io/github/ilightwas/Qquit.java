package io.github.ilightwas;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

@Plugin(id = "qquit", name = "Qquit", version = "1.0.0-SNAPSHOT", url = "https://github.com/ilightwas/mcrndplugins", description = "Q for quit", authors = {
        "ilightwas" })
public class Qquit {

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public Qquit(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        QuitCmd quitCmd = new QuitCmd();
        proxy.getCommandManager().register(quitCmd.commandMeta(), quitCmd);
        logger.info("Qquit enabled");
    }

    private final class QuitCmd implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            invocation.source().sendMessage(Component.text("Bye Bye"));
            proxy.shutdown();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source() instanceof ConsoleCommandSource;
        }

        public CommandMeta commandMeta() {
            return new CommandMeta() {

                @Override
                public Collection<String> getAliases() {
                    return List.of("q");
                }

                @Override
                public Collection<CommandNode<CommandSource>> getHints() {
                    return Collections.emptyList();
                }

                @Override
                public @Nullable Object getPlugin() {
                    return Qquit.this;
                }

            };
        }

    }
}
