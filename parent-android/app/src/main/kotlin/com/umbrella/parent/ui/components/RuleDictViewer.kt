package com.umbrella.parent.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umbrella.parent.data.models.RuleDict
import com.umbrella.parent.ui.theme.*

@Composable
fun RuleDictViewer(
    ruleDict: RuleDict,
    rawJson: String?,
    modifier: Modifier = Modifier,
    onApplyRules: ((RuleDict) -> Unit)? = null,
    isApplying: Boolean = false,
    isAlreadyApplied: Boolean = false
) {
    var isJsonExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: AI Badge & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(GemmaPurple, IndigoPrimary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemma 4 AI",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Gemma 4 RuleDict",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GemmaPurpleContainer
                ) {
                    Text(
                        text = "Structured SLM",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnGemmaPurpleContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Human Explanation Banner
            if (ruleDict.explanation.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = "Explanation",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = ruleDict.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Blocked Categories Section
            if (ruleDict.blockedCategories.isNotEmpty()) {
                Text(
                    text = "Blocked Categories",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OptInFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ruleDict.blockedCategories.forEach { category ->
                        CategoryChip(category = category)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Blocked Keywords Section
            if (ruleDict.blockedKeywords.isNotEmpty()) {
                Text(
                    text = "Monitored Keywords & Triggers",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OptInFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ruleDict.blockedKeywords.forEach { keyword ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "#$keyword",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // App Rules Section
            if (ruleDict.appRules.isNotEmpty()) {
                Text(
                    text = "App Enforcements",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ruleDict.appRules.forEach { appRule ->
                        AppRuleRow(
                            appName = appRule.appName,
                            packageName = appRule.packageName,
                            isBlocked = appRule.isBlocked,
                            dailyLimit = appRule.dailyLimitMinutes
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Time Schedules & SafeSearch Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ruleDict.bedtimeStart != null && ruleDict.bedtimeEnd != null) {
                    ScheduleBadge(
                        icon = Icons.Default.Bedtime,
                        title = "Bedtime Lock",
                        timeSpan = "${ruleDict.bedtimeStart} - ${ruleDict.bedtimeEnd}",
                        containerColor = IndigoContainer,
                        contentColor = OnIndigoContainer
                    )
                }

                if (ruleDict.schoolHoursStart != null && ruleDict.schoolHoursEnd != null) {
                    ScheduleBadge(
                        icon = Icons.Default.School,
                        title = "School Hours",
                        timeSpan = "${ruleDict.schoolHoursStart} - ${ruleDict.schoolHoursEnd}",
                        containerColor = TealContainer,
                        contentColor = OnTealContainer
                    )
                }
            }

            if (ruleDict.safeSearchForced) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "SafeSearch",
                        tint = SeveritySafe,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Local DNS VPN SafeSearch Forced ON",
                        style = MaterialTheme.typography.labelMedium,
                        color = SeveritySafe,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Expandable Raw JSON Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { isJsonExpanded = !isJsonExpanded },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isJsonExpanded) Icons.Default.ExpandLess else Icons.Default.Code,
                        contentDescription = "Toggle JSON",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isJsonExpanded) "Hide Raw JSON" else "Inspect RuleDict JSON",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (isJsonExpanded && !rawJson.isNullOrEmpty()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(rawJson))
                            Toast.makeText(context, "RuleDict JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy JSON",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isJsonExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = rawJson ?: "{}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Primary Apply Button
            if (onApplyRules != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onApplyRules(ruleDict) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isApplying && !isAlreadyApplied,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlreadyApplied) SeveritySafe else IndigoPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pushing to Kid Device...")
                    } else if (isAlreadyApplied) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Enforced",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rules Active & Enforced")
                    } else {
                        Icon(
                            imageVector = Icons.Default.SendToMobile,
                            contentDescription = "Apply",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm & Sync Rules via FCM")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: String) {
    val (bgColor, textColor, icon) = when (category.uppercase()) {
        "GAMING" -> Triple(SeverityWarningContainer, OnSeverityWarning, Icons.Default.SportsEsports)
        "SOCIAL_MEDIA" -> Triple(IndigoContainer, OnIndigoContainer, Icons.Default.Chat)
        "ADULT", "VIOLENCE" -> Triple(SeverityBlockedContainer, OnSeverityBlocked, Icons.Default.Block)
        "STREAMING" -> Triple(TealContainer, OnTealContainer, Icons.Default.SmartDisplay)
        else -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Icons.Default.Shield)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = category.replace("_", " "),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AppRuleRow(
    appName: String,
    packageName: String,
    isBlocked: Boolean,
    dailyLimit: Int?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            if (isBlocked) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SeverityBlockedContainer
                ) {
                    Text(
                        text = "BLOCKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSeverityBlocked,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else if (dailyLimit != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = TealContainer
                ) {
                    Text(
                        text = "$dailyLimit min/day",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnTealContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleBadge(
    icon: ImageVector,
    title: String,
    timeSpan: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timeSpan,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptInFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

