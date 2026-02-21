package com.code.key

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// --- STYLING CONSTANTS ---
val BgColor = Color(0xFF0A0A0C)
val KeyBgColor = Color(0xFF1E1E20)
val SpecialKeyBgColor = Color(0xFF2C2C2E)
val AccentColor = Color(0xFF0A84FF)
val TextColor = Color.White
val HintColor = Color(0xFF8E8E93)
val NeonColors = listOf(Color(0xFF00F0FF), Color(0xFFFF007F), Color(0xFF39FF14), Color(0xFFFFEA00), Color(0xFFB400FF))

data class KeyData(val primary: String, val secondary: String = "")

// --- UNIVERSAL RGB BUTTON ---
@Composable
fun RgbKey(
    text: String,
    modifier: Modifier = Modifier,
    secondaryText: String = "",
    bgColor: Color = KeyBgColor,
    isActionKey: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val charCode = text.firstOrNull()?.code ?: 0
    val neonColor = NeonColors[charCode % NeonColors.size]
    
    val animatedBgColor by animateColorAsState(
        targetValue = if (isPressed && bgColor != AccentColor) neonColor else bgColor,
        animationSpec = tween(durationMillis = if (isPressed) 10 else 400), label = "RGB"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    
                    if (up != null) { // If not cancelled
                        // Basic long press detection (no native combinedClickable to avoid bugs)
                        if (onLongClick != null && up.uptimeMillis - down.uptimeMillis > 300) {
                            onLongClick()
                        } else {
                            onClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (secondaryText.isNotEmpty()) {
            Text(secondaryText, color = if(isPressed) Color.Black else HintColor, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
        }
        Text(
            text = text, 
            color = if(isPressed && bgColor != AccentColor) Color.Black else TextColor, 
            fontSize = if (isActionKey) 15.sp else 21.sp, 
            fontWeight = FontWeight.Medium
        )
    }
}

// --- ROCK SOLID DELETE KEY ---
@Composable
fun DeleteKey(modifier: Modifier = Modifier, settings: AppSettings, onDelete: (DeleteMode) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val animatedBgColor by animateColorAsState(targetValue = if (isPressed) NeonColors[1] else SpecialKeyBgColor, animationSpec = tween(if (isPressed) 10 else 400), label = "DelRGB")

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onDelete(settings.deleteMode) // Fire once immediately
            delay(settings.longPressDelay) // Wait for hold threshold
            while (isPressed) {
                onDelete(settings.deleteMode)
                delay(if (settings.deleteMode == DeleteMode.LETTER) 50L else 150L) // Rapid fire
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text("⌫", color = if(isPressed) Color.Black else TextColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

// --- JOYSTICK ---
@Composable
fun JoystickKey(modifier: Modifier = Modifier, isSelecting: Boolean, sensitivityLevel: Float, onToggleSelect: () -> Unit, onMoveCursor: (Int, Boolean) -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    var lastReleaseTime by remember { mutableLongStateOf(0L) }
    val maxRadius = 38f
    val triggerThreshold = 15f
    val cursorDelay = (160L - (sensitivityLevel * 8L).toLong()).coerceAtLeast(30L)


    LaunchedEffect(isPressed) {
        while (isPressed) {
            if (offsetX > triggerThreshold) onMoveCursor(KeyEvent.KEYCODE_DPAD_RIGHT, isSelecting)
            if (offsetX < -triggerThreshold) onMoveCursor(KeyEvent.KEYCODE_DPAD_LEFT, isSelecting)
            if (offsetY > triggerThreshold) onMoveCursor(KeyEvent.KEYCODE_DPAD_DOWN, isSelecting)
            if (offsetY < -triggerThreshold) onMoveCursor(KeyEvent.KEYCODE_DPAD_UP, isSelecting)
            delay(cursorDelay) 
        }
    }

    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(SpecialKeyBgColor),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF18181A)))
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(22.dp).clip(CircleShape).background(if (isSelecting) AccentColor else Color(0xFF666666))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isPressed = true; if (System.currentTimeMillis() - lastReleaseTime < 300) onToggleSelect() },
                        onDragEnd = { isPressed = false; offsetX = 0f; offsetY = 0f; lastReleaseTime = System.currentTimeMillis() },
                        onDragCancel = { isPressed = false; offsetX = 0f; offsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dist = kotlin.math.sqrt(((offsetX + dragAmount.x) * (offsetX + dragAmount.x)) + ((offsetY + dragAmount.y) * (offsetY + dragAmount.y)))
                            if (dist <= maxRadius) { offsetX += dragAmount.x; offsetY += dragAmount.y } 
                            else { offsetX = ((offsetX + dragAmount.x) / dist) * maxRadius; offsetY = ((offsetY + dragAmount.y) / dist) * maxRadius }
                        }
                    )
                }
        )
    }
}
