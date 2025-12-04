package com.example.agent_app.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent_app.R
import com.example.agent_app.data.entity.IngestItem
import com.example.agent_app.data.entity.Contact
import com.example.agent_app.data.entity.Event
import com.example.agent_app.data.entity.Note
import com.example.agent_app.util.TimeFormatter
import com.example.agent_app.util.TestUserManager
import com.example.agent_app.ui.chat.ChatScreen
import com.example.agent_app.ui.chat.ChatViewModel
import com.example.agent_app.ui.share.ShareCalendarScreen
import com.example.agent_app.ui.share.ShareCalendarViewModel
import com.example.agent_app.ui.share.ShareCalendarUiState
import com.example.agent_app.ui.common.components.EmptyState
import com.example.agent_app.ui.theme.Dimens
import androidx.compose.ui.platform.LocalContext
import com.example.agent_app.util.PushNotificationFilterSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun AssistantApp(
    mainViewModel: MainViewModel,
    chatViewModel: ChatViewModel,
    googleSignInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    shareCalendarViewModel: ShareCalendarViewModel,
    initialTab: AssistantTab? = null,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(initialTab ?: AssistantTab.Dashboard) }
    var selectedDrawerMenu by rememberSaveable { mutableStateOf(DrawerMenu.Menu) }
    var showNeedsReviewScreen by rememberSaveable { mutableStateOf(false) }
    val shareCalendarUiState by shareCalendarViewModel.uiState.collectAsStateWithLifecycle()
    val selectedEmail = mainViewModel.getSelectedAccountEmail()
    
    // 초기 탭 설정 (위젯에서 전달된 경우)
    LaunchedEffect(initialTab) {
        initialTab?.let { selectedTab = it }
    }
    
    // 푸시 알림 권한 안내 다이얼로그 상태
    var showPushNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var hasCheckedPermission by rememberSaveable { mutableStateOf(false) }

    // 앱 시작 시 푸시 알림 권한 확인
    LaunchedEffect(Unit) {
        if (!hasCheckedPermission) {
            hasCheckedPermission = true
            val hasPermission = mainViewModel.checkNotificationListenerPermission()
            if (!hasPermission) {
                // 권한이 없으면 다이얼로그 표시
                showPushNotificationPermissionDialog = true
            }
        }
    }

    LaunchedEffect(shareCalendarUiState.snackbarMessage) {
        shareCalendarUiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            shareCalendarViewModel.consumeMessage()
        }
    }

    LaunchedEffect(uiState.loginState.statusMessage, uiState.syncMessage) {
        val messages = listOfNotNull(uiState.loginState.statusMessage, uiState.syncMessage)
        if (messages.isNotEmpty()) {
            messages.forEach { snackbarHostState.showSnackbar(it) }
            mainViewModel.consumeStatusMessage()
        }
    }

    LaunchedEffect(selectedTab, selectedEmail) {
        if (selectedTab == AssistantTab.ShareCalendar) {
            shareCalendarViewModel.loadMyProfile(selectedEmail)
        }
    }

    AssistantScaffold(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        selectedDrawerMenu = selectedDrawerMenu,
        onDrawerMenuSelected = { selectedDrawerMenu = it },
        chatViewModel = chatViewModel,
        mainViewModel = mainViewModel,
        onAccessTokenChange = mainViewModel::updateAccessToken,
        onRefreshTokenChange = mainViewModel::updateRefreshToken,
        onEmailChange = mainViewModel::updateEmail,
        onScopeChange = mainViewModel::updateScope,
        onExpiresAtChange = mainViewModel::updateExpiresAt,
        onSaveToken = mainViewModel::saveToken,
        onClearToken = mainViewModel::clearToken,
        onSync = { /* Gmail 동기화는 GmailSyncCard에서 직접 처리 */ },
        onResetDatabase = mainViewModel::resetDatabase,
        onClearEvents = mainViewModel::clearAllEvents,
        googleSignInLauncher = googleSignInLauncher,
        shareCalendarUiState = shareCalendarUiState,
        onShareCalendarNameChange = shareCalendarViewModel::updateName,
        onShareCalendarDescriptionChange = shareCalendarViewModel::updateDescription,
        onShareCalendarSubmit = { shareCalendarViewModel.createCalendar(selectedEmail) },
        onShareCalendarLoadProfile = { shareCalendarViewModel.loadMyProfile(selectedEmail) },
        onShareCalendarSearchProfileInputChange = shareCalendarViewModel::updateSearchProfileInput,
        onShareCalendarSearchProfile = shareCalendarViewModel::searchProfileByShareId,
        onShareCalendarSearchCalendarInputChange = shareCalendarViewModel::updateSearchCalendarInput,
        onShareCalendarSearchCalendar = shareCalendarViewModel::searchCalendarByShareId,
        onShareCalendarSearchCalendarClick = shareCalendarViewModel::showSearchCalendarPreview,
        onShareCalendarMyCalendarClick = { id -> shareCalendarViewModel.loadMyCalendarDetail(selectedEmail, id) },
        onShareCalendarDismissPreview = shareCalendarViewModel::clearMyCalendarPreview,
        onShareCalendarApplyInternalData = { calendarId ->
            shareCalendarViewModel.syncInternalEvents(selectedEmail, calendarId, uiState.events)
        },
        showNeedsReviewScreen = showNeedsReviewScreen,
        onNavigateToNeedsReview = { showNeedsReviewScreen = true },
        onNavigateBackFromNeedsReview = { showNeedsReviewScreen = false },
    )
    
    // 푸시 알림 권한 안내 다이얼로그
    if (showPushNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPushNotificationPermissionDialog = false },
            title = {
                Text(stringResource(R.string.permission_push_title))
            },
            text = {
                Text(
                    stringResource(R.string.permission_push_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPushNotificationPermissionDialog = false
                        mainViewModel.openNotificationListenerSettings()
                    }
                ) {
                    Text(stringResource(R.string.permission_push_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPushNotificationPermissionDialog = false }
                ) {
                    Text(stringResource(R.string.permission_push_later))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantScaffold(
    uiState: AssistantUiState,
    snackbarHostState: SnackbarHostState,
    selectedTab: AssistantTab,
    onTabSelected: (AssistantTab) -> Unit,
    selectedDrawerMenu: DrawerMenu,
    onDrawerMenuSelected: (DrawerMenu) -> Unit,
    chatViewModel: ChatViewModel,
    mainViewModel: MainViewModel,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
    onResetDatabase: () -> Unit,
    onClearEvents: () -> Unit,
    googleSignInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    shareCalendarUiState: ShareCalendarUiState,
    onShareCalendarNameChange: (String) -> Unit,
    onShareCalendarDescriptionChange: (String) -> Unit,
    onShareCalendarSubmit: () -> Unit,
    onShareCalendarLoadProfile: () -> Unit,
    onShareCalendarSearchProfileInputChange: (String) -> Unit,
    onShareCalendarSearchProfile: () -> Unit,
    onShareCalendarSearchCalendarInputChange: (String) -> Unit,
    onShareCalendarSearchCalendar: () -> Unit,
    onShareCalendarSearchCalendarClick: () -> Unit,
    onShareCalendarMyCalendarClick: (String) -> Unit,
    onShareCalendarDismissPreview: () -> Unit,
    onShareCalendarApplyInternalData: (String) -> Unit,
    showNeedsReviewScreen: Boolean,
    onNavigateToNeedsReview: () -> Unit,
    onNavigateBackFromNeedsReview: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val calendarAccentColorInt by mainViewModel.calendarAccentColor.collectAsStateWithLifecycle()
    val calendarAccentColor = remember(calendarAccentColorInt) { Color(calendarAccentColorInt) }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarMenu(
                selectedMenu = selectedDrawerMenu,
                onMenuSelected = { menu ->
                    onDrawerMenuSelected(menu)
                    scope.launch { drawerState.close() }
                },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "메뉴",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
                )
            },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(96.dp)  // 높이를 96dp로 더 증가하여 텍스트 잘림 완전 방지
            ) {
                AssistantTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { 
                            onTabSelected(tab)
                            // 개발자 기능 화면에서 탭을 클릭하면 메인 화면으로 전환
                            if (selectedDrawerMenu == DrawerMenu.Developer) {
                                onDrawerMenuSelected(DrawerMenu.Menu)
                            }
                        },
                        label = { 
                            Text(
                                text = stringResource(tab.labelResId),
                                style = MaterialTheme.typography.labelSmall,  // 텍스트 크기를 작게 조정
                                modifier = Modifier.padding(vertical = 8.dp),  // 텍스트 상하 여유 공간 더 증가
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis  // 텍스트가 길 경우 말줄임표 표시
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = getTabIcon(tab),
                                contentDescription = stringResource(tab.labelResId),
                                modifier = Modifier.size(22.dp)  // 아이콘 크기를 약간 줄여 공간 확보
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
        ) { paddingValues ->
            // Needs Review 화면 표시
            if (showNeedsReviewScreen) {
                NeedsReviewScreen(
                    viewModel = mainViewModel,
                    onNavigateBack = onNavigateBackFromNeedsReview,
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                // 사이드바 메뉴에 따른 화면 표시
                when (selectedDrawerMenu) {
                    DrawerMenu.Menu -> {
                        // 메뉴 화면에서는 탭 화면을 표시
                        when (selectedTab) {
                        AssistantTab.Dashboard -> {
                            DashboardScreen(
                                viewModel = mainViewModel,
                                onNavigateToCalendar = { onTabSelected(AssistantTab.Calendar) },
                                onNavigateToInbox = { onTabSelected(AssistantTab.Inbox) },
                                onNavigateToNeedsReview = onNavigateToNeedsReview,
                                modifier = Modifier.padding(paddingValues),
                            )
                        }

                        AssistantTab.Chat -> ChatScreen(
                            viewModel = chatViewModel,
                            modifier = Modifier.padding(paddingValues),
                            onUpdateEvent = { event -> mainViewModel.updateEvent(event) },
                            onDeleteEvent = { event -> mainViewModel.deleteEvent(event) },
                            onNavigateToCalendar = { onTabSelected(AssistantTab.Calendar) },
                        )

                        AssistantTab.Calendar -> CalendarContent(
                            events = uiState.events,
                            contentPadding = paddingValues,
                            onUpdateEvent = { event -> mainViewModel.updateEvent(event) },
                            onDeleteEvent = { event -> mainViewModel.deleteEvent(event) },
                            mainViewModel = mainViewModel,
                            accentColor = calendarAccentColor,
                        )

                        AssistantTab.Inbox -> {
                            val gmailEventsMap = remember(uiState.gmailItems, uiState.events) {
                                uiState.gmailItems.associate { item ->
                                    item.id to uiState.events.filter { event ->
                                        event.sourceType == "gmail" && event.sourceId == item.id
                                    }
                                }
                            }
                            val smsEventsMap = remember(uiState.smsItems, uiState.events) {
                                uiState.smsItems.associate { item ->
                                    item.id to uiState.events.filter { event ->
                                        event.sourceType == "sms" && event.sourceId == item.id
                                    }
                                }
                            }
                            val pushEventsMap = remember(uiState.pushNotificationItems, uiState.events) {
                                uiState.pushNotificationItems.associate { item ->
                                    item.id to uiState.events.filter { event ->
                                        event.sourceType == "push_notification" && event.sourceId == item.id
                                    }
                                }
                            }
                            InboxContent(
                                ocrItems = uiState.ocrItems,
                                ocrEvents = uiState.ocrEvents,
                                smsItems = uiState.smsItems,
                                smsEvents = smsEventsMap,
                                gmailItems = uiState.gmailItems,
                                gmailEvents = gmailEventsMap,
                                pushNotificationItems = uiState.pushNotificationItems,
                                pushNotificationEvents = pushEventsMap,
                                contentPadding = paddingValues,
                                mainViewModel = mainViewModel,
                            )
                        }
                        AssistantTab.ShareCalendar -> ShareCalendarScreen(
                            uiState = shareCalendarUiState,
                            onNameChange = onShareCalendarNameChange,
                            onDescriptionChange = onShareCalendarDescriptionChange,
                            onSubmit = onShareCalendarSubmit,
                            onLoadProfile = onShareCalendarLoadProfile,
                            onSearchProfileInputChange = onShareCalendarSearchProfileInputChange,
                            onSearchProfile = onShareCalendarSearchProfile,
                            onSearchCalendarInputChange = onShareCalendarSearchCalendarInputChange,
                            onSearchCalendar = onShareCalendarSearchCalendar,
                            onSearchCalendarClick = onShareCalendarSearchCalendarClick,
                            onMyCalendarClick = onShareCalendarMyCalendarClick,
                            onDismissPreview = onShareCalendarDismissPreview,
                            onApplyInternalData = onShareCalendarApplyInternalData,
                            modifier = Modifier.padding(paddingValues),
                        )
                    }
                }
                DrawerMenu.Developer -> {
                    LaunchedEffect(uiState.smsScanState.message) {
                        uiState.smsScanState.message?.let { message ->
                            snackbarHostState.showSnackbar(message)
                            mainViewModel.consumeStatusMessage()
                        }
                    }
                    DeveloperContent(
                        uiState = uiState,
                        contentPadding = paddingValues,
                        mainViewModel = mainViewModel,
                        onAccessTokenChange = onAccessTokenChange,
                        onRefreshTokenChange = onRefreshTokenChange,
                        onEmailChange = onEmailChange,
                        onScopeChange = onScopeChange,
                        onExpiresAtChange = onExpiresAtChange,
                        onSaveToken = onSaveToken,
                        onClearToken = onClearToken,
                        onSync = onSync,
                        onResetDatabase = onResetDatabase,
                        onClearEvents = onClearEvents,
                        googleSignInLauncher = googleSignInLauncher,
                    )
                }
            }
        }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperContent(
    uiState: AssistantUiState,
    contentPadding: PaddingValues,
    mainViewModel: MainViewModel,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
    onResetDatabase: () -> Unit,
    onClearEvents: () -> Unit,
    googleSignInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
) {
    // 날짜 선택 다이얼로그 상태
    var showDatePicker by remember { mutableStateOf(false) }
    var showCallRecordDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30일 전 기본값
    )
    val callRecordDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30일 전 기본값
    )
    
    val context = LocalContext.current
    
    // 권한 요청 런처 (SMS 읽기 권한만 요청)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 권한이 허용되면 날짜 선택 다이얼로그 표시
            showDatePicker = true
        } else {
            // 거부된 경우는 UI만 유지 (사용자가 나중에 다시 시도 가능)
        }
    }

    // 앱 시작 시 SMS 읽기 권한이 없으면 바로 요청
    LaunchedEffect(Unit) {
        val readGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!readGranted) {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        GoogleAccountsCard(
            accounts = mainViewModel.getGoogleAccounts(),
            selectedEmail = mainViewModel.getSelectedAccountEmail(),
            onAccountSelected = { email -> mainViewModel.selectAccount(email) },
            onAccountDeleted = { email -> mainViewModel.clearToken(email) },
            onAddAccount = {
                // 다이얼로그에서 확인 버튼을 누르면 LoginCard로 스크롤하거나 강조 표시
                // 실제로는 다이얼로그에서 안내 메시지만 표시
            },
        )
        val scope = rememberCoroutineScope()
        LoginCard(
            loginState = uiState.loginState,
            onAccessTokenChange = onAccessTokenChange,
            onRefreshTokenChange = onRefreshTokenChange,
            onEmailChange = onEmailChange,
            onScopeChange = onScopeChange,
            onExpiresAtChange = onExpiresAtChange,
            onSaveToken = onSaveToken,
            onClearToken = onClearToken,
            onGoogleLogin = {
                // 코루틴 스코프에서 Intent 가져오기 (계정 선택 화면 표시를 위해)
                scope.launch {
                    try {
                        android.util.Log.d("MainScreen", "Google Sign-In Intent 생성 시작")
                        val intent = mainViewModel.getGoogleSignInIntent()
                        android.util.Log.d("MainScreen", "Google Sign-In Intent 생성 완료")
                        googleSignInLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("MainScreen", "Google Sign-In Intent 생성 실패", e)
                    }
                }
            },
            onOAuth2Login = {
                // OAuth 2.0 플로우 시작 (Refresh Token 포함)
                mainViewModel.startGoogleOAuth2Flow()
            },
        )
        GmailSyncCard(
            accounts = mainViewModel.getGoogleAccounts(),
            gmailSyncState = uiState.gmailSyncState,
            onSync = { email, sinceTimestamp -> mainViewModel.syncGmail(email, sinceTimestamp) },
        )
        
        // Gmail 업데이트 기록 표시 (각 계정별로)
        uiState.gmailSyncState.syncStatesByEmail.forEach { (email, accountState) ->
            if (accountState.recentUpdates.isNotEmpty()) {
                GmailUpdateHistoryCard(
                    accountEmail = email,
                    recentUpdates = accountState.recentUpdates
                )
            }
        }
        SmsScanCard(
            smsScanState = uiState.smsScanState,
            onScanClick = { sinceTimestamp ->
                if (mainViewModel.checkSmsPermission()) {
                    mainViewModel.scanSmsMessages(sinceTimestamp)
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                }
            },
        )
        
        // 최근 업데이트 기록 표시
        if (uiState.smsScanState.recentUpdates.isNotEmpty()) {
            SmsUpdateHistoryCard(
                recentUpdates = uiState.smsScanState.recentUpdates
            )
        }
        
        CallRecordScanCard(
            callRecordScanState = uiState.callRecordScanState,
            onScanClick = {
                if (mainViewModel.checkSmsPermission()) {
                    showCallRecordDatePicker = true
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                }
            }
        )
        
        // 푸시 알림 분석 카드
        PushNotificationAnalysisCard(
            mainViewModel = mainViewModel,
        )

        val calendarAccentColorInt by mainViewModel.calendarAccentColor.collectAsStateWithLifecycle()
        val calendarAccentColor = remember(calendarAccentColorInt) { Color(calendarAccentColorInt) }
        CalendarAppearanceCard(
            currentColor = calendarAccentColor,
            palette = calendarAccentPalette,
            onColorSelected = { color -> mainViewModel.updateCalendarAccentColor(color.toArgb()) }
        )
        
        // 테스트 사용자 관리
        TestUserManagementCard()
        
        // 일정 초기화 카드
        EventCleanupCard(onClearEvents = onClearEvents)
        
        // 신뢰도 재계산 카드
        ConfidenceRecalculationCard(
            isRecalculating = uiState.syncState.isSyncing,
            message = uiState.syncState.message,
            onRecalculate = { mainViewModel.recalculateConfidenceForAllItems() }
        )
        
        // DB 초기화 카드
        DatabaseResetCard(
            isResetting = uiState.syncState.isSyncing,
            message = uiState.syncState.message,
            onResetDatabase = onResetDatabase,
        )
    }
    
    // 날짜 선택 다이얼로그 (SMS)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { timestamp ->
                            // 날짜 선택 후 스캔 시작 전 권한 재확인
                            if (mainViewModel.checkSmsPermission()) {
                                mainViewModel.scanSmsMessages(timestamp)
                            } else {
                                // 권한이 없으면 권한 요청
                                permissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // 날짜 선택 다이얼로그 (통화 녹음)
    if (showCallRecordDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showCallRecordDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        callRecordDatePickerState.selectedDateMillis?.let { timestamp ->
                            mainViewModel.scanCallRecordTexts(timestamp)
                        }
                        showCallRecordDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallRecordDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = callRecordDatePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsScanCard(
    smsScanState: com.example.agent_app.ui.SmsScanState,
    onScanClick: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // 자동 처리 활성화 여부 확인 (실시간 업데이트)
    val isAutoProcessEnabled = remember {
        mutableStateOf(
            com.example.agent_app.util.AutoProcessSettings.isSmsAutoProcessEnabled(context)
        )
    }
    // 상태 업데이트를 위해 LaunchedEffect 사용
    LaunchedEffect(Unit) {
        while (true) {
            isAutoProcessEnabled.value = com.example.agent_app.util.AutoProcessSettings.isSmsAutoProcessEnabled(context)
            kotlinx.coroutines.delay(1000) // 1초마다 확인
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = stringResource(R.string.sms_scan_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            // 자동 처리 활성화 상태 표시
            if (isAutoProcessEnabled.value && !smsScanState.isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.sms_auto_process_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            
            if (smsScanState.isScanning) {
                Text(
                    text = "최신 메시지를 수집하고 있습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // 지금 바로 동기화 버튼 (마지막 스캔 시간부터 현재까지)
            Button(
                onClick = { 
                    val sinceTimestamp = if (smsScanState.lastScanTimestamp > 0L) {
                        smsScanState.lastScanTimestamp
                    } else {
                        // 마지막 스캔 기록이 없으면 최근 업데이트 기록의 endTimestamp 사용
                        // 그것도 없으면 0L (전체 스캔)
                        smsScanState.recentUpdates.firstOrNull()?.endTimestamp ?: 0L
                    }
                    onScanClick(sinceTimestamp)
                },
                enabled = !smsScanState.isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (smsScanState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(com.example.agent_app.ui.theme.Dimens.spacingSM))
                    Text(text = stringResource(R.string.sms_scan_progress))
                } else {
                    Text(text = stringResource(R.string.sms_scan_recent))
                }
            }
            
            // 날짜 선택 다이얼로그
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000, // 기본값: 7일 전
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val selectedDate = datePickerState.selectedDateMillis
                                if (selectedDate != null) {
                                    android.util.Log.d("SmsScanCard", "지난 일정 가져오기 시작 - 날짜: $selectedDate")
                                    onScanClick(selectedDate)
                                } else {
                                    android.util.Log.w("SmsScanCard", "날짜가 선택되지 않음")
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text(stringResource(R.string.chat_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(R.string.chat_cancel))
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sms_scan_past_events_date_picker_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DatePicker(state = datePickerState)
                    }
                }
            }
            
            // 지난 일정 가져오기 버튼 (날짜 선택 다이얼로그 표시)
            Button(
                onClick = { showDatePicker = true },
                enabled = !smsScanState.isScanning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(text = stringResource(R.string.sms_scan_past_events))
            }
            
            // 지난 일정 가져오기 설명
            Text(
                text = stringResource(R.string.sms_scan_past_events_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
            
            // 최신 스캔 설명
            if (smsScanState.lastScanTimestamp > 0L || smsScanState.recentUpdates.isNotEmpty()) {
                val lastScanTime = if (smsScanState.lastScanTimestamp > 0L) {
                    smsScanState.lastScanTimestamp
                } else {
                    smsScanState.recentUpdates.firstOrNull()?.endTimestamp ?: 0L
                }
                if (lastScanTime > 0L) {
                    Text(
                        text = "마지막 스캔: ${TimeFormatter.formatTimestamp(lastScanTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            
            // 진행 상황 게이지 표시
            if (smsScanState.isScanning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 진행률 게이지
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = smsScanState.progressMessage ?: stringResource(R.string.sms_scan_progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${(smsScanState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { smsScanState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        )
                    }
                    
                    // 날짜 범위 표시 (며칠 차까지 완료되었는지)
                    if (smsScanState.scanStartTimestamp > 0L && smsScanState.scanEndTimestamp > 0L) {
                        val startDate = java.time.Instant.ofEpochMilli(smsScanState.scanStartTimestamp)
                            .atZone(java.time.ZoneId.of("Asia/Seoul"))
                            .toLocalDate()
                        val endDate = java.time.Instant.ofEpochMilli(smsScanState.scanEndTimestamp)
                            .atZone(java.time.ZoneId.of("Asia/Seoul"))
                            .toLocalDate()
                        
                        // 진행률에 따라 현재 처리 중인 날짜 계산
                        val currentDate = if (smsScanState.progress > 0f) {
                            val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
                            val progressDays = (daysDiff * smsScanState.progress).toLong()
                            startDate.plusDays(progressDays)
                        } else {
                            startDate
                        }
                        
                        val daysCompleted = java.time.temporal.ChronoUnit.DAYS.between(startDate, currentDate)
                        
                        Text(
                            text = stringResource(
                                R.string.sms_scan_date_range,
                                startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")),
                                endDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "완료: ${currentDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))} (${daysCompleted}일차)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (smsScanState.message != null) {
                Text(
                    text = smsScanState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SmsUpdateHistoryCard(
    recentUpdates: List<com.example.agent_app.ui.SmsUpdateRecord>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = stringResource(R.string.sms_scan_updates),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            recentUpdates.take(5).forEach { update ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = update.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${TimeFormatter.formatTimestamp(update.startTimestamp)} ~ ${TimeFormatter.formatTimestamp(update.endTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "처리 시간: ${TimeFormatter.formatTimestamp(update.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (update != recentUpdates.take(5).last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun GmailUpdateHistoryCard(
    accountEmail: String,
    recentUpdates: List<com.example.agent_app.ui.GmailUpdateRecord>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = if (accountEmail.isEmpty()) {
                    stringResource(R.string.gmail_sync_updates)
                } else {
                    stringResource(R.string.gmail_sync_updates_with_account, accountEmail)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            recentUpdates.take(5).forEach { update ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = update.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${TimeFormatter.formatTimestamp(update.startTimestamp)} ~ ${TimeFormatter.formatTimestamp(update.endTimestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "처리 시간: ${TimeFormatter.formatTimestamp(update.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (update != recentUpdates.take(5).last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CallRecordScanCard(
    callRecordScanState: com.example.agent_app.ui.CallRecordScanState,
    onScanClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.call_scan_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onScanClick, enabled = !callRecordScanState.isScanning) {
                    Text(text = stringResource(R.string.call_scan_button))
                }
            }
            if (callRecordScanState.isScanning) {
                com.example.agent_app.ui.common.components.LoadingState()
            }
            if (callRecordScanState.message != null) {
                Text(
                    text = callRecordScanState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.call_scan_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TestUserManagementCard() {
    val context = LocalContext.current
    var testUsers by remember { mutableStateOf(TestUserManager.getTestUsers(context)) }
    var emailInput by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "테스트 사용자 관리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "앱 사용을 허용할 테스트 사용자 이메일을 관리합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            
            if (testUsers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    testUsers.forEach { email: String ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                onClick = {
                                    TestUserManager.removeTestUser(context, email)
                                    testUsers = TestUserManager.getTestUsers(context)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "추가",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("테스트 사용자 추가")
            }
        }
    }
    
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("테스트 사용자 추가") },
            text = {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("이메일 주소") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailInput.isNotBlank()) {
                            TestUserManager.addTestUser(context, emailInput.trim())
                            testUsers = TestUserManager.getTestUsers(context)
                            emailInput = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("추가")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun EventCleanupCard(
    onClearEvents: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = stringResource(R.string.dev_event_clear_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.dev_event_clear_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text(stringResource(R.string.dev_event_clear_button))
            }
        }
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.dev_event_clear_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.dev_event_clear_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearEvents()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Text(stringResource(R.string.dev_event_clear_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ConfidenceRecalculationCard(
    isRecalculating: Boolean,
    message: String?,
    onRecalculate: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = "신뢰도 재계산",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "기존에 파싱한 메시지, 메일 등의 데이터를 다시 분석하여 신뢰도를 재계산하고 일정을 재배열합니다. 기존 일정은 삭제되고 새로 생성됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            if (isRecalculating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text("신뢰도 재계산 실행")
                }
            }
        }
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("신뢰도 재계산 확인") },
            text = {
                Text(
                    text = "기존 일정이 삭제되고 신뢰도가 재계산된 새로운 일정이 생성됩니다. 계속하시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRecalculate()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text("실행")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun DatabaseResetCard(
    isResetting: Boolean,
    message: String?,
    onResetDatabase: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = stringResource(R.string.dev_db_reset_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.dev_db_reset_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            )
            
            if (isResetting) {
                com.example.agent_app.ui.common.components.LoadingState(
                    message = stringResource(R.string.dev_db_reset_progress)
                )
            }
            
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            
            Button(
                onClick = { showConfirmDialog = true },
                enabled = !isResetting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.dev_db_reset_button))
            }
        }
    }
    
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.dev_db_reset_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.dev_db_reset_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDatabase()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.dev_db_reset_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * ⚠️ UI 리브랜딩 안전장치 ⚠️
 * 탭 레이블은 strings.xml에서 관리
 */
enum class AssistantTab(@androidx.annotation.StringRes val labelResId: Int) {
    Dashboard(R.string.tab_dashboard),
    Chat(R.string.tab_chat),
    Calendar(R.string.tab_calendar),
    Inbox(R.string.tab_inbox),
    ShareCalendar(R.string.tab_share_calendar),
}

@Composable
private fun getTabIcon(tab: AssistantTab): androidx.compose.ui.graphics.vector.ImageVector {
    return when (tab) {
        AssistantTab.Dashboard -> Icons.Filled.Home
        AssistantTab.Chat -> Icons.Filled.Email
        AssistantTab.Calendar -> Icons.Filled.DateRange
        AssistantTab.Inbox -> Icons.Filled.Email
        AssistantTab.ShareCalendar -> Icons.Filled.Share
    }
}

enum class DrawerMenu(val label: String) {
    Menu("메뉴"),
    Developer("개발자 기능"),
}

@Composable
private fun GoogleAccountsCard(
    accounts: List<com.example.agent_app.data.entity.AuthToken>,
    selectedEmail: String?,
    onAccountSelected: (String?) -> Unit,
    onAccountDeleted: (String?) -> Unit,
    onAddAccount: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Google 계정 관리",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "계정 추가",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("계정 추가")
                }
            }
            Text(
                text = "연결된 Google 계정 목록입니다. 계정을 선택하여 Gmail을 동기화할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (accounts.isEmpty()) {
                Text(
                    text = "연결된 계정이 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                accounts.forEach { account ->
                    val accountEmailKey = if (account.email.isEmpty()) null else account.email
                    val isSelected = accountEmailKey == selectedEmail || (accountEmailKey == null && selectedEmail == null && accounts.size == 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                val emailKey = if (account.email.isEmpty()) null else account.email
                                onAccountSelected(emailKey)
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (account.email.isEmpty()) "기본 계정" else account.email,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                text = "Scope: ${account.scope?.take(50) ?: "없음"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "선택됨",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { 
                                val emailKey = if (account.email.isEmpty()) null else account.email
                                onAccountDeleted(emailKey)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "삭제",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (account != accounts.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    
    // 계정 추가 다이얼로그
    if (showAddDialog) {
        AddGoogleAccountDialog(
            onDismiss = { showAddDialog = false },
            onAddAccount = {
                onAddAccount()
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddGoogleAccountDialog(
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Google 계정 추가",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Google 계정을 추가하려면 아래 'Google 로그인 설정' 카드에서 토큰을 입력해주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "토큰을 얻는 방법:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "1. Google Cloud Console에서 OAuth 2.0 클라이언트 ID 생성\n2. OAuth Playground에서 토큰 발급\n3. 또는 Google API 테스트 도구 사용",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = {
            Button(onClick = onAddAccount) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GmailSyncCard(
    accounts: List<com.example.agent_app.data.entity.AuthToken>,
    gmailSyncState: com.example.agent_app.ui.GmailSyncState,
    onSync: (String, Long) -> Unit,
) {
    if (accounts.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Gmail 동기화",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "동기화할 계정이 없습니다. 먼저 Google 계정을 추가해주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            accounts.forEach { account ->
                val accountEmail = if (account.email.isEmpty()) "" else account.email
                val accountState = gmailSyncState.syncStatesByEmail[accountEmail] ?: AccountSyncState()
                
                SingleAccountSyncCard(
                    accountEmail = accountEmail,
                    accountState = accountState,
                    account = account,
                    onSync = onSync,
                    context = context,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleAccountSyncCard(
    accountEmail: String,
    accountState: AccountSyncState,
    account: com.example.agent_app.data.entity.AuthToken,
    onSync: (String, Long) -> Unit,
    context: android.content.Context,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTokenExpiredDialog by remember { mutableStateOf(false) }
    
    // 자동 처리 활성화 여부 확인 (실시간 업데이트)
    val isAutoProcessEnabled = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.example.agent_app.util.AutoProcessSettings.isGmailAutoProcessEnabled(context)
        )
    }
    
    // 상태 업데이트를 위해 LaunchedEffect 사용
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            isAutoProcessEnabled.value = com.example.agent_app.util.AutoProcessSettings.isGmailAutoProcessEnabled(context)
            kotlinx.coroutines.delay(1000) // 1초마다 확인
        }
    }
    
    // refresh token 없을 때 기간 선택 후 기간 선택 버튼 숨김
    val hasRefreshToken = !account.refreshToken.isNullOrBlank()
    val shouldHideDatePicker = isAutoProcessEnabled.value && !hasRefreshToken
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (accountEmail.isEmpty()) "Gmail 동기화 (기본 계정)" else "Gmail 동기화: $accountEmail",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            // 동기화 진행 상태 표시
            if (accountState.isSyncing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = accountState.progressMessage ?: "동기화 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${(accountState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { accountState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    )
                }
            }
            
            // 지금 바로 동기화 버튼 (마지막 동기화 시간부터 현재까지)
            Button(
                onClick = { 
                    val sinceTimestamp = if (accountState.lastSyncTimestamp > 0L) {
                        accountState.lastSyncTimestamp
                    } else {
                        // 마지막 동기화 기록이 없으면 최근 업데이트 기록의 endTimestamp 사용
                        // 그것도 없으면 0L (전체 스캔)
                        accountState.recentUpdates.firstOrNull()?.endTimestamp ?: 0L
                    }
                    onSync(accountEmail, sinceTimestamp)
                },
                enabled = !accountState.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (accountState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(com.example.agent_app.ui.theme.Dimens.spacingSM))
                    Text(text = stringResource(R.string.gmail_sync_progress))
                } else {
                    Text(text = stringResource(R.string.gmail_sync_now))
                }
            }
            
            // 날짜 선택 다이얼로그 (지난 일정 가져오기용)
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000, // 기본값: 7일 전
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val selectedDate = datePickerState.selectedDateMillis
                                if (selectedDate != null) {
                                    android.util.Log.d("SingleAccountSyncCard", "지난 일정 가져오기 시작 - 날짜: $selectedDate, 이메일: $accountEmail")
                                    onSync(accountEmail, selectedDate)
                                } else {
                                    android.util.Log.w("SingleAccountSyncCard", "날짜가 선택되지 않음")
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text(stringResource(R.string.chat_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(R.string.chat_cancel))
                        }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }
            
            // 지난 일정 가져오기 버튼 (날짜 선택 다이얼로그 표시)
            Button(
                onClick = { showDatePicker = true },
                enabled = !accountState.isSyncing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(text = stringResource(R.string.gmail_sync_past_events))
            }
            
            // 지난 일정 가져오기 설명
            Text(
                text = stringResource(R.string.gmail_sync_past_events_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
            
            // 마지막 동기화 시간 표시
            if (accountState.lastSyncTimestamp > 0L || accountState.recentUpdates.isNotEmpty()) {
                val lastSyncTime = if (accountState.lastSyncTimestamp > 0L) {
                    accountState.lastSyncTimestamp
                } else {
                    accountState.recentUpdates.firstOrNull()?.endTimestamp ?: 0L
                }
                if (lastSyncTime > 0L) {
                    Text(
                        text = "마지막 동기화: ${TimeFormatter.formatTimestamp(lastSyncTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            
            if (accountState.message != null) {
                Text(
                    text = accountState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            
            // 토큰 만료 다이얼로그
            if (accountState.tokenExpired) {
                showTokenExpiredDialog = true
            }
        }
    }
    
    // 토큰 만료 다이얼로그
    if (showTokenExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showTokenExpiredDialog = false },
            title = {
                Text(
                    text = "토큰 만료",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "${accountEmail}에 대한 토큰 지속시간이 끝났습니다.\n다시 발급 후 동기화 하시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTokenExpiredDialog = false
                        // TODO: 토큰 재발급 로직 호출 (Google 로그인 화면으로 이동 등)
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenExpiredDialog = false }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun LoginCard(
    loginState: LoginUiState,
    onAccessTokenChange: (String) -> Unit,
    onRefreshTokenChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onExpiresAtChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onGoogleLogin: () -> Unit,
    onOAuth2Login: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Google 로그인 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Google 계정 로그인을 통해 gmail.readonly 권한을 부여하면 토큰이 자동으로 저장됩니다. 필요 시 아래 필드를 사용해 수동으로 토큰을 입력할 수도 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Google 로그인 버튼 (기존 방식 - Refresh Token 없음)
            Button(
                onClick = onGoogleLogin,
                enabled = !loginState.isGoogleLoginInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loginState.isGoogleLoginInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "로그인 진행 중...")
                } else {
                    Text(text = "Google 계정으로 로그인 (Refresh Token 없음)")
                }
            }
            
            // OAuth 2.0 플로우 버튼 (Refresh Token 포함)
            Button(
                onClick = onOAuth2Login,
                enabled = !loginState.isGoogleLoginInProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                if (loginState.isGoogleLoginInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "OAuth 2.0 로그인 진행 중...")
                } else {
                    Text(text = "OAuth 2.0 로그인 (Refresh Token 포함) ✅")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "또는 수동으로 토큰 입력:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            OutlinedTextField(
                value = loginState.emailInput,
                onValueChange = onEmailChange,
                label = { Text("계정 이메일 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("여러 계정 구분용 (예: user@gmail.com)") },
            )
            OutlinedTextField(
                value = loginState.accessTokenInput,
                onValueChange = onAccessTokenChange,
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = loginState.refreshTokenInput,
                onValueChange = onRefreshTokenChange,
                label = { Text("Refresh Token (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = loginState.scopeInput,
                onValueChange = onScopeChange,
                label = { Text("Scope") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = loginState.expiresAtInput,
                onValueChange = onExpiresAtChange,
                label = { Text("만료 시각 (epoch ms, 선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (loginState.hasStoredToken) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                val scope = loginState.storedScope ?: "미지"
                val expiry = loginState.storedExpiresAt?.let { TimeFormatter.format(it) } ?: "만료 시간 미설정"
                Text(
                    text = "저장된 Scope: $scope",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "만료 예정: $expiry",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSaveToken) {
                    Text(text = "토큰 저장")
                }
                TextButton(onClick = onClearToken) {
                    Text(text = "토큰 삭제")
                }
            }
        }
    }
}

@Composable
private fun ClassifiedDataCard(
    contacts: List<Contact>,
    events: List<Event>,
    notes: List<Note>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(com.example.agent_app.ui.theme.Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingMD)
        ) {
            Text(
                text = "분류된 데이터",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            // 연락처 섹션
            if (contacts.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.classified_contacts_title, contacts.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                contacts.take(3).forEach { contact ->
                    Text(
                        text = "${contact.name} - ${contact.email ?: contact.phone ?: "정보 없음"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (contacts.size > 3) {
                    Text(
                        text = stringResource(R.string.common_more_items, contacts.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                                HorizontalDivider()
            }
            
            // 이벤트 섹션
            if (events.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.classified_events_title, events.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                events.take(3).forEach { event ->
                    Text(
                        text = "${event.title} - ${event.location ?: "장소 미정"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (events.size > 3) {
                    Text(
                        text = stringResource(R.string.common_more_items, events.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                                HorizontalDivider()
            }
            
            // 노트 섹션
            if (notes.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.classified_notes_title, notes.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                notes.take(3).forEach { note ->
                    Text(
                        text = "${note.title} - ${note.body.take(50)}${if (note.body.length > 50) "..." else ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (notes.size > 3) {
                    Text(
                        text = stringResource(R.string.common_more_items, notes.size - 3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            
            if (contacts.isEmpty() && events.isEmpty() && notes.isEmpty()) {
                Text(
                    text = stringResource(R.string.classified_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun InboxContent(
    ocrItems: List<IngestItem>,
    ocrEvents: Map<String, List<Event>>,
    smsItems: List<IngestItem>,
    smsEvents: Map<String, List<Event>>,
    gmailItems: List<IngestItem>,
    gmailEvents: Map<String, List<Event>>,
    pushNotificationItems: List<IngestItem>,
    pushNotificationEvents: Map<String, List<Event>>,
    contentPadding: PaddingValues,
    mainViewModel: MainViewModel,
) {
    var selectedCategory by remember { mutableStateOf<InboxCategory?>(InboxCategory.All) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    
    // Debounce 검색 쿼리 (500ms)
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(500)
        debouncedQuery = searchQuery
    }
    
    // 검색 쿼리로 필터링 (UI 레이어에서만 필터링)
    val filterItems = { items: List<IngestItem> ->
        if (debouncedQuery.isBlank()) {
            items
        } else {
            val queryLower = debouncedQuery.lowercase()
            items.filter { item ->
                (item.title?.lowercase()?.contains(queryLower) ?: false) ||
                (item.body?.lowercase()?.contains(queryLower) ?: false)
            }
        }
    }
    
    val filteredOcrItems = remember(ocrItems, debouncedQuery) { filterItems(ocrItems) }
    val filteredSmsItems = remember(smsItems, debouncedQuery) { filterItems(smsItems) }
    val filteredGmailItems = remember(gmailItems, debouncedQuery) { filterItems(gmailItems) }
    val filteredPushNotificationItems = remember(pushNotificationItems, debouncedQuery) { filterItems(pushNotificationItems) }
    
    // Pull-to-refresh 상태
    val isRefreshing = mainViewModel.isRefreshing.collectAsStateWithLifecycle().value
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { mainViewModel.refreshInboxData() }
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        // 인박스 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Text(
                text = stringResource(R.string.inbox_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { 
                    mainViewModel.refreshInboxData()
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.inbox_refresh),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // 검색 바
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            placeholder = { Text("키워드로 검색하기") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "검색"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "검색 지우기"
                        )
                    }
                }
            },
            singleLine = true
        )
        
        // 카테고리 탭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 기본 카테고리들
            listOf(
                InboxCategory.All,
                InboxCategory.WithEvents,
                InboxCategory.OCR,
                InboxCategory.SMS,
                InboxCategory.Email,
                InboxCategory.PushNotification,
            ).forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(stringResource(category.labelResId)) },
                )
            }
        }
        
        // 요약 통계 블록 (검색 중일 때는 필터링된 개수 표시)
        InboxSummaryBlock(
            ocrCount = if (debouncedQuery.isBlank()) ocrItems.size else filteredOcrItems.size,
            smsCount = if (debouncedQuery.isBlank()) smsItems.size else filteredSmsItems.size,
            gmailCount = if (debouncedQuery.isBlank()) gmailItems.size else filteredGmailItems.size,
            pushNotificationCount = if (debouncedQuery.isBlank()) pushNotificationItems.size else filteredPushNotificationItems.size,
            totalCount = if (debouncedQuery.isBlank()) {
                ocrItems.size + smsItems.size + gmailItems.size + pushNotificationItems.size
            } else {
                filteredOcrItems.size + filteredSmsItems.size + filteredGmailItems.size + filteredPushNotificationItems.size
            },
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 카테고리별 컨텐츠 (Pull-to-refresh 지원)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            when (selectedCategory) {
                InboxCategory.All -> {
                    // 전체: OCR, SMS, 이메일만 표시 (푸시 알림은 제외)
                    if (filteredOcrItems.isNotEmpty()) {
                        item {
                            CategorySection(
                                titleResId = R.string.inbox_category_ocr,
                                items = filteredOcrItems,
                                events = ocrEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                    OcrItemCard(
                                        item = item,
                                        events = events,
                                        onUpdateEvent = onUpdate,
                                        onDeleteEvent = onDelete,
                                        onCreateEvent = onCreate,
                                    )
                                },
                            )
                        }
                    }
                    if (filteredSmsItems.isNotEmpty()) {
                        item {
                            CategorySection(
                                titleResId = R.string.inbox_category_sms,
                                items = filteredSmsItems,
                                events = smsEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                    SmsItemCard(
                                        item = item,
                                        events = events,
                                        onUpdateEvent = onUpdate,
                                        onDeleteEvent = onDelete,
                                        onCreateEvent = onCreate,
                                    )
                                },
                            )
                        }
                    }
                    // 이메일 통합 표시
                    if (filteredGmailItems.isNotEmpty()) {
                        item {
                            EmailCategorySection(
                                email = null, // 모든 이메일 통합 표시
                                items = filteredGmailItems,
                                events = gmailEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                            )
                        }
                    }
                }
                InboxCategory.OCR -> {
                    if (filteredOcrItems.isEmpty()) {
                        item {
                            EmptyStateCard(messageResId = R.string.inbox_empty_ocr)
                        }
                    } else {
                        item {
                            CategorySection(
                                titleResId = R.string.inbox_category_ocr,
                                items = filteredOcrItems,
                                events = ocrEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                    OcrItemCard(
                                        item = item,
                                        events = events,
                                        onUpdateEvent = onUpdate,
                                        onDeleteEvent = onDelete,
                                        onCreateEvent = onCreate,
                                    )
                                },
                            )
                        }
                    }
                }
                InboxCategory.SMS -> {
                    if (filteredSmsItems.isEmpty()) {
                        item {
                            EmptyStateCard(messageResId = R.string.inbox_empty_sms)
                        }
                    } else {
                        item {
                            CategorySection(
                                titleResId = R.string.inbox_category_sms,
                                items = filteredSmsItems,
                                events = smsEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                    SmsItemCard(
                                        item = item,
                                        events = events,
                                        onUpdateEvent = onUpdate,
                                        onDeleteEvent = onDelete,
                                        onCreateEvent = onCreate,
                                    )
                                },
                            )
                        }
                    }
                }
                InboxCategory.Email -> {
                    if (filteredGmailItems.isEmpty()) {
                        item {
                            EmptyStateCard(messageResId = R.string.inbox_empty_email)
                        }
                    } else {
                        item {
                            EmailCategorySection(
                                email = null, // 모든 이메일 통합 표시
                                items = filteredGmailItems,
                                events = gmailEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                            )
                        }
                    }
                }
                InboxCategory.PushNotification -> {
                    if (filteredPushNotificationItems.isEmpty()) {
                        item {
                            EmptyStateCard(messageResId = R.string.inbox_empty_push)
                        }
                    } else {
                        item {
                            CategorySection(
                                titleResId = R.string.inbox_category_push,
                                items = filteredPushNotificationItems,
                                events = pushNotificationEvents,
                                onUpdateEvent = { mainViewModel.updateEvent(it) },
                                onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                    PushNotificationItemCard(
                                        item = item,
                                        events = events,
                                        onUpdateEvent = onUpdate,
                                        onDeleteEvent = onDelete,
                                        onCreateEvent = onCreate,
                                    )
                                },
                            )
                        }
                    }
                }
                InboxCategory.WithEvents -> {
                    // 이벤트가 있는 항목만 필터링
                    val ocrItemsWithEvents = ocrItems.filter { !ocrEvents[it.id].isNullOrEmpty() }
                    val smsItemsWithEvents = smsItems.filter { !smsEvents[it.id].isNullOrEmpty() }
                    val gmailItemsWithEvents = gmailItems.filter { !gmailEvents[it.id].isNullOrEmpty() }
                    val pushNotificationItemsWithEvents = pushNotificationItems.filter { !pushNotificationEvents[it.id].isNullOrEmpty() }
                    
                    val totalItemsWithEvents = ocrItemsWithEvents.size + smsItemsWithEvents.size + gmailItemsWithEvents.size + pushNotificationItemsWithEvents.size
                    
                    if (totalItemsWithEvents == 0) {
                        item {
                            EmptyStateCard(messageResId = R.string.inbox_empty_events)
                        }
                    } else {
                        // OCR 항목 중 이벤트 있는 것만
                        if (ocrItemsWithEvents.isNotEmpty()) {
                            item {
                                CategorySection(
                                    titleResId = R.string.inbox_category_ocr,
                                    items = ocrItemsWithEvents,
                                    events = ocrEvents,
                                    onUpdateEvent = { mainViewModel.updateEvent(it) },
                                    onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                    onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                    itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                        OcrItemCard(
                                            item = item,
                                            events = events,
                                            onUpdateEvent = onUpdate,
                                            onDeleteEvent = onDelete,
                                            onCreateEvent = onCreate,
                                        )
                                    },
                                )
                            }
                        }
                        // SMS 항목 중 이벤트 있는 것만
                        if (smsItemsWithEvents.isNotEmpty()) {
                            item {
                                CategorySection(
                                    titleResId = R.string.inbox_category_sms,
                                    items = smsItemsWithEvents,
                                    events = smsEvents,
                                    onUpdateEvent = { mainViewModel.updateEvent(it) },
                                    onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                    onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                    itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                        SmsItemCard(
                                            item = item,
                                            events = events,
                                            onUpdateEvent = onUpdate,
                                            onDeleteEvent = onDelete,
                                            onCreateEvent = onCreate,
                                        )
                                    },
                                )
                            }
                        }
                        // Gmail 항목 중 이벤트 있는 것만
                        if (gmailItemsWithEvents.isNotEmpty()) {
                            item {
                                EmailCategorySection(
                                    email = null,
                                    items = gmailItemsWithEvents,
                                    events = gmailEvents,
                                    onUpdateEvent = { mainViewModel.updateEvent(it) },
                                    onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                    onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                )
                            }
                        }
                        // 푸시 알림 항목 중 이벤트 있는 것만
                        if (pushNotificationItemsWithEvents.isNotEmpty()) {
                            item {
                                CategorySection(
                                    titleResId = R.string.inbox_category_push,
                                    items = pushNotificationItemsWithEvents,
                                    events = pushNotificationEvents,
                                    onUpdateEvent = { mainViewModel.updateEvent(it) },
                                    onDeleteEvent = { mainViewModel.deleteEvent(it) },
                                    onCreateEvent = { mainViewModel.createEventFromItem(it) },
                                    itemCard = { item, events, onUpdate, onDelete, onCreate ->
                                        PushNotificationItemCard(
                                            item = item,
                                            events = events,
                                            onUpdateEvent = onUpdate,
                                            onDeleteEvent = onDelete,
                                            onCreateEvent = onCreate,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                null -> {
                    item {
                        EmptyStateCard(messageResId = R.string.inbox_empty_category)
                    }
                }
            }
            }
            
            // Pull-to-refresh 인디케이터 (당기는 동안에도 계속 회전)
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * ⚠️ UI 리브랜딩 안전장치 ⚠️
 * 카테고리 레이블은 strings.xml에서 관리
 */
private sealed class InboxCategory(@androidx.annotation.StringRes val labelResId: Int) {
    object All : InboxCategory(R.string.inbox_category_all)
    object OCR : InboxCategory(R.string.inbox_category_ocr)
    object SMS : InboxCategory(R.string.inbox_category_sms)
    object Email : InboxCategory(R.string.inbox_category_email)
    object PushNotification : InboxCategory(R.string.inbox_category_push)
    object WithEvents : InboxCategory(R.string.inbox_category_with_events)
}

// 이메일 주소 추출 헬퍼 함수
private fun extractEmailFromItem(item: IngestItem): String {
    // title에서 이메일 주소 추출 시도
    val title = item.title ?: ""
    val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    val emailMatch = emailRegex.find(title)
    if (emailMatch != null) {
        return emailMatch.value
    }
    
    // body에서 이메일 주소 추출 시도
    val body = item.body ?: ""
    val bodyEmailMatch = emailRegex.find(body)
    if (bodyEmailMatch != null) {
        return bodyEmailMatch.value
    }
    
    // 추출 실패 시 기본값
    return "알 수 없음"
}

@Composable
private fun InboxSummaryBlock(
    ocrCount: Int,
    smsCount: Int,
    gmailCount: Int,
    pushNotificationCount: Int,
    totalCount: Int,
) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryItem(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.inbox_summary_total),
                count = totalCount,
            )
            SummaryItem(
                icon = Icons.Filled.Info,
                label = stringResource(R.string.inbox_summary_ocr),
                count = ocrCount,
            )
            SummaryItem(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.inbox_summary_sms),
                count = smsCount,
            )
            SummaryItem(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.inbox_summary_email),
                count = gmailCount,
            )
            SummaryItem(
                icon = Icons.Filled.Info,
                label = stringResource(R.string.inbox_summary_push),
                count = pushNotificationCount,
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
        )
                Text(
            text = "$count",
                    style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
                )
                Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun <T> CategorySection(
    @androidx.annotation.StringRes titleResId: Int,
    items: List<T>,
    events: Map<String, List<Event>>,
    onUpdateEvent: (Event) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onCreateEvent: ((T) -> Unit)? = null,
    itemCard: @Composable (T, List<Event>, (Event) -> Unit, (Event) -> Unit, ((T) -> Unit)?) -> Unit,
) where T : IngestItem {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                Text(
                text = stringResource(titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                text = "총 ${items.size}개",
                style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                                HorizontalDivider()
            items.forEachIndexed { index, item ->
                itemCard(item, events[item.id] ?: emptyList(), onUpdateEvent, onDeleteEvent, onCreateEvent)
                if (index < items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }

@Composable
private fun EmailCategorySection(
    email: String?,
    items: List<IngestItem>,
    events: Map<String, List<Event>>,
    onUpdateEvent: (Event) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onCreateEvent: ((IngestItem) -> Unit)? = null,
) {
    
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                    Text(
                text = email?.let { "이메일: $it" } ?: "이메일",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                text = "총 ${items.size}개",
                style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                                HorizontalDivider()
            items.forEachIndexed { index, item ->
                GmailItemCard(
                    item = item,
                    events = events[item.id] ?: emptyList(),
                    onUpdateEvent = onUpdateEvent,
                    onDeleteEvent = onDeleteEvent,
                    onCreateEvent = onCreateEvent,
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(@androidx.annotation.StringRes messageResId: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(com.example.agent_app.ui.theme.Dimens.spacingLG * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(com.example.agent_app.ui.theme.Dimens.spacingSM),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "정보 아이콘",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(messageResId),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GmailItemCard(
    item: IngestItem,
    events: List<Event>,
    onUpdateEvent: (Event) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onCreateEvent: ((IngestItem) -> Unit)? = null,
) {
    var showTextDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "📧 이메일",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = item.title ?: "(제목 없음)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.body?.take(200) ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            
            Text(
                text = "수신 시간: ${TimeFormatter.format(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
            )
            
            if (item.confidence != null) {
                Text(
                    text = "신뢰도: ${(item.confidence * 100).coerceIn(0.0, 100.0).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            
            // 연결된 이벤트 표시
            if (events.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "📅 추출된 일정 (${events.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        EventDetailRow(
                            event = event,
                            onUpdateEvent = onUpdateEvent,
                            onDeleteEvent = onDeleteEvent,
                        )
                    }
                }
            } else {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "추출된 일정 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                // 일정 생성 버튼
                if (onCreateEvent != null) {
                    Button(
                        onClick = { onCreateEvent(item) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "일정 추가",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("일정 생성")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showTextDialog = true }) {
                    Text("전체 보기")
                }
            }
        }
    }
    
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("이메일 내용") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.title ?: "(제목 없음)",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = item.body ?: "")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("닫기")
                }
            },
        )
    }
}

@Composable
private fun PushNotificationItemCard(
    item: IngestItem,
    events: List<Event>,
    onUpdateEvent: ((Event) -> Unit)? = null,
    onDeleteEvent: ((Event) -> Unit)? = null,
    onCreateEvent: ((IngestItem) -> Unit)? = null,
) {
    var showTextDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 푸시 알림 정보
            Text(
                text = "🔔 푸시 알림",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            
            Text(
                text = "앱: ${item.title ?: "(알 수 없음)"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            
            Text(
                text = item.body.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTextDialog = true },
            )
            
            Text(
                text = "수신 시간: ${TimeFormatter.format(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
            )
            
            if (item.confidence != null) {
                Text(
                    text = "신뢰도: ${(item.confidence * 100).coerceIn(0.0, 100.0).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            
            // 연결된 이벤트 표시
            if (events.isNotEmpty()) {
                                HorizontalDivider()
                Text(
                    text = "추출된 일정 (${events.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                events.forEach { event ->
                    EventDetailRow(
                        event = event,
                        onUpdateEvent = onUpdateEvent,
                        onDeleteEvent = onDeleteEvent,
                    )
                }
            } else if (onCreateEvent != null) {
                                HorizontalDivider()
                // 일정 생성 버튼
                Button(
                    onClick = { onCreateEvent(item) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "일정 추가",
                            modifier = Modifier.size(18.dp),
                        )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("일정 생성")
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showTextDialog = true }) {
                    Text("전체 보기")
                }
            }
        }
    }
    
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("푸시 알림 내용") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.title ?: "(제목 없음)",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = item.body ?: "")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("닫기")
                }
            },
        )
    }
}

@Composable
private fun SmsItemCard(
    item: IngestItem,
    events: List<Event>,
    onUpdateEvent: ((Event) -> Unit)? = null,
    onDeleteEvent: ((Event) -> Unit)? = null,
    onCreateEvent: ((IngestItem) -> Unit)? = null,
) {
    var showTextDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SMS 정보
            Text(
                text = "📱 SMS 메시지",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            
            Text(
                text = "발신자: ${item.title ?: "(알 수 없음)"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            
            Text(
                text = item.body.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTextDialog = true },
            )
            
            Text(
                text = "수신 시간: ${TimeFormatter.format(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
            )
            
            if (item.confidence != null) {
                Text(
                    text = "신뢰도: ${(item.confidence * 100).coerceIn(0.0, 100.0).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            
            // 연결된 이벤트 표시
            if (events.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "📅 추출된 일정 (${events.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        EventDetailRow(
                            event = event,
                            onUpdateEvent = onUpdateEvent,
                            onDeleteEvent = onDeleteEvent,
                        )
                    }
                }
            } else {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "추출된 일정 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                // 일정 생성 버튼
                if (onCreateEvent != null) {
                    Button(
                        onClick = { onCreateEvent(item) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "일정 추가",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("일정 생성")
                    }
                }
            }
        }
    }
    
    if (showTextDialog) {
        TextDetailDialog(
            title = "SMS 원본 텍스트",
            text = item.body.orEmpty(),
            onDismiss = { showTextDialog = false }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun CalendarContent(
    events: List<Event>,
    contentPadding: PaddingValues,
    onUpdateEvent: ((Event) -> Unit)? = null,
    onDeleteEvent: ((Event) -> Unit)? = null,
    mainViewModel: MainViewModel? = null,
    accentColor: Color,
) {
    var showAddEventDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val today = LocalDate.now()
    var selectedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val eventRanges = remember(events) {
        events.mapNotNull { event ->
            val startDate = event.startAt?.let { timestamp ->
                java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .toLocalDate()
            } ?: return@mapNotNull null

            val endDate = event.endAt?.let { timestamp ->
                java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .toLocalDate()
            } ?: startDate

            CalendarEventRange(event = event, startDate = startDate, endDate = endDate)
        }
    }
    val multiDayRanges = remember(eventRanges) {
        eventRanges.filter { it.startDate != it.endDate }
    }
    val density = LocalDensity.current
    
    // Pull-to-refresh 상태
    val isRefreshing = mainViewModel?.isRefreshing?.collectAsStateWithLifecycle()?.value ?: false
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            mainViewModel?.let { vm ->
                scope.launch {
                    vm.loadClassifiedData()
                }
            }
        }
    )
    
    // 선택된 날짜의 일정
    val selectedDateEvents = remember(selectedDate, eventRanges) {
        selectedDate?.let { date ->
            eventRanges.filter { range ->
                !date.isBefore(range.startDate) && !date.isAfter(range.endDate)
            }.map { it.event }
        } ?: emptyList()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 월 이동 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            selectedMonth = selectedMonth.minusMonths(1)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "이전 달",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Text(
                        text = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy년 MM월")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    
                    IconButton(
                        onClick = {
                            selectedMonth = selectedMonth.plusMonths(1)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "다음 달",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                HorizontalDivider()
                
                // 요일 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (day == "일") Color.Red else if (day == "토") Color.Blue else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                
                // 캘린더 그리드
                val firstDayOfMonth = selectedMonth.atDay(1)
                val lastDayOfMonth = selectedMonth.atEndOfMonth()
                val startDate = firstDayOfMonth.minusDays(firstDayOfMonth.dayOfWeek.value.toLong() % 7)
                
                // 날짜 범위를 7일씩 묶어서 주(week) 단위로 분할
                // 항상 6주(42일)를 채우기 위해 다음 달 날짜도 포함
                val allDates = mutableListOf<LocalDate>()
                var cursorDate = startDate
                
                // 6주(42일)를 채울 때까지 날짜 추가
                val totalDays = 42
                for (i in 0 until totalDays) {
                    allDates.add(cursorDate)
                    cursorDate = cursorDate.plusDays(1)
                }
                
                val weeks = allDates.chunked(7)
                
                val barHeightPx = with(density) { 12.dp.toPx() }
                val barBottomPaddingPx = with(density) { 6.dp.toPx() }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    weeks.forEach { week: List<LocalDate> ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val cellWidth = size.width / 7f
                                val weekStart = week.first()
                                val weekEnd = week.last()
                                
                                // 이 주에 해당하는 범위 일정 필터링
                                val weekRanges = multiDayRanges.mapNotNull { range ->
                                    val intersectionStart = if (range.startDate.isAfter(weekStart)) range.startDate else weekStart
                                    val intersectionEnd = if (range.endDate.isBefore(weekEnd)) range.endDate else weekEnd
                                    if (!intersectionStart.isAfter(intersectionEnd)) {
                                        val startIndex = week.indexOf(intersectionStart)
                                        val endIndex = week.indexOf(intersectionEnd)
                                        if (startIndex != -1 && endIndex != -1) {
                                            Triple(range, startIndex, endIndex)
                                        } else null
                                    } else null
                                }
                                
                                // 겹치는 일정을 레이어로 분류
                                val layers = mutableListOf<MutableList<Triple<CalendarEventRange, Int, Int>>>()
                                
                                weekRanges.forEach { (range, startIdx, endIdx) ->
                                    // 기존 레이어 중 겹치지 않는 레이어 찾기
                                    var placed = false
                                    for (layer in layers) {
                                        val hasOverlap = layer.any { (_, existingStart, existingEnd) ->
                                            // 겹치는지 확인: (startIdx <= existingEnd && endIdx >= existingStart)
                                            startIdx <= existingEnd && endIdx >= existingStart
                                        }
                                        if (!hasOverlap) {
                                            layer.add(Triple(range, startIdx, endIdx))
                                            placed = true
                                            break
                                        }
                                    }
                                    // 겹치는 레이어가 없으면 새 레이어 생성
                                    if (!placed) {
                                        layers.add(mutableListOf(Triple(range, startIdx, endIdx)))
                                    }
                                }
                                
                                // 각 레이어별로 그리기
                                layers.forEachIndexed { layerIndex, layer ->
                                    val layerY = (size.height - barHeightPx - barBottomPaddingPx) - (layerIndex * (barHeightPx + 2f))
                                    
                                    layer.forEach { (range, startIdx, endIdx) ->
                                        val left = cellWidth * startIdx + cellWidth * 0.08f
                                        val right = cellWidth * (endIdx + 1) - cellWidth * 0.08f
                                        val width = (right - left).coerceAtLeast(0f)
                                        
                                        if (width > 0f && layerY >= 0f) {
                                            // 각 일정마다 다른 색상 사용 (색상 팔레트에서 선택)
                                            val colorIndex = (range.event.id.toInt() % calendarAccentPalette.size).coerceAtLeast(0)
                                            val eventColor = calendarAccentPalette[colorIndex]
                                            
                                            drawRoundRect(
                                                color = eventColor.copy(alpha = 0.4f),
                                                topLeft = Offset(left, layerY),
                                                size = Size(width, barHeightPx),
                                                cornerRadius = CornerRadius(barHeightPx / 2, barHeightPx / 2)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .matchParentSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                week.forEach { date: LocalDate ->
                                    val isCurrentMonth = date.monthValue == selectedMonth.monthValue
                                    val isToday = date == today
                                    val hasEvent = eventRanges.any { range ->
                                        !date.isBefore(range.startDate) && !date.isAfter(range.endDate)
                                    }
                                    val isSelected = date == selectedDate
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedDate = date
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(50)
                                                    )
                                                    .border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(50)
                                                    )
                                            )
                                        }
                                        if (isSelected && !isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(50)
                                                    )
                                            )
                                        }
                                        
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = date.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when {
                                                    !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                    isToday -> Color.White
                                                    date.dayOfWeek.value == 7 -> Color.Red
                                                    date.dayOfWeek.value == 6 -> Color.Blue
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            )
                                            if (hasEvent) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(
                                                            if (isToday) {
                                                                Color.White
                                                            } else {
                                                                accentColor
                                                            },
                                                            shape = RoundedCornerShape(50)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 선택된 날짜의 일정 목록
                if (selectedDate != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "${selectedDate!!.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))} 일정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    
                    if (selectedDateEvents.isEmpty()) {
                        EmptyState(
                            messageResId = R.string.empty_events_today,
                            icon = Icons.Filled.Event
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSM)) {
                            selectedDateEvents.forEach { event ->
                                EventDetailRow(
                                    event = event,
                                    onUpdateEvent = onUpdateEvent,
                                    onDeleteEvent = onDeleteEvent,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
        
        // FAB (Floating Action Button)
        if (mainViewModel != null) {
            FloatingActionButton(
                onClick = { showAddEventDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .minimumInteractiveComponentSize(),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "일정 추가",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        // Snackbar Host
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Pull-to-refresh 인디케이터
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    // 자연어 일정 추가 다이얼로그
    if (showAddEventDialog && mainViewModel != null) {
        AddEventFromNaturalLanguageDialog(
            onDismiss = { showAddEventDialog = false },
            onSuccess = {
                showAddEventDialog = false
                // Snackbar는 다이얼로그 내부에서 처리
            },
            onFailure = {
                // Snackbar는 다이얼로그 내부에서 처리
            },
            snackbarHostState = snackbarHostState,
            mainViewModel = mainViewModel
        )
    }
}

private val calendarAccentPalette = listOf(
    Color(0xFFB5EAEA),
    Color(0xFFF8D7DA),
    Color(0xFFE2F0CB),
    Color(0xFFFCE1A8),
    Color(0xFFD7C4F3),
    Color(0xFFB8E0D2)
)

@Composable
private fun CalendarAppearanceCard(
    currentColor: Color,
    palette: List<Color>,
    onColorSelected: (Color) -> Unit
) {
    val currentColorInt = currentColor.toArgb()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "캘린더 표시 색상",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "캘린더 탭의 포인트 색상을 변경해 사용자 정의 색감으로 일정 범위를 표시할 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { colorOption ->
                    val isSelected = colorOption.toArgb() == currentColorInt
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 36.dp else 28.dp)
                            .clip(CircleShape)
                            .background(colorOption.copy(alpha = 0.95f))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else colorOption.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(colorOption) }
                    )
                }
            }
        }
    }
}

private data class CalendarEventRange(
    val event: Event,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddEventFromNaturalLanguageDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    mainViewModel: MainViewModel,
    snackbarHostState: androidx.compose.material3.SnackbarHostState
) {
    val zoneId = remember { java.time.ZoneId.of("Asia/Seoul") }
    val now = remember { java.time.ZonedDateTime.now(zoneId).withSecond(0).withNano(0) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(now.toLocalDate()) }
    var startTime by remember { mutableStateOf(now.toLocalTime()) }
    var endDate by remember { mutableStateOf(now.plusHours(1).toLocalDate()) }
    var endTime by remember { mutableStateOf(now.plusHours(1).toLocalTime()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))

    fun formatTime(time: LocalTime): String =
        time.format(DateTimeFormatter.ofPattern("HH:mm"))

    fun combine(date: LocalDate, time: LocalTime): ZonedDateTime =
        date.atTime(time).atZone(zoneId)

    fun ensureEndAfterStart() {
        val startDateTime = combine(startDate, startTime)
        var endDateTime = combine(endDate, endTime)
        if (!endDateTime.isAfter(startDateTime)) {
            endDateTime = startDateTime.plusHours(1)
            endDate = endDateTime.toLocalDate()
            endTime = endDateTime.toLocalTime()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수동으로 일정 추가") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMD)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("제목") },
                    placeholder = { Text("예: 운동하기") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("장소 (선택)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    label = { Text("메모 (선택)") },
                    placeholder = { Text("추가 메모를 입력하세요") },
                    maxLines = 4
                )
                Text(
                    text = "시작",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showStartDatePicker = true }
                    ) {
                        Text(formatDate(startDate))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showStartTimePicker = true }
                    ) {
                        Text(formatTime(startTime))
                    }
                }
                Text(
                    text = "종료",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showEndDatePicker = true }
                    ) {
                        Text(formatDate(endDate))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showEndTimePicker = true }
                    ) {
                        Text(formatTime(endTime))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startAt = combine(startDate, startTime).toInstant().toEpochMilli()
                    val endAt = combine(endDate, endTime).toInstant().toEpochMilli()
                    if (endAt <= startAt) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("종료 시간이 시작 시간보다 앞설 수 없어요.")
                        }
                        return@Button
                    }
                    if (title.isBlank()) return@Button
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val success = mainViewModel.createManualEvent(
                                title = title.trim(),
                                description = description.ifBlank { null },
                                location = location.ifBlank { null },
                                startAt = startAt,
                                endAt = endAt
                            )
                            if (success) {
                                snackbarHostState.showSnackbar("일정을 추가했어요.")
                                onSuccess()
                                onDismiss()
                            } else {
                                snackbarHostState.showSnackbar("일정을 저장하지 못했어요.")
                                onFailure()
                            }
                        } catch (e: Exception) {
                            Log.e("ManualEvent", "수동 일정 생성 실패", e)
                            snackbarHostState.showSnackbar("일정을 저장하는 중 문제가 발생했어요.")
                            onFailure()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = title.isNotBlank() && !isSaving
            ) {
                Text(if (isSaving) "저장 중..." else "추가하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("취소")
            }
        }
    )

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = combine(startDate, LocalTime.MIDNIGHT).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            startDate = java.time.Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
                            if (startDate.isAfter(endDate)) {
                                endDate = startDate
                            }
                            ensureEndAfterStart()
                        }
                        showStartDatePicker = false
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = combine(endDate, LocalTime.MIDNIGHT).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            endDate = java.time.Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
                            ensureEndAfterStart()
                        }
                        showEndDatePicker = false
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("시작 시간 선택") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        startTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        ensureEndAfterStart()
                        showStartTimePicker = false
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("취소") }
            }
        )
    }

    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("종료 시간 선택") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        endTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        ensureEndAfterStart()
                        showEndTimePicker = false
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun OcrItemCard(
    item: IngestItem,
    events: List<Event>,
    onUpdateEvent: ((Event) -> Unit)? = null,
    onDeleteEvent: ((Event) -> Unit)? = null,
    onCreateEvent: ((IngestItem) -> Unit)? = null,
) {
    var showTextDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // OCR 텍스트 정보
            Text(
                text = "📷 OCR 텍스트",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            
            Text(
                text = "제목: ${item.title ?: "(제목 없음)"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            
            Text(
                text = item.body.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTextDialog = true },
            )
            
            Text(
                text = "추출 시간: ${TimeFormatter.format(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
            )
            
            if (item.confidence != null) {
                Text(
                    text = "신뢰도: ${(item.confidence * 100).coerceIn(0.0, 100.0).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            
            // 연결된 이벤트 표시
            if (events.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "📅 추출된 일정 (${events.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        EventDetailRow(
                            event = event,
                            onUpdateEvent = onUpdateEvent,
                            onDeleteEvent = onDeleteEvent,
                        )
                    }
                }
            } else {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "추출된 일정 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                // 일정 생성 버튼
                if (onCreateEvent != null) {
                    Button(
                        onClick = { onCreateEvent(item) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "일정 추가",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("일정 생성")
                    }
                }
            }
        }
    }
    
    if (showTextDialog) {
        TextDetailDialog(
            title = "OCR 원본 텍스트",
            text = item.body.orEmpty(),
            onDismiss = { showTextDialog = false }
        )
    }
}

@Composable
private fun TextDetailDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = text.ifEmpty { "(텍스트 없음)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        dismissButton = null,
    )
}

@Composable
private fun EventDetailRow(
    event: Event,
    onEventClick: (Event) -> Unit = {},
    onUpdateEvent: ((Event) -> Unit)? = null,
    onDeleteEvent: ((Event) -> Unit)? = null,
) {
    var showDetailDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(Dimens.badgeCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXS)
        ) {
            // 제목 (아이콘과 함께)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Event,
                    contentDescription = "일정 아이콘",
                    modifier = Modifier.size(Dimens.iconSmall),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 시간 정보 (한 줄로)
            if (event.startAt != null || event.endAt != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.startAt != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = "시간 아이콘",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = TimeFormatter.format(event.startAt).split(" ").getOrNull(1) ?: TimeFormatter.format(event.startAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (event.endAt != null && event.startAt != null) {
                        Text(
                            text = "~",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = TimeFormatter.format(event.endAt).split(" ").getOrNull(1) ?: TimeFormatter.format(event.endAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 장소 정보
            if (event.location != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "위치 아이콘",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    if (showDetailDialog) {
        EventDetailDialog(
            event = event,
            onDismiss = { showDetailDialog = false },
            onUpdateEvent = onUpdateEvent,
            onDeleteEvent = onDeleteEvent,
        )
    }
}

@Composable
private fun EventDetailDialog(
    event: Event,
    onDismiss: () -> Unit,
    onUpdateEvent: ((Event) -> Unit)? = null,
    onDeleteEvent: ((Event) -> Unit)? = null,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("일정 상세 정보")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 제목
                Column {
                    Text(
                        text = "제목",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                
                                HorizontalDivider()
                
                // 시작 시간
                if (event.startAt != null) {
                    Column {
                        Text(
                            text = "시작 시간",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = TimeFormatter.format(event.startAt),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                
                // 종료 시간
                if (event.endAt != null) {
                    Column {
                        Text(
                            text = "종료 시간",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = TimeFormatter.format(event.endAt),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                
                // 장소
                if (event.location != null) {
                    Column {
                        Text(
                            text = "장소",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                
                // 본문
                if (event.body != null && event.body.isNotBlank()) {
                    Column {
                        Text(
                            text = "본문",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = event.body,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                
                // 상태
                if (event.status != null) {
                    Column {
                        Text(
                            text = "상태",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = event.status,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                
                // 출처 정보
                if (event.sourceType != null) {
                    Column {
                        Text(
                            text = "출처",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = event.sourceType,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onUpdateEvent != null) {
                    Button(onClick = { showEditDialog = true }) {
                        Text("수정")
                    }
                }
                if (onDeleteEvent != null) {
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("삭제")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        },
        dismissButton = null,
    )
    
    if (showEditDialog && onUpdateEvent != null) {
        EventEditDialog(
            event = event,
            onDismiss = { showEditDialog = false },
            onSave = { updatedEvent: Event ->
                onUpdateEvent(updatedEvent)
                showEditDialog = false
                onDismiss()
            }
        )
    }
    
    if (showDeleteConfirmDialog && onDeleteEvent != null) {
        DeleteConfirmDialog(
            eventTitle = event.title,
            onConfirm = {
                onDeleteEvent(event)
                showDeleteConfirmDialog = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    eventTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 삭제") },
        text = {
            Text("'${eventTitle}' 일정을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}


@Composable
private fun ScrollablePicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.size - 1))
    val scope = rememberCoroutineScope()
    
    // 선택된 항목이 변경되면 스크롤
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < items.size) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    
    // 스크롤 감지 - 스크롤이 멈췄을 때 중앙 항목 선택
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            delay(150) // 스크롤 완료 후 약간의 지연
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerY = viewportHeight / 2
            
            if (visibleItems.isNotEmpty()) {
                // 중앙에 가까운 항목 찾기 (여백 제외)
                val dataItems = visibleItems.filter { it.index > 0 && it.index <= items.size }
                if (dataItems.isNotEmpty()) {
                    val centerItem = dataItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        abs(itemCenter - centerY)
                    }
                    
                    centerItem?.let {
                        val newIndex = it.index - 1 // 여백 제외 (첫 번째 item이 여백이므로)
                        if (newIndex >= 0 && newIndex < items.size && newIndex != selectedIndex) {
                            onSelected(newIndex)
                        }
                    }
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단 여백
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
            
            items(items.size) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { 
                            scope.launch {
                                listState.animateScrollToItem(index + 1) // 여백 포함
                            }
                            onSelected(index)
                        }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // 하단 여백
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
        
        // 중앙 선택 표시
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // 중앙 선택 표시용 빈 Box
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PushNotificationAnalysisCard(
    mainViewModel: MainViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(mainViewModel.checkNotificationListenerPermission()) }
    var isLoading by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<PushNotificationStats?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000) // 7일 전 기본값
    )
    
    // 권한 상태 확인 (앱이 포그라운드로 돌아올 때)
    LaunchedEffect(Unit) {
        hasPermission = mainViewModel.checkNotificationListenerPermission()
        if (hasPermission) {
            // 권한이 있으면 통계 로드
            scope.launch {
                isLoading = true
                try {
                    stats = mainViewModel.getPushNotificationStats()
                } catch (e: Exception) {
                    android.util.Log.e("PushNotificationAnalysis", "통계 로드 실패", e)
                } finally {
                    isLoading = false
                }
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "푸시 알림 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            
            if (!hasPermission) {
                Text(
                    text = "푸시 알림을 수집하려면 알림 접근 권한이 필요합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Button(
                    onClick = {
                        mainViewModel.openNotificationListenerSettings()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("권한 설정 열기")
                }
            } else {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (stats != null) {
                    Text(
                        text = "총 ${stats!!.totalCount}개의 푸시 알림",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    
                    // 앱별 통계 + 저장 제외 액션
                    if (stats!!.appStatistics.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "앱별 알림 수",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        stats!!.appStatistics.take(10).forEach { appStat ->
                            val isExcluded = PushNotificationFilterSettings.isPackageExcluded(context, appStat.packageName)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = appStat.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = appStat.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                    if (isExcluded) {
                                        Text(
                                            text = "저장 제외 중",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        val newExclude = !isExcluded
                                        mainViewModel.togglePushNotificationExclusion(
                                            packageName = appStat.packageName,
                                            exclude = newExclude,
                                        )
                                        // 설정 변경 후 통계 갱신
                                        scope.launch {
                                            isLoading = true
                                            try {
                                                stats = mainViewModel.getPushNotificationStats()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (isExcluded)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(
                                        text = if (isExcluded) "다시 저장하기" else "저장 안 하기",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                    
                    // 시간대별 통계
                    if (stats!!.hourlyStatistics.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "시간대별 알림 수",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        stats!!.hourlyStatistics.forEach { hourlyStat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${hourlyStat.hour}시",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${hourlyStat.count}개",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("기간별 분석")
                    }
                } else {
                    Text(
                        text = "아직 수집된 푸시 알림이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
    
    // 날짜 선택 다이얼로그
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { startTime ->
                            val endTime = System.currentTimeMillis()
                            scope.launch {
                                isLoading = true
                                try {
                                    stats = mainViewModel.getPushNotificationStats(startTime, endTime)
                                } catch (e: Exception) {
                                    android.util.Log.e("PushNotificationAnalysis", "통계 로드 실패", e)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

