package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.ActionResult
import com.example.bridge.ContactMatch
import com.example.bridge.ToolExecutionReport

@Composable
fun OrbVisualizer(
    state: AssistantState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")

    // Ambient breathing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Rotation for thinking state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val currentAmplitude = remember { Animatable(0f) }
    LaunchedEffect(amplitude) {
        currentAmplitude.animateTo(amplitude, tween(80))
    }

    val dynamicScale = when (state) {
        AssistantState.IDLE -> pulseScale
        AssistantState.LISTENING -> pulseScale * 1.15f
        AssistantState.THINKING -> 1.0f
        AssistantState.SPEAKING -> 1.0f + (currentAmplitude.value * 0.45f)
    }

    val baseGradient = when (state) {
        AssistantState.IDLE -> listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF06B6D4))
        AssistantState.LISTENING -> listOf(Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFF3B82F6))
        AssistantState.THINKING -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))
        AssistantState.SPEAKING -> listOf(Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF3B82F6))
    }

    Box(
        modifier = modifier
            .size(190.dp)
            .testTag("orb_visualizer")
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp * dynamicScale)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // Outer glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseGradient[0].copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Mid core gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        baseGradient[1].copy(alpha = 0.7f),
                        baseGradient[2].copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 0.85f
                ),
                radius = radius * 0.85f,
                center = center
            )

            // Inner solid luminous core
            drawCircle(
                brush = Brush.linearGradient(
                    colors = baseGradient,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                ),
                radius = radius * 0.55f,
                center = center
            )
        }

        // Center status icon or label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (state) {
                    AssistantState.SPEAKING -> Icons.Default.Stop
                    AssistantState.LISTENING -> Icons.Default.Mic
                    AssistantState.THINKING -> Icons.Default.CheckCircle
                    AssistantState.IDLE -> Icons.Default.Mic
                },
                contentDescription = state.name,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (state) {
                    AssistantState.IDLE -> "Tap to Speak"
                    AssistantState.LISTENING -> "Listening..."
                    AssistantState.THINKING -> "Thinking..."
                    AssistantState.SPEAKING -> "Speaking"
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ToolReportCard(
    report: ToolExecutionReport,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tool_report_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (report.result) {
                is ActionResult.Success -> Color(0xFF0F291E)
                is ActionResult.Failure -> Color(0xFF2D1515)
                is ActionResult.DisambiguationNeeded -> Color(0xFF28200F)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (report.result) {
                        is ActionResult.Success -> Icons.Default.CheckCircle
                        is ActionResult.Failure -> Icons.Default.Error
                        is ActionResult.DisambiguationNeeded -> Icons.Default.Person
                    },
                    contentDescription = null,
                    tint = when (report.result) {
                        is ActionResult.Success -> Color(0xFF34D399)
                        is ActionResult.Failure -> Color(0xFFF87171)
                        is ActionResult.DisambiguationNeeded -> Color(0xFFFBBF24)
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (report.result) {
                        is ActionResult.Success -> "Action Executed: ${report.toolName}"
                        is ActionResult.Failure -> "Action Notice: ${report.toolName}"
                        is ActionResult.DisambiguationNeeded -> "Contact Clarification"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val res = report.result) {
                is ActionResult.Success -> {
                    Text(
                        text = res.message,
                        color = Color(0xFFD1FAE5),
                        fontSize = 13.sp
                    )
                }
                is ActionResult.Failure -> {
                    Text(
                        text = res.message,
                        color = Color(0xFFFEE2E2),
                        fontSize = 13.sp
                    )

                    if (res.suggestedAlternatives.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Suggested Alternatives:",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        res.suggestedAlternatives.forEach { alt ->
                            Text(
                                text = "• $alt",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }

                    // Action buttons for web fallback and play store
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (res.webFallbackUrl != null) {
                            Button(
                                onClick = { onOpenUrl(res.webFallbackUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("action_web_fallback_btn")
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open on Web", fontSize = 12.sp)
                            }
                        }
                        if (res.playStorePackage != null) {
                            OutlinedButton(
                                onClick = { onOpenUrl("market://details?id=${res.playStorePackage}") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("action_playstore_btn")
                            ) {
                                Icon(Icons.Default.Shop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play Store", fontSize = 12.sp)
                            }
                        }
                    }
                }
                is ActionResult.DisambiguationNeeded -> {
                    Text(
                        text = res.message,
                        color = Color(0xFFFEF3C7),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DisambiguationDialog(
    candidates: List<ContactMatch>,
    onSelect: (ContactMatch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Multiple Contacts Found",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Which contact would you like to call?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                candidates.forEach { contact ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(contact) }
                            .testTag("candidate_${contact.id}"),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${contact.typeLabel}: ${contact.phoneNumber}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onSelect(contact) }) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call ${contact.name}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionsBanner(
    micGranted: Boolean,
    contactsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!micGranted || !contactsGranted) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("permissions_banner"),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permissions Recommended",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = buildString {
                            if (!micGranted) append("Microphone ")
                            if (!micGranted && !contactsGranted) append("& ")
                            if (!contactsGranted) append("Contacts ")
                            append("needed for voice actions")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Button(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("grant_permissions_btn")
                ) {
                    Text("Grant", fontSize = 12.sp)
                }
            }
        }
    }
}
