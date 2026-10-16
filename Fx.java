package com.lucky.mod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Fx {
    /** Particles visible from far away (normal spawnParticles only reaches ~32 blocks). */
    public static void burst(ServerWorld sw, ParticleEffect e, double x, double y, double z,
                             int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(x, y, z) < 160 * 160) {
                sw.spawnParticles(p, e, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void ring(ServerWorld sw, ParticleEffect e, double cx, double cy, double cz, double r, int pts) {
        for (int i = 0; i < pts; i++) {
            double a = Math.PI * 2 * i / pts;
            burst(sw, e, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void sound(ServerWorld sw, BlockPos pos, SoundEvent s, float vol, float pitch) {
        sw.playSound(null, pos, s, SoundCategory.PLAYERS, vol, pitch);
    }

    /** Screen flash + camera shake for nearby players. */
    public static void screen(ServerWorld sw, Vec3d pos, double radius, float flash, float shake, int duration, int color) {
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(pos) < radius * radius) {
                ServerPlayNetworking.send(p, new FxPayload(flash, shake, duration, color));
            }
        }
    }

    /** Run a command silently as the server (used to spawn/update the laser's display entities). */
    public static void cmd(ServerWorld sw, String command) {
        try {
            var server = sw.getServer();
            server.getCommandManager().getDispatcher()
                    .execute(command, server.getCommandSource().withWorld(sw).withSilent());
        } catch (Exception ignored) {
            // e.g. "no entity found" when cleaning up - harmless
        }
    }
}
