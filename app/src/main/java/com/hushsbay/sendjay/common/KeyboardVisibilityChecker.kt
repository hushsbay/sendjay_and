package com.hushsbay.sendjay.common

import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.Window

class KeyboardVisibilityChecker ( //https://googry.tistory.com/43
    private val window: Window,
    private val onShowKeyboard: ((keyboardHeight: Int) -> Unit)? = null,
    private val onHideKeyboard: (() -> Unit)? = null
) {

    private val MIN_KEYBOARD_HEIGHT_PX = 150
    private val windowVisibleDisplayFrame = Rect()
    private var lastVisibleDecorViewHeight: Int = 0

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        window.decorView.getWindowVisibleDisplayFrame(windowVisibleDisplayFrame)
        val visibleDecorViewHeight = windowVisibleDisplayFrame.height()
        if (lastVisibleDecorViewHeight != 0) { // Decide whether keyboard is visible from changing decor view height.
            if (lastVisibleDecorViewHeight > visibleDecorViewHeight + MIN_KEYBOARD_HEIGHT_PX) {
                val currentKeyboardHeight = lastVisibleDecorViewHeight - visibleDecorViewHeight
                onShowKeyboard?.invoke(currentKeyboardHeight) // Notify listener about keyboard being shown.
            } else if (lastVisibleDecorViewHeight + MIN_KEYBOARD_HEIGHT_PX < visibleDecorViewHeight) {
                onHideKeyboard?.invoke() // Notify listener about keyboard being hidden.
            }
        }
        lastVisibleDecorViewHeight = visibleDecorViewHeight // Save current decor view height for the next call.
    }

    init {
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
    }

    fun detachKeyboardListeners() {
        window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
    }

}
