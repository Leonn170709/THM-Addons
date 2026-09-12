/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.thm.addon.THMAddon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.block.enums.NoteBlockInstrument.*;

public class NotebotStealer extends Module {
    private static final long TICK_NANOS = 50_000_000L;
    // One server tick's sounds arrive as one burst; this keeps a chord on one tick.
    private static final long CHORD_WINDOW_NANOS = 20_000_000L;
    private static final long SILENCE_NANOS = 3_000_000_000L;
    // Shorter recordings are tuning noise, not songs.
    private static final int MIN_LENGTH_TICKS = 100;
    // Index = NBS instrument id (opennbs.org/nbs).
    private static final List<NoteBlockInstrument> NBS_INSTRUMENTS = List.of(
        HARP, BASS, BASEDRUM, SNARE, HAT, GUITAR, FLUTE, BELL, CHIME, XYLOPHONE, IRON_XYLOPHONE, COW_BELL, DIDGERIDOO, BIT, BANJO, PLING
    );
    private static final Map<Identifier, NoteBlockInstrument> BY_SOUND = new HashMap<>();

    static {
        for (NoteBlockInstrument instrument : NBS_INSTRUMENTS) BY_SOUND.put(instrument.getSound().value().id(), instrument);
    }

    private final Map<BlockPos, Note> lastHeard = new HashMap<>();
    private final Multimap<Integer, Note> notes = MultimapBuilder.treeKeys().linkedHashSetValues().build();
    private long startNanos;
    private long lastNoteNanos;
    private int lastTick;

    public NotebotStealer() {
        super(THMAddon.MAIN, "NotebotStealer", "Records nearby noteblock songs as .nbs, one file per song.");
    }

    @Override
    public void onActivate() {
        lastHeard.clear();
        notes.clear();
    }

    @Override
    public void onDeactivate() {
        save();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        save();
        lastHeard.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!notes.isEmpty() && System.nanoTime() - lastNoteNanos > SILENCE_NANOS) save();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlaySoundS2CPacket packet)) return;
        NoteBlockInstrument instrument = BY_SOUND.get(packet.getSound().value().id());
        if (instrument == null) return;
        int level = Math.round((float) (12 * Math.log(packet.getPitch()) / Math.log(2))) + 12;
        if (level < 0 || level > 24) return;

        // Timestamped on the netty thread so the hop to the client thread doesn't skew timing.
        long now = System.nanoTime();
        Note note = new Note(instrument, level);
        BlockPos pos = BlockPos.ofFloored(packet.getX(), packet.getY(), packet.getZ());
        mc.execute(() -> onNote(note, pos, now));
    }

    private void onNote(Note note, BlockPos pos, long now) {
        if (mc.world == null) return;
        // A noteblock changing pitch is being tuned, so the song before it is over.
        // Plugin-played songs have no noteblock at the sound position and only split on silence.
        if (mc.world.getBlockState(pos).isOf(Blocks.NOTE_BLOCK)) {
            Note previous = lastHeard.put(pos, note);
            if (previous != null && !previous.equals(note)) {
                save();
                return;
            }
        }

        int tick;
        if (notes.isEmpty()) {
            startNanos = now;
            tick = 0;
        } else {
            tick = now - lastNoteNanos < CHORD_WINDOW_NANOS ? lastTick : (int) Math.round((now - startNanos) / (double) TICK_NANOS);
        }
        notes.put(tick, note);
        lastTick = tick;
        lastNoteNanos = now;
    }

    private void save() {
        int length = notes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (length < MIN_LENGTH_TICKS) {
            notes.clear();
            return;
        }

        String name = "stolen-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        byte[] nbs = toNbs(notes, name, length);
        int count = notes.size();
        notes.clear();

        Path path = MeteorClient.FOLDER.toPath().resolve("notebot").resolve(name + ".nbs");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, nbs);
            info("Saved %s (%d notes, %ds).", path.getFileName(), count, length / 20);
        } catch (IOException e) {
            error("Couldn't save song: %s", e.getMessage());
        }
    }

    // NBS v5 at 20 ticks/s, one layer per simultaneous note.
    private static byte[] toNbs(Multimap<Integer, Note> song, String name, int length) {
        int layers = song.asMap().values().stream().mapToInt(Collection::size).max().orElse(1);
        LittleEndianOut out = new LittleEndianOut();

        out.s(0); out.b(5); out.b(NBS_INSTRUMENTS.size()); out.s(length); out.s(layers);
        out.str(name); out.str("NotebotStealer"); out.str(""); out.str("");
        out.s(2000); out.b(0); out.b(10); out.b(4);
        for (int i = 0; i < 5; i++) out.i(0);
        out.str("");
        out.b(0); out.b(0); out.s(0);

        int previousTick = -1;
        for (Map.Entry<Integer, Collection<Note>> entry : song.asMap().entrySet()) {
            out.s(entry.getKey() - previousTick);
            previousTick = entry.getKey();
            for (Note note : entry.getValue()) {
                out.s(1);
                out.b(NBS_INSTRUMENTS.indexOf(note.getInstrument()));
                out.b(note.getNoteLevel() + 33);
                out.b(100); out.b(100); out.s(0);
            }
            out.s(0);
        }
        out.s(0);

        for (int i = 0; i < layers; i++) {
            out.str(""); out.b(0); out.b(100); out.b(100);
        }
        out.b(0);
        return out.toByteArray();
    }

    private static final class LittleEndianOut extends ByteArrayOutputStream {
        void b(int v) { write(v); }
        void s(int v) { write(v); write(v >>> 8); }
        void i(int v) { s(v); s(v >>> 16); }
        void str(String v) {
            byte[] bytes = v.getBytes(StandardCharsets.UTF_8);
            i(bytes.length);
            write(bytes, 0, bytes.length);
        }
    }
}
