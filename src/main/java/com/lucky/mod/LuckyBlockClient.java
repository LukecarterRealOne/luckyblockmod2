package com.lucky.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class LuckyBlockClient implements ClientModInitializer {
    private static float flash = 0f;
    private static int flashColor = 0xFFF07A;
    private static float shakeAmp = 0f;
    private static int shakeTicks = 0;
    private static int shakeMax = 1;
    private static float lastYaw = 0f;
    private static float lastPitch = 0f;
    private static final Random RNG = new Random();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.ID, (payload, context) -> {
            if (payload.flash() > flash) {
                flash = payload.flash();
                flashColor = payload.color();
            }
            float current = shakeTicks > 0 ? shakeAmp * shakeTicks / shakeMax : 0f;
            if (payload.shake() >= current) {
                shakeAmp = payload.shake();
                shakeMax = Math.max(1, payload.duration());
                shakeTicks = shakeMax;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (flash > 0) flash = Math.max(0f, flash - 0.035f);
            ClientPlayerEntity p = client.player;
            if (p == null) {
                lastYaw = 0f;
                lastPitch = 0f;
                shakeTicks = 0;
                return;
            }
            // undo last tick's shake offset, then apply a new one
            p.setYaw(p.getYaw() - lastYaw);
            p.setPitch(p.getPitch() - lastPitch);
            lastYaw = 0f;
            lastPitch = 0f;
            if (shakeTicks > 0) {
                float amp = shakeAmp * shakeTicks / shakeMax;
                lastYaw = (RNG.nextFloat() - 0.5f) * 2f * amp;
                lastPitch = (RNG.nextFloat() - 0.5f) * 2f * amp;
                p.setYaw(p.getYaw() + lastYaw);
                p.setPitch(MathHelper.clamp(p.getPitch() + lastPitch, -90f, 90f));
                shakeTicks--;
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (flash <= 0.01f) return;
            int a = (int) (Math.min(1f, flash) * 200);
            int argb = (a << 24) | (flashColor & 0xFFFFFF);
            ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), argb);
        });
    }
}
