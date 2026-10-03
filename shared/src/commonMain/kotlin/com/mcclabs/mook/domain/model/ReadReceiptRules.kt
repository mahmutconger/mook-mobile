package com.mcclabs.mook.domain.model

/**
 * Okundu bilgisi (Premium ayrıcalığı) kuralları — saf fonksiyonlar.
 *
 * Karşı taraf sohbeti en son [peerLastReadAt] anında okuduysa, gönderenin o ana kadar
 * gönderilmiş EN SON mesajının altında "Görüldü" yazar (WhatsApp/Instagram deseni:
 * yalnızca tek bir etiket, her mesajda değil).
 */
object ReadReceiptRules {

    /**
     * "Görüldü" etiketinin gösterileceği mesajın kimliği; görülen mesaj yoksa `null`.
     * Yalnızca sunucuya ulaşmış (gönderilmiş) kendi mesajlarım dikkate alınır.
     */
    fun lastSeenOwnMessageId(messages: List<ChatMessage>, peerLastReadAt: Long?): String? {
        if (peerLastReadAt == null) return null
        return messages.lastOrNull { message ->
            message.isMine && !message.isSystem && message.status == MessageStatus.SENT && message.timestamp <= peerLastReadAt
        }?.id
    }
}
