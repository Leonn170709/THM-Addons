/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(value = Notebot.class, remap = false)
public abstract class NotebotMixin extends Module {
    public NotebotMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Shadow @Final private SettingGroup sgGeneral;
    @Shadow @Final public Setting<Integer> concurrentTuneBlocks;
    @Shadow @Final public Setting<Boolean> polyphonic;
    @Shadow @Final public Setting<Boolean> autoRotate;
    @Shadow @Final public Setting<Boolean> swingArm;
    @Shadow private Song song;
    @Shadow private int currentTick;
    @Shadow private boolean anyNoteblockTuned;
    @Shadow @Final private Map<Note, BlockPos> noteBlockPositions;
    @Shadow @Final private Map<BlockPos, Integer> tuneHits;
    @Shadow @Final private java.util.Set<BlockPos> clickedBlocks;

    @Shadow public abstract void pause();

    @Unique private Setting<Integer> thm$tuneHitsPerTick;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$init(CallbackInfo ci) {
        thm$tuneHitsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("tune-hits-per-tick")
            .description("Hits per noteblock per tick while tuning.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 24)
            .build()
        );
    }

    // Batching is safe: the server dedupes a noteblock's sound events per tick and plays the final pitch.
    @Inject(method = "tuneBlocks", at = @At("HEAD"), cancellable = true)
    private void thm$tuneBlocks(CallbackInfo ci) {
        ci.cancel();
        if (mc.level == null || mc.player == null) return;
        // Server skips NoteBlock#onUse while sneaking with an item and uses the item instead.
        if (mc.player.isSecondaryUseActive()) return;
        if (swingArm.get()) mc.player.swing(InteractionHand.MAIN_HAND);

        int iterations = 0;
        Iterator<Map.Entry<BlockPos, Integer>> iterator = tuneHits.entrySet().iterator();
        while (iterator.hasNext() && iterations++ < concurrentTuneBlocks.get()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            BlockPos pos = entry.getKey();
            int hits = Math.min(entry.getValue(), thm$tuneHitsPerTick.get());

            if (autoRotate.get()) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 100, () -> thm$tune(pos, hits));
            else thm$tune(pos, hits);

            clickedBlocks.add(pos);
            if (entry.getValue() == hits) iterator.remove();
            else entry.setValue(entry.getValue() - hits);
        }
    }

    // Upstream returns on the first unmapped note (drops the rest of the chord) and ignores polyphonic.
    @Inject(method = "onTickPlay", at = @At("HEAD"), cancellable = true)
    private void thm$onTickPlay(CallbackInfo ci) {
        ci.cancel();
        Collection<Note> notes = song.getNotesMap().get(currentTick);

        BlockPos first = null;
        for (Note note : notes) {
            first = noteBlockPositions.get(note);
            if (first != null) break;
        }
        if (first == null) return;

        // START_DESTROY_BLOCK insta-mines the noteblock with an Efficiency IV+ axe or Haste.
        BlockState state = mc.level.getBlockState(first);
        if (state.is(Blocks.NOTE_BLOCK) && state.getDestroyProgress(mc.player, mc.level, first) >= 1) {
            error("Your held item would break the noteblocks. Switch items and resume.");
            pause();
            return;
        }

        if (autoRotate.get()) Rotations.rotate(Rotations.getYaw(first), Rotations.getPitch(first));
        if (swingArm.get()) mc.player.swing(InteractionHand.MAIN_HAND);

        for (Note note : notes) {
            BlockPos pos = noteBlockPositions.get(note);
            if (pos == null) continue;
            thm$send(seq -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.DOWN, seq));
            if (!polyphonic.get()) break;
        }
    }

    @Unique
    private void thm$tune(BlockPos pos, int hits) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false);
        for (int i = 0; i < hits; i++) thm$send(seq -> new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, seq));
        anyNoteblockTuned = true;
    }

    @Unique
    private void thm$send(net.minecraft.client.multiplayer.prediction.PredictiveAction creator) {
        if (mc.gameMode == null || mc.level == null) return;
        ((ClientPlayerInteractionManagerTHMAccessor) mc.gameMode).thm$sendSequencedPacket(mc.level, creator);
    }
}
