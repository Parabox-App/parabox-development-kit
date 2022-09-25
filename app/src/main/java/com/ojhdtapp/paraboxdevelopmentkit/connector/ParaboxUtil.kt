package com.ojhdtapp.paraboxdevelopmentkit.connector

object ParaboxUtil {
    fun getRandomNumStr(length: Int): String {
        return StringBuilder()
            .apply {
                repeat(length) {
                    append((0..9).random())
                }
            }
            .toString()
    }
}