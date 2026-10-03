/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.RecolorGuiTheme;
import xyz.thm.addon.system.THMSystem;

/**
 * THM theme - makes Meteor's GUI look like the addon's main-menu window chrome (see MainMenuFx),
 * and derives its ENTIRE palette from the two player-editable THM colors (THMSystem.thmColor /
 * thmSideColor). Nothing is hardcoded: changing those two colors in the THM tab recolors the whole
 * theme live. The main color is the brand "line" color (accent, borders, sliders, separators,
 * bright text), the side color the translucent "fill" (window body); every other Meteor color is a
 * lightened/darkened shade of the main color.
 *
 * <p>The window chrome (border/body/title bar/buttons) is drawn by the ThmChrome mixins straight
 * from the THM colors; this class handles everything Meteor draws itself (checkboxes, sliders,
 * scrollbars, module list, text, starscript, ...) by re-applying the derived palette into Meteor's
 * own colour settings every frame in {@link #beforeRender()} - so the GUI-tab colour pickers no
 * longer apply (the two THM colors are the single source of truth by design).
 */
public class ThmTheme extends MeteorGuiTheme implements RecolorGuiTheme {
    public static final ThmTheme INSTANCE = new ThmTheme();

    // Blur strength for each window's own backdrop (read by ThmMeteorWindowMixin). Appended to the
    // theme's default "General" group so it shows up as a slider in the clickgui's GUI tab.
    public final Setting<Integer> blur = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("blur")
        .description("Blurs the backdrop behind THM windows.")
        .defaultValue(0)
        .min(0).max(100)
        .sliderRange(0, 100)
        .build()
    );

    // Joke toggle: warps the whole screen (world + HUD + this GUI) with a nausea-style wave and a
    // green<->red melt. Read globally via ThmTheme.INSTANCE#isHigh() (see ImHighMixin).
    public final Setting<Boolean> imHigh = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("im-high")
        .description("It's just a joke option. Everything goes wavey, greenish-red and melts together.")
        .defaultValue(false)
        .build()
    );

    /** Global accessor for the joke effect (the setting lives on this theme but the effect is
     *  full-screen and theme-independent). */
    public static boolean isHigh() {
        return INSTANCE.imHigh.get();
    }

    // ponytail: im-high never survives a restart - reset it right after the persisted settings
    // load, instead of racing Meteor's save order with a shutdown hook.
    @Override
    public meteordevelopment.meteorclient.gui.GuiTheme fromTag(net.minecraft.nbt.CompoundTag tag) {
        var theme = super.fromTag(tag);
        imHigh.reset();
        return theme;
    }

    @Override
    public String getName() {
        return "THM";
    }

    @Override
    public boolean getCategoryIcons() {
        return true;
    }

    // ml/dl: lighten/darken a single channel toward white / black by t (0..1).
    private static int l(int c, float t) { return Math.round(c + (255 - c) * t); }
    private static int d(int c, float t) { return Math.round(c * (1 - t)); }

    /**
     * Re-derives Meteor's whole colour palette from the two THM colors and writes it into Meteor's
     * own colour settings in place (mutating the live SettingColor objects, so no change events /
     * saves fire). Called every frame before the GUI draws, so edits to the THM colors apply
     * instantly and nothing stays hardcoded.
     */
    @Override
    public void beforeRender() {
        super.beforeRender();

        THMSystem sys = THMSystem.get();
        if (sys == null) return; // not initialised yet - fall back to Meteor defaults for one frame

        SettingColor m = sys.thmColor.get();
        int r = m.r, g = m.g, b = m.b;

        // Interactive / brand elements = the main color; +/- and favourites are light/dark shades
        // (kept distinct without hardcoding green/red).
        accentColor.get().set(r, g, b, 255);
        checkboxColor.get().set(r, g, b, 255);
        plusColor.get().set(l(r, .5f), l(g, .5f), l(b, .5f), 255);
        minusColor.get().set(d(r, .5f), d(g, .5f), d(b, .5f), 255);
        favoriteColor.get().set(l(r, .65f), l(g, .65f), l(b, .65f), 255);

        // Text: near-white but faintly tinted by the brand color, so it tracks the hue.
        textColor.get().set(l(r, .85f), l(g, .85f), l(b, .85f), 255);
        textSecondaryColor.get().set(l(r, .55f), l(g, .55f), l(b, .55f), 255);
        textHighlightColor.get().set(r, g, b, 110);
        titleTextColor.get().set(l(r, .92f), l(g, .92f), l(b, .92f), 255);
        loggedInColor.get().set(l(r, .5f), l(g, .5f), l(b, .5f), 255);
        placeholderColor.get().set(l(r, .85f), l(g, .85f), l(b, .85f), 20);

        // Module list background = a dark, translucent shade of the brand color.
        moduleBackground.get().set(d(r, .72f), d(g, .72f), d(b, .72f), 140);

        // Separators track the main color.
        separatorText.get().set(l(r, .9f), l(g, .9f), l(b, .9f), 255);
        separatorCenter.get().set(r, g, b, 255);
        separatorEdges.get().set(r, g, b, 150);

        // Slider fill = main color; empty track = dark shade.
        sliderLeft.get().set(r, g, b, 210);
        sliderRight.get().set(d(r, .85f), d(g, .85f), d(b, .85f), 210);

        // Three-state (normal / hovered / pressed) colours - mutate each live SettingColor.
        setTri(backgroundColor, d(r, .86f), d(g, .86f), d(b, .86f), 215, d(r, .80f), d(g, .80f), d(b, .80f), 215, d(r, .74f), d(g, .74f), d(b, .74f), 215);
        setTri(outlineColor, d(r, .80f), d(g, .80f), d(b, .80f), 255, d(r, .40f), d(g, .40f), d(b, .40f), 255, r, g, b, 255);
        setTri(scrollbarColor, d(r, .75f), d(g, .75f), d(b, .75f), 200, d(r, .45f), d(g, .45f), d(b, .45f), 200, r, g, b, 200);
        setTri(sliderHandle, d(r, .40f), d(g, .40f), d(b, .40f), 255, d(r, .20f), d(g, .20f), d(b, .20f), 255, r, g, b, 255);

        // Starscript syntax (private settings - reach them via the public accessor methods, which
        // return the live SettingColor). Monochrome over the brand hue: main for keywords/braces,
        // light shades for the rest.
        starscriptTextColor().set(l(r, .85f), l(g, .85f), l(b, .85f), 255);
        starscriptBraceColor().set(r, g, b, 255);
        starscriptParenthesisColor().set(l(r, .4f), l(g, .4f), l(b, .4f), 255);
        starscriptDotColor().set(l(r, .6f), l(g, .6f), l(b, .6f), 255);
        starscriptCommaColor().set(l(r, .6f), l(g, .6f), l(b, .6f), 255);
        starscriptOperatorColor().set(l(r, .6f), l(g, .6f), l(b, .6f), 255);
        starscriptStringColor().set(l(r, .3f), l(g, .3f), l(b, .3f), 255);
        starscriptNumberColor().set(l(r, .5f), l(g, .5f), l(b, .5f), 255);
        starscriptKeywordColor().set(r, g, b, 255);
        starscriptAccessedObjectColor().set(l(r, .55f), l(g, .55f), l(b, .55f), 255);
    }

    private static void setTri(ThreeStateColorSetting t,
                               int nr, int ng, int nb, int na,
                               int hr, int hg, int hb, int ha,
                               int pr, int pg, int pb, int pa) {
        t.get(false, false).set(nr, ng, nb, na);          // normal
        t.get(false, true, true).set(hr, hg, hb, ha);      // hovered (bypass disable-hover)
        t.get(true, false).set(pr, pg, pb, pa);            // pressed
    }
}
