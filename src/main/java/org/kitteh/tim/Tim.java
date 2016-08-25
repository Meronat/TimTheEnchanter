/*
 * * Copyright (C) 2016 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.tim;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.meta.ItemEnchantment;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.item.Enchantment;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * I... am an enchanter.
 */
@Plugin(id = "tim", name = "Tim the Enchanter", version = "4.0.3-for-4.0")
public class Tim {
    public static final String COMMAND_ARG_ENCHANTMENT = "enchantment";
    public static final String COMMAND_ARG_LEVEL = "level";

    public static final String PERMISSION_ENCHANT = "enchanter.enchant";

    public static final List<String> QUOTES = ImmutableList.of(
            "Behold the cave of Caerbannog!",
            "That's no ordinary rabbit.\n" +
                    "That's the most foul, cruel,\n" +
                    "and bad tempered rodent\n" +
                    "you ever set eyes on.",
            "Look, that rabbit's got a vicious\n" +
                    "streak a mile wide, it's a killer!",
            "I warned you, I warned you but did you\n" +
                    "listen to me? Oh no, you know, didn't you?\n" +
                    "It's just a harmless little bunny, isn't it?\n" +
                    "Well it's always the same, I always tell them ...\n" +
                    "do they listen to me?"
    );

    private int quoteCount = 0;
    private Map<String, Enchantment> enchantments;

    @Inject
    private Game game;

    @Inject
    private Logger logger;

    @Listener
    public void onGameStarting(GamePostInitializationEvent event) {
        Function<ImmutableMap.Builder<String, Enchantment>, ImmutableMap<String, Enchantment>> finisher = ImmutableMap.Builder::build; // Compiler doesn't seem to like putting this in the collector.
        this.enchantments = Collections.unmodifiableMap(
                this.game.getRegistry().getAllOf(Enchantment.class).stream()
                        .collect(Collector.of(
                                ImmutableMap.Builder::new,
                                (builder, enchantment) -> builder.put(enchantment.getName(), enchantment),
                                (left, right) -> left.putAll(right.build()),
                                finisher)));
    }

    @Listener
    public void onGameServerStarting(GameStartingServerEvent event) {
        CommandSpec enchantAllCommandSpec = CommandSpec.builder()
                .arguments(GenericArguments.optional(GenericArguments.string(Text.of(COMMAND_ARG_LEVEL))))
                .executor(this::commandEnchantAll).build();
        CommandSpec enchantCommandSpec = CommandSpec.builder()
                .permission(PERMISSION_ENCHANT)
                .arguments(GenericArguments.choices(Text.of(COMMAND_ARG_ENCHANTMENT), this.enchantments), GenericArguments.optional(GenericArguments.string(Text.of(COMMAND_ARG_LEVEL))))
                .child(enchantAllCommandSpec, "all")
                .executor(this::commandEnchant).build();
        this.game.getCommandManager().register(this, enchantCommandSpec, "enchant");
        this.logger.info("There are some who call me... Tim?");
    }

    private CommandResult commandEnchant(CommandSource commandSource, CommandContext commandContext) throws CommandException {
        Enchantment enchantment = commandContext.<Enchantment>getOne(COMMAND_ARG_ENCHANTMENT).get();
        int level = this.getEnchantmentLevel(commandContext);
        this.enchant(this.getPlayer(commandSource), enchantment, getLevelForEnchantment(enchantment, level));
        return this.getSuccess(commandSource);
    }

    private CommandResult commandEnchantAll(CommandSource commandSource, CommandContext commandContext) throws CommandException {
        Player player = this.getPlayer(commandSource);
        int level = this.getEnchantmentLevel(commandContext);
        for (Enchantment enchantment : this.enchantments.values()) {
            this.enchant(player, enchantment, getLevelForEnchantment(enchantment, level));
        }
        return this.getSuccess(commandSource);
    }

    private int getEnchantmentLevel(CommandContext commandContext) throws CommandException {
        String levelString = commandContext.<String>getOne(COMMAND_ARG_LEVEL).orElse("1");
        int level;
        if (levelString.equals("max")) {
            return -1;
        } else {
            try {
                level = Integer.parseInt(levelString);
                if (level < 1) {
                    throw new CommandException(this.getErrorText("Enchantment level has to be greater than 0"));
                } else if (level > Short.MAX_VALUE) {
                    throw new CommandException(this.getErrorText("Enchantment level can't be higher than " + Short.MAX_VALUE));
                }
                return level;
            } catch (NumberFormatException e) {
                throw new CommandException(this.getErrorText("Enchantment level has to be a number (or \"max\")"));
            }
        }
    }

    private int getLevelForEnchantment(Enchantment enchantment, int level) {
        return level > 0 ? level : enchantment.getMaximumLevel();
    }

    private Player getPlayer(CommandSource commandSource) throws CommandException {
        if (commandSource instanceof Player) {
            return (Player) commandSource;
        }
        throw new CommandException(this.getErrorText("Only a player can enchant."));
    }

    private void enchant(Player player, Enchantment enchantment, int level) throws CommandException {
        ItemStack item = player.getItemInHand().orElseThrow(() -> new CommandException(this.getErrorText("You need to be holding an item to enchant it!")));
        item.transform(Keys.ITEM_ENCHANTMENTS, list -> {
            List<ItemEnchantment> newList = new LinkedList<>();
            if (list != null) {
                list.stream().filter(ench -> ench.getEnchantment() != enchantment).forEach(newList::add);
            }
            newList.add(new ItemEnchantment(enchantment, level));
            return newList;
        });
        player.setItemInHand(item);
    }

    private Text getErrorText(String error) {
        return Text.builder(error).color(TextColors.RED).onHover(TextActions.showText(Text.of(QUOTES.get(this.quoteCount++ % QUOTES.size())))).build();
    }

    private CommandResult getSuccess(CommandSource commandSource) {
        commandSource.sendMessage(Text.of(TextColors.YELLOW, "Item enchanted. I... am an enchanter."));
        return CommandResult.builder().affectedItems(1).build();
    }
}
