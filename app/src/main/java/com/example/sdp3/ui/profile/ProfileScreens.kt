package com.example.sdp3.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.sdp3.AdminProfile
import com.example.sdp3.UserProfile
import com.example.sdp3.database.AppDatabase
import com.example.sdp3.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AdminProfilePage(
    adminProfile: AdminProfile,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val dataStore = context.dataStore
    val themePrefFlow = remember { dataStore.data.map { it[stringPreferencesKey("theme_preference")] ?: "System" } }
    val themePreference by themePrefFlow.collectAsState(initial = "System")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = adminProfile.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Admin Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                ProfileInfoRow("Admin ID", "ADM-${adminProfile.adminId}")
                ProfileInfoRow("Name", adminProfile.name)
                ProfileInfoRow("Phone", adminProfile.phone)
                ProfileInfoRow("Address", adminProfile.adminAddress)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.titleSmall)
        }

        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                currentTheme = themePreference,
                onThemeChange = { newTheme -> scope.launch { dataStore.edit { it[stringPreferencesKey("theme_preference")] = newTheme } } }
            )
        }
    }
}

@Composable
fun ProfilePage(
    userProfile: UserProfile,
    database: AppDatabase,
    onUpdateProfile: (String, Int, String, String, String, String) -> Unit,
    onSignOut: () -> Unit,
    totalDistance: Double,
    totalTime: Int,
    totalTrips: Int,
    totalCancelled: Int
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dataStore = context.dataStore
    val themePrefFlow = remember { dataStore.data.map { it[stringPreferencesKey("theme_preference")] ?: "System" } }
    val themePreference by themePrefFlow.collectAsState(initial = "System")

    if (showEditDialog) {
        var editName by remember { mutableStateOf(userProfile.name) }
        var editAge by remember { mutableStateOf(userProfile.age.toString()) }
        var editHometown by remember { mutableStateOf(userProfile.hometown) }
        var editUpazila by remember { mutableStateOf(userProfile.upazila) }
        var editDetailedAddress by remember { mutableStateOf(userProfile.detailedAddress) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false }, 
            title = { Text("Edit Profile") }, 
            text = { 
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = editAge, onValueChange = { editAge = it }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = editHometown, onValueChange = { editHometown = it }, label = { Text("Hometown") }, singleLine = true)
                    OutlinedTextField(value = editUpazila, onValueChange = { editUpazila = it }, label = { Text("Upazila") }, singleLine = true)
                    OutlinedTextField(value = editDetailedAddress, onValueChange = { editDetailedAddress = it }, label = { Text("Detailed Address") }, singleLine = false, maxLines = 3)
                    OutlinedTextField(value = userProfile.phone, onValueChange = { }, label = { Text("Phone (Locked)") }, enabled = false, readOnly = true, singleLine = true) 
                } 
            }, 
            confirmButton = { 
                Button(
                    onClick = { 
                        onUpdateProfile(editName, editAge.toIntOrNull() ?: 0, userProfile.phone, editHometown, editUpazila, editDetailedAddress)
                        showEditDialog = false 
                    }, 
                    enabled = editName.isNotBlank() && editAge.isNotBlank() && editHometown.isNotBlank() && editUpazila.isNotBlank() && editDetailedAddress.isNotBlank()
                ) { Text("Save") } 
            }, 
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showTopUpDialog) {
        var transactionId by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showTopUpDialog = false },
            title = { Text("Request Balance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pay to our MFS number (017XXXXXXXX) and enter details below:")
                    OutlinedTextField(
                        value = transactionId,
                        onValueChange = { transactionId = it },
                        label = { Text("Transaction ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amountText = it },
                        label = { Text("Amount Paid (BDT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (transactionId.isNotBlank() && amount > 0) {
                            scope.launch {
                                database.topUpDao().insertRequest(
                                    com.example.sdp3.database.TopUpRequest(
                                        id = "TOP${System.currentTimeMillis()}",
                                        userPhone = userProfile.phone,
                                        userName = userProfile.name,
                                        transactionId = transactionId,
                                        amount = amount,
                                        date = java.text.SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(java.util.Date())
                                    )
                                )
                                showTopUpDialog = false
                                android.widget.Toast.makeText(context, "Balance request sent to admin", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = transactionId.isNotBlank() && amountText.isNotBlank()
                ) { Text("Submit Request") }
            },
            dismissButton = {
                TextButton(onClick = { showTopUpDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = userProfile.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        ElevatedCard(
            modifier = Modifier.padding(horizontal = 32.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = String.format(Locale.US, "%.2f BDT", userProfile.balance),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (userProfile.pendingFine > 0) {
                    Text(
                        text = String.format(Locale.US, "Debt/Fine: %.2f BDT", userProfile.pendingFine),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "Required to Clear: %.2f BDT", userProfile.pendingFine),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showTopUpDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Balance Request", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Ride Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))

        val totalAttempts = totalTrips + totalCancelled
        val isThresholdReached = totalAttempts >= 5
        val successRate = if (isThresholdReached) (totalTrips.toDouble() / totalAttempts) * 100.0 else 0.0
        val successColor = when {
            !isThresholdReached -> MaterialTheme.colorScheme.outline
            successRate >= 80 -> Color(0xFF4CAF50)
            successRate >= 50 -> Color(0xFFFFB300)
            else -> Color(0xFFF44336)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
                ProfileStatCard(
                    label = "Distance", 
                    value = String.format(Locale.US, "%.1f", totalDistance), 
                    unit = "km", 
                    icon = Icons.AutoMirrored.Filled.DirectionsBike,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatCard(
                    label = "Time", 
                    value = totalTime.toString(), 
                    unit = "min", 
                    icon = Icons.Default.AccessTime,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
                ProfileStatCard(
                    label = "Trips", 
                    value = totalTrips.toString(), 
                    unit = "", 
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f)
                )
                ProfileStatCard(
                    label = "Success", 
                    value = if (isThresholdReached) String.format(Locale.US, "%.0f%%", successRate) else "Need ${5 - totalAttempts}",
                    unit = if (isThresholdReached) "" else "more",
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f),
                    color = successColor
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(24.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(text = "Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(20.dp)) } }; Spacer(modifier = Modifier.height(8.dp)); ProfileInfoRow("Name", userProfile.name); ProfileInfoRow("Age", userProfile.age.toString()); ProfileInfoRow("Hometown", userProfile.hometown); ProfileInfoRow("Upazila", userProfile.upazila); ProfileInfoRow("Detailed Address", userProfile.detailedAddress); ProfileInfoRow("Phone", userProfile.phone) } }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.titleSmall)
        }

        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                currentTheme = themePreference,
                onThemeChange = { newTheme -> scope.launch { dataStore.edit { it[stringPreferencesKey("theme_preference")] = newTheme } } }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onThemeChange("System") }) {
                    RadioButton(selected = currentTheme == "System", onClick = { onThemeChange("System") })
                    Text("System Default")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onThemeChange("Light") }) {
                    RadioButton(selected = currentTheme == "Light", onClick = { onThemeChange("Light") })
                    Text("Light")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onThemeChange("Dark") }) {
                    RadioButton(selected = currentTheme == "Dark", onClick = { onThemeChange("Dark") })
                    Text("Dark")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("App Information", style = MaterialTheme.typography.titleMedium)
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionName = packageInfo.versionName ?: "Unknown"
                Text("Version: $versionName", style = MaterialTheme.typography.bodyMedium)
                Text("Database Version: 22", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ProfileStatCard(
    label: String, 
    value: String, 
    unit: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = color
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " ($unit)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) { Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline); Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
}
