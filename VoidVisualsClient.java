package com.voidvisuals.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

public class VoidVisualsClient implements ClientModInitializer {
    private static final String MOD_ID = "voidvisuals";
    private static boolean menuOpen = false;
    private static boolean musicPanelOpen = false;
    private static boolean musicPlaying = false;
    private static int selectedTrack = 0;
    private static int playingTrack = -1;
    private static final long START_TIME = System.currentTimeMillis();
    private static KeyMapping menuKey;
    private static KeyMapping musicTabKey;
    private static KeyMapping playPauseKey;
    private static KeyMapping nextTrackKey;
    private static KeyMapping trackOneKey;
    private static KeyMapping trackTwoKey;
    private static KeyMapping trackThreeKey;

    // ---- Palette: dark glass + violet accent ----
    private static final int PANEL       = 0xE60B0C14; // translucent near-black
    private static final int PANEL_EDGE  = 0x2EFFFFFF; // hairline border
    private static final int ROW         = 0x12FFFFFF; // subtle row fill
    private static final int ROW_ACTIVE  = 0x388B5CF6; // selected row
    private static final int ACCENT      = 0xFF8B5CF6; // violet
    private static final int ACCENT_2    = 0xFFC4A7FF; // light violet
    private static final int TEXT        = 0xFFF2F2F8;
    private static final int MUTED       = 0xFF8D90A6;
    private static final int TRACK_OFF   = 0x33FFFFFF;
    private static final int KNOB        = 0xFFFFFFFF;

    private record Track(String title, String subtitle, ResourceLocation soundId) {}

    private static final Track[] TRACKS = new Track[] {
            new Track("VOID NIGHT", "AMBIENT / 01", id("music.void_night")),
            new Track("DARK SPACE", "AMBIENT / 02", id("music.dark_space")),
            new Track("WHITE DREAM", "AMBIENT / 03", id("music.white_dream"))
    };

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(id("main"));
        menuKey = register("key.voidvisuals.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        musicTabKey = register("key.voidvisuals.music_tab", GLFW.GLFW_KEY_M, category);
        playPauseKey = register("key.voidvisuals.play_pause", GLFW.GLFW_KEY_P, category);
        nextTrackKey = register("key.voidvisuals.next_track", GLFW.GLFW_KEY_N, category);
        trackOneKey = register("key.voidvisuals.track_one", GLFW.GLFW_KEY_1, category);
        trackTwoKey = register("key.voidvisuals.track_two", GLFW.GLFW_KEY_2, category);
        trackThreeKey = register("key.voidvisuals.track_three", GLFW.GLFW_KEY_3, category);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) menuOpen = !menuOpen;
            while (musicTabKey.consumeClick()) musicPanelOpen = !musicPanelOpen;
            while (playPauseKey.consumeClick()) toggleMusic();
            while (nextTrackKey.consumeClick()) selectTrack((selectedTrack + 1) % TRACKS.length, true);
            while (trackOneKey.consumeClick()) selectTrack(0, true);
            while (trackTwoKey.consumeClick()) selectTrack(1, true);
            while (trackThreeKey.consumeClick()) selectTrack(2, true);
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                id("overlay"),
                (graphics, deltaTracker) -> renderOverlay(graphics)
        );
    }

    private static KeyMapping register(String translationKey, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey, InputConstants.Type.KEYSYM, key, category
        ));
    }

    private static void toggleMusic() {
        if (musicPlaying) {
            stopCustomTrack();
            musicPlaying = false;
        } else {
            playSelectedTrack();
        }
    }

    private static void selectTrack(int index, boolean playIfActive) {
        selectedTrack = index;
        if (musicPlaying || playIfActive) playSelectedTrack();
    }

    private static void playSelectedTrack() {
        Minecraft mc = Minecraft.getInstance();
        stopCustomTrack();
        Track track = TRACKS[selectedTrack];
        SoundEvent event = SoundEvent.createVariableRangeEvent(track.soundId());
        mc.getSoundManager().play(SimpleSoundInstance.forMusic(event));
        playingTrack = selectedTrack;
        musicPlaying = true;
    }

    private static void stopCustomTrack() {
        if (playingTrack >= 0) {
            Minecraft.getInstance().getSoundManager().stop(TRACKS[playingTrack].soundId(), SoundSource.MUSIC);
            playingTrack = -1;
        }
    }

    // =====================================================================
    //  Rendering
    // =====================================================================

    private static void renderOverlay(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        long elapsed = System.currentTimeMillis() - START_TIME;

        renderBadge(g, mc, elapsed);

        if (mc.screen == null) renderCrosshair(g, width / 2, height / 2);
        if (musicPanelOpen) renderMusicPanel(g, mc, width, elapsed);
        if (menuOpen) renderMenu(g, mc, width, height);
    }

    /** Top-left brand pill with a breathing violet dot. */
    private static void renderBadge(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc, long elapsed) {
        int x = 8, y = 8, w = 104, h = 22;
        roundRect(g, x, y, x + w, y + h, 11, PANEL);
        roundRect(g, x, y, x + w, y + h, 11, PANEL_EDGE);
        roundRect(g, x + 1, y + 1, x + w - 1, y + h - 1, 10, PANEL);

        float pulse = 0.55f + 0.45f * (float) Math.sin(elapsed / 650.0);
        int dotColor = withAlpha(ACCENT, pulse);
        roundRect(g, x + 9, y + 8, x + 15, y + 14, 3, dotColor);

        g.drawString(mc.font, Component.literal("VOID"), x + 22, y + 7, TEXT, false);
        g.drawString(mc.font, Component.literal("VISUALS"), x + 54, y + 7, MUTED, false);
    }

    /** Minimal crosshair: centre dot plus four ticks. */
    private static void renderCrosshair(net.minecraft.client.gui.GuiGraphicsExtractor g, int cx, int cy) {
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, ACCENT);
        int tick = 0xD9FFFFFF;
        g.fill(cx - 6, cy, cx - 3, cy + 1, tick);
        g.fill(cx + 4, cy, cx + 7, cy + 1, tick);
        g.fill(cx, cy - 6, cx + 1, cy - 3, tick);
        g.fill(cx, cy + 4, cx + 1, cy + 7, tick);
    }

    /** Settings card: header, toggle rows, footer hints. */
    private static void renderMenu(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc, int width, int height) {
        int w = 220, h = 140;
        int x = width / 2 - w / 2;
        int y = height / 2 - h / 2;

        roundRect(g, x - 1, y - 1, x + w + 1, y + h + 1, 11, PANEL_EDGE);
        roundRect(g, x, y, x + w, y + h, 10, PANEL);

        // Header
        g.drawString(mc.font, Component.literal("VOID VISUALS"), x + 16, y + 14, TEXT, false);
        g.drawString(mc.font, Component.literal("WHITE / BLACK  -  v1.0.0"), x + 16, y + 26, MUTED, false);
        gradientH(g, x + 16, y + 39, w - 32, 1, ACCENT, withAlpha(ACCENT, 0f));

        // Toggle rows
        drawToggleRow(g, mc, x, y + 46, "HUD", true);
        drawToggleRow(g, mc, x, y + 68, "CROSSHAIR", true);
        drawToggleRow(g, mc, x, y + 90, "MUSIC PANEL", musicPanelOpen);

        // Footer
        g.fill(x + 16, y + 116, x + w - 16, y + 117, ROW);
        g.drawString(mc.font, Component.literal("M  music      RSHIFT  close"), x + 16, y + 122, MUTED, false);
    }

    private static void drawToggleRow(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc,
                                      int x, int ry, String label, boolean on) {
        int rx1 = x + 12, rx2 = x + 208, rh = 18;
        roundRect(g, rx1, ry, rx2, ry + rh, 5, ROW);

        g.drawString(mc.font, Component.literal(label), rx1 + 10, ry + 5, TEXT, false);

        // Pill switch
        int tx1 = rx2 - 34, ty1 = ry + 5, tx2 = rx2 - 10, ty2 = ry + 13;
        roundRect(g, tx1, ty1, tx2, ty2, 4, on ? ACCENT : TRACK_OFF);
        int kx = on ? tx2 - 6 : tx1 + 2;
        roundRect(g, kx, ty1 + 1, kx + 4, ty2 - 1, 2, KNOB);
    }

    /** Music card: header, status pill, track list, animated equaliser. */
    private static void renderMusicPanel(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc,
                                         int width, long elapsed) {
        int w = 184, h = 134;
        int x = width - w - 10;
        int y = 8;

        roundRect(g, x - 1, y - 1, x + w + 1, y + h + 1, 11, PANEL_EDGE);
        roundRect(g, x, y, x + w, y + h, 10, PANEL);

        // Header
        g.drawString(mc.font, Component.literal("VOID MUSIC"), x + 12, y + 11, TEXT, false);
        g.drawString(mc.font, Component.literal("BUILT-IN PLAYLIST"), x + 12, y + 23, MUTED, false);

        int sx1 = x + 124, sy1 = y + 10, sx2 = x + 172, sy2 = y + 22;
        roundRect(g, sx1, sy1, sx2, sy2, 6, musicPlaying ? ACCENT : TRACK_OFF);
        g.drawString(mc.font, Component.literal(musicPlaying ? "PLAYING" : "PAUSED"),
                sx1 + 7, sy1 + 4, TEXT, false);

        // Track list
        for (int i = 0; i < TRACKS.length; i++) {
            int ty = y + 42 + i * 22;
            boolean active = i == selectedTrack;
            roundRect(g, x + 8, ty, x + w - 8, ty + 19, 5, active ? ROW_ACTIVE : ROW);
            if (active) roundRect(g, x + 8, ty + 4, x + 10, ty + 15, 1, ACCENT_2);

            g.drawString(mc.font, Component.literal(TRACKS[i].title()), x + 17, ty + 5,
                    active ? TEXT : MUTED, false);
            g.drawString(mc.font, Component.literal("0" + (i + 1)), x + w - 24, ty + 5,
                    active ? ACCENT_2 : MUTED, false);
        }

        // Equaliser: 16 gradient bars, animated while playing
        int baseY = y + h - 10;
        for (int i = 0; i < 16; i++) {
            int bh = musicPlaying
                    ? 2 + (int) (10 * Math.abs(Math.sin(elapsed / 170.0 + i * 0.55)))
                    : 2;
            int bx = x + 12 + i * 6;
            int color = lerp(ACCENT_2, ACCENT, i / 15f);
            g.fill(bx, baseY - bh, bx + 3, baseY, color);
        }

        g.drawString(mc.font, Component.literal("P play  N next"), x + 12 + 16 * 6 + 2, y + h - 17, MUTED, false);
    }

    // =====================================================================
    //  Drawing helpers
    // =====================================================================

    /** Rounded rectangle built from fills (no shader needed). Keep colours opaque or translucent once per pixel. */
    private static void roundRect(net.minecraft.client.gui.GuiGraphicsExtractor g,
                                  int x1, int y1, int x2, int y2, int r, int color) {
        int h = y2 - y1;
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, h / 2)));
        if (r == 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        // Centre block (full height between the corner columns)
        g.fill(x1 + r, y1, x2 - r, y2, color);
        // Side strips for the middle rows only
        g.fill(x1, y1 + r, x1 + r, y2 - r, color);
        g.fill(x2 - r, y1 + r, x2, y2 - r, color);
        // Corner rows, curved via circle equation
        for (int dy = 0; dy < r; dy++) {
            double dist = r - dy - 0.5;
            int inset = r - (int) Math.round(Math.sqrt((double) r * r - dist * dist));
            g.fill(x1 + inset, y1 + dy, x1 + r, y1 + dy + 1, color);
            g.fill(x2 - r, y1 + dy, x2 - inset, y1 + dy + 1, color);
            g.fill(x1 + inset, y2 - dy - 1, x1 + r, y2 - dy, color);
            g.fill(x2 - r, y2 - dy - 1, x2 - inset, y2 - dy, color);
        }
    }

    private static void gradientH(net.minecraft.client.gui.GuiGraphicsExtractor g,
                                  int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i++) {
            float t = i / (float) Math.max(1, w - 1);
            g.fill(x + i, y, x + i + 1, y + h, lerp(c1, c2, t));
        }
    }

    private static int withAlpha(int argb, float alphaMul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alphaMul)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int na = Math.round(aa + (ba - aa) * t);
        int nr = Math.round(ar + (br - ar) * t);
        int ng = Math.round(ag + (bg - ag) * t);
        int nb = Math.round(ab + (bb - ab) * t);
        return (na << 24) | (nr << 16) | (ng << 8) | nb;
    }
}
