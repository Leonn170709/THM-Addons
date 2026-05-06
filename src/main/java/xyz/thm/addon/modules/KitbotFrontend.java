package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.KitbotChatCommandParser;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

public class KitbotFrontend extends Module {
    public static final String KITBOT_NAME = "KitBot1";
    private static final long REQUEST_WINDOW_MS = 100_000L;
    private static final long GOTO_TPA_DELAY_MS = 500L;
    private static final int KITBOT_NEARBY_TICKS_REQUIRED = 20;
    private static final double KITBOT_NEARBY_DISTANCE = 30.0;

    private static final ManagedRuntime RUNTIME = ManagedRuntime.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which kitbot command to send.")
        .defaultValue(Mode.Goto)
        .build()
    );

    private final Setting<Direction> updateDirection = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("update-direction")
        .description("Direction used for $update.")
        .defaultValue(Direction.West)
        .visible(() -> mode.get() == Mode.Update)
        .build()
    );

    private final Setting<Direction> gotoDirection = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("goto-direction")
        .description("Direction used for $goto.")
        .defaultValue(Direction.West)
        .visible(() -> mode.get() == Mode.Goto)
        .build()
    );

    private final Setting<KitName> kitName = sgGeneral.add(new EnumSetting.Builder<KitName>()
        .name("kit")
        .description("Kit to order.")
        .defaultValue(KitName.Pvp)
        .visible(() -> mode.get() == Mode.Kit)
        .build()
    );

    private final Setting<Integer> kitAmount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("Amount to order.")
        .defaultValue(1)
        .min(1)
        .max(16)
        .sliderMax(16)
        .visible(() -> mode.get() == Mode.Kit)
        .build()
    );

    private final Setting<String> sendPlayer = sgGeneral.add(new StringSetting.Builder()
        .name("send-player")
        .description("Player name used for $send.")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.Send)
        .build()
    );

    private final Setting<KitName> sendKit = sgGeneral.add(new EnumSetting.Builder<KitName>()
        .name("send-kit")
        .description("Kit used for $send.")
        .defaultValue(KitName.Pvp)
        .visible(() -> mode.get() == Mode.Send)
        .build()
    );

    private final Setting<Integer> sendAmount = sgGeneral.add(new IntSetting.Builder()
        .name("send-amount")
        .description("Amount used for $send.")
        .defaultValue(1)
        .min(1)
        .max(16)
        .sliderMax(16)
        .visible(() -> mode.get() == Mode.Send)
        .build()
    );

    private final Setting<Boolean> autoTp = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-tp")
        .description("Automatically handles teleport requests for KitBot commands sent from the module UI.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Kit || mode.get() == Mode.Goto || mode.get() == Mode.Update)
        .build()
    );

    public KitbotFrontend() {
        super(THMAddon.MAIN, "Kitbot-frontend", "Send kitbot commands ($update, $goto, $kit, $send, $token, $claim)");
        RUNTIME.ensureInitialized();
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            warning("You must be in-game to use KitBot.");
            toggle();
            return;
        }

        ManagedRequest request = createManualRequest();
        if (request == null) {
            warning("Unable to build a valid KitBot request from current settings.");
            toggle();
            return;
        }

        if (!RUNTIME.submit(request, RequestOrigin.MANUAL, true)) {
            warning("KitBot request window is already active.");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        RUNTIME.detachManualDisplay();
    }

    public static boolean submitChatRequest(KitbotChatCommandParser.CommandRequest request) {
        ManagedRequest managed = fromParserRequest(request, true);
        if (managed == null) return false;
        return RUNTIME.submit(managed, RequestOrigin.ROUTER, false);
    }

    public static boolean sendUpdate(Direction direction) {
        if (direction == null) return false;
        return RUNTIME.submit(ManagedRequest.directional(KitbotChatCommandParser.CommandType.Update, Mode.Update, direction, true), RequestOrigin.API, false);
    }

    public static boolean sendGoto(Direction direction) {
        if (direction == null) return false;
        return RUNTIME.submit(ManagedRequest.directional(KitbotChatCommandParser.CommandType.Goto, Mode.Goto, direction, true), RequestOrigin.API, false);
    }

    public static boolean kitOrder(KitName kit, int amount) {
        if (kit == null) return false;
        return kitOrder(kit.label, amount);
    }

    public static boolean kitOrder(String kit, int amount) {
        String safeKit = safeString(kit);
        if (safeKit.isEmpty()) return false;
        return RUNTIME.submit(ManagedRequest.kitRequest(Mode.Kit, safeKit, amount, true), RequestOrigin.API, false);
    }

    public static boolean sendToPlayer(String player, KitName kit, int amount) {
        if (kit == null) return false;
        return sendToPlayer(player, kit.label, amount);
    }

    public static boolean sendToPlayer(String player, String kit, int amount) {
        String safePlayer = safeString(player);
        String safeKit = safeString(kit);
        if (safePlayer.isEmpty() || safeKit.isEmpty()) return false;
        return RUNTIME.submit(ManagedRequest.sendRequest(safePlayer, safeKit, amount), RequestOrigin.API, false);
    }

    public static boolean sendToken() {
        return RUNTIME.submit(ManagedRequest.simple(KitbotChatCommandParser.CommandType.Token, Mode.Token), RequestOrigin.API, false);
    }

    public static boolean sendClaim() {
        return RUNTIME.submit(ManagedRequest.simple(KitbotChatCommandParser.CommandType.Claim, Mode.Claim), RequestOrigin.API, false);
    }

    public static boolean sendUpdate(String direction) {
        return sendUpdate(Direction.fromString(direction));
    }

    public static boolean sendGoto(String direction) {
        return sendGoto(Direction.fromString(direction));
    }

    public static boolean hasActiveWindow() {
        return RUNTIME.hasActiveWindow();
    }

    public static long getRemainingWindowMs() {
        return RUNTIME.getRemainingWindowMs();
    }

    public static LifecycleState getCurrentLifecycleState() {
        return RUNTIME.getCurrentLifecycleState();
    }

    public static FailureReason getCurrentFailureReason() {
        return RUNTIME.getCurrentFailureReason();
    }

    public static String getCurrentDetail() {
        return RUNTIME.getCurrentDetail();
    }

    public static void addLifecycleListener(LifecycleListener listener) {
        RUNTIME.addListener(listener);
    }

    public static void removeLifecycleListener(LifecycleListener listener) {
        RUNTIME.removeListener(listener);
    }

    private ManagedRequest createManualRequest() {
        return switch (mode.get()) {
            case Goto -> ManagedRequest.directional(KitbotChatCommandParser.CommandType.Goto, Mode.Goto, gotoDirection.get(), autoTp.get());
            case Update -> ManagedRequest.directional(KitbotChatCommandParser.CommandType.Update, Mode.Update, updateDirection.get(), autoTp.get());
            case Kit -> ManagedRequest.kitRequest(Mode.Kit, kitName.get() == null ? "" : kitName.get().label, kitAmount.get(), autoTp.get());
            case Send -> {
                String player = safeString(sendPlayer.get());
                String kit = sendKit.get() == null ? "" : sendKit.get().label;
                if (player.isEmpty() || kit.isEmpty()) yield null;
                yield ManagedRequest.sendRequest(player, kit, sendAmount.get());
            }
            case Token -> ManagedRequest.simple(KitbotChatCommandParser.CommandType.Token, Mode.Token);
            case Claim -> ManagedRequest.simple(KitbotChatCommandParser.CommandType.Claim, Mode.Claim);
        };
    }

    private static ManagedRequest fromParserRequest(KitbotChatCommandParser.CommandRequest request, boolean autoTransport) {
        if (request == null) return null;
        return switch (request.mode()) {
            case Goto -> ManagedRequest.directional(KitbotChatCommandParser.CommandType.Goto, Mode.Goto, request.direction(), autoTransport);
            case Update -> ManagedRequest.directional(KitbotChatCommandParser.CommandType.Update, Mode.Update, request.direction(), autoTransport);
            case Kit -> ManagedRequest.kitRequest(Mode.Kit, request.kit() == null ? "" : request.kit().label, request.amount(), autoTransport);
            case Send -> ManagedRequest.sendRequest(request.playerName(), request.kit() == null ? "" : request.kit().label, request.amount());
            case Token -> ManagedRequest.simple(KitbotChatCommandParser.CommandType.Token, Mode.Token);
            case Claim -> ManagedRequest.simple(KitbotChatCommandParser.CommandType.Claim, Mode.Claim);
        };
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private static void announceInfo(String message) {
        KitbotFrontend frontend = Modules.get().get(KitbotFrontend.class);
        if (frontend != null) frontend.info(message);
        else THMAddon.LOG.info(message);
    }

    private static void announceWarning(String message) {
        KitbotFrontend frontend = Modules.get().get(KitbotFrontend.class);
        if (frontend != null) frontend.warning(message);
        else THMAddon.LOG.warn(message);
    }

    private static void detachManualModuleDisplay() {
        KitbotFrontend frontend = Modules.get().get(KitbotFrontend.class);
        if (frontend != null && frontend.isActive()) frontend.toggle();
    }

    @FunctionalInterface
    public interface LifecycleListener {
        void onKitbotLifecycle(LifecycleEvent event);
    }

    public enum LifecycleState {
        OrderSent,
        InFlight,
        RetryScheduled,
        Succeeded,
        Failed,
        NoCurrentOrder,
        BlockedByWindow
    }

    public enum FailureReason {
        OutOfStock,
        TimedOut,
        InsufficientTokens,
        VoucherCredits,
        InvalidKit,
        NotWhitelisted,
        UnableToFulfill,
        GenericError,
        MaxOrderViolation,
        Disconnected,
        PermissionDenied,
        TransportFailure
    }

    public enum RequestOrigin {
        MANUAL,
        ROUTER,
        API
    }

    public record LifecycleEvent(
        LifecycleState state,
        RequestOrigin origin,
        Mode mode,
        FailureReason failureReason,
        String detail,
        long remainingWindowMs
    ) {}

    private record ManagedRequest(
        KitbotChatCommandParser.CommandType type,
        Mode mode,
        Direction direction,
        String kitLabel,
        String playerName,
        int amount,
        boolean autoTransport
    ) {
        private static ManagedRequest directional(KitbotChatCommandParser.CommandType type, Mode mode, Direction direction, boolean autoTransport) {
            if (direction == null) return null;
            return new ManagedRequest(type, mode, direction, null, null, 1, autoTransport);
        }

        private static ManagedRequest kitRequest(Mode mode, String kitLabel, int amount, boolean autoTransport) {
            String safeKit = safeString(kitLabel);
            if (safeKit.isEmpty()) return null;
            return new ManagedRequest(KitbotChatCommandParser.CommandType.Kit, mode, null, safeKit, null, clampAmount(amount), autoTransport);
        }

        private static ManagedRequest sendRequest(String playerName, String kitLabel, int amount) {
            String safePlayer = safeString(playerName);
            String safeKit = safeString(kitLabel);
            if (safePlayer.isEmpty() || safeKit.isEmpty()) return null;
            return new ManagedRequest(KitbotChatCommandParser.CommandType.Send, Mode.Send, null, safeKit, safePlayer, clampAmount(amount), true);
        }

        private static ManagedRequest simple(KitbotChatCommandParser.CommandType type, Mode mode) {
            return new ManagedRequest(type, mode, null, null, null, 1, true);
        }

        private static int clampAmount(int amount) {
            return Math.max(1, Math.min(16, amount));
        }

        private boolean isOneOff() {
            return mode == Mode.Send || mode == Mode.Token || mode == Mode.Claim;
        }

        private boolean requiresExplicitOutcome() {
            return mode == Mode.Kit || mode == Mode.Update || mode == Mode.Goto;
        }

        private String commandString() {
            return switch (mode) {
                case Goto -> "/msg " + KITBOT_NAME + " $goto " + direction.command;
                case Update -> "/msg " + KITBOT_NAME + " $update " + direction.command;
                case Kit -> String.format(Locale.ROOT, "/msg %s $kit %s %d", KITBOT_NAME, kitLabel, amount);
                case Send -> String.format(Locale.ROOT, "/msg %s $send %s %s %d", KITBOT_NAME, playerName, kitLabel, amount);
                case Token -> "/msg " + KITBOT_NAME + " $token";
                case Claim -> "/msg " + KITBOT_NAME + " $claim";
            };
        }
    }

    private static final class ManagedRuntime {
        private static final Pattern KIT_NOT_FOUND_PATTERN = Pattern.compile("Kit \".*\" not found");
        private static final Pattern KIT_NOT_WHITELISTED_PATTERN = Pattern.compile("You are not whitelisted for the .* kit");
        private static final Pattern DETAILED_TOKEN_PATTERN = Pattern.compile("Insufficient tokens\\. You have .* but need .*");
        private static final Pattern VOUCHER_PATTERN = Pattern.compile("Insufficient voucher credits \\(.* remaining\\)");
        private static final Pattern STOCK_PATTERN = Pattern.compile("Insufficient stock for .*\\. Only .*/.* available");
        private static final Pattern UPDATE_NOT_WHITELISTED_PATTERN = Pattern.compile("You are not whitelisted to update .*");
        private static final Pattern UPDATE_CANT_PATTERN = Pattern.compile("You cant update .*");
        private static final Pattern UPDATE_REQUESTED_PATTERN = Pattern.compile("You have requested an update for .* sending tpa", Pattern.CASE_INSENSITIVE);
        private static final Pattern UPDATE_HIGHWAY_REQUESTED_PATTERN = Pattern.compile("You have requested an update for highway .* sending tpa", Pattern.CASE_INSENSITIVE);
        private static final Pattern UPDATE_WRONG_DIRECTION_PATTERN = Pattern.compile("Update rejected! You are at .* doesn't match your request for .*", Pattern.CASE_INSENSITIVE);
        private static final Pattern UPDATE_DISTANCE_PATTERN = Pattern.compile("Update rejected! New position is .* Max jump is 100k\\.", Pattern.CASE_INSENSITIVE);
        private static final Pattern UPDATE_SUCCESS_PATTERN = Pattern.compile("Highway .* updated");
        private static final Pattern GOTO_GOING_PATTERN = Pattern.compile("Going to highway .* please wait to teleport", Pattern.CASE_INSENSITIVE);
        private static final Pattern GOTO_ARRIVED_PATTERN = Pattern.compile("Bot has arrived at highway .* you may teleport", Pattern.CASE_INSENSITIVE);

        private static volatile ManagedRuntime INSTANCE;

        private final Object lock = new Object();
        private final CopyOnWriteArraySet<LifecycleListener> listeners = new CopyOnWriteArraySet<>();

        private ManagedRequest windowRequest;
        private ManagedRequest activeRequest;
        private RequestOrigin windowOrigin;
        private long windowStartedAtMs;
        private long windowEndsAtMs;
        private long pendingGotoTpaAtMs;
        private boolean manualDisplayAttached;
        private boolean kitMustLeaveBeforeProof;
        private int kitNearbyTicks;

        private LifecycleState currentState = LifecycleState.NoCurrentOrder;
        private FailureReason currentFailureReason;
        private String currentDetail = "";

        private ManagedRuntime() {
            MeteorClient.EVENT_BUS.subscribe(this);
        }

        static ManagedRuntime getInstance() {
            if (INSTANCE == null) {
                synchronized (ManagedRuntime.class) {
                    if (INSTANCE == null) INSTANCE = new ManagedRuntime();
                }
            }
            return INSTANCE;
        }

        void ensureInitialized() {
        }

        void addListener(LifecycleListener listener) {
            if (listener != null) listeners.add(listener);
        }

        void removeListener(LifecycleListener listener) {
            if (listener != null) listeners.remove(listener);
        }

        boolean hasActiveWindow() {
            synchronized (lock) {
                return isWindowActiveLocked(System.currentTimeMillis());
            }
        }

        long getRemainingWindowMs() {
            synchronized (lock) {
                return remainingWindowMsLocked(System.currentTimeMillis());
            }
        }

        LifecycleState getCurrentLifecycleState() {
            synchronized (lock) {
                return currentState;
            }
        }

        FailureReason getCurrentFailureReason() {
            synchronized (lock) {
                return currentFailureReason;
            }
        }

        String getCurrentDetail() {
            synchronized (lock) {
                return currentDetail;
            }
        }

        boolean submit(ManagedRequest request, RequestOrigin origin, boolean attachManualDisplay) {
            if (request == null) return false;
            if (MeteorClient.mc == null || MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
                announceWarning("You must be in-game to use KitBot.");
                return false;
            }

            expireWindowIfNeeded(System.currentTimeMillis());

            LifecycleEvent blockedEvent = null;
            LifecycleEvent sentEvent = null;
            String commandToSend = null;
            synchronized (lock) {
                long now = System.currentTimeMillis();
                if (isWindowActiveLocked(now)) {
                    blockedEvent = transientEventLocked(LifecycleState.BlockedByWindow, origin, request, null, "KitBot request window is already active.", remainingWindowMsLocked(now));
                } else {
                    windowRequest = request;
                    activeRequest = request;
                    windowOrigin = origin;
                    windowStartedAtMs = now;
                    windowEndsAtMs = now + REQUEST_WINDOW_MS;
                    pendingGotoTpaAtMs = 0L;
                    manualDisplayAttached = origin == RequestOrigin.MANUAL && attachManualDisplay;
                    initializeKitProofLocked();
                    sentEvent = setStateLocked(LifecycleState.OrderSent, null, request.commandString(), now);
                    commandToSend = request.commandString();
                }
            }

            if (sentEvent != null && commandToSend != null) {
                ChatUtils.sendPlayerMsg(commandToSend);
                fire(sentEvent);
                return true;
            }
            if (blockedEvent != null) fire(blockedEvent);
            return false;
        }

        void detachManualDisplay() {
            synchronized (lock) {
                if (windowOrigin == RequestOrigin.MANUAL) manualDisplayAttached = false;
            }
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            long now = System.currentTimeMillis();
            expireWindowIfNeeded(now);

            PendingActions actions = null;
            synchronized (lock) {
                if (activeRequest == null) return;

                if (pendingGotoTpaAtMs > 0L && now >= pendingGotoTpaAtMs && activeRequest.mode == Mode.Goto) {
                    pendingGotoTpaAtMs = 0L;
                    LifecycleEvent successEvent = succeedLocked("Bot has arrived at highway and TPA was sent.", now);
                    actions = new PendingActions(successEvent, "/tpa " + KITBOT_NAME, "TPA has been sent to KitBot1.", null);
                } else if (activeRequest.mode == Mode.Kit && updateKitArrivalProofLocked()) {
                    LifecycleEvent successEvent = succeedLocked("Confirmed KitBot1 nearby after kit delivery.", now);
                    actions = new PendingActions(successEvent, null, "Confirmed KitBot1 delivery nearby.", null);
                }
            }

            runPendingActions(actions);
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            PendingActions actions = null;
            synchronized (lock) {
                if (activeRequest == null) return;
                LifecycleEvent failureEvent = failLocked(FailureReason.Disconnected, "Disconnected while waiting for KitBot.", System.currentTimeMillis());
                actions = new PendingActions(failureEvent, null, null, "KitBot request interrupted by disconnect.");
            }

            runPendingActions(actions);
        }

        @EventHandler
        private void onMessage(ReceiveMessageEvent event) {
            String message = event.getMessage().getString();
            if (message == null || message.isBlank()) return;

            PendingActions actions = null;
            synchronized (lock) {
                if (activeRequest == null) return;
                long now = System.currentTimeMillis();

                if (message.contains("Bot is busy, you have been added to the queue")) {
                    actions = new PendingActions(progressLocked(message, now), null, null, null);
                } else {
                    actions = switch (activeRequest.mode) {
                        case Kit -> handleKitMessageLocked(message, now);
                        case Update -> handleUpdateMessageLocked(message, now);
                        case Goto -> handleGotoMessageLocked(message, now);
                        case Send -> handleSendMessageLocked(message, now);
                        case Token -> handleTokenOrClaimMessageLocked(message, now);
                        case Claim -> handleTokenOrClaimMessageLocked(message, now);
                    };
                }
            }

            runPendingActions(actions);
        }

        private PendingActions handleKitMessageLocked(String message, long now) {
            if (message.contains(KITBOT_NAME + " wants to teleport to you")) {
                LifecycleEvent progressEvent = progressLocked("Accepted KitBot teleport request for kit order.", now);
                String command = activeRequest.autoTransport ? "/tpy " + KITBOT_NAME : null;
                String info = activeRequest.autoTransport ? "Accepted KitBot1 teleport request." : null;
                return new PendingActions(progressEvent, command, info, null);
            }

            if (message.contains("Order cancelled: Insufficient tokens.")) {
                return failureActionsLocked(FailureReason.InsufficientTokens, message, "KitBot cancelled the order because you do not have enough tokens.", now);
            }
            if (message.contains("Unable to fulfill order. Report this issue")) {
                return failureActionsLocked(FailureReason.UnableToFulfill, message, "KitBot could not fulfill that kit request.", now);
            }
            if (message.contains("Bot has your kits, sending a teleport")) {
                return new PendingActions(progressLocked(message, now), null, null, null);
            }
            if (message.contains("Oops, looks like you didnt accept your teleport in time. Report issues at")) {
                return failureActionsLocked(FailureReason.TransportFailure, message, "KitBot delivery teleport timed out.", now);
            }
            if (message.contains("An error occurred. Please try again later.")) {
                return failureActionsLocked(FailureReason.GenericError, message, "KitBot reported an error while processing the kit request.", now);
            }
            if (KIT_NOT_FOUND_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.InvalidKit, message, "KitBot does not recognize that kit.", now);
            }
            if (KIT_NOT_WHITELISTED_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.NotWhitelisted, message, "You are not whitelisted for that kit.", now);
            }
            if (message.contains("Maximum 16 kits total per order")) {
                return failureActionsLocked(FailureReason.MaxOrderViolation, message, "KitBot orders cannot exceed 16 kits.", now);
            }
            if (DETAILED_TOKEN_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.InsufficientTokens, message, "Insufficient tokens. Check your balance before ordering again.", now);
            }
            if (VOUCHER_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.VoucherCredits, message, "Insufficient voucher credits for that kit order.", now);
            }
            if (STOCK_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.OutOfStock, message, "KitBot does not have enough stock for that kit order.", now);
            }

            return null;
        }

        private PendingActions handleUpdateMessageLocked(String message, long now) {
            if (message.contains(KITBOT_NAME + " wants to teleport to you")) {
                LifecycleEvent progressEvent = progressLocked("Accepted KitBot teleport request for update.", now);
                String command = activeRequest.autoTransport ? "/tpy " + KITBOT_NAME : null;
                String info = activeRequest.autoTransport ? "Accepted KitBot1 teleport request." : null;
                return new PendingActions(progressEvent, command, info, null);
            }

            if (UPDATE_NOT_WHITELISTED_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.NotWhitelisted, message, "You are not whitelisted to issue that update.", now);
            }
            if (UPDATE_CANT_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.PermissionDenied, message, "You are not allowed to issue that update.", now);
            }
            if (UPDATE_REQUESTED_PATTERN.matcher(message).find() || UPDATE_HIGHWAY_REQUESTED_PATTERN.matcher(message).find()) {
                return new PendingActions(progressLocked(message, now), null, null, null);
            }
            if (message.contains("Teleport failed or timed out")) {
                return failureActionsLocked(FailureReason.TransportFailure, message, "KitBot update teleport failed or timed out.", now);
            }
            if (message.contains("Update rejected! This location is locked to staff in this dimension.")) {
                return failureActionsLocked(FailureReason.PermissionDenied, message, "That update location is locked to staff in this dimension.", now);
            }
            if (message.contains("Cannot update highway from protected coordinates")) {
                return failureActionsLocked(FailureReason.PermissionDenied, message, "You cannot update the highway from protected coordinates.", now);
            }
            if (UPDATE_WRONG_DIRECTION_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.GenericError, message, "You are trying to update the wrong highway direction.", now);
            }
            if (UPDATE_DISTANCE_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.PermissionDenied, message, "That update is too far from the last point. Contact Highwayman or above.", now);
            }
            if (UPDATE_SUCCESS_PATTERN.matcher(message).find()) {
                LifecycleEvent successEvent = succeedLocked(message, now);
                return new PendingActions(successEvent, null, "Highway update completed.", null);
            }

            return null;
        }

        private PendingActions handleGotoMessageLocked(String message, long now) {
            if (GOTO_GOING_PATTERN.matcher(message).find()) {
                return new PendingActions(progressLocked(message, now), null, null, null);
            }
            if (message.contains("Failed to teleport to highway")) {
                return failureActionsLocked(FailureReason.TransportFailure, message, "KitBot failed to teleport to that highway.", now);
            }
            if (message.contains("Bot died on highway. Report and try a diffrent highway")) {
                return failureActionsLocked(FailureReason.TransportFailure, message, "KitBot died while traveling to that highway.", now);
            }
            if (GOTO_ARRIVED_PATTERN.matcher(message).find()) {
                if (activeRequest.autoTransport) {
                    pendingGotoTpaAtMs = now + GOTO_TPA_DELAY_MS;
                    return new PendingActions(progressLocked(message, now), null, null, null);
                }

                LifecycleEvent successEvent = succeedLocked(message, now);
                return new PendingActions(successEvent, null, "KitBot has arrived at the requested highway.", null);
            }
            if (message.contains("Dont forget to update with $update")) {
                return null;
            }

            return null;
        }

        private PendingActions handleSendMessageLocked(String message, long now) {
            if (message.contains("Order cancelled: Insufficient tokens.")) {
                return failureActionsLocked(FailureReason.InsufficientTokens, message, "KitBot cancelled the send order because you do not have enough tokens.", now);
            }
            if (message.contains("Unable to fulfill order. Report this issue")) {
                return failureActionsLocked(FailureReason.UnableToFulfill, message, "KitBot could not fulfill that send request.", now);
            }
            if (message.contains("An error occurred. Please try again later.")) {
                return failureActionsLocked(FailureReason.GenericError, message, "KitBot reported an error while processing the send request.", now);
            }
            if (KIT_NOT_FOUND_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.InvalidKit, message, "KitBot does not recognize that kit.", now);
            }
            if (KIT_NOT_WHITELISTED_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.NotWhitelisted, message, "You are not whitelisted for that kit.", now);
            }
            if (message.contains("Maximum 16 kits total per order")) {
                return failureActionsLocked(FailureReason.MaxOrderViolation, message, "KitBot orders cannot exceed 16 kits.", now);
            }
            if (DETAILED_TOKEN_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.InsufficientTokens, message, "Insufficient tokens. Check your balance before ordering again.", now);
            }
            if (VOUCHER_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.VoucherCredits, message, "Insufficient voucher credits for that send request.", now);
            }
            if (STOCK_PATTERN.matcher(message).find()) {
                return failureActionsLocked(FailureReason.OutOfStock, message, "KitBot does not have enough stock for that send request.", now);
            }

            return null;
        }

        private PendingActions handleTokenOrClaimMessageLocked(String message, long now) {
            if (message.contains("Order cancelled: Insufficient tokens.")) {
                return failureActionsLocked(FailureReason.InsufficientTokens, message, "KitBot rejected that request because you do not have enough tokens.", now);
            }
            if (message.contains("Unable to fulfill order. Report this issue")) {
                return failureActionsLocked(FailureReason.UnableToFulfill, message, "KitBot could not fulfill that request.", now);
            }
            if (message.contains("An error occurred. Please try again later.")) {
                return failureActionsLocked(FailureReason.GenericError, message, "KitBot reported an error while processing that request.", now);
            }

            return null;
        }

        private void expireWindowIfNeeded(long now) {
            LifecycleEvent firstEvent = null;
            LifecycleEvent secondEvent = null;
            boolean detachManualDisplay = false;

            synchronized (lock) {
                if (windowRequest == null || now < windowEndsAtMs) return;

                if (activeRequest != null && activeRequest.requiresExplicitOutcome()) {
                    firstEvent = failLocked(FailureReason.TimedOut, "Timed out waiting for final KitBot outcome.", now);
                    secondEvent = clearWindowLocked(now);
                    detachManualDisplay = consumeManualDisplayLocked();
                } else if (activeRequest != null && activeRequest.isOneOff()) {
                    firstEvent = succeedLocked("No matched KitBot failure arrived before the request window expired.", now);
                    secondEvent = clearWindowLocked(now);
                    detachManualDisplay = consumeManualDisplayLocked();
                } else {
                    secondEvent = clearWindowLocked(now);
                    detachManualDisplay = consumeManualDisplayLocked();
                }
            }

            if (firstEvent != null) {
                if (firstEvent.state() == LifecycleState.Failed && firstEvent.failureReason() == FailureReason.TimedOut) {
                    announceWarning("KitBot request timed out without a final response.");
                }
                fire(firstEvent);
            }
            if (secondEvent != null) fire(secondEvent);
            if (detachManualDisplay) detachManualModuleDisplay();
        }

        private LifecycleEvent progressLocked(String detail, long now) {
            return setStateLocked(LifecycleState.InFlight, null, detail, now);
        }

        private LifecycleEvent succeedLocked(String detail, long now) {
            clearAttemptLocalLocked();
            return setStateLocked(LifecycleState.Succeeded, null, detail, now);
        }

        private LifecycleEvent failLocked(FailureReason reason, String detail, long now) {
            clearAttemptLocalLocked();
            return setStateLocked(LifecycleState.Failed, reason, detail, now);
        }

        private PendingActions failureActionsLocked(FailureReason reason, String detail, String warning, long now) {
            return new PendingActions(failLocked(reason, detail, now), null, null, warning);
        }

        private LifecycleEvent clearWindowLocked(long now) {
            LifecycleEvent event = transientEventLocked(LifecycleState.NoCurrentOrder, windowOrigin, windowRequest, null, "", 0L);
            windowRequest = null;
            activeRequest = null;
            windowOrigin = null;
            windowStartedAtMs = 0L;
            windowEndsAtMs = 0L;
            pendingGotoTpaAtMs = 0L;
            kitMustLeaveBeforeProof = false;
            kitNearbyTicks = 0;
            currentState = LifecycleState.NoCurrentOrder;
            currentFailureReason = null;
            currentDetail = "";
            return event;
        }

        private boolean consumeManualDisplayLocked() {
            boolean result = manualDisplayAttached;
            manualDisplayAttached = false;
            return result;
        }

        private void clearAttemptLocalLocked() {
            activeRequest = null;
            pendingGotoTpaAtMs = 0L;
            kitNearbyTicks = 0;
            kitMustLeaveBeforeProof = false;
        }

        private void initializeKitProofLocked() {
            kitNearbyTicks = 0;
            if (activeRequest != null && activeRequest.mode == Mode.Kit) {
                kitMustLeaveBeforeProof = isKitbotNearbyNow();
            } else {
                kitMustLeaveBeforeProof = false;
            }
        }

        private boolean updateKitArrivalProofLocked() {
            boolean nearby = isKitbotNearbyNow();
            if (kitMustLeaveBeforeProof) {
                if (!nearby) kitMustLeaveBeforeProof = false;
                kitNearbyTicks = 0;
                return false;
            }

            if (!nearby) {
                kitNearbyTicks = 0;
                return false;
            }

            kitNearbyTicks++;
            return kitNearbyTicks >= KITBOT_NEARBY_TICKS_REQUIRED;
        }

        private boolean isKitbotNearbyNow() {
            if (MeteorClient.mc == null || MeteorClient.mc.player == null || MeteorClient.mc.world == null) return false;

            for (PlayerEntity player : MeteorClient.mc.world.getPlayers()) {
                if (player == null) continue;
                if (!KITBOT_NAME.equals(player.getName().getString())) continue;
                if (player == MeteorClient.mc.player) continue;
                if (isWithinKitArrivalProofRange(player)) return true;
            }

            return false;
        }

        private boolean isWithinKitArrivalProofRange(PlayerEntity player) {
            if (MeteorClient.mc == null || MeteorClient.mc.player == null) return false;

            double dx = player.getX() - MeteorClient.mc.player.getX();
            double dy = player.getY() - MeteorClient.mc.player.getY();
            double dz = player.getZ() - MeteorClient.mc.player.getZ();
            double horizontalDistanceSq = dx * dx + dz * dz;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            double maxSq = KITBOT_NEARBY_DISTANCE * KITBOT_NEARBY_DISTANCE;

            // Intentionally loose by design: either full 3D distance or horizontal distance is
            // enough to prove nearby arrival, and this should not be simplified casually.
            return distanceSq <= maxSq || horizontalDistanceSq <= maxSq;
        }

        private boolean isWindowActiveLocked(long now) {
            return windowRequest != null && now < windowEndsAtMs;
        }

        private long remainingWindowMsLocked(long now) {
            if (windowRequest == null) return 0L;
            return Math.max(0L, windowEndsAtMs - now);
        }

        private LifecycleEvent setStateLocked(LifecycleState state, FailureReason reason, String detail, long now) {
            currentState = state;
            currentFailureReason = reason;
            currentDetail = detail == null ? "" : detail;
            return transientEventLocked(state, windowOrigin, windowRequest, reason, currentDetail, remainingWindowMsLocked(now));
        }

        private LifecycleEvent transientEventLocked(
            LifecycleState state,
            RequestOrigin origin,
            ManagedRequest request,
            FailureReason reason,
            String detail,
            long remainingWindowMs
        ) {
            return new LifecycleEvent(
                state,
                origin,
                request == null ? null : request.mode,
                reason,
                detail == null ? "" : detail,
                remainingWindowMs
            );
        }

        private void fire(LifecycleEvent event) {
            for (LifecycleListener listener : listeners) {
                try {
                    listener.onKitbotLifecycle(event);
                } catch (Throwable t) {
                    THMAddon.LOG.warn("KitBot lifecycle listener failed: {}", t.getMessage(), t);
                }
            }
        }

        private void runPendingActions(PendingActions actions) {
            if (actions == null) return;
            if (actions.chatCommand != null) ChatUtils.sendPlayerMsg(actions.chatCommand);
            if (actions.info != null) announceInfo(actions.info);
            if (actions.warning != null) announceWarning(actions.warning);
            if (actions.event != null) fire(actions.event);
        }
    }

    private record PendingActions(
        LifecycleEvent event,
        String chatCommand,
        String info,
        String warning
    ) {}

    public enum Mode {
        Update,
        Goto,
        Kit,
        Send,
        Token,
        Claim
    }

    public enum Direction {
        West("W"),
        East("E"),
        North("N"),
        South("S"),
        NorthEast("NE"),
        SouthEast("SE"),
        SouthWest("SW"),
        NorthWest("NW"),
        DugWest("dugW"),
        DugEast("dugE"),
        DugNorth("dugN"),
        DugSouth("dugS"),
        DugNorthEast("dugNE"),
        DugSouthEast("dugSE"),
        DugSouthWest("dugSW"),
        DugNorthWest("dugNW");

        public final String command;

        Direction(String command) {
            this.command = command;
        }

        public static Direction fromString(String value) {
            if (value == null) return null;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Direction direction : values()) {
                if (direction.command.toLowerCase(Locale.ROOT).equals(normalized)) return direction;
                if (direction.name().toLowerCase(Locale.ROOT).equals(normalized)) return direction;
            }
            return null;
        }
    }

    public enum KitName {
        Refill("Refill", 1),
        Pvp("PvP", 0.5),
        Echest("Echest", 0.2),
        Pickaxe("Pickaxe", 1),
        Highway("Highway", 0.2),
        Lights("Lights", 1),
        Travel("Travel", 1.5),
        Grief("Grief", 1.5),
        Redstone("Redstone", 1),
        Exp("Exp", 1),
        Logs("Logs", 1),
        Mapart("Mapart", 1.5),
        Stash("Stash", 1),
        Bricks("Bricks", 1),
        Gapples("Gapples", 1),
        Concrete("Concrete", 1);

        public final String label;
        public final double tokens;

        KitName(String label, double tokens) {
            this.label = label;
            this.tokens = tokens;
        }

        @Override
        public String toString() {
            return label;
        }

        public String describe() {
            return String.format(Locale.ROOT, "%s (%.1f tokens)", label, tokens);
        }

        public static KitName fromString(String value) {
            if (value == null) return null;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (KitName kit : values()) {
                if (kit.label.toLowerCase(Locale.ROOT).equals(normalized) || kit.name().equalsIgnoreCase(normalized)) {
                    return kit;
                }
            }
            return null;
        }

        public static String list() {
            StringBuilder sb = new StringBuilder();
            for (KitName kit : values()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(kit.describe());
            }
            return sb.toString();
        }
    }
}
