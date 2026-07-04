package com.example.addon.macro;

import com.example.addon.ExampleAddon;
import com.example.addon.mixin.ChatScreenAccessor;
import com.example.addon.mixin.CommandSuggestionsAccessor;
import autismclient.api.macro.ActionSchema;
import autismclient.api.macro.AddonContextAction;
import autismclient.api.macro.MacroExecutionContext;
import autismclient.util.macro.MacroValue;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ExtractUsernameAction extends AddonContextAction {
    public static final String TYPE_ID = ExampleAddon.ID + ":extract-username";

    public String source = "TABLIST";
    public String selectionMode = "RANDOM";
    public String prefix = ".";
    public String variableName = "value";

    private static int sequentialIndex = 0;

    public ExtractUsernameAction() {
        super(TYPE_ID);
    }

    private static synchronized int getNextSequentialIndex(int size) {
        int index = sequentialIndex % size;
        sequentialIndex = (sequentialIndex + 1) % size;
        return index;
    }

    @Override
    public void run(MacroExecutionContext ctx) throws InterruptedException {
        List<String> names = ctx.callOnClientThread(() -> {
            List<String> list = new ArrayList<>();
            if ("TABLIST".equals(source)) {
                if (ctx.mc().getConnection() != null) {
                    for (var player : ctx.mc().getConnection().getOnlinePlayers()) {
                        list.add(player.getProfile().name());
                    }
                }
            } else if ("AUTOFILL".equals(source)) {
                var screen = ctx.mc().gui.screen();
                if (screen instanceof ChatScreen chatScreen) {
                    var commandSuggestions = ((ChatScreenAccessor) chatScreen).getCommandSuggestions();
                    if (commandSuggestions != null) {
                        var pending = ((CommandSuggestionsAccessor) commandSuggestions).getPendingSuggestions();
                        if (pending != null) {
                            var suggestions = pending.getNow(null);
                            if (suggestions != null) {
                                for (var sug : suggestions.getList()) {
                                    list.add(sug.getText());
                                }
                            }
                        }
                    }
                }
            }
            return list;
        });

        if (names.isEmpty()) {
            ctx.setStatus("No names found to extract.");
            return;
        }

        String selectedName = null;
        if ("RANDOM".equals(selectionMode)) {
            Random rand = new Random();
            selectedName = names.get(rand.nextInt(names.size()));
        } else if ("PREFIX".equals(selectionMode)) {
            List<String> matching = new ArrayList<>();
            for (String name : names) {
                if (name.toLowerCase().startsWith(prefix.toLowerCase())) {
                    matching.add(name);
                }
            }
            if (!matching.isEmpty()) {
                selectedName = matching.get(0);
            }
        } else if ("SEQUENTIAL".equals(selectionMode)) {
            int index = getNextSequentialIndex(names.size());
            selectedName = names.get(index);
        }

        if (selectedName != null) {
            ctx.setVariable(variableName, MacroValue.text(selectedName));
            ctx.setStatus("Extracted username: " + selectedName);
        } else {
            ctx.setStatus("No matching username found.");
        }
    }

    @Override
    protected void save(CompoundTag tag) {
        putString(tag, "source", source);
        putString(tag, "selectionMode", selectionMode);
        putString(tag, "prefix", prefix);
        putString(tag, "variableName", variableName);
    }

    @Override
    protected void load(CompoundTag tag) {
        source = getString(tag, "source", source);
        selectionMode = getString(tag, "selectionMode", selectionMode);
        prefix = getString(tag, "prefix", prefix);
        variableName = getString(tag, "variableName", variableName);
    }

    @Override
    public ActionSchema schema() {
        return ActionSchema.builder()
            .enumField("source", "Source", "TABLIST", "AUTOFILL")
            .enumField("selectionMode", "Selection Mode", "RANDOM", "PREFIX", "SEQUENTIAL")
            .text("prefix", "Prefix").showWhenEnum("selectionMode", "PREFIX")
            .text("variableName", "Variable Name")
            .build();
    }

    @Override
    public String getDisplayName() {
        return "Extract Username (" + source + ") -> " + variableName;
    }

    @Override
    public String getIcon() {
        return "U";
    }
}
