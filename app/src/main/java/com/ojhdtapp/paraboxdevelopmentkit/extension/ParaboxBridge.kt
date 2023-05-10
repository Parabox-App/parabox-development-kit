package com.example.extensionlibrary

import com.ojhdtapp.paraboxdevelopmentkit.model.ReceiveMessage

interface ParaboxBridge {
    fun receiveMessage(message: ReceiveMessage)

    fun recallMessage()
}