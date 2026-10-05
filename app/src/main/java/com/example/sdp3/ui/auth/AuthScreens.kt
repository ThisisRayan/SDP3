package com.example.sdp3.ui.auth

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sdp3.database.AppDatabase
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "CycleRent",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Eco-friendly rides for everyone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Get Started", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onGoToRegister: () -> Unit,
    database: AppDatabase
) {
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var showOtpField by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Login",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    if (it.length <= 11) {
                        phone = it
                        if (errorText == "User not found. Please register." || errorText == "Enter valid phone number") {
                            errorText = ""
                        }
                    }
                },
                label = { Text("Phone Number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !showOtpField && !isLoading
            )

            if (showOtpField) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6) otp = it },
                    label = { Text("Enter 6-digit OTP") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isLoading
                )
            }

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (!showOtpField) {
                        if (phone.length == 11) {
                            isLoading = true
                            scope.launch {
                                val user = database.userProfileDao().getUserByPhone(phone)
                                val admin = database.adminProfileDao().getAdminProfile(phone)
                                if (user != null || admin != null) {
                                    val fullPhone = if (phone.startsWith("+88")) phone else "+88$phone"
                                    val options = PhoneAuthOptions.newBuilder(auth)
                                        .setPhoneNumber(fullPhone)
                                        .setTimeout(60L, TimeUnit.SECONDS)
                                        .setActivity(context as Activity)
                                        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                                    if (task.isSuccessful) {
                                                        onLoginSuccess(phone)
                                                    } else {
                                                        errorText = "Auto-verification failed: ${task.exception?.message}"
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                            override fun onVerificationFailed(e: FirebaseException) {
                                                errorText = "Verification failed: ${e.message}"
                                                isLoading = false
                                            }
                                            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                                verificationId = id
                                                showOtpField = true
                                                isLoading = false
                                            }
                                        }).build()
                                    PhoneAuthProvider.verifyPhoneNumber(options)
                                } else {
                                    errorText = "User not found. Please register."
                                    isLoading = false
                                }
                            }
                        } else {
                            errorText = "Enter valid phone number"
                        }
                    } else {
                        isLoading = true
                        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                        auth.signInWithCredential(credential).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                onLoginSuccess(phone)
                            } else {
                                errorText = "Invalid OTP or Session Expired"
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(if (showOtpField) "Verify & Login" else "Get OTP")
                }
            }

            TextButton(onClick = onGoToRegister, enabled = !isLoading) {
                Text("Don't have an account? Register Now", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fetching your profile...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onAllPermissionsGranted: () -> Unit) {
    val permissionsToRequest = mutableListOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissionsToRequest)

    if (permissionState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            onAllPermissionsGranted()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To provide you with a seamless and secure ride-sharing experience, CycleRent needs the following permissions:",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(32.dp))

            PermissionReasonItem(
                icon = Icons.Default.LocationOn,
                title = "GPS & Location",
                description = "Required to track your ride path, find nearby cycle stations, and calculate distance for accurate billing."
            )
            Spacer(modifier = Modifier.height(16.dp))
            PermissionReasonItem(
                icon = Icons.Default.QrCodeScanner,
                title = "Camera Access",
                description = "Used to scan QR codes on cycles for quick unlocking and returning at stations."
            )
            Spacer(modifier = Modifier.height(16.dp))
            PermissionReasonItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Essential for showing your live ride timer, booking status, and critical balance alerts while the app is in background."
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { permissionState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Grant Permissions", style = MaterialTheme.typography.titleMedium)
            }
            
            TextButton(
                onClick = onAllPermissionsGranted,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("I'll do it later", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun PermissionReasonItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    modifier: Modifier = Modifier,
    initialPhone: String = "",
    onProfileSaved: (String, Int, String, String, String, String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(initialPhone) }
    var selectedDivision by remember { mutableStateOf("") }
    var selectedUpazila by remember { mutableStateOf("") }
    var detailedAddress by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var divisionExpanded by remember { mutableStateOf(false) }
    var upazilaExpanded by remember { mutableStateOf(false) }

    val bangladeshData = remember {
        mapOf(
            "Dhaka" to listOf("Dhanmondi", "Gulshan", "Mirpur", "Uttara", "Motijheel", "Savar", "Gazipur"),
            "Chottogram" to listOf("Panchlaish", "Double Mooring", "Halishahar", "Bakalia", "Patiya", "Sitakunda"),
            "Sylhet" to listOf("Sylhet Sadar", "Beanibazar", "Golapganj", "Sreemangal", "Kulaura"),
            "Rajshahi" to listOf("Boalia", "Motihar", "Rajpara", "Shah Mokhdum", "Puthia", "Bagmara"),
            "Khulna" to listOf("Khulna Sadar", "Daulatpur", "Khalishpur", "Sonadanga", "Bagerhat", "Jessore"),
            "Barishal" to listOf("Barishal Sadar", "Bakerganj", "Mehendiganj", "Bhola", "Patuakhali"),
            "Rangpur" to listOf("Rangpur Sadar", "Pirganj", "Badarganj", "Kurigram", "Gaibandha"),
            "Mymensingh" to listOf("Mymensingh Sadar", "Muktagacha", "Fulbaria", "Gaffargaon")
        )
    }
    val divisions = bangladeshData.keys.toList()
    val upazilas = bangladeshData[selectedDivision] ?: emptyList()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Welcome to CycleRent", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = selectedDivision, onValueChange = { }, readOnly = true, label = { Text("Hometown (Division)") }, leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = divisionExpanded) }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = divisionExpanded, onDismissRequest = { divisionExpanded = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                        divisions.forEach { division -> DropdownMenuItem(text = { Text(division) }, onClick = { selectedDivision = division; selectedUpazila = ""; divisionExpanded = false }) }
                    }
                    Box(modifier = Modifier.matchParentSize().clickable { divisionExpanded = true })
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = selectedUpazila, onValueChange = { }, readOnly = true, enabled = selectedDivision.isNotEmpty(), label = { Text("Upazila") }, leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = upazilaExpanded) }, modifier = Modifier.fillMaxWidth())
                    if (selectedDivision.isNotEmpty()) {
                        DropdownMenu(expanded = upazilaExpanded, onDismissRequest = { upazilaExpanded = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                            upazilas.forEach { upazila -> DropdownMenuItem(text = { Text(upazila) }, onClick = { selectedUpazila = upazila; upazilaExpanded = false }) }
                        }
                    }
                    Box(modifier = Modifier.matchParentSize().clickable(enabled = selectedDivision.isNotEmpty()) { upazilaExpanded = true })
                }
                OutlinedTextField(value = detailedAddress, onValueChange = { detailedAddress = it }, label = { Text("Detailed Address") }, leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 2)
                OutlinedTextField(value = postalCode, onValueChange = { if (it.length <= 4) postalCode = it }, label = { Text("Postal Code") }, leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { if (it.length <= 11) phone = it }, label = { Text("Phone Number (01XXXXXXXXX)") }, leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                val isFormValid = name.isNotBlank() && age.isNotBlank() && selectedDivision.isNotBlank() && selectedUpazila.isNotBlank() && detailedAddress.isNotBlank() && postalCode.length == 4 && phone.length == 11
                Button(onClick = { if (isFormValid) { onProfileSaved(name, age.toIntOrNull() ?: 0, phone, selectedDivision, selectedUpazila, "$detailedAddress - $postalCode") } }, enabled = isFormValid, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text("Create Profile") }
                TextButton(onClick = onBackToLogin) { Text("Already have an account? Login") }
            }
        }
    }
}
