package savage.essentials.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import savage.essentials.api.data.Location;
import savage.essentials.api.data.Warp;
import savage.essentials.core.EssentialsManager;
import savage.essentials.core.manager.WarpManager;
import savage.essentials.core.util.LocationUtil;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class WarpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /warp <name>
        dispatcher.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WarpCommand::suggestWarps)
                        .executes(WarpCommand::executeWarp)));

        // /setwarp <name>
        dispatcher.register(Commands.literal("setwarp")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(WarpCommand::executeSetWarp)));

        // /delwarp <name>
        dispatcher.register(Commands.literal("delwarp")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(WarpCommand::suggestWarps)
                        .executes(WarpCommand::executeDelWarp)));

        // /warps
        dispatcher.register(Commands.literal("warps")
                .executes(WarpCommand::executeListWarps));
    }

    private static CompletableFuture<Suggestions> suggestWarps(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        WarpManager manager = EssentialsManager.getInstance().getWarpManager();
        return SharedSuggestionProvider.suggest(manager.getWarps().stream().map(Warp::name), builder);
    }

    private static int executeWarp(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");
            WarpManager manager = EssentialsManager.getInstance().getWarpManager();
            Warp warp = manager.getWarp(name);

            if (warp == null) {
                context.getSource().sendFailure(Component.literal("Warp '" + name + "' not found!"));
                return 0;
            }

            EssentialsManager.getInstance().getTeleportManager().requestTeleport(player, warp.location());
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeSetWarp(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");
            WarpManager manager = EssentialsManager.getInstance().getWarpManager();

            Location loc = LocationUtil.fromPlayer(player);
            Warp warp = new Warp(name, loc, EssentialsManager.getInstance().getConfig().getServerId());
            
            manager.setWarp(warp).thenAccept(success -> {
                if (success) {
                    player.sendSystemMessage(Component.literal("Warp '" + name + "' set!"));
                } else {
                    // Collision occurred! Wait a second and try again with updated revision
                    manager.loadAll().thenRun(() -> {
                        Warp existing = manager.getWarp(name);
                        Warp retryWarp = (existing != null) ? warp.withIncrementedRevision() : warp;
                        manager.setWarp(retryWarp).thenAccept(retrySuccess -> {
                            if (retrySuccess) {
                                player.sendSystemMessage(Component.literal("Warp '" + name + "' set! (Resolved sync conflict)"));
                            } else {
                                player.sendSystemMessage(Component.literal("Failed to save warp: Database busy. Please try again."));
                            }
                        });
                    });
                }
            });

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDelWarp(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            WarpManager manager = EssentialsManager.getInstance().getWarpManager();

            if (manager.getWarp(name) == null) {
                context.getSource().sendFailure(Component.literal("Warp '" + name + "' not found!"));
                return 0;
            }

            manager.deleteWarp(name).thenAccept(success -> {
                if (success) {
                    context.getSource().sendSuccess(() -> Component.literal("Warp '" + name + "' deleted!"), true);
                } else {
                    context.getSource().sendFailure(Component.literal("Failed to delete warp '" + name + "': Database busy."));
                }
            });

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeListWarps(CommandContext<CommandSourceStack> context) {
        WarpManager manager = EssentialsManager.getInstance().getWarpManager();
        Collection<Warp> warps = manager.getWarps();

        if (warps.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No warps found."), false);
            return 1;
        }

        MutableComponent message = Component.literal("Available Warps: ").withStyle(ChatFormatting.YELLOW);
        
        boolean first = true;
        for (Warp warp : warps) {
            if (!first) {
                message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            
            message.append(Component.literal(warp.name())
                    .withStyle(style -> style
                            .withColor(ChatFormatting.WHITE)
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to teleport to " + warp.name())))
                            .withClickEvent(new ClickEvent.RunCommand("/warp " + warp.name()))));
            first = false;
        }

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
