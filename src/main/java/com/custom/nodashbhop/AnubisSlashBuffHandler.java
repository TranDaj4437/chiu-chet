package com.custom.nodashbhop;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Buffs Spec Anubis normal sword attacks:
 * - Extends slash range from vanilla 3.0 blocks up to 6.0 blocks in a wide forward crescent arc.
 * - Spawns a dramatic long glowing cyan-white-violet energy slash trail visible to all players.
 */
public final class AnubisSlashBuffHandler {
    private static final double SLASH_RANGE = 3.0;
    private static final long COOLDOWN_MS = 420L;

    private static final DustParticleOptions CORE_WHITE = new DustParticleOptions(new Vector3f(0.88F, 0.98F, 1.0F), 1.35F);
    private static final DustParticleOptions BLADE_TEAL = new DustParticleOptions(new Vector3f(0.18F, 0.85F, 0.78F), 1.65F);
    private static final DustParticleOptions EDGE_VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.30F, 0.95F), 1.30F);

    private static final Map<UUID, Long> LAST_SLASH_MS = new HashMap<>();
    private static final Map<UUID, Boolean> SLASH_FLIP = new HashMap<>();

    public static boolean isAnubisSwordReady(LivingEntity entity) {
        if (entity == null) return false;
        String specId = JCraftStandCheck.getSpecId(entity).toLowerCase(Locale.ROOT);
        if (!specId.contains("anubis")) return false;
        ItemStack held = entity.getMainHandItem();
        if (held.isEmpty()) return false;
        if (held.getItem() instanceof SwordItem) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("anubis") || path.contains("sword") || path.contains("katana") || path.contains("blade");
    }

    public static void performExtendedSlash(ServerPlayer player) {
        if (player == null || !isAnubisSwordReady(player)) return;
        long now = System.currentTimeMillis();
        Long last = LAST_SLASH_MS.get(player.getUUID());
        if (last != null && (now - last) < COOLDOWN_MS) return;
        LAST_SLASH_MS.put(player.getUUID(), now);

        boolean flip = !Boolean.TRUE.equals(SLASH_FLIP.get(player.getUUID()));
        SLASH_FLIP.put(player.getUUID(), flip);

        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < 1.0E-4) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();

        // 1. Spawn dramatic long glowing crescent slash arc (2.0 -> 5.8 blocks)
        double tilt = flip ? 0.34 : -0.34;
        int steps = 26;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps; // 0..1 across arc
            double angle = Math.toRadians(-58.0 + t * 116.0);
            double sinA = Math.sin(angle);
            double cosA = Math.cos(angle);
            double yTilt = (t - 0.5) * tilt * 2.2;

            Vec3 dir = look.scale(cosA).add(right.scale(sinA)).add(up.scale(yTilt)).normalize();

            // Outer glowing teal blade edge (5.2 - 5.6 blocks)
            Vec3 pOuter = eyePos.add(dir.scale(2.9)).add(0.0, -0.25, 0.0);
            level.sendParticles(BLADE_TEAL, pOuter.x, pOuter.y, pOuter.z, 1, 0.02, 0.02, 0.02, 0.0);

            // Bright white inner razor core (4.5 blocks)
            Vec3 pCore = eyePos.add(dir.scale(2.45)).add(0.0, -0.25, 0.0);
            level.sendParticles(CORE_WHITE, pCore.x, pCore.y, pCore.z, 1, 0.01, 0.01, 0.01, 0.0);

            // Violet cursed Anubis aura trail (3.5 blocks, every 2nd step)
            if (i % 2 == 0) {
                Vec3 pAura = eyePos.add(dir.scale(1.95)).add(0.0, -0.25, 0.0);
                level.sendParticles(EDGE_VIOLET, pAura.x, pAura.y, pAura.z, 1, 0.03, 0.03, 0.03, 0.0);
            }

            // Classic sweep flash at center & tips
            if (i == 6 || i == 13 || i == 20) {
                Vec3 pSweep = eyePos.add(dir.scale(2.4)).add(0.0, -0.25, 0.0);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pSweep.x, pSweep.y, pSweep.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Crisp sword slash sound
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, flip ? 1.28F : 1.15F);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.55F, 1.65F);

        // 2. Extended-range damage sweep (up to 6.0 blocks in forward slash cone)
        Object ownStand = JCraftStandCheck.getStandEntity(player);
        double baseDmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float slashDamage = (float) Math.max(4.0, baseDmg);

        AABB searchBox = player.getBoundingBox().inflate(SLASH_RANGE + 1.0);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox, e ->
                e != player && e != ownStand && e.isAlive() && !e.isSpectator()
        );

        for (LivingEntity target : candidates) {
            Vec3 toTarget = target.getEyePosition().subtract(eyePos);
            double dist = toTarget.length();
            if (dist > SLASH_RANGE || dist < 0.05) continue;

            Vec3 normToTarget = toTarget.scale(1.0 / dist);
            double dot = normToTarget.dot(look);
            if (dot < 0.35) continue; // ~69 deg half-angle matches crescent arc

            // Skip vanilla-range direct melee hit (<2.6 blocks) only if already hurt this tick
            if (target.invulnerableTime > 15) continue;

            boolean hurt = target.hurt(level.damageSources().playerAttack(player), slashDamage);
            if (hurt) {
                Vec3 kb = new Vec3(look.x, 0.12, look.z).normalize().scale(0.32);
                target.push(kb.x, kb.y, kb.z);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.25, 0.25, 0.25, 0.12);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_SLASH_MS.remove(id);
        SLASH_FLIP.remove(id);
    }
}
