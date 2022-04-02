/*
 * Copyright � 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdacontrols.client.controller;

import dev.lambdaurora.lambdacontrols.LambdaControls;
import dev.lambdaurora.lambdacontrols.client.LambdaControlsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import org.aperlambda.lambdacommon.utils.Nameable;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.lwjgl.BufferUtils.createByteBuffer;

/**
 * Represents a controller.
 *
 * @author LambdAurora
 * @version 1.7.0
 * @since 1.0.0
 */
public record Controller(int id) implements Nameable {
    private static final Map<Integer, Controller> CONTROLLERS = new HashMap<>();

    /**
     * Gets the controller's globally unique identifier.
     *
     * @return the controller's GUID
     */
    public String getGuid() {
        String guid = GLFW.glfwGetJoystickGUID(this.id);
        return guid == null ? "" : guid;
    }

    /**
     * Returns whether this controller is connected or not.
     *
     * @return true if this controller is connected, else false
     */
    public boolean isConnected() {
        return GLFW.glfwJoystickPresent(this.id);
    }

    /**
     * Returns whether this controller is a gamepad or not.
     *
     * @return true if this controller is a gamepad, else false
     */
    public boolean isGamepad() {
        return this.isConnected() && GLFW.glfwJoystickIsGamepad(this.id);
    }

    /**
     * Gets the name of the controller.
     *
     * @return the controller's name
     */
    @Override
    public String getName() {
        var name = this.isGamepad() ? GLFW.glfwGetGamepadName(this.id) : GLFW.glfwGetJoystickName(this.id);
        return name == null ? String.valueOf(this.id()) : name;
    }

    /**
     * Gets the state of the controller.
     *
     * @return the state of the controller input
     */
    public GLFWGamepadState getState() {
        var state = GLFWGamepadState.create();
        if (this.isGamepad())
            GLFW.glfwGetGamepadState(this.id, state);
        return state;
    }

    public static Controller byId(int id) {
        if (id > GLFW.GLFW_JOYSTICK_LAST) {
            LambdaControlsClient.get().log("Controller '" + id + "' doesn't exist.");
            id = GLFW.GLFW_JOYSTICK_LAST;
        }
        Controller controller;
        if (CONTROLLERS.containsKey(id))
            return CONTROLLERS.get(id);
        else {
            controller = new Controller(id);
            CONTROLLERS.put(id, controller);
            return controller;
        }
    }

    public static Optional<Controller> byGuid(@NotNull String guid) {
        return CONTROLLERS.values().stream().filter(Controller::isConnected)
                .filter(controller -> controller.getGuid().equals(guid))
                .max(Comparator.comparingInt(Controller::id));
    }

    /**
     * Reads the specified resource and returns the raw data as a ByteBuffer.
     *
     * @param resource the resource to read
     * @param bufferSize the initial buffer size
     * @return the resource data
     * @throws IOException If an IO error occurs.
     */
    private static ByteBuffer ioResourceToBuffer(String resource, int bufferSize) throws IOException {
        ByteBuffer buffer = null;

        var path = Paths.get(resource);
        if (Files.isReadable(path)) {
            try (var fc = Files.newByteChannel(path)) {
                buffer = createByteBuffer((int) fc.size() + 2);
                while (fc.read(buffer) != -1) ;
                buffer.put((byte) 0);
            }
        }

        buffer.flip(); // Force Java 8 >.<
        return buffer;
    }

    /**
     * Updates the controller mappings.
     */
    public static void updateMappings() {
        try {
            if (!LambdaControlsClient.MAPPINGS_FILE.exists())
                return;
            LambdaControlsClient.get().log("Updating controller mappings...");
            var buffer = ioResourceToBuffer(LambdaControlsClient.MAPPINGS_FILE.getPath(), 1024);
            GLFW.glfwUpdateGamepadMappings(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (var memoryStack = MemoryStack.stackPush()) {
            var pointerBuffer = memoryStack.mallocPointer(1);
            int i = GLFW.glfwGetError(pointerBuffer);
            if (i != 0) {
                long l = pointerBuffer.get();
                var string = l == 0L ? "" : MemoryUtil.memUTF8(l);
                var client = MinecraftClient.getInstance();
                if (client != null) {
                    client.getToastManager().add(SystemToast.create(client, SystemToast.Type.TUTORIAL_HINT,
                            new TranslatableText("lambdacontrols.controller.mappings.error"), new LiteralText(string)));
                }
            }
        } catch (Throwable e) {
            /* Ignored :concern: */
        }

        if (LambdaControlsClient.get().config.hasDebug()) {
            for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_16; i++) {
                var controller = byId(i);

                if (!controller.isConnected())
                    continue;

                LambdaControls.get().log(String.format("Controller #%d name: \"%s\"\n GUID: %s\n Gamepad: %s",
                        controller.id,
                        controller.getName(),
                        controller.getGuid(),
                        controller.isGamepad()));
            }
        }
    }
}
