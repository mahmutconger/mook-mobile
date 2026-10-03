package com.mcclabs.mook

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * RevenueCat webhook'unun (`functions/src/revenuecatWebhook.ts`) gönderdiği abonelik yaşam
 * döngüsü VERİ mesajlarını dinler ve yerel bildirim olarak gösterir.
 *
 * Veri-yalnızca mesajlar `onMessageReceived()`'i uygulama ön planda, arka planda veya
 * kapalıyken HER durumda tetikler. Desteklenen türler:
 * - `pending_purchase_confirmed`: onay bekleyen ödeme onaylandı (Gereksinim 1.12).
 * - `billing_issue`: ödeme alınamadı — dokunulduğunda mağazanın abonelik yönetimi açılır.
 * - `subscription_expired`: abonelik sona erdi / plan düştü — dokunulduğunda uygulama açılır.
 *
 * Başlık ve metin sunucudan (Türkçe) gelir; eksikse buradaki Türkçe varsayılanlar kullanılır.
 */
class RevenueCatWebhookService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        when (data["type"]) {
            TYPE_PENDING_PURCHASE_CONFIRMED -> showNotification(
                notificationId = PENDING_PURCHASE_NOTIFICATION_ID,
                title = data["title"] ?: PENDING_TITLE_TR,
                body = data["body"] ?: PENDING_BODY_TR,
                contentIntent = launchAppIntent(),
            )
            TYPE_BILLING_ISSUE -> showNotification(
                notificationId = BILLING_ISSUE_NOTIFICATION_ID,
                title = data["title"] ?: BILLING_ISSUE_TITLE_TR,
                body = data["body"] ?: BILLING_ISSUE_BODY_TR,
                contentIntent = manageSubscriptionIntent(),
            )
            TYPE_SUBSCRIPTION_EXPIRED -> showNotification(
                notificationId = EXPIRATION_NOTIFICATION_ID,
                title = data["title"] ?: EXPIRED_TITLE_TR,
                body = data["body"] ?: EXPIRED_BODY_TR,
                contentIntent = launchAppIntent(),
            )
        }
    }

    private fun showNotification(notificationId: Int, title: String, body: String, contentIntent: PendingIntent?) {
        ensureNotificationChannel()

        // Android 13+ bildirim izni verilmemişse sessizce vazgeç — izin burada istenemez.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    /** Uygulamayı (varsa mevcut görevi öne getirerek) açan intent. */
    private fun launchAppIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return null
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_LAUNCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Google Play'in bu uygulamaya ait abonelik yönetimi sayfası (ödeme yöntemini güncellemek için). */
    private fun manageSubscriptionIntent(): PendingIntent {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_MANAGE_SUBSCRIPTION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME_TR,
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val TYPE_PENDING_PURCHASE_CONFIRMED = "pending_purchase_confirmed"
        const val TYPE_BILLING_ISSUE = "billing_issue"
        const val TYPE_SUBSCRIPTION_EXPIRED = "subscription_expired"

        const val CHANNEL_ID = "billing_updates"
        const val CHANNEL_NAME_TR = "Ödeme Bildirimleri"

        const val PENDING_TITLE_TR = "Ödemeniz onaylandı"
        const val PENDING_BODY_TR = "Premium aktif! Yeni avantajların keyfini çıkar."
        const val BILLING_ISSUE_TITLE_TR = "Ödemen alınamadı"
        const val BILLING_ISSUE_BODY_TR = "Aboneliğinin kesintiye uğramaması için ödeme yöntemini güncelle."
        const val EXPIRED_TITLE_TR = "Aboneliğin sona erdi"
        const val EXPIRED_BODY_TR = "Artık Ücretsiz plandasın. Dilediğin zaman yeniden abone olabilirsin."

        const val PENDING_PURCHASE_NOTIFICATION_ID = 4821
        const val BILLING_ISSUE_NOTIFICATION_ID = 4822
        const val EXPIRATION_NOTIFICATION_ID = 4823
        const val REQUEST_CODE_LAUNCH = 4830
        const val REQUEST_CODE_MANAGE_SUBSCRIPTION = 4831
    }
}
