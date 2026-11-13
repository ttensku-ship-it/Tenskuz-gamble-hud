package com.tensku.gamblehud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.sdl.SDLScancode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TenskuGambleHud implements ClientModInitializer {
    public static final String MOD_ID = "tenskugamblehud";
    public static final String TITLE = "TENSKU GAMBLE HUD";
    public static final String CREDIT = "by tensku";

    // "$" may be followed by a space on some servers: "paid you $ 5M"
    private static final String AMT = "\\$\\s*([0-9][0-9.,]*[KkMmBbTt]?)";
    private static final Pattern PAID_YOU = Pattern.compile("(\\S+) paid you " + AMT);
    private static final Pattern YOU_PAID = Pattern.compile("You paid (\\S+) " + AMT);
    private static final Pattern HAS_BAL = Pattern.compile("(\\w{3,16}) has " + AMT);
    private static final Pattern MY_BAL = Pattern.compile("(?i)(?:you have|your (?:money|balance)(?: is)?:?|balance(?: is)?:?)\\s*" + AMT);
    private static final Pattern PAY_CMD = Pattern.compile("^pay\\s+(\\S+)\\s+(\\S+).*$", Pattern.CASE_INSENSITIVE);

    /** Menu song: put an .ogg at assets/tenskugamblehud/sounds/menu_music.ogg (see sounds.json). */
    public static final SoundEvent MENU_MUSIC =
        SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, "menu_music"));

    private static KeyMapping openKey;
    private static boolean bypassPayGuard = false;

    @Override
    public void onInitializeClient() {
        Config.load();
        Config.I.snapshot(); // baseline point for this session's P&L graph

        // Keybind (default: Right Shift) - change it in Options > Controls > Key Binds.
        // 26.3 uses SDL scancodes and the 3-argument KeyMapping constructor (no InputConstants.Type).
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tenskugamblehud.open",
            SDLScancode.SDL_SCANCODE_RSHIFT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new HubScreen());
                }
            }
        });

        // HUD: profit card + payment animation
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            ProfitCard.render(graphics, mc);
            AuctionManager.render(graphics, mc, w, h);
            TextOverlay.render(graphics, mc);
            Fireworks.render(graphics, w, h);
            PaymentAnimation.render(graphics, w, h);
        });

        // Detect payments + balance
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String text = message.getString();
            AuctionManager.onChat(text);

            Matcher in = PAID_YOU.matcher(text);
            if (in.find()) {
                double v = Money.parse(in.group(2));
                if (v > 0) {
                    AuctionManager.onPayment(in.group(1), v);
                    Config.I.record(in.group(1), v, true);
                    Config.I.rollDay();
                    Config.I.received += v;
                    if (Config.I.balance >= 0) Config.I.balance += v;
                    Config.I.snapshot();
                    Config.save();
                    if (Config.I.paymentAlerts && v >= Config.I.alertThreshold) {
                        PaymentAnimation.trigger(v, in.group(1));
                    }
                }
                return;
            }
            Matcher out = YOU_PAID.matcher(text);
            if (out.find()) {
                double v = Money.parse(out.group(2));
                if (v > 0) {
                    Config.I.record(out.group(1), v, false);
                    Config.I.rollDay();
                    Config.I.sent += v;
                    if (Config.I.balance >= 0) Config.I.balance = Math.max(0, Config.I.balance - v);
                    Config.I.snapshot();
                    Config.save();
                }
                return;
            }

            // Balance sync: "<you> has $ 27.9B", "Balance: $27.9B", "You have $27.9B"
            Minecraft mc = Minecraft.getInstance();
            String me = mc.player != null ? mc.player.getName().getString() : "";
            Matcher has = HAS_BAL.matcher(text);
            if (has.find() && !me.isEmpty() && has.group(1).equalsIgnoreCase(me)) {
                double v = Money.parse(has.group(2));
                if (v >= 0) { Config.I.balance = v; Config.I.snapshot(); Config.save(); }
                return;
            }
            Matcher mine = MY_BAL.matcher(text);
            if (mine.find()) {
                double v = Money.parse(mine.group(1));
                if (v >= 0) { Config.I.balance = v; Config.I.snapshot(); Config.save(); }
            }
        });

        // Pay confirm: intercept big /pay commands
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (bypassPayGuard || !Config.I.payConfirm) return true;
            Matcher m = PAY_CMD.matcher(command.trim());
            if (!m.matches()) return true;
            double v = Money.parse(m.group(2));
            if (v < 0 || v < Config.I.payThreshold) return true;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.gui.setScreen(new ConfirmPayScreen(command, m.group(1), v)));
            return false;
        });
    }

    /** Sends a command without being caught by the pay guard again. */
    public static void sendCommandBypass(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        bypassPayGuard = true;
        try {
            mc.getConnection().sendCommand(command);
        } finally {
            bypassPayGuard = false;
        }
    }
}
