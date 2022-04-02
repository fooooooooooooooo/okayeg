/*
 * Copyright � 2021 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package dev.lambdaurora.lambdacontrols.client.controller;

import dev.lambdaurora.lambdacontrols.client.ButtonState;
import dev.lambdaurora.lambdacontrols.client.LambdaInput;
import dev.lambdaurora.lambdacontrols.client.mixin.AdvancementsScreenAccessor;
import dev.lambdaurora.lambdacontrols.client.mixin.CreativeInventoryScreenAccessor;
import dev.lambdaurora.lambdacontrols.client.mixin.RecipeBookWidgetAccessor;
import dev.lambdaurora.lambdacontrols.client.util.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemGroup;
import net.minecraft.screen.slot.Slot;
import org.aperlambda.lambdacommon.utils.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents some input handlers.
 *
 * @author LambdAurora
 * @version 1.7.0
 * @since 1.1.0
 */
public class InputHandlers {
    private InputHandlers() {
    }

    public static PressAction handleHotbar(boolean next) {
        return (client, button, value, action) -> {
            if (action == ButtonState.RELEASE)
                return false;

            // When ingame
            if (client.currentScreen == null && client.player != null) {
                if (next)
                    client.player.getInventory().selectedSlot = client.player.getInventory().selectedSlot == 8 ? 0 : client.player.getInventory().selectedSlot + 1;
                else
                    client.player.getInventory().selectedSlot = client.player.getInventory().selectedSlot == 0 ? 8 : client.player.getInventory().selectedSlot - 1;
                return true;
            } else if (client.currentScreen instanceof CreativeInventoryScreenAccessor inventory) {
                int currentTab = inventory.getSelectedTab();
                int nextTab = currentTab + (next ? 1 : -1);
                if (nextTab < 0)
                    nextTab = ItemGroup.GROUPS.length - 1;
                else if (nextTab >= ItemGroup.GROUPS.length)
                    nextTab = 0;
                inventory.lambdacontrols$setSelectedTab(ItemGroup.GROUPS[nextTab]);
                return true;
            } else if (client.currentScreen instanceof InventoryScreen inventoryScreen) {
                var recipeBook = (RecipeBookWidgetAccessor) inventoryScreen.getRecipeBookWidget();
                var tabs = recipeBook.getTabButtons();
                var currentTab = recipeBook.getCurrentTab();
                if (currentTab == null)
                    return false;
                int nextTab = tabs.indexOf(currentTab) + (next ? 1 : -1);
                if (nextTab < 0)
                    nextTab = tabs.size() - 1;
                else if (nextTab >= tabs.size())
                    nextTab = 0;
                currentTab.setToggled(false);
                recipeBook.setCurrentTab(currentTab = tabs.get(nextTab));
                currentTab.setToggled(true);
                recipeBook.lambdacontrols$refreshResults(true);
                return true;
            } else if (client.currentScreen instanceof AdvancementsScreenAccessor screen) {
                var tabs = screen.getTabs().values().stream().distinct().collect(Collectors.toList());
                var tab = screen.getSelectedTab();
                if (tab == null)
                    return false;
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i).equals(tab)) {
                        int nextTab = i + (next ? 1 : -1);
                        if (nextTab < 0)
                            nextTab = tabs.size() - 1;
                        else if (nextTab >= tabs.size())
                            nextTab = 0;
                        screen.getAdvancementManager().selectTab(tabs.get(nextTab).getRoot(), true);
                        break;
                    }
                }
                return true;
            }
            return false;
        };
    }

    public static boolean handlePauseGame(@NotNull MinecraftClient client, @NotNull ButtonBinding binding, float value, @NotNull ButtonState action) {
        if (action == ButtonState.PRESS) {
            // If in game, then pause the game.
            if (client.currentScreen == null)
                client.openPauseMenu(false);
            else if (client.currentScreen instanceof HandledScreen && client.player != null) // If the current screen is a container then close it.
                client.player.closeHandledScreen();
            else // Else just close the current screen.
                client.currentScreen.onClose();
        }
        return true;
    }

    /**
     * Handles the screenshot action.
     *
     * @param client the client instance
     * @param binding the binding which fired the action
     * @param action the action done on the binding
     * @return true if handled, else false
     */
    public static boolean handleScreenshot(@NotNull MinecraftClient client, @NotNull ButtonBinding binding, float value, @NotNull ButtonState action) {
        if (action == ButtonState.RELEASE)
            ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer(),
                    text -> client.execute(() -> client.inGameHud.getChatHud().addMessage(text)));
        return true;
    }

    public static boolean handleToggleSneak(@NotNull MinecraftClient client, @NotNull ButtonBinding button, float value, @NotNull ButtonState action) {
        button.asKeyBinding().ifPresent(binding -> {
            boolean sneakToggled = client.options.sneakToggled;
            if (client.player.getAbilities().flying && sneakToggled)
                client.options.sneakToggled = false;
            binding.setPressed(button.pressed);
            if (client.player.getAbilities().flying && sneakToggled)
                client.options.sneakToggled = true;
        });
        return true;
    }

    public static PressAction handleInventorySlotPad(int direction) {
        return (client, binding, value, action) -> {
            if (!(client.currentScreen instanceof HandledScreen inventory && action != ButtonState.RELEASE))
                return false;

            var accessor = (HandledScreenAccessor) inventory;
            int guiLeft = accessor.getX();
            int guiTop = accessor.getY();
            double mouseX = client.mouse.getX() * (double) client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * (double) client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();

            // Finds the hovered slot.
            var mouseSlot = accessor.lambdacontrols$getSlotAt(mouseX, mouseY);

            // Finds the closest slot in the GUI within 14 pixels.
            Optional<Slot> closestSlot = inventory.getScreenHandler().slots.parallelStream()
                    .filter(Predicate.isEqual(mouseSlot).negate())
                    .map(slot -> {
                        int posX = guiLeft + slot.x + 8;
                        int posY = guiTop + slot.y + 8;

                        int otherPosX = (int) mouseX;
                        int otherPosY = (int) mouseY;
                        if (mouseSlot != null) {
                            otherPosX = guiLeft + mouseSlot.x + 8;
                            otherPosY = guiTop + mouseSlot.y + 8;
                        }

                        // Distance between the slot and the cursor.
                        double distance = Math.sqrt(Math.pow(posX - otherPosX, 2) + Math.pow(posY - otherPosY, 2));
                        return Pair.of(slot, distance);
                    }).filter(entry -> {
                        var slot = entry.key;
                        int posX = guiLeft + slot.x + 8;
                        int posY = guiTop + slot.y + 8;
                        int otherPosX = (int) mouseX;
                        int otherPosY = (int) mouseY;
                        if (mouseSlot != null) {
                            otherPosX = guiLeft + mouseSlot.x + 8;
                            otherPosY = guiTop + mouseSlot.y + 8;
                        }
                        if (direction == 0)
                            return posY < otherPosY;
                        else if (direction == 1)
                            return posY > otherPosY;
                        else if (direction == 2)
                            return posX > otherPosX;
                        else if (direction == 3)
                            return posX < otherPosX;
                        else
                            return false;
                    })
                    .min(Comparator.comparingDouble(p -> p.value))
                    .map(p -> p.key);

            if (closestSlot.isPresent()) {
                var slot = closestSlot.get();
                int x = guiLeft + slot.x + 8;
                int y = guiTop + slot.y + 8;
                InputManager.queueMousePosition(x * (double) client.getWindow().getWidth() / (double) client.getWindow().getScaledWidth(),
                        y * (double) client.getWindow().getHeight() / (double) client.getWindow().getScaledHeight());
                return true;
            }
            return false;
        };
    }

    /**
     * Returns always true to the filter.
     *
     * @param client the client instance
     * @param binding the affected binding
     * @return true
     */
    public static boolean always(@NotNull MinecraftClient client, @NotNull ButtonBinding binding) {
        return true;
    }

    /**
     * Returns whether the client is in game or not.
     *
     * @param client the client instance
     * @param binding the affected binding
     * @return true if the client is in game, else false
     */
    public static boolean inGame(@NotNull MinecraftClient client, @NotNull ButtonBinding binding) {
        return client.currentScreen == null;
    }

    /**
     * Returns whether the client is in a non-interactive screen (which means require mouse input) or not.
     *
     * @param client the client instance
     * @param binding the affected binding
     * @return true if the client is in a non-interactive screen, else false
     */
    public static boolean inNonInteractiveScreens(@NotNull MinecraftClient client, @NotNull ButtonBinding binding) {
        if (client.currentScreen == null)
            return false;
        return !LambdaInput.isScreenInteractive(client.currentScreen);
    }

    /**
     * Returns whether the client is in an inventory or not.
     *
     * @param client the client instance
     * @param binding the affected binding
     * @return true if the client is in an inventory, else false
     */
    public static boolean inInventory(@NotNull MinecraftClient client, @NotNull ButtonBinding binding) {
        return client.currentScreen instanceof HandledScreen;
    }

    /**
     * Returns whether the client is in the advancements screen or not.
     *
     * @param client the client instance
     * @param binding the affected binding
     * @return true if the client is in the advancements screen, else false
     */
    public static boolean inAdvancements(@NotNull MinecraftClient client, @NotNull ButtonBinding binding) {
        return client.currentScreen instanceof AdvancementsScreen;
    }
}
