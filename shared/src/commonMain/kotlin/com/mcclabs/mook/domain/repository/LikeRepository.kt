package com.mcclabs.mook.domain.repository

import com.mcclabs.mook.domain.model.MatchResult

/** Beğeni durumunu sunucuda belirler; hata ve iptal çağırana iletilir, yeniden deneme kuyruğu kullanılmaz. */
interface LikeRepository {
    suspend fun setLiked(profileId: String, isLiked: Boolean): MatchResult
}
