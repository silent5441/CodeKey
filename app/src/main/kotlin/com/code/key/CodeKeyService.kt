package com.code.key

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

enum class DeleteMode { LETTER, WORD, LINE }
data class AppSettings(var joystickSpeed: Float = 5f, var longPressDelay: Long = 300L, var deleteMode: DeleteMode = DeleteMode.LETTER)

class CodeKeyService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // State trackers for settings
    private var kbHeightState = mutableStateOf(390.dp)
    private var kbColorIndexState = mutableIntStateOf(0)
    private var kbImagePathState = mutableStateOf<String?>(null)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    // This gets called every time the keyboard pops up!
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val prefs = getSharedPreferences("CodeKeyPrefs", Context.MODE_PRIVATE)
        
        // Adjust the height instantly based on Dashboard setting
        kbHeightState.value = when (prefs.getString("kb_height", "Medium")) {
            "Small" -> 320.dp
            "Big" -> 460.dp
            else -> 390.dp
        }
        kbColorIndexState.intValue = prefs.getInt("kb_color_idx", 0)
        kbImagePathState.value = prefs.getString("kb_image_path", null)
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        val decorView = window.window?.decorView
        if (decorView != null) {
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        } else {
            composeView.setViewTreeLifecycleOwner(this)
            composeView.setViewTreeViewModelStoreOwner(this)
            composeView.setViewTreeSavedStateRegistryOwner(this)
        }

        composeView.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // Reads the active height!
                    .height(kbHeightState.value)
            ) {
                CodeKeyAppScreen(
                    kbColorIndex = kbColorIndexState.intValue,
                    kbImagePath = kbImagePathState.value,
                    onCommitText = { text -> currentInputConnection?.commitText(text, 1) },
                    onSpecialKey = { keyEventCode ->
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEventCode))
                    },
                    onDelete = { mode -> handleSmartDelete(mode) },
                    onPerformAction = { actionId ->
                        currentInputConnection?.performContextMenuAction(actionId)
                        when (actionId) {
                            android.R.id.copy -> Toast.makeText(this@CodeKeyService, "Copied", Toast.LENGTH_SHORT).show()
                            android.R.id.paste -> Toast.makeText(this@CodeKeyService, "Pasted", Toast.LENGTH_SHORT).show()
                            android.R.id.cut -> Toast.makeText(this@CodeKeyService, "Cut", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSwitchKeyboard = {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    },
                    onMoveCursor = { keyEventCode, isSelecting ->
                        if (isSelecting) currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode))
                        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEventCode))
                        if (isSelecting) currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT))
                    }
                )
            }
        }
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return composeView
    }

    private fun handleSmartDelete(mode: DeleteMode) {
        val ic = currentInputConnection ?: return
        when (mode) {
            DeleteMode.LETTER -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            DeleteMode.WORD -> {
                val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                var len = 1
                if (text.isNotEmpty()) {
                    val reversed = text.reversed()
                    val firstChar = reversed[0]
                    len = if (!firstChar.isLetterOrDigit()) reversed.takeWhile { !it.isLetterOrDigit() }.length
                    else reversed.takeWhile { it.isLetterOrDigit() }.length
                }
                ic.deleteSurroundingText(if(len == 0) 1 else len, 0)
            }
            DeleteMode.LINE -> {
                val text = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                val len = text.reversed().takeWhile { it != '\n' }.length
                ic.deleteSurroundingText(if (len == 0) 1 else len, 0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
