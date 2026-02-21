// Feature branch test change
package com.code.key

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

// Card color specifically for the Dashboard
val CardBgColor = Color(0xFF1E1E20)

// The 10 Premium Background Themes (Shared with KeyboardUI)
val PremiumPalettes = listOf(
    Color(0xFF0A0A0C), // OLED Black
    Color(0xFF1E1E20), // Studio Gray
    Color(0xFF0F172A), // Midnight Blue
    Color(0xFF1E112A), // Deep Amethyst
    Color(0xFF0D1F15), // Matrix Green
    Color(0xFF2A0A0A), // Crimson Dark
    Color(0xFF001C27), // Ocean Abyss
    Color(0xFF1F130B), // Mocha Dark
    Color(0xFF19233C), // Slate Blue
    Color(0xFF111827)  // Carbon
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize(), containerColor = BgColor) { innerPadding ->
                CodeKeyDashboard(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun CodeKeyDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("CodeKeyPrefs", Context.MODE_PRIVATE)

    var selectedHeight by remember { mutableStateOf(prefs.getString("kb_height", "Medium") ?: "Medium") }
    var selectedColorIndex by remember { mutableIntStateOf(prefs.getInt("kb_color_idx", 0)) }
    var customImagePath by remember { mutableStateOf(prefs.getString("kb_image_path", null)) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = File(context.filesDir, "kb_bg.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                prefs.edit().putString("kb_image_path", file.absolutePath).apply()
                customImagePath = file.absolutePath
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(AccentColor), contentAlignment = Alignment.Center) {
                Text("{ }", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("CodeKey", color = TextColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Premium Developer Keyboard", color = HintColor, fontSize = 14.sp)
        }

        DashboardCard(title = "System Setup") {
            DashboardButton("1. Enable Keyboard in Settings", CardBgColor) { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            Spacer(modifier = Modifier.height(12.dp))
            DashboardButton("2. Select CodeKey", AccentColor) { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        }

        DashboardCard(title = "Appearance & Layout") {
            Text("Keyboard Height", color = HintColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small", "Medium", "Big").forEach { heightLabel ->
                    val isSelected = selectedHeight == heightLabel
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (isSelected) AccentColor else CardBgColor).clickable {
                            selectedHeight = heightLabel; prefs.edit().putString("kb_height", heightLabel).apply()
                        }, contentAlignment = Alignment.Center
                    ) { Text(heightLabel, color = if (isSelected) Color.Black else TextColor, fontWeight = FontWeight.Medium) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text("Background Theme", color = HintColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PremiumPalettes.forEachIndexed { index, color ->
                    val isSelected = selectedColorIndex == index
                    val animatedBorderColor by animateColorAsState(targetValue = if (isSelected) AccentColor else Color.Transparent, label = "border")
                    Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(color).border(3.dp, animatedBorderColor, CircleShape).clickable {
                        selectedColorIndex = index; customImagePath = null
                        prefs.edit().putInt("kb_color_idx", index).remove("kb_image_path").apply()
                    })
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text("Custom Background Image", color = HintColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            DashboardButton(if (customImagePath != null) "Image Selected (Tap to Change)" else "Choose Image from Gallery", if (customImagePath != null) Color(0xFF39FF14).copy(alpha = 0.2f) else CardBgColor, if (customImagePath != null) Color(0xFF39FF14) else TextColor) { 
                imagePickerLauncher.launch("image/*") 
            }
            if (customImagePath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                DashboardButton("Remove Image", Color(0xFFFF007F).copy(alpha = 0.2f), Color(0xFFFF007F)) {
                    customImagePath = null; prefs.edit().remove("kb_image_path").apply()
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF141416)).padding(20.dp)) {
        Text(title, color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(20.dp))
        content()
    }
}

@Composable
fun DashboardButton(text: String, bgColor: Color, textColor: Color = TextColor, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(bgColor).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
