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
                        .executes(HomeCommand::executeHome))
                .executes(context -> executeHome(context, "home")));

        // /homes
        dispatcher.register(Commands.literal("homes")
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
            profile.setHome(name, loc);
            
            // Save async
            EssentialsManager.getInstance().getProfileManager().save(player.getUUID());
            
            context.getSource().sendSuccess(() -> Component.literal("Home '" + name + "' set!"), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
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
            LocationUtil.teleport(player, loc);
            
            context.getSource().sendSuccess(() -> Component.literal("Teleported to home '" + name + "'"), false);
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
                
                message.append(Component.literal(homeName)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.WHITE)
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to teleport to " + homeName)))
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
}
