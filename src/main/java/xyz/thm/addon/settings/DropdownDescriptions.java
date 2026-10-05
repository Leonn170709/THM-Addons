/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.world.Dimension;

import java.util.function.Function;

public final class DropdownDescriptions {
    private DropdownDescriptions() {}

    public static String description(Object value) {
        return switch (value) {
            case DescribedOption option -> option.description();
            case ShapeMode mode -> switch (mode) {
                case Lines -> "Draw outlines only.";
                case Sides -> "Draw filled faces only.";
                case Both -> "Draw filled faces and outlines.";
            };
            case SortPriority priority -> switch (priority) {
                case LowestDistance -> "Target nearest players first.";
                case HighestDistance -> "Target farthest players first.";
                case LowestHealth -> "Target players with the least health first.";
                case HighestHealth -> "Target players with the most health first.";
                case ClosestAngle -> "Target players closest to your crosshair first.";
            };
            case Dimension dimension -> switch (dimension) {
                case Overworld -> "Use Overworld coordinates.";
                case Nether -> "Use Nether coordinates.";
                case End -> "Use End coordinates.";
            };
            case null, default -> null;
        };
    }

    public static void configure(WDropdown<?> dropdown, Function<Object, String> descriptions) {
        ((DescribedDropdown) dropdown).thm$setDescriptions(descriptions);
    }

    public static String providedString(String setting, Object value) {
        return switch (setting) {
            case "show-branch" -> switch (value.toString()) {
                case "All" -> "Show members from every branch.";
                case "Main" -> "Show main-branch members only.";
                case "PvP" -> "Show PvP-branch members only.";
                default -> null;
            };
            case "thm-cape" -> value.equals("None") ? "Hide your THM cape." : "Use this THM cape.";
            case "shader" -> value.equals("None") ? "Use the vanilla panorama." : "Use this menu background shader.";
            default -> null;
        };
    }
}
