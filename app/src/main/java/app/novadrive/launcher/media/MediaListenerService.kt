package app.novadrive.launcher.media

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import app.novadrive.launcher.NovaDriveApp

/**
 * Existe só para o Android liberar MediaSessionManager.getActiveSessions() ao launcher.
 * Não sobrescreve onNotificationPosted: o conteúdo das notificações nunca é lido.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        (application as NovaDriveApp).container.media.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        // Algumas centrais matam serviços para economizar RAM; pede para o sistema religar (API 24+).
        requestRebind(ComponentName(this, MediaListenerService::class.java))
    }
}
