package net.gtn.dimensionalpocket.client.commands;

import java.util.List;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Client command {@code dptweak} (alias {@code dimptweak}): changes the rendering options at runtime.
 *
 * <p>1.7.10 origin: {@code client/commands/RenderTweakCommand.java} (an {@code ICommand} that mutated
 * the annotated statics in {@code Reference}). In 1.20.1 the same options live in
 * {@link DPConfig} and are changed through their {@code ForgeConfigSpec} values.
 *
 * <p>Options: {@code colorblindMode <bool>}, {@code fancyRendering <0..2>},
 * {@code particlePlanes <1..50>}, {@code save}, {@code help}. The upstream
 * {@code useFieldShader} option is gone together with {@code Reference.USE_SHADER_FOR_PARTICLE_FIELD}
 * (the legacy GLSL particle field is not part of this port, see {@link DPConfig}'s class comment).
 */
public final class RenderTweakCommand {

    public static final String OPTION_COLORBLIND = "colorblindMode";
    public static final String OPTION_FANCY = "fancyRendering";
    public static final String OPTION_PARTICLE_PLANES = "particlePlanes";
    public static final String OPTION_SAVE = "save";
    public static final String OPTION_HELP = "help";

    private static final String VALUE_ARGUMENT = "value";

    /** Upstream order of {@code ALL_OPTIONS}; {@link #SHORT_DESCRIPTIONS} is index aligned. */
    public static final List<String> ALL_OPTIONS = List.of(
            OPTION_COLORBLIND,
            OPTION_FANCY,
            OPTION_PARTICLE_PLANES,
            OPTION_SAVE,
            OPTION_HELP);

    public static final List<String> SHORT_DESCRIPTIONS = List.of(
            "turn colorblind mode on/off",
            "force fancy rendering. 0-auto, 1-off, 2-on",
            "adjust the 'thickness' of the particle field",
            "saves the current render settings",
            "shows this help information");

    public static final String COMMAND_NAME = "dptweak";
    public static final String COMMAND_ALIAS = "dimptweak";

    private RenderTweakCommand() {
    }

    /** For logging. */
    public static String commandNames() {
        return COMMAND_NAME + "," + COMMAND_ALIAS;
    }

    /** 1.7.10: {@code getCommandUsage(sender)}. */
    public static Component usage() {
        return Component.literal("\u00a7lUsage: \u00a7rdptweak <\u00a7ooption\u00a7r> [\u00a7ovalue\u00a7r]");
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(COMMAND_NAME)
                // 1.7.10: canCommandSenderUseCommand() -> sender instanceof EntityPlayer
                .requires(source -> source.getEntity() instanceof Player)
                .executes(RenderTweakCommand::printHelp)
                .then(Commands.literal(OPTION_COLORBLIND)
                        .executes(context -> reply(context, OPTION_COLORBLIND, DPConfig.colorBlindMode()))
                        .then(Commands.argument(VALUE_ARGUMENT, BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean value = BoolArgumentType.getBool(context, VALUE_ARGUMENT);
                                    DPConfig.CFG.colorBlindMode.set(value);
                                    return reply(context, OPTION_COLORBLIND, value);
                                })))
                .then(Commands.literal(OPTION_FANCY)
                        .executes(context -> reply(context, OPTION_FANCY, DPConfig.CFG.forceFancyRendering.get()))
                        .then(Commands.argument(VALUE_ARGUMENT, IntegerArgumentType.integer(0, 2))
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, VALUE_ARGUMENT);
                                    DPConfig.CFG.forceFancyRendering.set(value);
                                    return reply(context, OPTION_FANCY, value);
                                })))
                .then(Commands.literal(OPTION_PARTICLE_PLANES)
                        .executes(context -> reply(context, OPTION_PARTICLE_PLANES, DPConfig.CFG.numberOfParticlePlanes()))
                        .then(Commands.argument(VALUE_ARGUMENT, IntegerArgumentType.integer(1, 50))
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, VALUE_ARGUMENT);
                                    DPConfig.CFG.numberOfParticlePlanes.set(value);
                                    return reply(context, OPTION_PARTICLE_PLANES, value);
                                })))
                .then(Commands.literal(OPTION_SAVE).executes(RenderTweakCommand::save))
                .then(Commands.literal(OPTION_HELP).executes(RenderTweakCommand::printHelp));

        dispatcher.register(root);
        dispatcher.register(Commands.literal(COMMAND_ALIAS).redirect(dispatcher.getRoot().getChild(COMMAND_NAME)));
    }

    private static int reply(CommandContext<CommandSourceStack> context, String option, Object value) {
        context.getSource().sendSuccess(() -> Component.literal(option + " set to: " + value), false);
        ModLog.step("RenderTweak", "option", "option={} value={} side=CLIENT result=OK", option, value);
        return 1;
    }

    /**
     * 1.7.10 wrote the config file immediately ({@code ConfigHandler.save(MOD_ID)}). 1.20.1 config
     * values are persisted by Forge when it saves the config (on world unload / game exit), so this
     * option reports what actually happened instead of claiming a file write.
     */
    private static int save(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "Graphics settings applied - Forge writes them to the DP config file on config save."), false);
        ModLog.step("RenderTweak", "save",
                "colorBlind={} forceFancy={} particlePlanes={} note=forge_persists_on_config_save result=OK",
                DPConfig.colorBlindMode(), DPConfig.CFG.forceFancyRendering.get(),
                DPConfig.CFG.numberOfParticlePlanes());
        return 1;
    }

    /** 1.7.10: the no-args / {@code help} branch - blank line, usage, then every option + description. */
    private static int printHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(RenderTweakCommand::usage, false);
        source.sendSuccess(() -> Component.literal("Valid options: "), false);
        for (int i = 0; i < ALL_OPTIONS.size(); i++) {
            String option = "  " + ALL_OPTIONS.get(i);
            String description = "    " + SHORT_DESCRIPTIONS.get(i);
            source.sendSuccess(() -> Component.literal(option).withStyle(ChatFormatting.GOLD), false);
            source.sendSuccess(() -> Component.literal(description)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        }
        return 1;
    }
}
