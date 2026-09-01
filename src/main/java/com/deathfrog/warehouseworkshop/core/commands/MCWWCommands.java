package com.deathfrog.warehouseworkshop.core.commands;

import com.deathfrog.warehouseworkshop.WarehouseWorkshopMod;
import com.deathfrog.warehouseworkshop.api.util.TraceUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Registers Warehouse Workshop administrative and diagnostic commands. */
@EventBusSubscriber(modid = WarehouseWorkshopMod.MODID)
public final class MCWWCommands
{
    private static final String ARG_TRACE_KEY = "category";
    private static final String ARG_TRACE_SETTING = "enabled";

    /** Prevents command-registry construction. */
    private MCWWCommands()
    {
    }

    /** Registers the {@code /mcww trace <category> <enabled>} command tree. */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void registerCommands(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("mcww")
            .then(Commands.literal("trace")
                .requires(MCWWCommands::canUseTraceCommand)
                .then(Commands.argument(ARG_TRACE_KEY, StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(TraceUtils.getTraceKeys(), builder))
                    .then(Commands.argument(ARG_TRACE_SETTING, BoolArgumentType.bool())
                        .executes(context -> setTrace(context.getSource(),
                            StringArgumentType.getString(context, ARG_TRACE_KEY),
                            BoolArgumentType.getBool(context, ARG_TRACE_SETTING)))))));
    }

    /** Applies a validated trace setting and reports it to the command source. */
    @SuppressWarnings("null")
    private static int setTrace(final CommandSourceStack source, final String traceKey, final boolean enabled)
    {
        if (!TraceUtils.getTraceKeys().contains(traceKey))
        {
            source.sendFailure(Component.translatable("com.warehouseworkshop.command.trace.unknown", traceKey));
            return 0;
        }
        TraceUtils.setTrace(traceKey, enabled);
        source.sendSuccess(() -> Component.translatable("com.warehouseworkshop.command.trace.set", traceKey, enabled), true);
        return 1;
    }

    /** Allows server operators, integrated-server owners, and MineColonies operators to control tracing. */
    private static boolean canUseTraceCommand(final CommandSourceStack source)
    {
        if (source.hasPermission(4)) return true;
        if (!(source.getEntity() instanceof Player player)) return false;
        if (com.minecolonies.core.commands.commandTypes.IMCCommand.isPlayerOped(player)) return true;
        final GameProfile profile = player.getGameProfile();

        if (profile == null) return false;

        return source.getServer().isSingleplayerOwner(profile);
    }
}
