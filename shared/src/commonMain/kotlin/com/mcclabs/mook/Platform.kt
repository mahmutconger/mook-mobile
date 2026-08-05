package com.mcclabs.mook

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform