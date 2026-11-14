package com.easysurvivalpack.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Mixin(Gui.class)
public abstract class StatusDisplayHudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private Component overlayMessageString;

	@Shadow
	private int overlayMessageTime;

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void easySurvivalPack$wrapOverlayMessage(
			GuiGraphics guiGraphics,
			DeltaTracker deltaTracker,
			CallbackInfo callback
	) {
		if (overlayMessageTime <= 0) {
			return;
		}

		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int maxWidth = screenWidth - 20;
		if (minecraft.font.width(overlayMessageString) <= maxWidth) {
			return;
		}

		List<FormattedCharSequence> lines = minecraft.font.split(overlayMessageString, maxWidth);
		if (lines.size() <= 1) {
			return;
		}

		callback.cancel();
		int lineHeight = 9;
		int widestLine = lines.stream().mapToInt(minecraft.font::width).max().orElse(0);
		int firstLineY = minecraft.getWindow().getGuiScaledHeight() - 68 - (lines.size() - 1) * lineHeight;
		guiGraphics.fill(
				(screenWidth - widestLine) / 2 - 2,
				firstLineY - 2,
				(screenWidth + widestLine) / 2 + 2,
				firstLineY + lines.size() * lineHeight + 2,
				0x90000000
		);
		for (int index = 0; index < lines.size(); index++) {
			FormattedCharSequence line = lines.get(index);
			guiGraphics.drawString(
					minecraft.font,
					line,
					(screenWidth - minecraft.font.width(line)) / 2,
					firstLineY + index * lineHeight,
					0xFFFFFF
			);
		}
	}
}
