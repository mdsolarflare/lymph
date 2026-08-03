package com.example.examplekmpapplication

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform