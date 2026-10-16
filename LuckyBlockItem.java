package com.lucky.mod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Right-click: random ability.  Sneak + right-click: guaranteed YELLOW OMEGA BEAM.
 */
public class LuckyBlockItem extends Item {
    private static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.0f), 2.0f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.0f), 1.4f);
    private static final DustParticleEffect WHITE_HOT = new DustParticleEffect(new Vector3f(1.0f, 1.0f, 0.8f), 1.2f);
    private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.3f, 1.0f, 0.4f), 1.3f);

    public LuckyBlockItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw)) {
            return TypedActionResult.success(stack);
        }
        int roll = user.isSneaking() ? 0 : world.random.nextInt(9);
        switch (roll) {
            case 0 -> omegaBeam(sw, user);
            case 1 -> lightningStorm(sw, user);
            case 2 -> meteorShower(sw, user);
            case 3 -> healingNova(sw, user);
            case 4 -> skyLaunch(sw, user);
            case 5 -> goldRain(sw, user);
            case 6 -> gravityWarp(sw, user);
            case 7 -> summonAllies(sw, user);
            default -> orbitalStrike(sw, user);
        }
        user.getItemCooldownManager().set(this, switch (roll) {
            case 0 -> 160;
            case 8 -> 200;
            case 7 -> 100;
            case 6 -> 80;
            default -> 40;
        });
        return TypedActionResult.success(stack);
    }

    // ---------------- helpers ----------------
    private static void msg(PlayerEntity p, String text) {
        p.sendMessage(Text.literal(text), true);
    }

    private static boolean gone(ServerWorld sw, PlayerEntity user) {
        return !user.isAlive() || user.isRemoved() || user.getWorld() != sw;
    }

    private record Beam(Vec3d eye, Vec3d dir, double len, Vec3d impact) {}

    private static Beam aim(ServerWorld sw, PlayerEntity user) {
        Vec3d eye = user.getEyePos();
        Vec3d dir = user.getRotationVec(1.0f);
        Vec3d end = eye.add(dir.multiply(96));
        var hit = sw.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, user));
        boolean miss = hit.getType() == HitResult.Type.MISS;
        double len = miss ? 96 : eye.distanceTo(hit.getPos());
        return new Beam(eye, dir, len, miss ? end : hit.getPos());
    }

    private static Vec3d[] basis(Vec3d dir) {
        Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(up).normalize();
        Vec3d realUp = right.crossProduct(dir).normalize();
        return new Vec3d[]{right, realUp};
    }

    // ---------------- 0: YELLOW OMEGA BEAM ----------------
    // 1.5s charge-up -> 1.8s REAL laser (stretched block-display entities, 4 glowing layers) you can sweep -> huge finale
    private static final double MUZZLE = 3.5;
    private static final String[] BEAM_BLOCKS = {
            "minecraft:yellow_stained_glass", "minecraft:yellow_stained_glass",
            "minecraft:yellow_concrete", "minecraft:white_concrete"};
    private static final double[] BEAM_WIDTHS = {6.0, 3.4, 1.7, 0.8};
    private static int beamCounter = 0;

    private void omegaBeam(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7e\u00a7l\u26a1 CHARGING OMEGA BEAM... \u26a1");
        final int charge = 30;
        final int fire = 36;
        final int id = ++beamCounter;
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, charge + 5, 4, false, false));
        Fx.sound(sw, user.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 3f, 0.5f);
        Fx.screen(sw, user.getPos(), 48, 0f, 0.5f, charge, 0xFFF07A);

        for (int t = 0; t < charge; t++) {
            final int tt = t;
            Scheduler.later(t + 1, () -> chargeTick(sw, user, tt, charge));
        }
        Scheduler.later(charge + 1, () -> fireStart(sw, user));
        for (int t = 0; t < fire; t++) {
            final int tt = t;
            Scheduler.later(charge + 1 + t, () -> beamTick(sw, user, tt, fire, id));
        }
        Scheduler.later(charge + fire + 2, () -> finale(sw, user, id));
    }

    private void chargeTick(ServerWorld sw, PlayerEntity user, int t, int total) {
        if (gone(sw, user)) return;
        float p = t / (float) total;
        Vec3d dir = user.getRotationVec(1f);
        Vec3d c = user.getEyePos().add(dir.multiply(MUZZLE)).add(0, -0.3, 0);
        Vec3d[] ax = basis(dir);
        double radius = 6.0 * (1 - p) + 0.4;
        int pts = 20;
        for (int i = 0; i < pts; i++) {
            double a = Math.PI * 2 * i / pts + t * 0.5;
            Vec3d o = ax[0].multiply(Math.cos(a) * radius).add(ax[1].multiply(Math.sin(a) * radius));
            Fx.burst(sw, GOLD, c.x + o.x, c.y + o.y, c.z + o.z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                Fx.burst(sw, ParticleTypes.END_ROD, c.x + o.x, c.y + o.y, c.z + o.z, 1, 0, 0, 0, 0);
            }
        }
        DustParticleEffect orb = new DustParticleEffect(new Vector3f(1f, 0.92f, 0.1f), 1f + 3f * p);
        Fx.burst(sw, orb, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0);
        if (t % 4 == 0) {
            Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 0.5f + 1.5f * p);
        }
        if (t == total - 4) {
            Fx.screen(sw, user.getPos(), 48, 0.35f, 1.2f, 6, 0xFFF07A);
        }
    }

    private void fireStart(ServerWorld sw, PlayerEntity user) {
        if (gone(sw, user)) return;
        msg(user, "\u00a7e\u00a7l\u26a1 YELLOW OMEGA BEAM! \u26a1");
        Vec3d m = user.getEyePos().add(user.getRotationVec(1f).multiply(MUZZLE));
        Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 4f, 0.7f);
        Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 1.4f);
        Fx.burst(sw, ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        Fx.burst(sw, ParticleTypes.SONIC_BOOM, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        Fx.screen(sw, user.getPos(), 96, 1.0f, 4.5f, 44, 0xFFF07A);
    }

    /** SNBT transformation that stretches a unit block into a w x w x len box pointing along q's local +Z. */
    private static String transformSnbt(Quaternionf q, double w, double len) {
        Vector3f tr = new Vector3f((float) (-w / 2), (float) (-w / 2), 0f);
        q.transform(tr);
        return String.format(Locale.ROOT,
                "transformation:{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],"
                        + "scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,%.4ff,%.4ff]}",
                q.x, q.y, q.z, q.w, w, w, len, tr.x, tr.y, tr.z);
    }

    private void beamTick(ServerWorld sw, PlayerEntity user, int t, int total, int id) {
        if (gone(sw, user)) return;
        Beam b = aim(sw, user);
        Vec3d dir = b.dir();
        Vec3d origin = b.eye().add(dir.multiply(MUZZLE)).add(0, -0.3, 0);
        double len = Math.max(0.5, b.len() - MUZZLE);
        double grow = Math.min(1.0, (t + 1) / 4.0);
        if (t >= total - 4) grow = Math.min(grow, (total - t) / 4.0);
        double pulse = 1.0 + 0.06 * Math.sin(t * 1.7);
        Quaternionf q = new Quaternionf().rotationTo(0f, 0f, 1f, (float) dir.x, (float) dir.y, (float) dir.z);

        // the actual laser: 4 nested glowing block-display boxes
        for (int k = 0; k < BEAM_BLOCKS.length; k++) {
            double w = BEAM_WIDTHS[k] * grow * pulse;
            String tf = transformSnbt(q, w, len);
            if (t == 0) {
                Fx.cmd(sw, String.format(Locale.ROOT,
                        "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},"
                                + "Tags:[\"lbm_beam\",\"lbm%d\",\"lbm%d_%d\"],view_range:4f,teleport_duration:1,"
                                + "brightness:{sky:15,block:15},%s}",
                        origin.x, origin.y, origin.z, BEAM_BLOCKS[k], id, id, k, tf));
            } else {
                Fx.cmd(sw, String.format(Locale.ROOT,
                        "data merge entity @e[tag=lbm%d_%d,limit=1] {%s,start_interpolation:0,interpolation_duration:1}",
                        id, k, tf));
            }
        }
        if (t > 0) {
            Fx.cmd(sw, String.format(Locale.ROOT, "tp @e[tag=lbm%d] %.3f %.3f %.3f", id, origin.x, origin.y, origin.z));
        }

        // extra energy: crackling sparks + a wide rotating double spiral around the laser
        Vec3d[] ax = basis(dir);
        for (double d = MUZZLE; d <= b.len(); d += 1.2) {
            Vec3d p = b.eye().add(dir.multiply(d)).add(0, -0.3, 0);
            int idx = (int) (d / 1.2);
            if (idx % 2 == 0) {
                Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 2.5, 2.5, 2.5, 0.4);
            }
            if (idx % 3 == 0) {
                Fx.burst(sw, ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 1.5, 1.5, 1.5, 0.05);
            }
            double ang = d * 0.9 + t * 0.9;
            for (int s = 0; s < 2; s++) {
                double a = ang + s * Math.PI;
                double r = 3.6 * grow;
                Vec3d o = ax[0].multiply(Math.cos(a) * r).add(ax[1].multiply(Math.sin(a) * r));
                Fx.burst(sw, GOLD, p.x + o.x, p.y + o.y, p.z + o.z, 1, 0, 0, 0, 0);
            }
        }

        if (t % 4 == 0) {
            damageAlong(sw, user, b, 70f, 4.5);
        }

        // impact: bigger fire, lava, smoke, shockwave rings, pillar of light
        Vec3d im = b.impact();
        Fx.burst(sw, ParticleTypes.FLAME, im.x, im.y + 0.3, im.z, 25, 2.5, 0.8, 2.5, 0.1);
        Fx.burst(sw, ParticleTypes.LAVA, im.x, im.y + 0.3, im.z, 6, 2.5, 0.4, 2.5, 0);
        Fx.burst(sw, ParticleTypes.LARGE_SMOKE, im.x, im.y + 0.3, im.z, 12, 2.5, 1.0, 2.5, 0.06);
        Fx.burst(sw, YELLOW, im.x, im.y + 0.5, im.z, 25, 3.0, 1.0, 3.0, 0);
        Fx.ring(sw, YELLOW, im.x, im.y + 0.2, im.z, 2 + (t % 10) * 1.0, 40);
        for (double y = 0; y <= 60; y += 2) {
            Fx.burst(sw, YELLOW, im.x, im.y + y, im.z, 4, 0.9, 0.1, 0.9, 0);
        }
        if (t % 6 == 0) {
            Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2f, 0.5f);
        }
    }

    private void damageAlong(ServerWorld sw, PlayerEntity user, Beam b, float dmg, double radius) {
        Vec3d end = b.eye().add(b.dir().multiply(b.len()));
        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class,
                new Box(b.eye(), end).expand(radius + 0.5), e -> e != user);
        for (LivingEntity e : targets) {
            Vec3d center = e.getPos().add(0, e.getHeight() / 2, 0);
            double t = MathHelper.clamp(center.subtract(b.eye()).dotProduct(b.dir()), 0, b.len());
            Vec3d closest = b.eye().add(b.dir().multiply(t));
            if (closest.distanceTo(center) <= radius) {
                e.damage(sw.getDamageSources().indirectMagic(user, user), dmg);
                e.setOnFireFor(10);
                e.addVelocity(b.dir().x * 0.8, 0.3, b.dir().z * 0.8);
                e.velocityModified = true;
                Fx.burst(sw, ParticleTypes.EXPLOSION, e.getX(), e.getBodyY(0.5), e.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

    private void finale(ServerWorld sw, PlayerEntity user, int id) {
        Fx.cmd(sw, "kill @e[tag=lbm" + id + "]");   // always remove the laser entities
        if (gone(sw, user)) return;
        Beam b = aim(sw, user);
        Vec3d im = b.impact();
        sw.createExplosion(user, im.x, im.y, im.z, 8f, World.ExplosionSourceType.NONE);
        Fx.burst(sw, ParticleTypes.EXPLOSION_EMITTER, im.x, im.y, im.z, 1, 0, 0, 0, 0);
        Fx.burst(sw, ParticleTypes.FLASH, im.x, im.y + 1, im.z, 1, 0, 0, 0, 0);
        Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.6f);
        Fx.screen(sw, im, 96, 1.0f, 6.0f, 30, 0xFFF07A);
        damageAlong(sw, user, b, 100f, 6.0);
        for (int k = 0; k < 6; k++) {
            final double r = 2 + k * 4;
            Scheduler.later(k * 2 + 1, () -> {
                Fx.ring(sw, YELLOW, im.x, im.y + 0.3, im.z, r, 64);
                Fx.ring(sw, ParticleTypes.END_ROD, im.x, im.y + 0.6, im.z, r, 32);
            });
        }
    }

    // ---------------- 1: LIGHTNING STORM ----------------
    private void lightningStorm(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7b\u00a7lLightning Storm!");
        for (int i = 0; i < 10; i++) {
            double a = sw.random.nextDouble() * Math.PI * 2;
            double r = 4 + sw.random.nextDouble() * 7;
            final Vec3d pos = user.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Scheduler.later(i * 3 + 1, () -> {
                if (gone(sw, user)) return;
                Fx.ring(sw, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.1, pos.z, 2.0, 24);
                Fx.sound(sw, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.8f);
            });
            Scheduler.later(i * 3 + 13, () -> {
                if (gone(sw, user)) return;
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(pos);
                    sw.spawnEntity(bolt);
                }
                Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 40, 0.6, 1.2, 0.6, 0.4);
                Fx.screen(sw, pos, 64, 0.4f, 1.5f, 8, 0xBFE9FF);
            });
        }
    }

    // ---------------- 2: METEOR SHOWER ----------------
    private void meteorShower(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7c\u00a7lMeteor Shower!");
        Vec3d target = user.getEyePos().add(user.getRotationVec(1f).multiply(18));
        List<FireballEntity> fireballs = new ArrayList<>();
        Fx.screen(sw, user.getPos(), 64, 0.3f, 0.8f, 20, 0xFF7A1A);
        for (int i = 0; i < 8; i++) {
            final Vec3d start = target.add((sw.random.nextDouble() - 0.5) * 12, 25 + sw.random.nextDouble() * 8,
                    (sw.random.nextDouble() - 0.5) * 12);
            Scheduler.later(i * 4 + 1, () -> {
                FireballEntity fb = new FireballEntity(sw, user, new Vec3d(0, -1.2, 0), 2);
                fb.setPosition(start);
                sw.spawnEntity(fb);
                fireballs.add(fb);
                Fx.burst(sw, ParticleTypes.FLAME, start.x, start.y, start.z, 25, 0.6, 0.6, 0.6, 0.1);
                Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 2f, 0.6f);
            });
        }
        trail(sw, fireballs, 100);
    }

    private void trail(ServerWorld sw, List<FireballEntity> fireballs, int remaining) {
        for (FireballEntity fb : new ArrayList<>(fireballs)) {
            if (!fb.isRemoved()) {
                Fx.burst(sw, ParticleTypes.FLAME, fb.getX(), fb.getY(), fb.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
                Fx.burst(sw, ParticleTypes.LARGE_SMOKE, fb.getX(), fb.getY(), fb.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                Fx.burst(sw, ParticleTypes.LAVA, fb.getX(), fb.getY(), fb.getZ(), 1, 0.2, 0.2, 0.2, 0);
            }
        }
        if (remaining > 0) {
            Scheduler.later(1, () -> trail(sw, fireballs, remaining - 1));
        }
    }

    // ---------------- 3: HEALING NOVA ----------------
    private void healingNova(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7a\u00a7lHealing Nova!");
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 2));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 3));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
        user.setHealth(user.getMaxHealth());
        Fx.sound(sw, user.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 2f, 1.2f);
        Fx.screen(sw, user.getPos(), 32, 0.3f, 0f, 1, 0x66FF99);
        for (int t = 0; t < 20; t++) {
            final int tt = t;
            Scheduler.later(t + 1, () -> {
                if (gone(sw, user)) return;
                double x = user.getX(), y = user.getY(), z = user.getZ();
                Fx.ring(sw, ParticleTypes.HAPPY_VILLAGER, x, y + 0.1, z, 0.5 + tt * 0.4, 36);
                Fx.ring(sw, GREEN, x, y + 0.2, z, 0.5 + tt * 0.4, 24);
                for (int s = 0; s < 2; s++) {
                    double a = tt * 0.7 + s * Math.PI;
                    Fx.burst(sw, GOLD, x + Math.cos(a), y + tt * 0.12, z + Math.sin(a), 2, 0.05, 0.05, 0.05, 0);
                }
                if (tt == 19) {
                    Fx.burst(sw, ParticleTypes.HEART, x, y + 1.5, z, 20, 0.6, 0.6, 0.6, 0.1);
                }
            });
        }
    }

    // ---------------- 4: SKY LAUNCH ----------------
    private void skyLaunch(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7d\u00a7lSky Launch!");
        user.addVelocity(0, 2.2, 0);
        user.velocityModified = true;
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 300, 0));
        Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 1f);
        Fx.screen(sw, user.getPos(), 48, 0.5f, 2.0f, 8, 0xFFFFFF);
        for (int k = 1; k <= 4; k++) {
            Fx.ring(sw, ParticleTypes.CLOUD, user.getX(), user.getY() + 0.1, user.getZ(), k, 32);
        }
        for (int t = 0; t < 25; t++) {
            final int tt = t;
            Scheduler.later(t + 1, () -> {
                if (gone(sw, user)) return;
                double x = user.getX(), y = user.getY(), z = user.getZ();
                Fx.burst(sw, ParticleTypes.FIREWORK, x, y, z, 6, 0.2, 0.1, 0.2, 0.05);
                Fx.burst(sw, ParticleTypes.CLOUD, x, y, z, 4, 0.3, 0.1, 0.3, 0.02);
                Fx.burst(sw, ParticleTypes.FLAME, x, y, z, 3, 0.15, 0.05, 0.15, 0.02);
                if (tt % 5 == 0) {
                    Fx.ring(sw, ParticleTypes.END_ROD, x, y, z, 1 + tt * 0.1, 20);
                }
            });
        }
    }

    // ---------------- 5: GOLD RAIN ----------------
    private void goldRain(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a76\u00a7lGold Rain!");
        final Vec3d base = user.getPos();
        Fx.sound(sw, user.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 2f, 1f);
        Fx.screen(sw, base, 32, 0.35f, 0.6f, 10, 0xFFD84A);
        for (int t = 0; t < 12; t++) {
            Scheduler.later(t + 1, () -> {
                for (double y = 0; y <= 14; y += 1) {
                    Fx.burst(sw, GOLD, base.x, base.y + y, base.z, 2, 0.4, 0, 0.4, 0);
                }
            });
        }
        for (int i = 0; i < 12; i++) {
            ItemStack loot = switch (sw.random.nextInt(5)) {
                case 0 -> new ItemStack(Items.DIAMOND, 1 + sw.random.nextInt(2));
                case 1 -> new ItemStack(Items.EMERALD, 1 + sw.random.nextInt(4));
                case 2 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                default -> new ItemStack(Items.GOLD_INGOT, 2 + sw.random.nextInt(5));
            };
            final double x = base.x + (sw.random.nextDouble() - 0.5) * 4;
            final double z = base.z + (sw.random.nextDouble() - 0.5) * 4;
            final double y = base.y + 4 + sw.random.nextDouble() * 2;
            Scheduler.later(i * 2 + 2, () -> {
                sw.spawnEntity(new ItemEntity(sw, x, y, z, loot));
                Fx.burst(sw, ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 10, 0.2, 0.2, 0.2, 0.3);
            });
        }
    }

    // ---------------- targeting helper ----------------
    private record Target(Vec3d pos, LivingEntity entity) {}

    /** What the player is looking at: the nearest living entity on the ray, else the block hit (or far point). */
    private static Target pickTarget(ServerWorld sw, PlayerEntity user, double range) {
        Vec3d eye = user.getEyePos();
        Vec3d dir = user.getRotationVec(1.0f);
        Vec3d end = eye.add(dir.multiply(range));
        var hit = sw.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, user));
        boolean miss = hit.getType() == HitResult.Type.MISS;
        double bestT = miss ? range : eye.distanceTo(hit.getPos());
        LivingEntity best = null;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, new Box(eye, end).expand(2), x -> x != user)) {
            Vec3d c = e.getPos().add(0, e.getHeight() / 2, 0);
            double t = c.subtract(eye).dotProduct(dir);
            if (t < 0 || t > bestT) continue;
            if (eye.add(dir.multiply(t)).distanceTo(c) <= 1.5 + e.getWidth() / 2) {
                best = e;
                bestT = t;
            }
        }
        if (best != null) {
            return new Target(best.getPos().add(0, best.getHeight() / 2, 0), best);
        }
        return new Target(miss ? end : hit.getPos(), null);
    }

    // ---------------- 6: GRAVITY WARP ----------------
    // Look at something: everything in a 7-block bubble loses gravity and floats up, then gets SLAMMED down.
    private void gravityWarp(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a75\u00a7lGRAVITY WARP!");
        Target tg = pickTarget(sw, user, 48);
        final Vec3d c = tg.pos();
        final double r = 7;
        final List<LivingEntity> victims = sw.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(r),
                e -> e != user && e.squaredDistanceTo(c) <= r * r);
        final DustParticleEffect purple = new DustParticleEffect(new Vector3f(0.6f, 0.2f, 1.0f), 1.6f);

        Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
        Fx.screen(sw, c, 64, 0.3f, 1.0f, 10, 0xAA66FF);
        for (LivingEntity e : victims) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 3, false, true));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0, false, false));
        }
        for (int t = 0; t < 60; t++) {
            final int tt = t;
            Scheduler.later(t + 1, () -> {
                double rise = (tt % 30) * 0.5;
                Fx.ring(sw, purple, c.x, c.y + rise, c.z, r, 40);
                Fx.ring(sw, ParticleTypes.REVERSE_PORTAL, c.x, c.y + rise, c.z, r * 0.6, 20);
                for (LivingEntity e : victims) {
                    if (e.isAlive()) {
                        Fx.burst(sw, ParticleTypes.PORTAL, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(),
                                6, 0.4, 0.6, 0.4, 0.3);
                    }
                }
                if (tt % 10 == 0) {
                    Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.BLOCK_BEACON_AMBIENT, 2f, 0.5f);
                }
            });
        }
        Scheduler.later(62, () -> {
            for (LivingEntity e : victims) {
                if (!e.isAlive()) continue;
                e.removeStatusEffect(StatusEffects.LEVITATION);
                e.setVelocity(0, -3.5, 0);
                e.velocityModified = true;
                e.damage(sw.getDamageSources().magic(), 12f);
            }
            Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 3f, 0.7f);
            Fx.screen(sw, c, 64, 0.4f, 2.5f, 12, 0xAA66FF);
            for (int k = 0; k < 6; k++) {
                final double rr = 2 + k * 2.5;
                Scheduler.later(k + 1, () -> Fx.ring(sw, purple, c.x, c.y, c.z, rr, 48));
            }
        });
    }

    // ---------------- 7: ALLY SUMMON (works with modded mobs) ----------------
    // Picks random mobs from ANY other installed mod; falls back to vanilla helpers. They fight hostile mobs for 60s.
    private void summonAllies(ServerWorld sw, PlayerEntity user) {
        List<EntityType<?>> modded = new ArrayList<>();
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            String ns = id.getNamespace();
            if (ns.equals("minecraft") || ns.equals(LuckyBlockMod.MOD_ID)) continue;
            EntityType<?> type = Registries.ENTITY_TYPE.get(id);
            if (type.getSpawnGroup() == SpawnGroup.MISC || !type.isSummonable()) continue;
            modded.add(type);
        }
        List<EntityType<?>> fallback = List.<EntityType<?>>of(EntityType.IRON_GOLEM, EntityType.WOLF,
                EntityType.WOLF, EntityType.VINDICATOR, EntityType.IRON_GOLEM);

        msg(user, modded.isEmpty()
                ? "\u00a7b\u00a7lAllies Summoned!"
                : "\u00a7b\u00a7lModded Allies Summoned! (" + modded.size() + " mob types found)");

        final int total = 5;
        final List<MobEntity> allies = new ArrayList<>();
        final Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < total; i++) {
            final int idx = i;
            double a = Math.PI * 2 * i / total;
            final Vec3d pos = user.getPos().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
            Scheduler.later(i * 6 + 1, () -> {
                MobEntity mob = null;
                for (int attempt = 0; attempt < 6 && mob == null; attempt++) {
                    EntityType<?> type = (!modded.isEmpty() && idx < 4 && attempt < 5)
                            ? modded.get(sw.random.nextInt(modded.size()))
                            : fallback.get(idx % fallback.size());
                    try {
                        Entity e = type.create(sw);
                        if (e instanceof MobEntity m) mob = m;
                    } catch (Exception ignored) {
                    }
                }
                if (mob == null) return;
                mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), 0f);
                try {
                    mob.initialize(sw, sw.getLocalDifficulty(mob.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
                } catch (Exception ignored) {
                }
                if (mob instanceof IronGolemEntity golem) golem.setPlayerCreated(true);
                mob.setPersistent();
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 1300, 1));
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1300, 1));
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1300, 1));
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 1300, 0, false, false));
                sw.spawnEntity(mob);
                allies.add(mob);
                ids.add(mob.getUuid());
                user.sendMessage(Text.literal("\u00a7b+ " + mob.getType().getName().getString() + " joined you"), false);

                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(pos);
                    bolt.setCosmetic(true);
                    sw.spawnEntity(bolt);
                }
                Fx.ring(sw, ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.1, pos.z, 2.0, 24);
                Fx.burst(sw, ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 30, 0.3, 1.2, 0.3, 0.1);
                Fx.burst(sw, ParticleTypes.PORTAL, pos.x, pos.y + 1, pos.z, 40, 0.6, 1.0, 0.6, 0.5);
            });
        }
        Scheduler.later(total * 6 + 2, () -> allyTick(sw, user, allies, ids, 1200));
    }

    private void allyTick(ServerWorld sw, PlayerEntity user, List<MobEntity> allies, Set<UUID> ids, int remaining) {
        boolean any = false;
        for (MobEntity a : allies) {
            if (a.isRemoved() || !a.isAlive()) continue;
            any = true;
            if (remaining <= 0) {
                Fx.burst(sw, ParticleTypes.POOF, a.getX(), a.getY() + 1, a.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
                a.discard();
                continue;
            }
            LivingEntity tgt = a.getTarget();
            if (tgt instanceof PlayerEntity || (tgt != null && ids.contains(tgt.getUuid()))) {
                a.setTarget(null);
            }
            LivingEntity cur = a.getTarget();
            if (cur == null || !cur.isAlive()) {
                MobEntity best = null;
                double bd = Double.MAX_VALUE;
                for (MobEntity f : sw.getEntitiesByClass(MobEntity.class, a.getBoundingBox().expand(20),
                        m -> m instanceof Monster && m.isAlive() && !ids.contains(m.getUuid()))) {
                    double d = a.squaredDistanceTo(f);
                    if (d < bd) {
                        bd = d;
                        best = f;
                    }
                }
                if (best != null) {
                    a.setTarget(best);
                } else if (a.squaredDistanceTo(user) > 100) {
                    a.getNavigation().startMovingTo(user, 1.3);
                }
            }
        }
        if (any && remaining > 0) {
            Scheduler.later(5, () -> allyTick(sw, user, allies, ids, remaining - 5));
        }
    }

    // ---------------- 8: ORBITAL STRIKE ----------------
    // Look anywhere (up to 128 blocks): a targeting reticle locks on, then a giant laser fires down from space.
    private static final String[] ORB_BLOCKS = {
            "minecraft:cyan_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:white_concrete"};
    private static final double[] ORB_WIDTHS = {11.0, 6.5, 3.0};

    private static String verticalBeamSnbt(double w, double h) {
        return String.format(Locale.ROOT,
                "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,0f,%.4ff]}",
                w, h, w, -w / 2, -w / 2);
    }

    private void orbitalStrike(ServerWorld sw, PlayerEntity user) {
        Target tg = pickTarget(sw, user, 128);
        final Vec3d c = tg.pos();
        final int charge = 50;
        final int fire = 50;
        final int id = ++beamCounter;
        final BlockPos bp = BlockPos.ofFloored(c);
        final DustParticleEffect cyan = new DustParticleEffect(new Vector3f(0.3f, 0.9f, 1.0f), 2.0f);

        msg(user, "\u00a7b\u00a7lORBITAL STRIKE LOCKED ON!");
        Fx.sound(sw, bp, SoundEvents.BLOCK_BEACON_ACTIVATE, 4f, 0.5f);

        for (int t = 0; t < charge; t++) {
            final int tt = t;
            Scheduler.later(t + 1, () -> {
                float p = tt / (float) charge;
                double radius = 12 * (1 - p) + 1;
                Fx.ring(sw, cyan, c.x, c.y + 0.2, c.z, radius, 40);
                Fx.ring(sw, cyan, c.x, c.y + 0.2, c.z, 12, 40);
                Fx.ring(sw, ParticleTypes.END_ROD, c.x, c.y + 0.3, c.z, 4, 16);
                for (double y = 0; y <= 80; y += 3) {
                    Fx.burst(sw, ParticleTypes.END_ROD, c.x, c.y + y, c.z, 1, 0.1, 0, 0.1, 0);
                }
                if (tt % 5 == 0) {
                    Fx.sound(sw, bp, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 3f, 0.5f + 1.5f * p);
                }
                if (tt == charge - 5) {
                    Fx.screen(sw, c, 200, 0.4f, 1.5f, 6, 0xBFEFFF);
                }
            });
        }
        for (int t = 0; t < fire; t++) {
            final int tt = t;
            Scheduler.later(charge + 1 + t, () -> orbitalTick(sw, user, c, cyan, tt, fire, id));
        }
        Scheduler.later(charge + fire + 2, () -> orbitalFinale(sw, user, c, cyan, id));
    }

    private void orbitalTick(ServerWorld sw, PlayerEntity user, Vec3d c, DustParticleEffect cyan,
                             int t, int total, int id) {
        double grow = Math.min(1.0, (t + 1) / 2.0);
        if (t >= total - 6) grow = Math.min(grow, (total - t) / 6.0);
        double pulse = 1.0 + 0.05 * Math.sin(t * 1.9);

        for (int k = 0; k < ORB_BLOCKS.length; k++) {
            double w = ORB_WIDTHS[k] * grow * pulse;
            String tf = verticalBeamSnbt(w, 400);
            if (t == 0) {
                Fx.cmd(sw, String.format(Locale.ROOT,
                        "summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},"
                                + "Tags:[\"lbm_beam\",\"lbm%d\",\"lbm%d_%d\"],view_range:8f,"
                                + "brightness:{sky:15,block:15},%s}",
                        c.x, c.y - 2, c.z, ORB_BLOCKS[k], id, id, k, tf));
            } else if (t % 2 == 0) {
                Fx.cmd(sw, String.format(Locale.ROOT,
                        "data merge entity @e[tag=lbm%d_%d,limit=1] {%s,start_interpolation:0,interpolation_duration:2}",
                        id, k, tf));
            }
        }
        if (t == 0) {
            Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 6f, 0.5f);
            Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 6f, 0.8f);
            Fx.screen(sw, c, 250, 1.0f, 7.0f, 55, 0xBFEFFF);
        }
        if (t % 3 == 0) {
            Box zone = new Box(c.x - 9, c.y - 6, c.z - 9, c.x + 9, c.y + 60, c.z + 9);
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, zone, e -> e != user)) {
                double dx = e.getX() - c.x;
                double dz = e.getZ() - c.z;
                if (dx * dx + dz * dz <= 81) {
                    e.damage(sw.getDamageSources().indirectMagic(user, user), 60f);
                    e.setOnFireFor(10);
                    e.addVelocity(0, 0.6, 0);
                    e.velocityModified = true;
                }
            }
        }
        Fx.burst(sw, ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 30, 4.0, 1.0, 4.0, 0.12);
        Fx.burst(sw, ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 8, 4.0, 0.5, 4.0, 0);
        Fx.burst(sw, ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.3, c.z, 14, 4.0, 1.5, 4.0, 0.06);
        Fx.burst(sw, cyan, c.x, c.y + 0.5, c.z, 30, 4.5, 1.2, 4.5, 0);
        Fx.ring(sw, cyan, c.x, c.y + 0.2, c.z, 3 + (t % 12) * 1.2, 48);
        for (double y = 0; y <= 40; y += 3) {
            Fx.burst(sw, ParticleTypes.ELECTRIC_SPARK, c.x, c.y + y, c.z, 3, 3.5, 1.0, 3.5, 0.4);
        }
        if (t % 6 == 0) {
            Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 4f, 0.5f);
        }
    }

    private void orbitalFinale(ServerWorld sw, PlayerEntity user, Vec3d c, DustParticleEffect cyan, int id) {
        Fx.cmd(sw, "kill @e[tag=lbm" + id + "]");
        sw.createExplosion(user, c.x, c.y, c.z, 10f, World.ExplosionSourceType.NONE);
        Fx.burst(sw, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        Fx.burst(sw, ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        Fx.sound(sw, BlockPos.ofFloored(c), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 6f, 0.5f);
        Fx.screen(sw, c, 250, 1.0f, 8.0f, 35, 0xBFEFFF);
        Box zone = new Box(c.x - 12, c.y - 8, c.z - 12, c.x + 12, c.y + 20, c.z + 12);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, zone, e -> e != user)) {
            if (e.squaredDistanceTo(c) <= 144) {
                e.damage(sw.getDamageSources().indirectMagic(user, user), 100f);
                e.setOnFireFor(10);
            }
        }
        for (int k = 0; k < 8; k++) {
            final double r = 3 + k * 4;
            Scheduler.later(k * 2 + 1, () -> {
                Fx.ring(sw, cyan, c.x, c.y + 0.3, c.z, r, 72);
                Fx.ring(sw, ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, r, 36);
            });
        }
    }
}
