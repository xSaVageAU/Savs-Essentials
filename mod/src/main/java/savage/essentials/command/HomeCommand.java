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
import savage.essentials.core.EssentialsManager;
import savage.essentials.core.util.LocationUtil;
import savage.essentials.api.data.Location;
import savage.essentials.api.data.Profile;
import net.minecraft.server.permissions.Permissions;

import java.util.concurrent.CompletableFuture;

public class HomeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /sethome [name]
        dispatcher.register(Commands.literal("sethome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(HomeCommand::executeSetHome))
                .executes(context -> executeSetHome(context, "home")));

        // /home [name]
        dispatcher.register(Commands.literal("home")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(HomeCommand::suggestHomes)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                                .executes(context -> executeHomeOther(context, StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "player"))))
                        .executes(HomeCommand::executeHome))
                .executes(context -> executeHome(context, "home")));

        // /homes
        dispatcher.register(Commands.literal("homes")
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> executeListHomesOther(context, StringArgumentType.getString(context, "player"))))
                .executes(HomeCommand::executeListHomes));
    }

    private static CompletableFuture<Suggestions> suggestHomes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Profile profile = EssentialsManager.getInstance().getProfileManager().getProfile(player.getUUID());
            if (profile != null) {
                return SharedSuggestionProvider.suggest(profile.getHomes().keySet(), builder);
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    private static int executeSetHome(CommandContext<CommandSourceStack> context) {
        return executeSetHome(context, StringArgumentType.getString(context, "name"));
    }

    private static int executeSetHome(CommandContext<CommandSourceStack> context, String name) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Profile profile = EssentialsManager.getInstance().getProfileManager().getProfile(player.getUUID());
            
            if (profile == null) {
                context.getSource().sendFailure(Component.literal("Failed to load your profile!"));
                return 0;
            }

            Location loc = LocationUtil.fromPlayer(player);
            
            // Check home limits
            int maxHomes = EssentialsManager.getInstance().getConfig().getDefaultMaxHomes();
            if (!profile.getHomes().containsKey(name.toLowerCase()) && profile.getHomes().size() >= maxHomes) {
                context.getSource().sendFailure(Component.literal("You have reached your limit of " + maxHomes + " homes!"));
                return 0;
            }

            profile.setHome(name, loc, EssentialsManager.getInstance().getConfig().getServerId());
            saveHomeWithRetry(player, name, loc, 3);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static void saveHomeWithRetry(ServerPlayer player, String homeName, Location loc, int retriesLeft) {
        EssentialsManager.getInstance().getProfileManager().save(player.getUUID()).thenAccept(success -> {
            if (success) {
                if (retriesLeft < 3) {
                    player.sendSystemMessage(Component.literal("Home '" + homeName + "' set! (Resolved sync conflict)"));
                } else {
                    player.sendSystemMessage(Component.literal("Home '" + homeName + "' set!"));
                }
            } else if (retriesLeft > 0) {
                EssentialsManager.getInstance().getProfileManager().load(player.getUUID(), player.getScoreboardName()).thenAccept(newProfile -> {
                    // Re-validate limits against the fresh data
                    int maxHomes = EssentialsManager.getInstance().getConfig().getDefaultMaxHomes();
                    if (!newProfile.getHomes().containsKey(homeName.toLowerCase()) && newProfile.getHomes().size() >= maxHomes) {
                        player.sendSystemMessage(Component.literal("Failed to save home: You reached your limit of " + maxHomes + " homes."));
                        return;
                    }

                    newProfile.setHome(homeName, loc, EssentialsManager.getInstance().getConfig().getServerId());
                    saveHomeWithRetry(player, homeName, loc, retriesLeft - 1);
                });
            } else {
                player.sendSystemMessage(Component.literal("Failed to save home: Database busy after max retries."));
            }
        });
    }

    private static int executeHome(CommandContext<CommandSourceStack> context) {
        return executeHome(context, StringArgumentType.getString(context, "name"));
    }

    private static int executeHome(CommandContext<CommandSourceStack> context, String name) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Profile profile = EssentialsManager.getInstance().getProfileManager().getProfile(player.getUUID());
            
            if (profile == null || !profile.getHomes().containsKey(name.toLowerCase())) {
                context.getSource().sendFailure(Component.literal("Home '" + name + "' not found!"));
                return 0;
            }

            Location loc = profile.getHomes().get(name.toLowerCase()).location();
            EssentialsManager.getInstance().getTeleportManager().requestTeleport(player, loc);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeHomeOther(CommandContext<CommandSourceStack> context, String name, String targetPlayer) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Profile profile = EssentialsManager.getInstance().getProfileManager().getProfileByName(targetPlayer);
            
            if (profile == null) {
                context.getSource().sendFailure(Component.literal("Player '" + targetPlayer + "' not found!"));
                return 0;
            }

            if (!profile.getHomes().containsKey(name.toLowerCase())) {
                context.getSource().sendFailure(Component.literal("Player '" + targetPlayer + "' does not have a home named '" + name + "'!"));
                return 0;
            }

            Location loc = profile.getHomes().get(name.toLowerCase()).location();
            EssentialsManager.getInstance().getTeleportManager().requestTeleport(player, loc);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeListHomes(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Profile profile = EssentialsManager.getInstance().getProfileManager().getProfile(player.getUUID());

            if (profile == null || profile.getHomes().isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("You have no homes set."), false);
                return 1;
            }

            MutableComponent message = Component.literal("Your Homes: ").withStyle(ChatFormatting.GOLD);
            
            boolean first = true;
            for (String homeName : profile.getHomes().keySet()) {
                if (!first) {
                    message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                
                savage.essentials.api.data.Home homeData = profile.getHomes().get(homeName);
                MutableComponent hoverText = Component.literal("Click to teleport to ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(homeName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("\nServer: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(homeData.serverId()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\nDimension: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(homeData.location().dimension()).withStyle(ChatFormatting.GOLD));

                message.append(Component.literal(homeName)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.WHITE)
                                .withHoverEvent(new HoverEvent.ShowText(hoverText))
                                .withClickEvent(new ClickEvent.RunCommand("/home " + homeName))));
                first = false;
            }

            context.getSource().sendSuccess(() -> message, false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeListHomesOther(CommandContext<CommandSourceStack> context, String targetPlayer) {
        try {
            Profile profile = EssentialsManager.getInstance().getProfileManager().getProfileByName(targetPlayer);

            if (profile == null) {
                context.getSource().sendFailure(Component.literal("Player '" + targetPlayer + "' not found."));
                return 0;
            }

            if (profile.getHomes().isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("Player '" + targetPlayer + "' has no homes set."), false);
                return 1;
            }

            MutableComponent message = Component.literal(targetPlayer + "'s Homes: ").withStyle(ChatFormatting.GOLD);
            
            boolean first = true;
            for (String homeName : profile.getHomes().keySet()) {
                if (!first) {
                    message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                
                savage.essentials.api.data.Home homeData = profile.getHomes().get(homeName);
                MutableComponent hoverText = Component.literal("Click to teleport to ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(targetPlayer + "'s " + homeName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("\nServer: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(homeData.serverId()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\nDimension: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(homeData.location().dimension()).withStyle(ChatFormatting.GOLD));

                message.append(Component.literal(homeName)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.WHITE)
                                .withHoverEvent(new HoverEvent.ShowText(hoverText))
                                .withClickEvent(new ClickEvent.RunCommand("/home " + homeName + " " + targetPlayer))));
                first = false;
            }

            context.getSource().sendSuccess(() -> message, false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
