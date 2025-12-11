package com.choculaterie.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import org.lwjgl.glfw.GLFW;

/**
 * A simple text input field that handles ALL input through GLFW.
 * Uses character callback for proper unicode support.
 */
public class SimpleTextField implements Drawable, Element {
    private static final int BG_COLOR = 0xFF2A2A2A;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int BORDER_FOCUSED_COLOR = 0xFF888888;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PLACEHOLDER_COLOR = 0xFF888888;
    private static final int CURSOR_COLOR = 0xFFFFFFFF;

    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private StringBuilder text = new StringBuilder();
    private String placeholder = "";
    private int cursorPosition = 0;
    private int maxLength = 255;
    private boolean focused = false;
    private Runnable onChanged;
    private Runnable onEnterPressed;

    // Key state tracking for special keys
    private boolean wasBackspacePressed = false;
    private boolean wasDeletePressed = false;
    private boolean wasLeftPressed = false;
    private boolean wasRightPressed = false;
    private boolean wasHomePressed = false;
    private boolean wasEndPressed = false;
    private boolean wasMousePressed = false;

    // Key repeat for backspace/delete
    private long backspaceHoldStart = 0;
    private long deleteHoldStart = 0;
    private long lastBackspaceRepeat = 0;
    private long lastDeleteRepeat = 0;

    // Key repeat for arrow keys
    private long leftHoldStart = 0;
    private long rightHoldStart = 0;
    private long lastLeftRepeat = 0;
    private long lastRightRepeat = 0;

    // Text scroll offset for long text
    private int scrollOffset = 0;

    // For cursor blinking
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;

    // Character callback
    private static SimpleTextField activeField = null;
    private static boolean callbackInstalled = false;
    private static long installedWindowHandle = 0;

    // Flag to handle Enter key press in next render cycle (after char callback)
    private boolean enterPressedThisFrame = false;

    public SimpleTextField(MinecraftClient client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void setOnEnterPressed(Runnable onEnterPressed) {
        this.onEnterPressed = onEnterPressed;
    }

    public String getText() {
        return text.toString();
    }

    public void setText(String newText) {
        this.text = new StringBuilder(newText);
        this.cursorPosition = text.length();
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            cursorPosition = text.length();
            activeField = this;
            installCharCallback();
        } else if (activeField == this) {
            activeField = null;
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    private void installCharCallback() {
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;
        if (windowHandle != 0 && (!callbackInstalled || installedWindowHandle != windowHandle)) {
            // Install character callback for text input
            GLFW.glfwSetCharCallback(windowHandle, (window, codepoint) -> {
                if (activeField != null && activeField.focused) {
                    activeField.onCharTyped((char) codepoint);
                }
            });

            // Install key callback for special keys (Enter, Escape, etc)
            GLFW.glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
                if (activeField != null && activeField.focused && action == GLFW.GLFW_PRESS) {
                    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                        // Mark that Enter was pressed - will be handled in next render after char processing
                        activeField.enterPressedThisFrame = true;
                    }
                }
            });

            callbackInstalled = true;
            installedWindowHandle = windowHandle;
        }
    }

    private void onCharTyped(char c) {
        // Check if it's Enter key (char code 13 or 10)
        if (c == '\r' || c == '\n') {
            return; // Don't add Enter to text
        }

        if (text.length() < maxLength) {
            text.insert(cursorPosition, c);
            cursorPosition++;
            if (onChanged != null) onChanged.run();
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;

        if (windowHandle != 0 && focused) {
            // Ensure callback is installed
            if (activeField != this) {
                activeField = this;
                installCharCallback();
            }

            // Handle Enter key press from previous frame (after char callback processed)
            if (enterPressedThisFrame) {
                enterPressedThisFrame = false;
                if (onEnterPressed != null) {
                    onEnterPressed.run();
                }
            }

            handleSpecialKeys(windowHandle);
            handleMouseClick(windowHandle, mouseX, mouseY);
        }

        // Draw background
        context.fill(x, y, x + width, y + height, BG_COLOR);

        // Draw border
        int borderColor = focused ? BORDER_FOCUSED_COLOR : BORDER_COLOR;
        context.fill(x, y, x + width, y + 1, borderColor);
        context.fill(x, y + height - 1, x + width, y + height, borderColor);
        context.fill(x, y, x + 1, y + height, borderColor);
        context.fill(x + width - 1, y, x + width, y + height, borderColor);

        // Draw text or placeholder
        int textY = y + (height - 8) / 2;
        int textX = x + 4;
        int maxTextWidth = width - 10; // Padding on both sides, extra for cursor

        if (text.length() == 0 && !focused) {
            context.drawTextWithShadow(client.textRenderer, placeholder, textX, textY, PLACEHOLDER_COLOR);
        } else {
            String displayText = text.toString();
            int fullTextWidth = client.textRenderer.getWidth(displayText);

            // Calculate scroll offset to keep cursor visible
            if (focused) {
                String beforeCursor = text.substring(0, cursorPosition);
                int cursorX = client.textRenderer.getWidth(beforeCursor);

                // If cursor is beyond visible area, scroll right (leave room for cursor)
                if (cursorX - scrollOffset > maxTextWidth - 4) {
                    scrollOffset = cursorX - maxTextWidth + 10;
                }
                // If cursor is before visible area, scroll left
                if (cursorX - scrollOffset < 0) {
                    scrollOffset = Math.max(0, cursorX - 10);
                }
            }

            // Clamp scroll offset
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, fullTextWidth - maxTextWidth + 4)));

            // Enable scissor to clip text (add extra pixel for cursor at end)
            context.enableScissor(textX, y, textX + maxTextWidth + 2, y + height);

            // Draw text with scroll offset
            context.drawTextWithShadow(client.textRenderer, displayText, textX - scrollOffset, textY, TEXT_COLOR);

            // Draw cursor if focused
            if (focused) {
                long now = System.currentTimeMillis();
                if (now - lastCursorBlink > 500) {
                    cursorVisible = !cursorVisible;
                    lastCursorBlink = now;
                }

                if (cursorVisible) {
                    String beforeCursor = text.substring(0, cursorPosition);
                    int cursorDrawX = textX + client.textRenderer.getWidth(beforeCursor) - scrollOffset;
                    context.fill(cursorDrawX, textY - 1, cursorDrawX + 1, textY + 9, CURSOR_COLOR);
                }
            }

            context.disableScissor();
        }
    }

    private void handleMouseClick(long windowHandle, int mouseX, int mouseY) {
        boolean mousePressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // Only process on mouse down (not held)
        if (mousePressed && !wasMousePressed) {
            // Check if click is within the text field
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                // Calculate cursor position based on click location (accounting for scroll)
                int textX = x + 4;
                int clickX = mouseX - textX + scrollOffset;

                // Find the closest character position
                int newCursorPos = 0;
                String currentText = text.toString();

                for (int i = 0; i <= currentText.length(); i++) {
                    String substring = currentText.substring(0, i);
                    int charX = client.textRenderer.getWidth(substring);

                    if (charX > clickX) {
                        // Check if we're closer to this position or the previous one
                        if (i > 0) {
                            String prevSubstring = currentText.substring(0, i - 1);
                            int prevCharX = client.textRenderer.getWidth(prevSubstring);
                            if (clickX - prevCharX < charX - clickX) {
                                newCursorPos = i - 1;
                            } else {
                                newCursorPos = i;
                            }
                        } else {
                            newCursorPos = 0;
                        }
                        break;
                    }
                    newCursorPos = i;
                }

                cursorPosition = newCursorPos;
                cursorVisible = true;
                lastCursorBlink = System.currentTimeMillis();
            }
        }

        wasMousePressed = mousePressed;
    }

    private void handleSpecialKeys(long windowHandle) {
        long now = System.currentTimeMillis();

        // Backspace with key repeat
        boolean backspacePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS;
        if (backspacePressed && !wasBackspacePressed) {
            handleBackspace();
            backspaceHoldStart = now;
            lastBackspaceRepeat = now;
        } else if (backspacePressed && now - backspaceHoldStart > 400) {
            if (now - lastBackspaceRepeat > 30) {
                handleBackspace();
                lastBackspaceRepeat = now;
            }
        }
        wasBackspacePressed = backspacePressed;

        // Delete with key repeat
        boolean deletePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS;
        if (deletePressed && !wasDeletePressed) {
            handleDelete();
            deleteHoldStart = now;
            lastDeleteRepeat = now;
        } else if (deletePressed && now - deleteHoldStart > 400) {
            if (now - lastDeleteRepeat > 30) {
                handleDelete();
                lastDeleteRepeat = now;
            }
        }
        wasDeletePressed = deletePressed;

        // Left arrow with key repeat
        boolean leftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
        if (leftPressed && !wasLeftPressed) {
            if (cursorPosition > 0) cursorPosition--;
            leftHoldStart = now;
            lastLeftRepeat = now;
        } else if (leftPressed && now - leftHoldStart > 400) {
            if (now - lastLeftRepeat > 30) {
                if (cursorPosition > 0) cursorPosition--;
                lastLeftRepeat = now;
            }
        }
        wasLeftPressed = leftPressed;

        // Right arrow with key repeat
        boolean rightPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
        if (rightPressed && !wasRightPressed) {
            if (cursorPosition < text.length()) cursorPosition++;
            rightHoldStart = now;
            lastRightRepeat = now;
        } else if (rightPressed && now - rightHoldStart > 400) {
            if (now - lastRightRepeat > 30) {
                if (cursorPosition < text.length()) cursorPosition++;
                lastRightRepeat = now;
            }
        }
        wasRightPressed = rightPressed;

        // Home
        boolean homePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_HOME) == GLFW.GLFW_PRESS;
        if (homePressed && !wasHomePressed) {
            cursorPosition = 0;
        }
        wasHomePressed = homePressed;

        // End
        boolean endPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_END) == GLFW.GLFW_PRESS;
        if (endPressed && !wasEndPressed) {
            cursorPosition = text.length();
        }
        wasEndPressed = endPressed;
    }

    private void handleBackspace() {
        if (cursorPosition > 0) {
            text.deleteCharAt(cursorPosition - 1);
            cursorPosition--;
            if (onChanged != null) onChanged.run();
        }
    }

    private void handleDelete() {
        if (cursorPosition < text.length()) {
            text.deleteCharAt(cursorPosition);
            if (onChanged != null) onChanged.run();
        }
    }


    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

