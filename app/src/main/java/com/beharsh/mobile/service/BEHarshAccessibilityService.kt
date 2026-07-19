package com.beharsh.mobile.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.beharsh.mobile.data.SettingsRepository
import com.beharsh.mobile.model.AppMode

class BEHarshAccessibilityService : AccessibilityService() {

    private lateinit var repo: SettingsRepository

    override fun onServiceConnected() {
        repo = SettingsRepository(this)
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            packageNames = arrayOf(
                "com.whatsapp",
                "com.google.android.youtube",
                "com.android.settings"
            )
            notificationTimeout = 50
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val settings = repo.load()
        when (pkg) {
            "com.whatsapp" -> handleWhatsApp(
                settings.features.waBlockStatus,
                settings.features.waBlockChannels
            )
            "com.google.android.youtube" -> handleYouTube(settings)
            "com.android.settings" -> {
                if (settings.appMode == AppMode.STRICT) {
                    OverlayService.show(this)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun handleWhatsApp(blockStatus: Boolean, blockChannels: Boolean) {
        if (!blockStatus && !blockChannels) return
        val root = rootInActiveWindow ?: return
        try {
            if (blockStatus && containsViewId(root, "com.whatsapp:id/status_container")) {
                performGlobalAction(GLOBAL_ACTION_HOME); return
            }
            if (blockChannels && containsViewId(root, "com.whatsapp:id/channels_container")) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } finally {
            root.recycle()
        }
    }

    private fun handleYouTube(settings: com.beharsh.mobile.model.Settings) {
        if (!settings.features.youtubeEngine) return
        val root = rootInActiveWindow ?: return
        try {
            if (settings.features.ytBlockShorts &&
                (containsViewId(root, "com.google.android.youtube:id/reel_player_page_container") ||
                        containsText(root, "Shorts"))
            ) {
                performGlobalAction(GLOBAL_ACTION_HOME); return
            }
            if (settings.features.ytBlockFeed &&
                containsViewId(root, "com.google.android.youtube:id/browse_feed_container")
            ) {
                performGlobalAction(GLOBAL_ACTION_HOME); return
            }
            val videoId = extractCurrentVideoId(root)
            val channelName = extractCurrentChannelName(root)
            if (videoId != null || channelName != null) {
                val videoAllowed = videoId != null && settings.whitelistedVideos.contains(videoId)
                val channelAllowed = channelName != null && settings.whitelistedChannels.any {
                    channelName.contains(it, ignoreCase = true)
                }
                if (!videoAllowed && !channelAllowed) {
                    startService(Intent(this, VideoGateService::class.java).apply {
                        putExtra("video_id", videoId)
                        putExtra("channel_name", channelName)
                    })
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun containsViewId(node: AccessibilityNodeInfo, viewId: String): Boolean {
        val results = node.findAccessibilityNodeInfosByViewId(viewId)
        val found = results.isNotEmpty()
        results.forEach { it.recycle() }
        return found
    }

    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
        val results = node.findAccessibilityNodeInfosByText(text)
        val found = results.isNotEmpty()
        results.forEach { it.recycle() }
        return found
    }

    private fun extractCurrentVideoId(root: AccessibilityNodeInfo): String? {
        val urlNodes = root.findAccessibilityNodeInfosByText("youtube.com/watch")
        if (urlNodes.isNotEmpty()) {
            val url = urlNodes.firstOrNull()?.text?.toString() ?: ""
            urlNodes.forEach { it.recycle() }
            val id = url.substringAfter("v=", "").substringBefore("&", "").substringBefore(" ", "")
            if (id.length in 8..12) return id
        }
        val shortNodes = root.findAccessibilityNodeInfosByText("youtu.be/")
        if (shortNodes.isNotEmpty()) {
            val url = shortNodes.firstOrNull()?.text?.toString() ?: ""
            shortNodes.forEach { it.recycle() }
            val id = url.substringAfter("youtu.be/", "").substringBefore("?", "").substringBefore(" ", "")
            if (id.length in 8..12) return id
        }
        return null
    }

    private fun extractCurrentChannelName(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/channel_name")
        val name = nodes.firstOrNull()?.text?.toString()?.takeIf { it.isNotEmpty() }
        nodes.forEach { it.recycle() }
        return name
    }

    override fun onInterrupt() {}
}
