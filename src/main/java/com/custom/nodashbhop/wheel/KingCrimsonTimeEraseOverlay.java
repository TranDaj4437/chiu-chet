package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.AnubisSlashBuffHandler;
import com.custom.nodashbhop.CombinedNetwork;
import com.custom.nodashbhop.JCraftStandCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Handles:
 * 1. Client trigger for Anubis extended sword slash on left-click.
 * 2. Improved anime-style Crimson-Void visual overlay + HUD countdown bar during King Crimson Time Erase (10s).
 */
public class KingCrimsonTimeEraseOverlay {
    private static final int MAX_TE_TICKS = 200; // 10 seconds
    private static final DustParticleOptions CRIMSON_DUST = new DustParticleOptions(new Vector3f(0.92F, 0.08F, 0.28F), 1.15F);
    private static final DustParticleOptions VOID_VIOLET = new DustParticleOptions(new Vector3f(0.42F, 0.05F, 0.55F), 1.25F);

    private int currentTeTicks = 0;

    @SubscribeEvent
    public void onLeftClickAnubis(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (AnubisSlashBuffHandler.isAnubisSwordReady(mc.player)) {
            CombinedNetwork.sendAnubisSlash();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTeTicks = 0;
            return;
        }

        int te = getKingCrimsonTETicks(player);
        currentTeTicks = te;

        // Spawn atmospheric crimson-violet rift particles around player during Time Erase
        if (te > 0 && (player.tickCount % 2) == 0) {
            for (int i = 0; i < 4; i++) {
                double ox = (mc.level.random.nextDouble() - 0.5) * 6.0;
                double oy = mc.level.random.nextDouble() * 3.0;
                double oz = (mc.level.random.nextDouble() - 0.5) * 6.0;
                mc.level.addParticle(
                        (i % 2 == 0) ? CRIMSON_DUST : VOID_VIOLET,
                        player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                        0.0, 0.03, 0.0
                );
            }
            if ((player.tickCount % 4) == 0) {
                double ox = (mc.level.random.nextDouble() - 0.5) * 5.0;
                double oy = 0.4 + mc.level.random.nextDouble() * 2.2;
                double oz = (mc.level.random.nextDouble() - 0.5) * 5.0;
                mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, player.getX() + ox, player.getY() + oy, player.getZ() + oz, 0.0, 0.02, 0.0);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (currentTeTicks <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        float time = (mc.player.tickCount + event.getPartialTick()) * 0.12F;
        float pulse = 0.72F + 0.28F * (float) Math.sin(time);

        // 1. Subtle deep crimson-purple void tint over screen
        int tintAlpha = (int) (26 * pulse);
        gui.fill(0, 0, w, h, (tintAlpha << 24) | 0x3A0418);

        // 2. Dramatic crimson-void vignette along all 4 borders
        int edgeLayers = 8;
        int maxBand = Math.max(24, Math.min(w, h) / 7);
        for (int i = 0; i < edgeLayers; i++) {
            float frac = 1.0F - (float) i / edgeLayers;
            int alpha = (int) (frac * frac * 92 * pulse);
            int color = (alpha << 24) | ((i % 2 == 0) ? 0x990826 : 0x450638);
            int thick = Math.max(2, maxBand / edgeLayers);
            int off = i * thick;
            gui.fill(off, off, w - off, off + thick, color);             // top
            gui.fill(off, h - off - thick, w - off, h - off, color);     // bottom
            gui.fill(off, off + thick, off + thick, h - off - thick, color); // left
            gui.fill(w - off - thick, off + thick, w - off, h - off - thick, color); // right
        }

        // 3. Drifting fractured crimson time-shard streaks in outer peripheral space
        for (int i = 0; i < 14; i++) {
            float seed = i * 47.3F;
            int sx = (int) ((Math.sin(seed + time * 0.22F) * 0.5F + 0.5F) * w);
            int sy = (int) (((seed * 13.7F + time * (12.0F + (i % 5) * 4.0F)) % h + h) % h);
            // Keep center clear for combat visibility
            if (Math.abs(sx - w / 2) < w * 0.24 && Math.abs(sy - h / 2) < h * 0.24) continue;
            int len = 10 + (i % 4) * 6;
            int shardColor = (i % 3 == 0) ? 0x99FF2A5F : 0x77C084FC;
            gui.fill(sx, sy, sx + len, sy + 2, shardColor);
            gui.fill(sx + 2, sy - 1, sx + len - 2, sy + 3, 0x33FF5E8A);
        }

        // 4. Sleek top-center TIME ERASE countdown bar (10.0s)
        float ratio = Math.min(1.0F, Math.max(0.0F, currentTeTicks / (float) MAX_TE_TICKS));
        int barW = 152;
        int barH = 6;
        int bx = (w - barW) / 2;
        int by = 18;

        Font font = mc.font;
        String title = String.format("§c✦ §lTIME ERASE §r§d• §f%.1fs §c✦", currentTeTicks / 20.0F);
        gui.drawCenteredString(font, title, w / 2, by - 11, 0xFFFFE4EC);

        gui.fill(bx - 2, by - 2, bx + barW + 2, by + barH + 2, 0xCC12030C);
        gui.renderOutline(bx - 2, by - 2, barW + 4, barH + 4, 0xDDFF2A5F);
        int fillW = Math.round(barW * ratio);
        if (fillW > 0) {
            gui.fill(bx, by, bx + fillW, by + barH, 0xFFE11D48);
            gui.fill(bx, by, bx + fillW, by + 2, 0xFFFF85A1);
        }
    }

    private int getKingCrimsonTETicks(LocalPlayer player) {
        Object stand = JCraftStandCheck.getStandEntity(player);
        if (stand == null) return 0;
        if (!stand.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("kingcrimson")) {
            return 0;
        }
        Object te = JCraftStandCheck.invokeNoArg(stand, "getTETime");
        return (te instanceof Number n) ? n.intValue() : 0;
    }
}
