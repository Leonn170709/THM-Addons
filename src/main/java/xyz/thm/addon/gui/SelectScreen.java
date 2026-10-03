/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reusable searchable single-select picker screen (like PaketLimiter's packet list, but one pick).
 * Open with {@code mc.gui.setScreen(...)}; the callback fires with the chosen option and the screen closes.
 *
 * <pre>{@code
 * mc.gui.setScreen(SelectScreen.of(theme, "Select Packet", names, name -> { selected = name; refresh(); }));
 * mc.gui.setScreen(new SelectScreen<>(theme, "Select Home", homes, Home::name, home -> ...));
 * }</pre>
 */
public class SelectScreen<T> extends WindowScreen {
    private final List<T> options;
    private final Function<T, String> nameFn;
    private final Consumer<T> onSelect;

    private WTextBox search;
    private WVerticalList listWidget;

    public SelectScreen(GuiTheme theme, String title, Collection<T> options, Function<T, String> nameFn, Consumer<T> onSelect) {
        super(theme, title);
        this.options = new ArrayList<>(options);
        this.nameFn = nameFn;
        this.onSelect = onSelect;
    }

    /** Convenience for a plain list of strings. */
    public static SelectScreen<String> of(GuiTheme theme, String title, String[] options, Consumer<String> onSelect) {
        return new SelectScreen<>(theme, title, Arrays.asList(options), Function.identity(), onSelect);
    }

    @Override
    public void initWidgets() {
        search = add(theme.textBox("")).minWidth(320).expandX().widget();
        search.setFocused(true);
        search.action = this::rebuild;

        listWidget = add(theme.verticalList()).expandX().widget();
        rebuild();
    }

    private void rebuild() {
        listWidget.clear();
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        for (T opt : options) {
            String name = nameFn.apply(opt);
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            WButton b = listWidget.add(theme.button(name)).expandX().widget();
            b.action = () -> {
                onSelect.accept(opt);
                onClose();
            };
        }
        listWidget.invalidate();
    }
}
