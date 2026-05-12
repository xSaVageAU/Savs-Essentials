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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.Permissions;
import savage.essentials.api.data.Profile;
import savage.essentials.core.EssentialsManager;
import savage.essentials.core.manager.ProfileManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class PlayerInfoCommand {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playerinfo")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PlayerInfoCommand::suggestPlayers)
                        .executes(PlayerInfoCommand::executeInfo)));
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Suggest online players names from our warm cache
        return SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder);
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();
        
        ProfileManager pm = EssentialsManager.getInstance().getProfileManager();
        Profile profile = pm.getProfileByName(playerName);

        if (profile == null) {
            source.sendFailure(Component.literal("No profile found for player '" + playerName + "'."));
            return 0;
        }

        MutableComponent message = Component.literal("\n--- Player Info: " + profile.getLastKnownName() + " ---").withStyle(ChatFormatting.GOLD)
                .append(line("Last Name", profile.getLastKnownName()))
                .append(line("Joined", DATE_FORMAT.format(new Date(profile.getJoinedDate()))))
                .append(line("Homes", String.valueOf(profile.getHomes().size())));

        if (!profile.getPreviousNames().isEmpty()) {
            message.append(line("Aliases", String.join(", ", profile.getPreviousNames())));
        }

        message.append(Component.literal("\n----------------------------").withStyle(ChatFormatting.GOLD));

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static MutableComponent line(String label, String value) {
        return Component.literal("\n")
                .append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
