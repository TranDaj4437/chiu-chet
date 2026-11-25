package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.CombinedNetwork;
import com.custom.nodashbhop.JCraftStandCheck;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Set;

/**
 * Unlocks normal player bare-hand attacks AND all item usage (swords, tools, blocks, food,
 * bows, guns, etc.) for Stands that have no normal Light attack or are autonomous:
 * - Base Purple Haze (purple_haze)
 * - Mandom, The Sun, Atum, Osiris, Dragon's Dream, Foo Fighters, Goo Goo Dolls, Diver Down, etc.
 * Also maps JCraft's Barrage key to Mandom's Rewind.
 */
public class MandomAttackFixHandler {
    private static final Set<String> PLAYER_HAND_ATTACK_STANDS = Set.of(
            "purple_haze",
            "mandom",
            "the_sun",
            "atum",
            "osiris",
            "dragons_dream",
            "foo_fighters",
            "goo_goo_dolls",
            "diver_down",
            "chariot_requiem",
            "silver_chariot_possessed"
    );

    private final WheelController controller;
    private boolean barrageWasDown = false;
    private KeyMapping cachedBarrageKey = null;
    private boolean barrageKeyScanned = false;

    public MandomAttackFixHandler(WheelController controller) {
        this.controller = controller;
    }

    private boolean shouldUsePlayerHandAndItems(Minecraft mc) {
        if (mc.player == null) return false;
        Object standEntity = JCraftStandCheck.getStandEntity(mc.player);
        if (standEntity == null) return false;

        // If player explicitly switched to Spec wheel mode and owns a Spec, let Spec handle M1
        if (controller.isSpecMode() && JCraftStandCheck.hasSpec(mc.player)) return false;

        String standId = JCraftStandCheck.getStandId(mc.player).toLowerCase(Locale.ROOT);
        if (PLAYER_HAND_ATTACK_STANDS.contains(standId)) {
            return true;
        }

        // Dynamic fallback for any autonomous Stand that rejects direct user move init
        Object acceptsInit = JCraftStandCheck.invokeNoArg(standEntity, "acceptsUserMoveInit");
        return Boolean.FALSE.equals(acceptsInit);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
        if (!shouldUsePlayerHandAndItems(mc)) return;

        // 1. Left-click: bare-hand / weapon attack & block breaking (never routed through Stand)
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult entityHit) {
                mc.gameMode.attack(mc.player, entityHit.getEntity());
                mc.player.swing(InteractionHand.MAIN_HAND);
                event.setCanceled(true);
            } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.startDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.player.resetAttackStrengthTicker();
                mc.player.swing(InteractionHand.MAIN_HAND);
                event.setCanceled(true);
            }
            return;
        }

        // 2. Right-click: allow using/placing/eating/interacting with ANY held item freely
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            InteractionHand hand = !mc.player.getMainHandItem().isEmpty()
                    ? InteractionHand.MAIN_HAND
                    : (!mc.player.getOffhandItem().isEmpty() ? InteractionHand.OFF_HAND : null);
            if (hand == null) return;

            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult entityHit) {
                InteractionResult res = mc.gameMode.interact(mc.player, entityHit.getEntity(), hand);
                if (res.consumesAction()) {
                    if (res.shouldSwing()) mc.player.swing(hand);
                    event.setCanceled(true);
                    return;
                }
            } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                InteractionResult res = mc.gameMode.useItemOn(mc.player, hand, blockHit);
                if (res.consumesAction()) {
                    if (res.shouldSwing()) mc.player.swing(hand);
                    event.setCanceled(true);
                    return;
                }
            }
            InteractionResult useRes = mc.gameMode.useItem(mc.player, hand);
            if (useRes.consumesAction()) {
                if (useRes.shouldSwing()) mc.player.swing(hand);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            barrageWasDown = false;
            return;
        }
        if (!controller.isCurrentMandom()) {
            barrageWasDown = false;
            return;
        }

        if (!barrageKeyScanned && mc.options != null) {
            for (KeyMapping km : mc.options.keyMappings) {
                if (km.getName().equalsIgnoreCase("key.jcraft.barrage")) {
                    cachedBarrageKey = km;
                    break;
                }
            }
            barrageKeyScanned = true;
        }

        if (cachedBarrageKey != null) {
            boolean down = cachedBarrageKey.isDown();
            if (down && !barrageWasDown) {
                CombinedNetwork.sendWheelCast(WheelSlot.ULTIMATE.index, false, false, false);
            }
            barrageWasDown = down;
        }
    }
}
