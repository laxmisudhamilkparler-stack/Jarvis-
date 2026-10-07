package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvesScreen(
    viewModel: JarvesViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assistantState by viewModel.assistantState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val latestReport by viewModel.latestReport.collectAsState()
    val disambiguationCandidates by viewModel.disambiguationCandidates.collectAsState()
    val liveSpokenText by viewModel.liveSpokenText.collectAsState()
    val amplitude by viewModel.audioPlayer.amplitude.collectAsState()

    val micGranted by viewModel.micPermissionGranted.collectAsState()
    val contactsGranted by viewModel.contactsPermissionGranted.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom of messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (assistantState) {
                                            AssistantState.IDLE -> Color(0xFF10B981)
                                            AssistantState.LISTENING -> Color(0xFF06B6D4)
                                            AssistantState.THINKING -> Color(0xFFF59E0B)
                                            AssistantState.SPEAKING -> Color(0xFFEC4899)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Jarves AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.testTag("clear_history_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History")
                        }
                    }
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Voice Assistant") },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Web Bridge Inspector") },
                        icon = { Icon(Icons.Default.Web, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (selectedTab == 1) {
            WebBridgeScreen(
                actionBridge = viewModel.actionBridge,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Permissions warning banner if needed
                PermissionsBanner(
                    micGranted = micGranted,
                    contactsGranted = contactsGranted,
                    onRequestPermissions = onRequestPermissions
                )

                // Top visualizer section with glowing Orb
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbVisualizer(
                        state = assistantState,
                        amplitude = amplitude,
                        onClick = {
                            if (assistantState == AssistantState.SPEAKING) {
                                viewModel.interruptSpeaking()
                            } else if (assistantState == AssistantState.LISTENING) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        }
                    )
                }

                // Live partial speech recognition indicator
                AnimatedVisibility(visible = liveSpokenText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = liveSpokenText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Quick action test chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val promptChips = listOf(
                        "WhatsApp kholo",
                        "Open WhatsApp",
                        "Mummy ko call karo",
                        "Call Rahul",
                        "Call 9876543210",
                        "Hindi mein baat karo",
                        "Talk to me in English",
                        "Hinglish mein baat karo",
                        "Open YouTube",
                        "Open Instagram",
                        "Open Settings",
                        "Open FakeApp"
                    )
                    promptChips.forEach { chipText ->
                        AssistChip(
                            onClick = { viewModel.executeManualAction(chipText) },
                            label = { Text(chipText, fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // Latest Action Report (Tool execution card)
                latestReport?.let { report ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ToolReportCard(
                            report = report,
                            onOpenUrl = { url -> viewModel.actionBridge.openUrl(url) }
                        )
                    }
                }

                // Conversation transcript
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg = msg)
                    }
                }

                // Bottom Input & Mic control bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Speak or type command...", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("command_text_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (textInput.isNotBlank()) {
                                    viewModel.executeManualAction(textInput)
                                    textInput = ""
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.executeManualAction(textInput)
                                    textInput = ""
                                },
                                modifier = Modifier.testTag("send_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            FloatingActionButton(
                                onClick = {
                                    if (assistantState == AssistantState.SPEAKING) {
                                        viewModel.interruptSpeaking()
                                    } else if (assistantState == AssistantState.LISTENING) {
                                        viewModel.stopListening()
                                    } else {
                                        viewModel.startListening()
                                    }
                                },
                                containerColor = when (assistantState) {
                                    AssistantState.LISTENING -> Color(0xFFEF4444)
                                    AssistantState.SPEAKING -> Color(0xFFEC4899)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mic_fab")
                            ) {
                                Icon(
                                    imageVector = if (assistantState == AssistantState.SPEAKING) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Microphone",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Disambiguation dialog for multiple contacts
            disambiguationCandidates?.let { candidates ->
                DisambiguationDialog(
                    candidates = candidates,
                    onSelect = { selectedContact ->
                        viewModel.selectContactCandidate(selectedContact)
                    },
                    onDismiss = {
                        viewModel.dismissDisambiguation()
                    }
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp
            ),
            color = if (msg.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .widthIn(max = 320.dp)
                .testTag(if (msg.isUser) "user_message_bubble" else "jarves_message_bubble")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (msg.languageBadge != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (msg.isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = msg.languageBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (msg.isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = msg.text,
                    fontSize = 14.sp,
                    color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
