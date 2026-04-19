package com.xremail.app

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.xremail.app.backend.service.AuthRepository
import com.xremail.app.backend.service.NetworkClient
import com.xremail.app.backend.service.TokenManager
import com.xremail.app.backend.service.GmailRepository
import com.xremail.app.backend.mock.MockEmailRepository
import com.xremail.app.tracking.FaceAttentionTracker
import com.xremail.app.tracking.GestureToActionMapper
import com.xremail.app.tracking.KeyboardGestureDispatcher
import com.xremail.app.tracking.SecondaryHandGestures
import com.xremail.app.tracking.TiltScrollController
import com.xremail.app.tracking.XrSessionManager
import com.xremail.app.ui.spatial.DisplayMode
import com.xremail.app.ui.spatial.DisplayModeRouter
import com.xremail.app.ui.feedback.GestureFeedbackOverlay
import com.xremail.app.ui.peripheral.EmulatorHelpHint
import com.xremail.app.ui.peripheral.EmulatorHelpOverlay
import com.xremail.app.ui.peripheral.GestureDebugBar
import com.xremail.app.ui.spatial.GlimmerEmailApp
import com.xremail.app.ui.spatial.InteractionTierRouter
import com.xremail.app.ui.theme.XREmailTheme
import com.xremail.app.viewmodel.EmailViewModel
import com.xremail.app.viewmodel.InteractionTier
import com.xremail.app.voice.GeminiLiveManager
import com.xremail.app.voice.TTSManager
import com.xremail.app.voice.VoiceCommandDispatcher
import com.xremail.app.voice.VoiceComposeManager

class MainActivity : ComponentActivity() {

    // ---------------------------------------------------------------------------
    // Backend wiring — swap USE_REAL_BACKEND to true once the server is running
    // ---------------------------------------------------------------------------

    private val USE_REAL_BACKEND = false
    private val BACKEND_URL = "http://10.0.2.2:8080/" // emulator → host loopback

    private lateinit var tokenManager: TokenManager
    private lateinit var authRepository: AuthRepository

    /** Set from [HeadsetEmailApp] so hardware keys reach [KeyboardGestureDispatcher]. */
    var keyboardDispatcher: KeyboardGestureDispatcher? = null

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Run emulator / keyboard shortcuts BEFORE super. Otherwise Compose or the
        // system may consume keys (e.g. H) and [KeyboardGestureDispatcher] never runs.
        if (keyboardDispatcher?.onKeyEvent(event) == true) {
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_MULTIPLE) {
            Log.w(
                "XrMailKeys",
                "Activity received action=${event.action} keyCode=${event.keyCode} unicode=${event.unicodeChar} chars='${event.characters}' scan=${event.scanCode}"
            )
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyboardDispatcher?.onKeyEvent(event) == true) {
            Log.w("XrMailKeys", "onKeyDown consumed keyCode=$keyCode")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyboardDispatcher?.onKeyEvent(event) == true) {
            Log.w("XrMailKeys", "onKeyUp consumed keyCode=$keyCode")
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        if (keyboardDispatcher?.onKeyEvent(event) == true) {
            Log.w("XrMailKeys", "dispatchKeyShortcutEvent consumed keyCode=${event.keyCode}")
            return true
        }
        return super.dispatchKeyShortcutEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tokenManager = TokenManager(applicationContext)

        val emailRepository = if (USE_REAL_BACKEND) {
            val api = NetworkClient.create(
                baseUrl = BACKEND_URL,
                tokenManager = tokenManager,
                debug = true,
            )
            authRepository = AuthRepository(api, tokenManager)
            GmailRepository(api)
        } else {
            // Phase 1: use mock data so the UI works without a running backend
            authRepository = AuthRepository(
                api = NetworkClient.create(BACKEND_URL, tokenManager),
                tokenManager = tokenManager,
            )
            MockEmailRepository()
        }

        setContent {
            XREmailTheme {
                XREmailApp(
                    viewModelFactory = EmailViewModel.Factory(emailRepository)
                )
            }
        }

        // Handle OAuth deep-link if the activity was launched via xrmail://auth/...
        intent?.let { handleOAuthIntent(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    /**
     * Processes the xrmail://auth/success or xrmail://auth/error deep link
     * that the Ktor backend sends after the Gmail OAuth flow completes.
     *
     * On success: saves the JWT via [AuthRepository] and reloads emails.
     * On error:   logs the reason (production would show a UI error state).
     */
    private fun handleOAuthIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "xrmail" || uri.host != "auth") return

        when (uri.path) {
            "/success" -> {
                val state = authRepository.handleCallback(uri)
                Log.i(TAG, "OAuth success — user: ${tokenManager.getUserEmail()}, state: $state")
            }
            "/error" -> {
                val reason = uri.getQueryParameter("reason") ?: "unknown"
                Log.e(TAG, "OAuth error: $reason")
            }
        }
    }

    companion object {
        private const val TAG = "XrMailAuth"
    }
}

@Composable
fun XREmailApp(viewModelFactory: EmailViewModel.Factory) {
    val displayMode = DisplayModeRouter.detect()

    when (displayMode) {
        DisplayMode.GLASSES_ADDITIVE -> GlimmerEmailApp()
        DisplayMode.HEADSET -> HeadsetEmailApp(viewModelFactory)
    }
}

@Composable
private fun HeadsetEmailApp(factory: EmailViewModel.Factory) {
    val viewModel: EmailViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ttsManager = remember { TTSManager(context) }
    val geminiLive = remember { GeminiLiveManager() }
    val voiceCompose = remember { VoiceComposeManager(geminiLive, ttsManager) }
    val voiceDispatcher = remember(viewModel) {
        VoiceCommandDispatcher(viewModel, ttsManager)
    }

    // Runtime permissions — mic for Gemini Live; XR tracking perms for hands/face.
    // SCENE_UNDERSTANDING is requested but does NOT gate hand tracking — denying it
    // no longer blocks [XrSessionManager.startAll] (previously broke all gestures).
    val xrTrackingPerms = remember {
        arrayOf(
            "android.permission.HAND_TRACKING",
            "android.permission.FACE_TRACKING",
            "android.permission.EYE_TRACKING_COARSE",
        )
    }
    val requiredPerms = remember {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            "android.permission.HAND_TRACKING",
            "android.permission.FACE_TRACKING",
            "android.permission.EYE_TRACKING_COARSE",
            "android.permission.SCENE_UNDERSTANDING",
        )
    }
    fun xrTrackingGranted(): Boolean = xrTrackingPerms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun allRequestedPermsGranted(): Boolean = requiredPerms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var xrInputReady by remember { mutableStateOf(xrTrackingGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        xrInputReady = xrTrackingGranted()
    }
    LaunchedEffect(Unit) {
        if (!allRequestedPermsGranted()) permissionLauncher.launch(requiredPerms)
    }

    // Connect / disconnect Gemini Live once the mic permission is settled.
    LaunchedEffect(micGranted) {
        if (micGranted) {
            geminiLive.setContextProvider { voiceDispatcher.currentContextSummary() }
            geminiLive.connect(scope)
        }
    }
    DisposableEffect(geminiLive) {
        onDispose { geminiLive.disconnect() }
    }

    // Route function calls the model emits into the ViewModel.
    LaunchedEffect(geminiLive, voiceDispatcher) {
        geminiLive.commands.collect { voiceDispatcher.dispatch(it) }
    }

    // Speak the AI summary whenever the selected email changes — the core
    // "can I hear it?" demo path. Keeps Gemini Live as the conversational layer
    // and uses on-device TTS for deterministic narration.
    LaunchedEffect(Unit) {
        viewModel.uiState
            .map { it.selectedEmail?.id to it.selectedEmail?.aiSummary }
            .distinctUntilChanged()
            .collect { (_, summary) ->
                if (!summary.isNullOrBlank()) ttsManager.speak(summary)
            }
    }

    // Keep the model grounded in what the user is looking at.
    LaunchedEffect(Unit) {
        viewModel.uiState
            .map { it.selectedEmail?.id }
            .distinctUntilChanged()
            .collect {
                geminiLive.sendContextUpdate(voiceDispatcher.currentContextSummary())
            }
    }

    DisposableEffect(ttsManager) {
        onDispose { ttsManager.shutdown() }
    }

    val faceTracker = remember { FaceAttentionTracker() }
    val handGestures = remember { SecondaryHandGestures() }
    val keyboardDispatcher = remember(viewModel, handGestures) {
        KeyboardGestureDispatcher(viewModel, handGestures)
    }
    val tiltScroll = remember { TiltScrollController() }
    val gestureMapper = remember(viewModel) { GestureToActionMapper(viewModel) }
    val xrSessionManager = remember { XrSessionManager(faceTracker, handGestures, tiltScroll) }

    val activity = context.findMainActivity()
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(activity, keyboardDispatcher) {
        activity?.keyboardDispatcher = keyboardDispatcher
        onDispose { activity?.keyboardDispatcher = null }
    }
    val composeView = LocalView.current
    DisposableEffect(composeView, keyboardDispatcher) {
        val listener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN || 
                event.action == KeyEvent.ACTION_UP || 
                event.action == KeyEvent.ACTION_MULTIPLE
            ) {
                if (event.action != KeyEvent.ACTION_UP) {
                    Log.w(
                        "XrMailKeys",
                        "Compose view received action=${event.action} keyCode=$keyCode unicode=${event.unicodeChar} chars='${event.characters}' scan=${event.scanCode}"
                    )
                }
                return@OnKeyListener keyboardDispatcher.onKeyEvent(event)
            }
            false
        }
        composeView.isFocusableInTouchMode = true
        composeView.requestFocus()
        composeView.setOnKeyListener(listener)
        onDispose { composeView.setOnKeyListener(null) }
    }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    val ttsState by ttsManager.playbackState.collectAsStateWithLifecycle()
    val ttsProgress by ttsManager.progress.collectAsStateWithLifecycle()
    val voiceSessionState by geminiLive.state.collectAsStateWithLifecycle()
    val voiceComposeState by voiceCompose.state.collectAsStateWithLifecycle()
    val voiceDraft by voiceCompose.draft.collectAsStateWithLifecycle()
    val tiltScrollDelta by tiltScroll.scrollDelta.collectAsStateWithLifecycle()

    val xrSession = LocalSession.current

    LaunchedEffect(xrSession, xrInputReady) {
        if (xrInputReady) {
            try {
                xrSessionManager.startAll(
                    session = xrSession,
                    contentResolver = context.contentResolver,
                    scope = scope,
                )
            } catch (t: Throwable) {
                Log.e("XrMail", "XR session start failed — voice will still work", t)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            xrSessionManager.stopAll()
        }
    }

    LaunchedEffect(handGestures, gestureMapper) {
        handGestures.gestures.collect { gesture ->
            gestureMapper.onGesture(gesture, viewModel.uiState.value.tier)
        }
    }

    LaunchedEffect(uiState.dominantHand) {
        handGestures.setDominantHand(uiState.dominantHand)
    }

    LaunchedEffect(faceTracker, ttsManager) {
        faceTracker.isAttentive.collect { attentive ->
            ttsManager.onAttentionChanged(attentive)
        }
    }

    // Optional: expand notifications when gazing from HUD. Do not auto-collapse when
    // look-away — that fights keyboard / on-screen controls when hand tracking is off.
    LaunchedEffect(faceTracker) {
        faceTracker.isGazingAtNotificationZone.collect { gazing ->
            val currentTier = viewModel.uiState.value.tier
            if (gazing && currentTier == InteractionTier.AMBIENT_HUD) {
                viewModel.expandToNotificationCards()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { composeKeyEvent ->
                val native = composeKeyEvent.nativeKeyEvent
                if (native.action == KeyEvent.ACTION_DOWN || 
                    native.action == KeyEvent.ACTION_UP || 
                    native.action == KeyEvent.ACTION_MULTIPLE
                ) {
                    if (native.action != KeyEvent.ACTION_UP) {
                        Log.w(
                            "XrMailKeys",
                            "Compose preview received action=${native.action} keyCode=${native.keyCode} unicode=${native.unicodeChar} chars='${native.characters}' scan=${native.scanCode}"
                        )
                    }
                    keyboardDispatcher.onKeyEvent(native)
                } else {
                    false
                }
            }
    ) {
        InteractionTierRouter(
            uiState = uiState,
            prioritySortedEmails = viewModel.prioritySortedEmails(),
            ttsState = ttsState,
            ttsProgress = ttsProgress,
            tiltScrollDelta = tiltScrollDelta,
            voiceSessionState = voiceSessionState,
            voiceComposeState = voiceComposeState,
            voiceDraft = voiceDraft,
            onExpandToNotifications = viewModel::expandToNotificationCards,
            onCollapseFromNotifications = viewModel::collapseFromNotificationCards,
            onExpandToTriage = viewModel::expandToTriage,
            onCollapseToHud = viewModel::collapseToHud,
            onExpandToFocus = viewModel::expandToFocus,
            onCollapseToTriage = viewModel::collapseToTriage,
            onEmailSelected = viewModel::selectEmail,
            onOpenFromNotification = viewModel::openFromNotification,
            onCategorySelected = viewModel::filterByCategory,
            onToggleAiSummary = viewModel::toggleAiSummary,
            onReply = viewModel::startCompose,
            onArchive = viewModel::archiveSelected,
            onArchiveEmail = viewModel::archiveEmail,
            onSnooze = viewModel::snoozeSelected,
            onSnoozeEmail = viewModel::snoozeEmail,
            onForward = viewModel::forwardSelected,
            onSend = viewModel::sendDraft,
            onCancelCompose = viewModel::cancelCompose,
            onDismissToast = viewModel::dismissToast,
        )

        if (uiState.showEmulatorHelp) {
            EmulatorHelpOverlay(
                currentTier = uiState.tier,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            GestureDebugBar(
                currentTier = uiState.tier,
                viewModel = viewModel,
                handGestures = handGestures,
            )
            if (!uiState.showEmulatorHelp) {
                EmulatorHelpHint(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }

        GestureFeedbackOverlay(gestures = handGestures.gestures)
    }
}

private tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}
