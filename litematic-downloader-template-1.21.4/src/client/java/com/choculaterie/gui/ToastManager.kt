package com.choculaterie.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

object ToastManager {
    private val toasts: MutableList<Toast?> = ArrayList<Toast?>()
    private const val TOAST_DURATION = 3000 // 3 seconds
    private const val TOAST_HEIGHT = 20
    private const val TOAST_MARGIN = 10

    fun addToast(message: String, isError: Boolean) {
        toasts.add(Toast(message, isError, System.currentTimeMillis()))
    }

    fun render(context: DrawContext, screenWidth: Int) {
        val currentTime = System.currentTimeMillis()
        val iterator: MutableIterator<Toast?> = toasts.iterator()

        // Save the current transform state
        context.matrices.push()

        // Ensure toasts are rendered at the highest z-index
        context.matrices.translate(0.0, 0.0, 1000.0)

        var y = TOAST_MARGIN
        while (iterator.hasNext()) {
            val toast = iterator.next()

            if (toast != null && currentTime - toast.creationTime > TOAST_DURATION) {
                iterator.remove()
                continue
            }

            var alpha = 1.0f
            val age = currentTime - (toast?.creationTime ?: 0L)
            if (age > TOAST_DURATION - 500) {
                alpha = (TOAST_DURATION - age).toFloat() / 500.0f
            }

            val width = MinecraftClient.getInstance().textRenderer.getWidth(toast?.message ?: "") + 20
            val x = screenWidth - width - TOAST_MARGIN

            // Fully opaque background colors
            val bgColor = if (toast?.isError == true)
                0xFFFF0000.toInt()  // Opaque red for errors
            else
                0xFF00AA00.toInt()  // Opaque green for success

            // Draw background with z-positioning
            context.fill(x, y, x + width, y + TOAST_HEIGHT, bgColor)

            // Draw text - fixed by using Text.literal to convert String to Text
            context.drawTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(toast?.message ?: ""),
                x + 10,
                y + 5,
                0xFFFFFFFF.toInt()
            )

            y += TOAST_HEIGHT + 5
        }

        // Restore the original transform state
        context.matrices.pop()
    }

    private class Toast(val message: String, val isError: Boolean, val creationTime: Long)
}