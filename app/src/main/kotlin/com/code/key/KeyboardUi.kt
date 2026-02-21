package com.code.key

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

enum class KeyboardMode { QWERTY, SYMBOLS, SETTINGS }
// NEW: 3-State Shift Engine
enum class ShiftState { LOWER, SHIFTED, CAPS_LOCK } 

@Composable
fun CodeKeyAppScreen(
    kbColorIndex: Int,
    kbImagePath: String?,
    onCommitText: (String) -> Unit, onSpecialKey: (Int) -> Unit, onDelete: (DeleteMode) -> Unit,
    onPerformAction: (Int) -> Unit, onSwitchKeyboard: () -> Unit, onMoveCursor: (Int, Boolean) -> Unit
) {
    var currentMode by remember { mutableStateOf(KeyboardMode.QWERTY) }
    var shiftState by remember { mutableStateOf(ShiftState.LOWER) }
    var lastShiftTap by remember { mutableLongStateOf(0L) }
    
    var isSelecting by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(AppSettings()) }

    // Inherits PremiumPalettes from MainActivity and BgColor from KeyboardComponents
    val activeBgColor = PremiumPalettes.getOrElse(kbColorIndex) { BgColor }

    // THE FIX: Decoupled casing engine. Evaluates case instantly on tap, completely eliminating lag.
    val handleKeyPress: (String) -> Unit = { rawText ->
        val isLetter = rawText.length == 1 && rawText.first().isLetter()
        val textToCommit = if (isLetter && shiftState != ShiftState.LOWER) rawText.uppercase() else rawText
        
        onCommitText(textToCommit)
        
        // Automatically reverts shift after one letter is typed! (Leaves Caps Lock alone)
        if (isLetter && shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.LOWER
        }
    }

    // THE FIX: Handles single tap (Shift) vs double tap (Caps Lock)
    val handleToggleShift = {
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 300) {
            shiftState = ShiftState.CAPS_LOCK // Double tap = Caps Lock
        } else if (shiftState == ShiftState.LOWER) {
            shiftState = ShiftState.SHIFTED // Single tap = Shift 1 letter
        } else {
            shiftState = ShiftState.LOWER // Turn off
        }
        lastShiftTap = now
    }

    Box(modifier = Modifier.fillMaxSize().background(activeBgColor)) {
        
        if (kbImagePath != null) {
            AsyncImage(
                model = File(kbImagePath),
                contentDescription = "Custom Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            
            if (currentMode != KeyboardMode.SETTINGS) {
                TopActionBar(isSelecting, { isSelecting = !isSelecting }, onPerformAction, { currentMode = KeyboardMode.SETTINGS })
                Spacer(modifier = Modifier.height(8.dp))
                QuickSymbolsRow(handleKeyPress)
                Spacer(modifier = Modifier.height(8.dp))
                CommandRow(isSelecting, onSpecialKey, onMoveCursor)
            }

            Spacer(modifier = Modifier.weight(1f))

            when (currentMode) {
                KeyboardMode.QWERTY -> QwertyLayout(shiftState, isSelecting, settings, handleToggleShift, { isSelecting = !isSelecting }, handleKeyPress, onSpecialKey, onDelete, { currentMode = it }, onSwitchKeyboard, onMoveCursor)
                KeyboardMode.SYMBOLS -> SymbolsLayout(handleKeyPress, onSpecialKey, onDelete, settings, { currentMode = it })
                KeyboardMode.SETTINGS -> SettingsLayout(settings, { settings = it }, { currentMode = KeyboardMode.QWERTY })
            }
        }
    }
}

// --- SCROLLABLE TOOLS ---
@Composable
fun TopActionBar(isSelecting: Boolean, onToggleSelect: () -> Unit, onPerformAction: (Int) -> Unit, onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScrollableRgbButton(if (isSelecting) "✓ Selecting" else "Select", Modifier.width(100.dp).height(38.dp), bgColor = if (isSelecting) AccentColor else SpecialKeyBgColor, onClick = onToggleSelect)
        ScrollableRgbButton("Copy", Modifier.width(64.dp).height(38.dp), bgColor = SpecialKeyBgColor, onClick = { onPerformAction(android.R.id.copy) })
        ScrollableRgbButton("Paste", Modifier.width(64.dp).height(38.dp), bgColor = SpecialKeyBgColor, onClick = { onPerformAction(android.R.id.paste) })
        ScrollableRgbButton("Cut", Modifier.width(64.dp).height(38.dp), bgColor = SpecialKeyBgColor, onClick = { onPerformAction(android.R.id.cut) })
        ScrollableRgbButton("⚙️ Settings", Modifier.width(100.dp).height(38.dp), bgColor = SpecialKeyBgColor, onClick = onOpenSettings)
    }
}

@Composable
fun QuickSymbolsRow(onKeyPress: (String) -> Unit) {
    val allSymbols = listOf(".", ",", "+", "-", "*", "/", "=", "(", ")", "[", "]", "{", "}", "<", ">", "\"", "'", ":", ";", "!", "?", "@", "#", "$", "%", "&", "_", "|", "\\", "~", "^")
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        allSymbols.forEach { sym -> ScrollableRgbButton(sym, Modifier.width(42.dp).height(38.dp), bgColor = KeyBgColor, onClick = { onKeyPress(sym) }) }
    }
}

@Composable
fun CommandRow(isSelecting: Boolean, onSpecialKey: (Int) -> Unit, onMoveCursor: (Int, Boolean) -> Unit) {
    val commands = listOf(Triple("Esc", KeyEvent.KEYCODE_ESCAPE, false), Triple("Tab", KeyEvent.KEYCODE_TAB, false), Triple("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, false), Triple("Alt", KeyEvent.KEYCODE_ALT_LEFT, false), Triple("Home", KeyEvent.KEYCODE_MOVE_HOME, true), Triple("End", KeyEvent.KEYCODE_MOVE_END, true), Triple("PgUp", KeyEvent.KEYCODE_PAGE_UP, true), Triple("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, true))
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        commands.forEach { (label, keyCode, isNav) ->
            ScrollableRgbButton(label as String, Modifier.width(64.dp).height(38.dp), bgColor = SpecialKeyBgColor, onClick = { if (isNav as Boolean) onMoveCursor(keyCode as Int, isSelecting) else onSpecialKey(keyCode as Int) })
        }
    }
}

@Composable
fun ScrollableRgbButton(text: String, modifier: Modifier = Modifier, bgColor: Color = SpecialKeyBgColor, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val charCode = text.firstOrNull()?.code ?: 0
    val neonColor = NeonColors[charCode % NeonColors.size]
    
    val animatedBgColor by animateColorAsState(targetValue = if (isPressed && bgColor != AccentColor) neonColor else bgColor, animationSpec = tween(if (isPressed) 10 else 400), label = "RGB")
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(animatedBgColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if(isPressed && bgColor != AccentColor) Color.Black else TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// --- PRO SETTINGS LAYOUT ---
@Composable
fun SettingsLayout(settings: AppSettings, onUpdate: (AppSettings) -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Joystick Speed", color = HintColor, fontSize = 14.sp)
                Text("${settings.joystickSpeed.toInt()}", color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Slider(value = settings.joystickSpeed, onValueChange = { onUpdate(settings.copy(joystickSpeed = it)) }, valueRange = 1f..15f, steps = 13, colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor, inactiveTrackColor = SpecialKeyBgColor))
        }
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Long Press Delay (ms)", color = HintColor, fontSize = 14.sp)
                Text("${settings.longPressDelay}", color = AccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Slider(value = settings.longPressDelay.toFloat(), onValueChange = { onUpdate(settings.copy(longPressDelay = it.toLong())) }, valueRange = 100f..500f, steps = 7, colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor, inactiveTrackColor = SpecialKeyBgColor))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Backspace Mode", color = HintColor, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf(DeleteMode.LETTER to "Letter", DeleteMode.WORD to "Word", DeleteMode.LINE to "Line")
                modes.forEach { (mode, label) ->
                    ScrollableRgbButton(text = label, modifier = Modifier.weight(1f).fillMaxHeight(), bgColor = if (settings.deleteMode == mode) AccentColor else SpecialKeyBgColor, onClick = { onUpdate(settings.copy(deleteMode = mode)) })
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        ScrollableRgbButton("Done", modifier = Modifier.fillMaxWidth().height(46.dp), bgColor = AccentColor, onClick = onClose)
    }
}

// --- MAIN KEYBOARD LAYOUTS ---
@Composable
fun QwertyLayout(
    shiftState: ShiftState, isSelecting: Boolean, settings: AppSettings, onToggleShift: () -> Unit, onToggleSelect: () -> Unit,
    onKeyPress: (String) -> Unit, onSpecialKey: (Int) -> Unit, onDelete: (DeleteMode) -> Unit, onModeChange: (KeyboardMode) -> Unit, onSwitchKeyboard: () -> Unit, onMoveCursor: (Int, Boolean) -> Unit
) {
    val row1 = listOf(KeyData("q","1"), KeyData("w","2"), KeyData("e","3"), KeyData("r","4"), KeyData("t","5"), KeyData("y","6"), KeyData("u","7"), KeyData("i","8"), KeyData("o","9"), KeyData("p","0"))
    val row2 = listOf(KeyData("a","@"), KeyData("s","#"), KeyData("d","$"), KeyData("f","%"), KeyData("g","&"), KeyData("h","-"), KeyData("j","+"), KeyData("k","("), KeyData("l",")"))
    val row3 = listOf(KeyData("z","*"), KeyData("x","\""), KeyData("c","'"), KeyData("v",":"), KeyData("b",";"), KeyData("n","!"), KeyData("m","?"))

    val isShiftActive = shiftState != ShiftState.LOWER
    val shiftIcon = when (shiftState) {
        ShiftState.LOWER -> "⇧"
        ShiftState.SHIFTED -> "⬆"
        ShiftState.CAPS_LOCK -> "⇪"
    }
    val shiftBgColor = if (shiftState == ShiftState.CAPS_LOCK) AccentColor else SpecialKeyBgColor

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { 
            // Note: We now pass the RAW lowercase keyData.primary to onKeyPress. The engine handles the uppercase conversion!
            row1.forEach { RgbKey(if (isShiftActive) it.primary.uppercase() else it.primary.lowercase(), Modifier.weight(1f).height(46.dp), secondaryText = it.secondary, onClick = { onKeyPress(it.primary) }, onLongClick = { if (it.secondary.isNotEmpty()) onKeyPress(it.secondary) }) } 
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { 
            Spacer(modifier = Modifier.weight(0.5f))
            row2.forEach { RgbKey(if (isShiftActive) it.primary.uppercase() else it.primary.lowercase(), Modifier.weight(1f).height(46.dp), secondaryText = it.secondary, onClick = { onKeyPress(it.primary) }, onLongClick = { if (it.secondary.isNotEmpty()) onKeyPress(it.secondary) }) } 
            Spacer(modifier = Modifier.weight(0.5f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RgbKey(shiftIcon, Modifier.weight(1.5f).height(46.dp), bgColor = shiftBgColor, isActionKey = true, onClick = onToggleShift)
            row3.forEach { RgbKey(if (isShiftActive) it.primary.uppercase() else it.primary.lowercase(), Modifier.weight(1f).height(46.dp), secondaryText = it.secondary, onClick = { onKeyPress(it.primary) }, onLongClick = { if (it.secondary.isNotEmpty()) onKeyPress(it.secondary) }) }
            DeleteKey(Modifier.weight(1.5f).height(46.dp), settings, onDelete)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RgbKey("?123", Modifier.weight(1.5f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onModeChange(KeyboardMode.SYMBOLS) })
            RgbKey("🌐", Modifier.weight(1f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = onSwitchKeyboard)
            RgbKey("Space", Modifier.weight(4f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onKeyPress(" ") })
            JoystickKey(Modifier.weight(1.5f).height(46.dp), isSelecting, settings.joystickSpeed, onToggleSelect, onMoveCursor)
            RgbKey("↩", Modifier.weight(2f).height(46.dp), bgColor = AccentColor, isActionKey = true, onClick = { onSpecialKey(KeyEvent.KEYCODE_ENTER) })
        }
    }
}

@Composable
fun SymbolsLayout(onKeyPress: (String) -> Unit, onSpecialKey: (Int) -> Unit, onDelete: (DeleteMode) -> Unit, settings: AppSettings, onModeChange: (KeyboardMode) -> Unit) {
    val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "=")
    val row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { row1.forEach { RgbKey(it, Modifier.weight(1f).height(46.dp), onClick = { onKeyPress(it) }) } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { row2.forEach { RgbKey(it, Modifier.weight(1f).height(46.dp), onClick = { onKeyPress(it) }) } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RgbKey("ABC", Modifier.weight(1.5f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onModeChange(KeyboardMode.QWERTY) })
            row3.forEach { RgbKey(it, Modifier.weight(1f).height(46.dp), onClick = { onKeyPress(it) }) }
            DeleteKey(Modifier.weight(1.5f).height(46.dp), settings, onDelete)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RgbKey("ABC", Modifier.weight(1.5f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onModeChange(KeyboardMode.QWERTY) })
            RgbKey(",", Modifier.weight(1f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onKeyPress(",") })
            RgbKey("Space", Modifier.weight(4f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onKeyPress(" ") })
            RgbKey(".", Modifier.weight(1.5f).height(46.dp), bgColor = SpecialKeyBgColor, isActionKey = true, onClick = { onKeyPress(".") })
            RgbKey("↩", Modifier.weight(2f).height(46.dp), bgColor = AccentColor, isActionKey = true, onClick = { onSpecialKey(KeyEvent.KEYCODE_ENTER) })
        }
    }
}
