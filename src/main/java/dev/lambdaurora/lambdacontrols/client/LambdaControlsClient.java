/*
 * Copyright � 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdacontrols.client;

import dev.lambdaurora.lambdacontrols.ControlsMode;
import dev.lambdaurora.lambdacontrols.LambdaControls;
import dev.lambdaurora.lambdacontrols.LambdaControlsConstants;
import dev.lambdaurora.lambdacontrols.LambdaControlsFeature;
import dev.lambdaurora.lambdacontrols.client.compat.LambdaControlsCompat;
import dev.lambdaurora.lambdacontrols.client.controller.ButtonBinding;
import dev.lambdaurora.lambdacontrols.client.controller.Controller;
import dev.lambdaurora.lambdacontrols.client.controller.InputManager;
import dev.lambdaurora.lambdacontrols.client.gui.LambdaControlsHud;
import dev.lambdaurora.lambdacontrols.client.ring.KeyBindingRingAction;
import dev.lambdaurora.lambdacontrols.client.ring.LambdaRing;
import dev.lambdaurora.spruceui.hud.HudManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * Represents the LambdaControls client mod.
 *
 * @author LambdAurora
 * @version 1.7.0
 * @since 1.1.0
 */
public class LambdaControlsClient extends LambdaControls implements ClientModInitializer {
    private static LambdaControlsClient INSTANCE;
    public static final KeyBinding BINDING_LOOK_UP = InputManager.makeKeyBinding(new Identifier(LambdaControlsConstants.NAMESPACE, "look_up"),
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_8, "key.categories.movement");
    public static final KeyBinding BINDING_LOOK_RIGHT = InputManager.makeKeyBinding(new Identifier(LambdaControlsConstants.NAMESPACE, "look_right"),
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_6, "key.categories.movement");
    public static final KeyBinding BINDING_LOOK_DOWN = InputManager.makeKeyBinding(new Identifier(LambdaControlsConstants.NAMESPACE, "look_down"),
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_2, "key.categories.movement");
    public static final KeyBinding BINDING_LOOK_LEFT = InputManager.makeKeyBinding(new Identifier(LambdaControlsConstants.NAMESPACE, "look_left"),
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_4, "key.categories.movement");
    /*public static final KeyBinding           BINDING_RING       = InputManager.makeKeyBinding(new Identifier(LambdaControlsConstants.NAMESPACE, "ring"),
            InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "key.categories.misc");*/
    public static final Identifier CONTROLLER_BUTTONS = new Identifier(LambdaControlsConstants.NAMESPACE, "textures/gui/controller_buttons.png");
    public static final Identifier CONTROLLER_AXIS = new Identifier(LambdaControlsConstants.NAMESPACE, "textures/gui/controller_axis.png");
    public static final Identifier CURSOR_TEXTURE = new Identifier(LambdaControlsConstants.NAMESPACE, "textures/gui/cursor.png");
    public final static File MAPPINGS_FILE = new File("config/gamecontrollerdb.txt");
    public final LambdaControlsConfig config = new LambdaControlsConfig(this);
    public final LambdaInput input = new LambdaInput(this);
    public final LambdaRing ring = new LambdaRing(this);
    public final LambdaReacharound reacharound = new LambdaReacharound();
    private LambdaControlsHud hud;
    private ControlsMode previousControlsMode;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        KeyBindingHelper.registerKeyBinding(BINDING_LOOK_UP);
        KeyBindingHelper.registerKeyBinding(BINDING_LOOK_RIGHT);
        KeyBindingHelper.registerKeyBinding(BINDING_LOOK_DOWN);
        KeyBindingHelper.registerKeyBinding(BINDING_LOOK_LEFT);
        //KeyBindingHelper.registerKeyBinding(BINDING_RING);

        this.ring.registerAction("keybinding", KeyBindingRingAction.FACTORY);

        ClientPlayNetworking.registerGlobalReceiver(CONTROLS_MODE_CHANNEL, (client, handler, buf, responseSender) -> {
            responseSender.sendPacket(CONTROLS_MODE_CHANNEL, this.makeControlsModeBuffer(this.config.getControlsMode()));
        });
        ClientPlayNetworking.registerGlobalReceiver(FEATURE_CHANNEL, (client, handler, buf, responseSender) -> {
            int features = buf.readVarInt();
            for (int i = 0; i < features; i++) {
                var name = buf.readString(64);
                boolean allowed = buf.readBoolean();
                LambdaControlsFeature.fromName(name).ifPresent(feature -> client.execute(() -> feature.setAllowed(allowed)));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sender.sendPacket(HELLO_CHANNEL, this.makeHello(this.config.getControlsMode()));
            sender.sendPacket(CONTROLS_MODE_CHANNEL, this.makeControlsModeBuffer(this.config.getControlsMode()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register(this::onLeave);

        ClientTickEvents.START_CLIENT_TICK.register(this.reacharound::tick);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        /*OpenScreenCallback.EVENT.register((client, screen) -> {
            if (screen == null && this.config.getControlsMode() == ControlsMode.TOUCHSCREEN) {
                screen = new TouchscreenOverlay(this);
                screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                client.skipGameRender = false;
                client.currentScreen = screen;
            } else if (screen != null) {
                this.input.onScreenOpen(client, client.getWindow().getWidth(), client.getWindow().getHeight());
            }
        });*/

        HudManager.register(this.hud = new LambdaControlsHud(this));
    }

    /**
     * This method is called when Minecraft is initializing.
     */
    public void onMcInit(@NotNull MinecraftClient client) {
        ButtonBinding.init(client.options);
        this.config.load();
        this.hud.setVisible(this.config.isHudEnabled());
        Controller.updateMappings();
        GLFW.glfwSetJoystickCallback((jid, event) -> {
            if (event == GLFW.GLFW_CONNECTED) {
                var controller = Controller.byId(jid);
                client.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, new TranslatableText("lambdacontrols.controller.connected", jid),
                        new LiteralText(controller.getName())));
            } else if (event == GLFW.GLFW_DISCONNECTED) {
                client.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, new TranslatableText("lambdacontrols.controller.disconnected", jid),
                        null));
            }

            this.switchControlsMode();
        });

        LambdaControlsCompat.init(this);
    }

    /**
     * This method is called every Minecraft tick.
     *
     * @param client the client instance
     */
    public void onTick(@NotNull MinecraftClient client) {
        this.input.tick(client);
        if (this.config.getControlsMode() == ControlsMode.CONTROLLER && (client.isWindowFocused() || this.config.hasUnfocusedInput()))
            this.input.tickController(client);

        /*if (BINDING_RING.wasPressed()) {
            client.openScreen(new RingScreen());
        }*/
    }

    public void onRender(MinecraftClient client) {
        this.input.onRender(client.getTickDelta(), client);
    }

    /**
     * Called when leaving a server.
     */
    public void onLeave(ClientPlayNetworkHandler handler, MinecraftClient client) {
        LambdaControlsFeature.resetAllAllowed();
    }

    /**
     * Switches the controls mode if the auto switch is enabled.
     */
    public void switchControlsMode() {
        if (this.config.hasAutoSwitchMode()) {
            if (this.config.getController().isGamepad()) {
                this.previousControlsMode = this.config.getControlsMode();
                this.config.setControlsMode(ControlsMode.CONTROLLER);
            } else {
                if (this.previousControlsMode == null) {
                    this.previousControlsMode = ControlsMode.DEFAULT;
                }

                this.config.setControlsMode(this.previousControlsMode);
            }
        }
    }

    /**
     * Sets whether the HUD is enabled or not.
     *
     * @param enabled true if the HUD is enabled, else false
     */
    public void setHudEnabled(boolean enabled) {
        this.config.setHudEnabled(enabled);
        this.hud.setVisible(enabled);
    }

    /**
     * Gets the LambdaControls client instance.
     *
     * @return the LambdaControls client instance
     */
    public static LambdaControlsClient get() {
        return INSTANCE;
    }
}
