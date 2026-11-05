package com.hkm.carlauncher.media

import android.service.notification.NotificationListenerService

/**
 * Does nothing itself. Android only lets an app read other apps' media sessions (title, artwork,
 * controls) while the user has enabled a notification listener belonging to that app.
 */
class LauncherNotificationListener : NotificationListenerService()
