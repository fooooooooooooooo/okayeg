/*
 * Copyright � 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdacontrols.client.controller;

import dev.lambdaurora.lambdacontrols.ControlsMode;
import dev.lambdaurora.lambdacontrols.client.ButtonState;
import dev.lambdaurora.lambdacontrols.client.LambdaControlsClient;
import dev.lambdaurora.lambdacontrols.client.LambdaControlsConfig;
import dev.lambdaurora.lambdacontrols.client.util.MouseAccessor;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.aperlambda.lambdacommon.Identifier;
import org.aperlambda.lambdacommon.utils.function.PairPredicate;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents an input manager for controllers.
 *
 * @author LambdAurora
 * @version 1.7.0
 * @since 1.1.0
 */
public class InputManager {
    public static final InputManager INPUT_MANAGER = new InputManager();
    private static final List<ButtonBinding> BINDINGS = new ArrayList<>();
    private static final List<ButtonCategory> CATEGORIES = new ArrayList<>();
    public static final Int2ObjectMap<ButtonState> STATES = new Int2ObjectOpenHashMap<>();
    public static final Int2FloatMap BUTTON_VALUES = new Int2FloatOpenHashMap();
    private int prevTargetMouseX = 0;
    private int prevTargetMouseY = 0;
    private int targetMouseX = 0;
    private int targetMouseY = 0;

    protected InputManager() {
    }

    public void tick(@NotNull MinecraftClient client) {
        if (LambdaControlsClient.get().config.getControlsMode() == ControlsMode.CONTROLLER) {
            this.controllerTick(client);
        }
    }

    public void controllerTick(@NotNull MinecraftClient client) {
        this.prevTargetMouseX = this.targetMouseX;
        this.prevTargetMouseY = this.targetMouseY;
    }

    /**
     * Updates the mouse position. Should only be called on pre render of a screen.
     *
     * @param client the client instance
     */
    public void updateMousePosition(@NotNull MinecraftClient client) {
        Objects.requireNonNull(client, "Client instance cannot be null.");
        if (this.prevTargetMouseX != this.targetMouseX || this.prevTargetMouseY != this.targetMouseY) {
            double mouseX = this.prevTargetMouseX + (this.targetMouseX - this.prevTargetMouseX) * client.getTickDelta() + 0.5;
            double mouseY = this.prevTargetMouseY + (this.targetMouseY - this.prevTargetMouseY) * client.getTickDelta() + 0.5;
            if (!LambdaControlsClient.get().config.hasVirtualMouse())
                GLFW.glfwSetCursorPos(client.getWindow().getHandle(), mouseX, mouseY);
            ((MouseAccessor) client.mouse).lambdacontrols$onCursorPos(client.getWindow().getHandle(), mouseX, mouseY);
        }
    }

    /**
     * Resets the mouse position.
     *
     * @param windowWidth the window width
     * @param windowHeight the window height
     */
    public void resetMousePosition(int windowWidth, int windowHeight) {
        this.targetMouseX = this.prevTargetMouseX = (int) (windowWidth / 2.F);
        this.targetMouseY = this.prevTargetMouseY = (int) (windowHeight / 2.F);
    }

    public void resetMouseTarget(@NotNull MinecraftClient client) {
        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();
        this.prevTargetMouseX = this.targetMouseX = (int) mouseX;
        this.prevTargetMouseY = this.targetMouseY = (int) mouseY;
    }

    /**
     * Returns whether the specified binding is registered or not.
     *
     * @param binding the binding to check
     * @return true if the binding is registered, else false
     */
    public static boolean hasBinding(@NotNull ButtonBinding binding) {
        return BINDINGS.contains(binding);
    }

    /**
     * Returns whether the specified binding is registered or not.
     *
     * @param name the name of the binding to check
     * @return true if the binding is registered, else false
     */
    public static boolean hasBinding(@NotNull String name) {
        return BINDINGS.parallelStream().map(ButtonBinding::getName).anyMatch(binding -> binding.equalsIgnoreCase(name));
    }

    /**
     * Returns whether the specified binding is registered or not.
     *
     * @param identifier the identifier of the binding to check
     * @return true if the binding is registered, else false
     */
    public static boolean hasBinding(@NotNull Identifier identifier) {
        return hasBinding(identifier.getNamespace() + "." + identifier.getName());
    }

    /**
     * Registers a button binding.
     *
     * @param binding the binding to register
     * @return the registered binding
     */
    public static @NotNull ButtonBinding registerBinding(@NotNull ButtonBinding binding) {
        if (hasBinding(binding))
            throw new IllegalStateException("Cannot register twice a button binding in the registry.");
        BINDINGS.add(binding);
        return binding;
    }

    public static @NotNull ButtonBinding registerBinding(@NotNull Identifier id, int[] defaultButton, @NotNull List<PressAction> actions, @NotNull PairPredicate<MinecraftClient, ButtonBinding> filter, boolean hasCooldown) {
        return registerBinding(new ButtonBinding(id.getNamespace() + "." + id.getName(), defaultButton, actions, filter, hasCooldown));
    }

    public static @NotNull ButtonBinding registerBinding(@NotNull Identifier id, int[] defaultButton, boolean hasCooldown) {
        return registerBinding(id, defaultButton, Collections.emptyList(), InputHandlers::always, hasCooldown);
    }

    public static @NotNull ButtonBinding registerBinding(@NotNull net.minecraft.util.Identifier id, int[] defaultButton, @NotNull List<PressAction> actions, @NotNull PairPredicate<MinecraftClient, ButtonBinding> filter, boolean hasCooldown) {
        return registerBinding(new Identifier(id.getNamespace(), id.getPath()), defaultButton, actions, filter, hasCooldown);
    }

    public static @NotNull ButtonBinding registerBinding(@NotNull net.minecraft.util.Identifier id, int[] defaultButton, boolean hasCooldown) {
        return registerBinding(id, defaultButton, Collections.emptyList(), InputHandlers::always, hasCooldown);
    }

    /**
     * Sorts bindings to get bindings with the higher button counts first.
     */
    public static void sortBindings() {
        synchronized (BINDINGS) {
            var sorted = BINDINGS.stream()
                    .sorted(Collections.reverseOrder(Comparator.comparingInt(binding -> binding.getButton().length)))
                    .collect(Collectors.toList());
            BINDINGS.clear();
            BINDINGS.addAll(sorted);
        }
    }

    /**
     * Registers a category of button bindings.
     *
     * @param category the category to register
     * @return the registered category
     */
    public static ButtonCategory registerCategory(@NotNull ButtonCategory category) {
        CATEGORIES.add(category);
        return category;
    }

    public static ButtonCategory registerCategory(@NotNull Identifier identifier, int priority) {
        return registerCategory(new ButtonCategory(identifier, priority));
    }

    public static ButtonCategory registerCategory(@NotNull Identifier identifier) {
        return registerCategory(new ButtonCategory(identifier));
    }

    protected static ButtonCategory registerDefaultCategory(@NotNull String key, @NotNull Consumer<ButtonCategory> keyAdder) {
        var category = registerCategory(new Identifier("minecraft", key), CATEGORIES.size());
        keyAdder.accept(category);
        return category;
    }

    /**
     * Loads the button bindings from configuration.
     *
     * @param config the configuration instance
     */
    public static void loadButtonBindings(@NotNull LambdaControlsConfig config) {
        var queue = new ArrayList<>(BINDINGS);
        queue.forEach(config::loadButtonBinding);
    }

    /**
     * Returns the binding state.
     *
     * @param binding the binding
     * @return the current state of the binding
     */
    public static @NotNull ButtonState getBindingState(@NotNull ButtonBinding binding) {
        var state = ButtonState.REPEAT;
        for (int btn : binding.getButton()) {
            var btnState = InputManager.STATES.getOrDefault(btn, ButtonState.NONE);
            if (btnState == ButtonState.PRESS)
                state = ButtonState.PRESS;
            else if (btnState == ButtonState.RELEASE) {
                state = ButtonState.RELEASE;
                break;
            } else if (btnState == ButtonState.NONE) {
                state = ButtonState.NONE;
                break;
            }
        }
        return state;
    }

    public static float getBindingValue(@NotNull ButtonBinding binding, @NotNull ButtonState state) {
        if (state.isUnpressed())
            return 0.f;

        float value = 0.f;
        for (int btn : binding.getButton()) {
            if (ButtonBinding.isAxis(btn)) {
                value = BUTTON_VALUES.getOrDefault(btn, 1.f);
                break;
            } else {
                value = 1.f;
            }
        }
        return value;
    }

    /**
     * Returns whether the button has duplicated bindings.
     *
     * @param button the button to check
     * @return true if the button has duplicated bindings, else false
     */
    public static boolean hasDuplicatedBindings(int[] button) {
        return BINDINGS.parallelStream().filter(binding -> areButtonsEquivalent(binding.getButton(), button)).count() > 1;
    }

    /**
     * Returns whether the button has duplicated bindings.
     *
     * @param binding the binding to check
     * @return true if the button has duplicated bindings, else false
     */
    public static boolean hasDuplicatedBindings(ButtonBinding binding) {
        return BINDINGS.parallelStream().filter(other -> areButtonsEquivalent(other.getButton(), binding.getButton()) && other.filter.equals(binding.filter)).count() > 1;
    }

    /**
     * Returns whether the specified buttons are equivalent or not.
     *
     * @param buttons1 first set of buttons
     * @param buttons2 second set of buttons
     * @return true if the two sets of buttons are equivalent, else false
     */
    public static boolean areButtonsEquivalent(int[] buttons1, int[] buttons2) {
        if (buttons1.length != buttons2.length)
            return false;
        int count = 0;
        for (int btn : buttons1) {
            for (int btn2 : buttons2) {
                if (btn == btn2) {
                    count++;
                    break;
                }
            }
        }
        return count == buttons1.length;
    }

    /**
     * Returns whether the button set contains the specified button or not.
     *
     * @param buttons the button set
     * @param button the button to check
     * @return true if the button set contains the specified button, else false
     */
    public static boolean containsButton(int[] buttons, int button) {
        return Arrays.stream(buttons).anyMatch(btn -> btn == button);
    }

    /**
     * Updates the button states.
     */
    public static void updateStates() {
        for (var entry : STATES.int2ObjectEntrySet()) {
            if (entry.getValue() == ButtonState.PRESS)
                STATES.put(entry.getIntKey(), ButtonState.REPEAT);
            else if (entry.getValue() == ButtonState.RELEASE)
                STATES.put(entry.getIntKey(), ButtonState.NONE);
        }
    }

    public static void updateBindings(@NotNull MinecraftClient client) {
        var skipButtons = new IntArrayList();
        record ButtonStateValue(ButtonState state, float value) {
        }
        var states = new Object2ObjectOpenHashMap<ButtonBinding, ButtonStateValue>();
        for (var binding : BINDINGS) {
            var state = binding.isAvailable(client) ? getBindingState(binding) : ButtonState.NONE;
            if (skipButtons.stream().anyMatch(btn -> containsButton(binding.getButton(), btn))) {
                if (binding.pressed)
                    state = ButtonState.RELEASE;
                else
                    state = ButtonState.NONE;
            }

            if (state == ButtonState.RELEASE && !binding.pressed) {
                state = ButtonState.NONE;
            }

            binding.pressed = state.isPressed();
            binding.update();
            if (binding.pressed)
                Arrays.stream(binding.getButton()).forEach(skipButtons::add);

            float value = getBindingValue(binding, state);

            states.put(binding, new ButtonStateValue(state, value));
        }

        states.forEach((binding, state) -> {
            if (state.state() != ButtonState.NONE) {
                binding.handle(client, state.value(), state.state());
            }
        });
    }

    public static void queueMousePosition(double x, double y) {
        INPUT_MANAGER.targetMouseX = (int) MathHelper.clamp(x, 0, MinecraftClient.getInstance().getWindow().getWidth());
        INPUT_MANAGER.targetMouseY = (int) MathHelper.clamp(y, 0, MinecraftClient.getInstance().getWindow().getHeight());
    }

    public static void queueMoveMousePosition(double x, double y) {
        queueMousePosition(INPUT_MANAGER.targetMouseX + x, INPUT_MANAGER.targetMouseY + y);
    }

    public static @NotNull Stream<ButtonBinding> streamBindings() {
        return BINDINGS.stream();
    }

    public static @NotNull Stream<ButtonCategory> streamCategories() {
        return CATEGORIES.stream();
    }

    /**
     * Returns a new key binding instance.
     *
     * @param id the identifier of the key binding
     * @param type the type
     * @param code the code
     * @param category the category of the key binding
     * @return the key binding
     * @see #makeKeyBinding(Identifier, InputUtil.Type, int, String)
     */
    public static @NotNull KeyBinding makeKeyBinding(@NotNull net.minecraft.util.Identifier id, InputUtil.Type type, int code, @NotNull String category) {
        return makeKeyBinding(new Identifier(id.getNamespace(), id.getPath()), type, code, category);
    }

    /**
     * Returns a new key binding instance.
     *
     * @param id the identifier of the key binding
     * @param type the type
     * @param code the code
     * @param category the category of the key binding
     * @return the key binding
     * @see #makeKeyBinding(net.minecraft.util.Identifier, InputUtil.Type, int, String)
     */
    public static @NotNull KeyBinding makeKeyBinding(@NotNull Identifier id, InputUtil.Type type, int code, @NotNull String category) {
        return new KeyBinding(String.format("key.%s.%s", id.getNamespace(), id.getName()), type, code, category);
    }
}
