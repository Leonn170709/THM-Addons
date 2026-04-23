package xyz.thm.addon.utils;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.PlayerListEntry;
import xyz.thm.addon.modules.KitbotFrontend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitbotChatCommandParser {
    private static final List<String> COMMANDS = List.of("goto", "update", "kit", "send", "token", "claim");
    private static final List<String> FAMILIES = List.of("dug", "paved");
    private static final List<String> PAVED_DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    private static final List<String> DUG_DIRECTIONS = List.of("dugN", "dugNE", "dugE", "dugSE", "dugS", "dugSW", "dugW", "dugNW");
    private static final List<String> AMOUNTS = List.of(
        "1", "2", "3", "4", "5", "6", "7", "8",
        "9", "10", "11", "12", "13", "14", "15", "16"
    );
    private static final String GOTO_USAGE = "Usage: $goto dug <dug-direction> or $goto paved <paved-direction>.";
    private static final String UPDATE_USAGE = "Usage: $update dug <dug-direction> or $update paved <paved-direction>.";
    private static final String KIT_USAGE = "Usage: $kit <kit> [amount].";
    private static final String SEND_USAGE = "Usage: $send <player> <kit> [amount].";

    private static final Map<String, KitbotFrontend.Direction> PAVED_DIRECTION_MAP = buildDirectionMap(false);
    private static final Map<String, KitbotFrontend.Direction> DUG_DIRECTION_MAP = buildDirectionMap(true);

    private KitbotChatCommandParser() {}

    public enum ParseStatus {
        UNRECOGNIZED,
        INVALID,
        VALID
    }

    public enum CommandType {
        Goto,
        Update,
        Kit,
        Send,
        Token,
        Claim
    }

    public enum FollowUpKind {
        NONE,
        TPA,
        TPY
    }

    public record CommandRequest(
        CommandType type,
        KitbotFrontend.Mode mode,
        KitbotFrontend.Direction direction,
        KitbotFrontend.KitName kit,
        String playerName,
        int amount
    ) {
        public FollowUpKind defaultFollowUpKind() {
            return switch (type) {
                case Goto -> FollowUpKind.TPA;
                case Update, Kit -> FollowUpKind.TPY;
                case Send, Token, Claim -> FollowUpKind.NONE;
            };
        }
    }

    public record ParseResult(ParseStatus status, CommandRequest request, String errorMessage) {
        public boolean isRecognized() {
            return status != ParseStatus.UNRECOGNIZED;
        }

        public boolean isValid() {
            return status == ParseStatus.VALID;
        }
    }

    public static ParseResult parse(String rawText, Collection<String> onlinePlayers) {
        if (rawText == null) return new ParseResult(ParseStatus.UNRECOGNIZED, null, null);

        String trimmed = rawText.trim();
        if (!trimmed.startsWith("$")) return new ParseResult(ParseStatus.UNRECOGNIZED, null, null);

        String body = trimmed.substring(1).trim();
        if (body.isEmpty()) return new ParseResult(ParseStatus.UNRECOGNIZED, null, null);

        String[] tokens = body.split("\\s+");
        String command = tokens[0].toLowerCase(Locale.ROOT);

        return switch (command) {
            case "goto" -> parseDirectional(CommandType.Goto, KitbotFrontend.Mode.Goto, tokens, GOTO_USAGE);
            case "update" -> parseDirectional(CommandType.Update, KitbotFrontend.Mode.Update, tokens, UPDATE_USAGE);
            case "kit" -> parseKit(tokens);
            case "send" -> parseSend(tokens, onlinePlayers == null ? List.of() : onlinePlayers);
            case "token" -> parseNoArg(CommandType.Token, KitbotFrontend.Mode.Token, tokens, "Usage: $token.");
            case "claim" -> parseNoArg(CommandType.Claim, KitbotFrontend.Mode.Claim, tokens, "Usage: $claim.");
            default -> new ParseResult(ParseStatus.UNRECOGNIZED, null, null);
        };
    }

    public static Suggestions buildSuggestions(String fullText, int cursor, Collection<String> onlinePlayers) {
        if (fullText == null || fullText.isEmpty() || fullText.charAt(0) != '$') {
            return Suggestions.empty().join();
        }

        int safeCursor = Math.max(0, Math.min(cursor, fullText.length()));
        SuggestionState state = SuggestionState.from(fullText, safeCursor);
        if (!state.validPrefix()) return Suggestions.empty().join();

        List<String> suggestions = suggest(state, onlinePlayers == null ? List.of() : onlinePlayers);
        if (suggestions.isEmpty()) return Suggestions.empty().join();

        SuggestionsBuilder builder = new SuggestionsBuilder(fullText, state.currentTokenStart());
        for (String suggestion : suggestions) builder.suggest(suggestion);
        return builder.build();
    }

    public static List<String> getOnlinePlayerNames() {
        if (MeteorClient.mc == null || MeteorClient.mc.getNetworkHandler() == null) return List.of();
        List<String> names = new ArrayList<>();
        for (PlayerListEntry entry : MeteorClient.mc.getNetworkHandler().getPlayerList()) {
            names.add(entry.getProfile().name());
        }
        return names;
    }

    private static ParseResult parseDirectional(CommandType type, KitbotFrontend.Mode mode, String[] tokens, String usage) {
        if (tokens.length != 3) return new ParseResult(ParseStatus.INVALID, null, usage);

        String family = tokens[1].toLowerCase(Locale.ROOT);
        Map<String, KitbotFrontend.Direction> directionMap = switch (family) {
            case "dug" -> DUG_DIRECTION_MAP;
            case "paved" -> PAVED_DIRECTION_MAP;
            default -> null;
        };

        if (directionMap == null) return new ParseResult(ParseStatus.INVALID, null, usage);

        KitbotFrontend.Direction direction = directionMap.get(tokens[2].toLowerCase(Locale.ROOT));
        if (direction == null) return new ParseResult(ParseStatus.INVALID, null, usage);

        return new ParseResult(ParseStatus.VALID, new CommandRequest(type, mode, direction, null, null, 1), null);
    }

    private static ParseResult parseKit(String[] tokens) {
        if (tokens.length < 2 || tokens.length > 3) return new ParseResult(ParseStatus.INVALID, null, KIT_USAGE);

        KitbotFrontend.KitName kit = KitbotFrontend.KitName.fromString(tokens[1]);
        if (kit == null) return new ParseResult(ParseStatus.INVALID, null, invalidKitMessage());

        int amount = parseAmount(tokens, 2);
        if (amount == -1) return new ParseResult(ParseStatus.INVALID, null, invalidAmountMessage());

        return new ParseResult(ParseStatus.VALID, new CommandRequest(CommandType.Kit, KitbotFrontend.Mode.Kit, null, kit, null, amount), null);
    }

    private static ParseResult parseSend(String[] tokens, Collection<String> onlinePlayers) {
        if (tokens.length < 3 || tokens.length > 4) return new ParseResult(ParseStatus.INVALID, null, SEND_USAGE);

        if (!containsExact(onlinePlayers, tokens[1])) {
            return new ParseResult(ParseStatus.INVALID, null, "Player must match an online tab-list name exactly.");
        }

        KitbotFrontend.KitName kit = KitbotFrontend.KitName.fromString(tokens[2]);
        if (kit == null) return new ParseResult(ParseStatus.INVALID, null, invalidKitMessage());

        int amount = parseAmount(tokens, 3);
        if (amount == -1) return new ParseResult(ParseStatus.INVALID, null, invalidAmountMessage());

        return new ParseResult(ParseStatus.VALID, new CommandRequest(CommandType.Send, KitbotFrontend.Mode.Send, null, kit, tokens[1], amount), null);
    }

    private static ParseResult parseNoArg(CommandType type, KitbotFrontend.Mode mode, String[] tokens, String usage) {
        if (tokens.length != 1) return new ParseResult(ParseStatus.INVALID, null, usage);
        return new ParseResult(ParseStatus.VALID, new CommandRequest(type, mode, null, null, null, 1), null);
    }

    private static int parseAmount(String[] tokens, int index) {
        if (tokens.length <= index) return 1;
        try {
            int amount = Integer.parseInt(tokens[index]);
            return amount >= 1 && amount <= 16 ? amount : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String invalidKitMessage() {
        return "Unknown kit. Valid kits: " + String.join(", ", kitLabels()) + '.';
    }

    private static String invalidAmountMessage() {
        return "Amount must be between 1 and 16.";
    }

    private static boolean containsExact(Collection<String> values, String needle) {
        if (values == null || needle == null) return false;
        for (String value : values) {
            if (needle.equals(value)) return true;
        }
        return false;
    }

    private static Map<String, KitbotFrontend.Direction> buildDirectionMap(boolean dug) {
        Map<String, KitbotFrontend.Direction> map = new LinkedHashMap<>();
        for (KitbotFrontend.Direction direction : KitbotFrontend.Direction.values()) {
            boolean isDug = direction.command.startsWith("dug");
            if (dug == isDug) {
                map.put(direction.command.toLowerCase(Locale.ROOT), direction);
            }
        }
        return map;
    }

    private static List<String> kitLabels() {
        List<String> labels = new ArrayList<>();
        for (KitbotFrontend.KitName kitName : KitbotFrontend.KitName.values()) {
            labels.add(kitName.label);
        }
        return labels;
    }

    private static List<String> suggest(SuggestionState state, Collection<String> onlinePlayers) {
        List<String> complete = state.completeTokens();
        String current = state.currentToken();

        if (complete.isEmpty()) return matchIgnoreCasePrefix(COMMANDS, current);

        String command = complete.getFirst().toLowerCase(Locale.ROOT);
        return switch (command) {
            case "goto", "update" -> suggestDirectional(state, command);
            case "kit" -> suggestKit(state);
            case "send" -> suggestSend(state, onlinePlayers);
            case "token", "claim" -> Collections.emptyList();
            default -> Collections.emptyList();
        };
    }

    private static List<String> suggestDirectional(SuggestionState state, String command) {
        List<String> complete = state.completeTokens();
        String current = state.currentToken();

        if (complete.size() == 1) return matchIgnoreCasePrefix(FAMILIES, current);

        String family = complete.get(1).toLowerCase(Locale.ROOT);
        if (!FAMILIES.contains(family)) return Collections.emptyList();

        if (complete.size() == 2) {
            return matchIgnoreCasePrefix(family.equals("dug") ? DUG_DIRECTIONS : PAVED_DIRECTIONS, current);
        }

        return Collections.emptyList();
    }

    private static List<String> suggestKit(SuggestionState state) {
        List<String> complete = state.completeTokens();
        String current = state.currentToken();

        if (complete.size() == 1) return matchIgnoreCasePrefix(kitLabels(), current);

        if (complete.size() == 2) {
            KitbotFrontend.KitName kit = KitbotFrontend.KitName.fromString(complete.get(1));
            if (kit == null) return Collections.emptyList();
            return matchIgnoreCasePrefix(AMOUNTS, current);
        }

        return Collections.emptyList();
    }

    private static List<String> suggestSend(SuggestionState state, Collection<String> onlinePlayers) {
        List<String> complete = state.completeTokens();
        String current = state.currentToken();

        if (complete.size() == 1) return matchIgnoreCasePrefix(onlinePlayers, current);

        if (complete.size() == 2) {
            if (!containsExact(onlinePlayers, complete.get(1))) return Collections.emptyList();
            return matchIgnoreCasePrefix(kitLabels(), current);
        }

        if (complete.size() == 3) {
            if (!containsExact(onlinePlayers, complete.get(1))) return Collections.emptyList();
            if (KitbotFrontend.KitName.fromString(complete.get(2)) == null) return Collections.emptyList();
            return matchIgnoreCasePrefix(AMOUNTS, current);
        }

        return Collections.emptyList();
    }

    private static List<String> matchIgnoreCasePrefix(Collection<String> candidates, String prefix) {
        List<String> matches = new ArrayList<>();
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate == null) continue;
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) matches.add(candidate);
        }
        return matches;
    }

    private record TokenSpan(String text, int start) {}

    private record SuggestionState(
        boolean validPrefix,
        List<String> completeTokens,
        String currentToken,
        int currentTokenStart
    ) {
        static SuggestionState from(String fullText, int cursor) {
            if (fullText == null || fullText.isEmpty() || fullText.charAt(0) != '$') {
                return new SuggestionState(false, List.of(), "", 0);
            }

            String beforeCursor = fullText.substring(0, Math.max(0, Math.min(cursor, fullText.length())));
            if (beforeCursor.isEmpty() || beforeCursor.charAt(0) != '$') {
                return new SuggestionState(false, List.of(), "", 0);
            }

            String afterPrefix = beforeCursor.substring(1);
            int leadingWhitespace = 0;
            while (leadingWhitespace < afterPrefix.length() && Character.isWhitespace(afterPrefix.charAt(leadingWhitespace))) {
                leadingWhitespace++;
            }

            String working = afterPrefix.substring(leadingWhitespace);
            int tokenBaseOffset = 1 + leadingWhitespace;
            List<TokenSpan> spans = new ArrayList<>();
            int index = 0;
            while (index < working.length()) {
                while (index < working.length() && Character.isWhitespace(working.charAt(index))) index++;
                if (index >= working.length()) break;
                int start = index;
                while (index < working.length() && !Character.isWhitespace(working.charAt(index))) index++;
                spans.add(new TokenSpan(working.substring(start, index), start));
            }

            boolean expectingNewToken = working.isEmpty() || Character.isWhitespace(working.charAt(working.length() - 1));
            List<String> completeTokens = new ArrayList<>();
            String currentToken = "";
            int currentTokenStart = tokenBaseOffset + working.length();

            if (expectingNewToken) {
                for (TokenSpan span : spans) completeTokens.add(span.text());
            } else if (!spans.isEmpty()) {
                for (int i = 0; i < spans.size() - 1; i++) completeTokens.add(spans.get(i).text());
                TokenSpan currentSpan = spans.getLast();
                currentToken = currentSpan.text();
                currentTokenStart = tokenBaseOffset + currentSpan.start();
            }

            return new SuggestionState(true, completeTokens, currentToken, currentTokenStart);
        }
    }
}
