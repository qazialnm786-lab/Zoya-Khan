package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonGreen
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaSurfaceDark
import com.example.ui.theme.ZoyaSurfaceElevated
import com.example.ui.theme.ZoyaTextDim
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary

data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isRequired: Boolean = true
)

@Composable
fun PermissionsScreen(
    onAllPermissionsHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissionsList = remember {
        buildList {
            add(
                PermissionItem(
                    Manifest.permission.RECORD_AUDIO,
                    "Microphone Access",
                    "For zero-touch voice conversation and background 'Zoya' wake-word activation.",
                    Icons.Default.Mic
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        Manifest.permission.POST_NOTIFICATIONS,
                        "Persistent Notifications",
                        "Keeps Zoya alive in the background without being killed by Android battery optimizations.",
                        Icons.Default.Notifications
                    )
                )
            }
            add(
                PermissionItem(
                    Manifest.permission.READ_CONTACTS,
                    "Contacts Access",
                    "Allows Zoya to find friends & family when you say 'Call Alex' or 'WhatsApp Mom'.",
                    Icons.Default.Contacts
                )
            )
            add(
                PermissionItem(
                    Manifest.permission.CALL_PHONE,
                    "Direct Phone Calls",
                    "Enables Zoya to place hands-free phone calls immediately when you ask.",
                    Icons.Default.Call
                )
            )
        }
    }

    var permissionStates by remember {
        mutableStateOf(
            permissionsList.associate {
                it.permission to (ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED)
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStates = results
        val hasMic = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (hasMic) {
            onAllPermissionsHandled()
        }
    }

    val allGranted = permissionStates.values.all { it }
    val micGranted = permissionStates[Manifest.permission.RECORD_AUDIO] == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ZoyaObsidian, Color(0xFF110F24), ZoyaObsidian)
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))

                // Futuristic Shield Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(ZoyaNeonPink.copy(alpha = 0.35f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                        .border(1.5.dp, ZoyaNeonPink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Security & Permissions",
                        tint = ZoyaNeonCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Welcome to Zoya",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZoyaTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Give Zoya the superpowers she needs to listen in the background, make calls, and execute native commands with her signature sass.",
                    fontSize = 14.sp,
                    color = ZoyaTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Permissions Cards
                permissionsList.forEach { item ->
                    val isGranted = permissionStates[item.permission] == true
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGranted) ZoyaSurfaceElevated else ZoyaSurfaceDark
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isGranted) ZoyaNeonGreen.copy(alpha = 0.5f) else Color(0x33FFFFFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isGranted) ZoyaNeonGreen.copy(alpha = 0.15f) else ZoyaNeonPurple.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (isGranted) ZoyaNeonGreen else ZoyaNeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = ZoyaTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    fontSize = 12.sp,
                                    color = ZoyaTextDim,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isGranted) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(ZoyaNeonGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Granted",
                                        tint = ZoyaObsidian,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Required",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ZoyaNeonPink
                                )
                            }
                        }
                    }
                }
            }

            // Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val toRequest = permissionsList.map { it.permission }.toTypedArray()
                        launcher.launch(toRequest)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("grant_permissions_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZoyaNeonPink,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (allGranted) "Superpowers Activated" else "Grant All Superpowers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (micGranted || allGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onAllPermissionsHandled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("continue_to_zoya_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZoyaNeonCyan)
                    ) {
                        Text(
                            text = "Meet Zoya",
                            color = ZoyaNeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
