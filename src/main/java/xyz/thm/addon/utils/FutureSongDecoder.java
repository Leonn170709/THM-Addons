/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.notebot.decoder.SongDecoder;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.meteorclient.utils.notebot.song.Song;
import net.minecraft.block.enums.NoteBlockInstrument;
import org.apache.commons.io.FilenameUtils;
import xyz.thm.addon.THMAddon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

// Future client .notebot: [instrument ordinal][note 0-24] per note, [0x40][ticks u16 LE] per delay.
public class FutureSongDecoder extends SongDecoder {
    private static final NoteBlockInstrument[] INSTRUMENTS = NoteBlockInstrument.values();

    // Copies new Future recordings (~/Future/songs) into Meteor's notebot folder; existing files are kept.
    public static void importFutureSongs() {
        Path source = Path.of(System.getProperty("user.home"), "Future", "songs");
        if (!Files.isDirectory(source)) return;
        Path target = MeteorClient.FOLDER.toPath().resolve("notebot");
        try (Stream<Path> songs = Files.list(source)) {
            Files.createDirectories(target);
            for (Path song : songs.filter(p -> p.toString().endsWith(".notebot")).toList()) {
                Path dest = target.resolve(song.getFileName());
                if (Files.notExists(dest)) Files.copy(song, dest);
            }
        } catch (IOException e) {
            THMAddon.LOG.warn("Couldn't import Future songs: {}", e.toString());
        }
    }

    @Override
    public Song parse(File file) throws Exception {
        byte[] data = Files.readAllBytes(file.toPath());
        // Set values: Future records every overlapping sound, so one tick can repeat a note many times.
        Multimap<Integer, Note> notes = MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();

        int tick = 0;
        int i = 0;
        while (i < data.length) {
            int type = data[i] & 0xFF;
            if (type == 0x40 && i + 2 < data.length) {
                tick += (data[i + 1] & 0xFF) | (data[i + 2] & 0xFF) << 8;
                i += 3;
            } else if (type < 0x40 && i + 1 < data.length) {
                if (type < INSTRUMENTS.length && INSTRUMENTS[type].canBePitched()) {
                    notes.put(tick, new Note(INSTRUMENTS[type], data[i + 1] & 0xFF));
                }
                i += 2;
            } else {
                throw new IOException("Bad .notebot record at byte " + i);
            }
        }

        if (notes.isEmpty()) throw new IOException("No notes in " + file.getName());
        return new Song(notes, FilenameUtils.getBaseName(file.getName()), "Future");
    }
}
