package io.github.takusan23.kotlinmultiplatformproject

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform