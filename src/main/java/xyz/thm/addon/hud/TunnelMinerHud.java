package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.TunnelMinerModule;

public class TunnelMinerHud extends HudElement {
    public static final HudElementInfo<TunnelMinerHud> INFO = new HudElementInfo<>(THMAddon.HUD_GROUP, "tunnel-miner-hud", "Displays Tunnel Miner stats.", TunnelMinerHud::new);

    public TunnelMinerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (TunnelMinerModule.INSTANCE == null || !TunnelMinerModule.INSTANCE.isActive()) {
            setSize(renderer.textWidth("Tunnel Miner: Inactive", true), renderer.textHeight(true));
            renderer.text("Tunnel Miner: Inactive", x, y, Color.GRAY, true);
            return;
        }

        double eta = TunnelMinerModule.INSTANCE.getEtaSeconds();
        String time;
        if (eta >= 3600) {
            time = String.format("%.2fh", eta / 3600.0);
        } else if (eta >= 60) {
            time = String.format("%.1fm", eta / 60.0);
        } else {
            time = String.format("%.0fs", eta);
        }

        String text = String.format("Tunnel: %d blocks left (ETA: %s)", TunnelMinerModule.INSTANCE.getBlocksLeft(), time);

        if (eta < 0) {
            text = String.format("Tunnel: %d blocks left", TunnelMinerModule.INSTANCE.getBlocksLeft());
        }

        setSize(renderer.textWidth(text, true), renderer.textHeight(true));
        renderer.text(text, x, y, Color.WHITE, true);
    }
}
