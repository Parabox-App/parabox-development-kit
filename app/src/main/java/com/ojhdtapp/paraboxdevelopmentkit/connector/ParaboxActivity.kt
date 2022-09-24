package com.ojhdtapp.paraboxdevelopmentkit.connector

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

abstract class ParaboxActivity<T>(private val serviceClass: Class<T>) : ComponentActivity() {
    abstract fun onParaboxServiceConnected()
    abstract fun onParaboxServiceDisconnected()
    abstract fun onParaboxServiceStateChanged(state: Int, message: String?)
    abstract fun customHandleMessage(msg: Message, metadata: ParaboxMetadata)

    var paraboxService: Messenger? = null
    private lateinit var client: Messenger
    private lateinit var paraboxServiceConnection: ServiceConnection

    var deferredMap = mutableMapOf<Long, CompletableDeferred<ParaboxResult>>()

    /*/
    推荐于onStart运行
     */
    fun bindParaboxService() {
        val intent = Intent(this, serviceClass)
        startService(intent)
        bindService(
            intent,
            paraboxServiceConnection, BIND_AUTO_CREATE
        )
    }

    /*/
    推荐于onStop执行
     */
    fun stopParaboxService() {
        if (paraboxService != null) {
            unbindService(paraboxServiceConnection)
        }
    }

    fun getState() {
        sendCommand(
            command = ParaboxKey.COMMAND_GET_STATE,
            onResult = {
                if (it is ParaboxResult.Success) {
                    val state = it.obj.getInt("state")
                    onParaboxServiceStateChanged(state, null)
                }
            }
        )
    }

    fun sendCommand(
        command: Int,
        extra: Bundle = Bundle(),
        timeoutMillis: Long = 3000,
        onResult: (ParaboxResult) -> Unit
    ) {
        lifecycleScope.launch {
            val timestamp = System.currentTimeMillis()
            try {
                withTimeout(timeoutMillis) {
                    val deferred = CompletableDeferred<ParaboxResult>()
                    deferredMap[timestamp] = deferred
                    coreSendCommand(timestamp, command, extra)
                    Log.d("parabox", "command sent")
                    deferred.await().also {
                        Log.d("parabox", "successfully complete")
                        onResult(it)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                deferredMap[timestamp]?.cancel()
                onResult(
                    ParaboxResult.Fail(
                        command,
                        timestamp,
                        ParaboxKey.ERROR_TIMEOUT
                    )
                )
            } catch (e: RemoteException) {
                deferredMap[timestamp]?.cancel()
                onResult(
                    ParaboxResult.Fail(
                        command,
                        timestamp,
                        ParaboxKey.ERROR_DISCONNECTED
                    )
                )
            }
        }
    }

    /*/
    what: 指令
    arg1: 客户端类型
    arg2: 指令类型
    obj: Bundle
     */
    private fun coreSendCommand(timestamp: Long, command: Int, extra: Bundle = Bundle()) {
        if (paraboxService == null) {
            deferredMap[timestamp]?.complete(
                ParaboxResult.Fail(
                    command, timestamp,
                    ParaboxKey.ERROR_DISCONNECTED
                )
            )
        } else {
            val msg = Message.obtain(
                null,
                command,
                ParaboxKey.CLIENT_CONTROLLER,
                ParaboxKey.TYPE_COMMAND,
                extra.apply {
                    putParcelable(
                        "metadata", ParaboxMetadata(
                            commandOrRequest = command,
                            timestamp = timestamp,
                            sender = ParaboxKey.CLIENT_CONTROLLER
                        )
                    )
                }).apply {
                replyTo = client
            }
            paraboxService!!.send(msg)
        }
    }

    fun sendRequestResponse(
        isSuccess: Boolean,
        metadata: ParaboxMetadata,
        extra: Bundle = Bundle(),
        errorCode: Int? = null
    ) {
        if (isSuccess) {
            ParaboxResult.Success(
                command = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                obj = extra,
            )
        } else {
            ParaboxResult.Fail(
                command = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                errorCode = errorCode!!
            )
        }.also {
            deferredMap[metadata.timestamp]?.complete(it)
//            coreSendCommandResponse(isSuccess, metadata, it)
        }
    }

    private fun coreSendRequestResponse(
        isSuccess: Boolean,
        metadata: ParaboxMetadata,
        result: ParaboxResult,
        extra: Bundle = Bundle()
    ) {
        val errorCode = if (!isSuccess) {
            (result as ParaboxResult.Fail).errorCode
        } else 0
        val msg = Message.obtain(
            null,
            metadata.commandOrRequest,
            ParaboxKey.CLIENT_CONTROLLER,
            ParaboxKey.TYPE_REQUEST,
            extra.apply {
                putBoolean("isSuccess", isSuccess)
                putParcelable("metadata", metadata)
                putInt("errorCode", errorCode)
            }).apply {
            replyTo = client
        }
        Log.d("parabox", "send back to service")
        paraboxService?.send(msg)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = Messenger(ParaboxServiceHandler())
        paraboxServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
                paraboxService = Messenger(p1)
                onParaboxServiceConnected()
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                paraboxService = null
                onParaboxServiceDisconnected()
            }
        }
    }

    inner class ParaboxServiceHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val obj = msg.obj as Bundle
            when (msg.arg2) {
                ParaboxKey.TYPE_REQUEST -> {
                    Log.d("parabox", "request received")
                    lifecycleScope.launch {
                        try {
                            obj.classLoader = ParaboxMetadata::class.java.classLoader
                            val metadata =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    obj.getParcelable("metadata", ParaboxMetadata::class.java)!!
                                } else {
                                    obj.getParcelable<ParaboxMetadata>("metadata")!!
                                }
                            val deferred =
                                CompletableDeferred<ParaboxResult>()
                            deferredMap[metadata.timestamp] = deferred

                            // 指令种类判断
                            when (msg.what) {
                                else -> customHandleMessage(msg, metadata)
                            }

                            deferred.await().also {
                                Log.d("parabox", "first deferred completed")
                                val resObj = if (it is ParaboxResult.Success) {
                                    it.obj
                                } else Bundle()
                                coreSendRequestResponse(
                                    it is ParaboxResult.Success,
                                    metadata,
                                    it,
                                    resObj
                                )
                            }
                        } catch (e: RemoteException) {
                            e.printStackTrace()
                        } catch (e: ClassNotFoundException) {
                            e.printStackTrace()
                        }
                    }
                }

                ParaboxKey.TYPE_COMMAND -> {
                    try {
                        obj.classLoader = ParaboxMetadata::class.java.classLoader
                        val metadata = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            obj.getParcelable("metadata", ParaboxMetadata::class.java)!!
                        } else {
                            obj.getParcelable<ParaboxMetadata>("metadata")!!
                        }
                        val isSuccess = obj.getBoolean("isSuccess")
                        val errorCode = obj.getInt("errorCode")
                        val result = if (isSuccess) {
                            ParaboxResult.Success(
                                command = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                obj = obj
                            )
                        } else {
                            ParaboxResult.Fail(
                                command = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                errorCode = errorCode
                            )
                        }
                        Log.d("parabox", "try complete second deferred")
                        deferredMap[metadata.timestamp]?.complete(result)
                    } catch (e: NullPointerException) {
                        e.printStackTrace()
                    } catch (e: ClassNotFoundException) {
                        e.printStackTrace()
                    }
                }

                ParaboxKey.TYPE_NOTIFICATION -> {
                    when (msg.what) {
                        ParaboxKey.NOTIFICATION_STATE_UPDATE -> {
                            val state = obj.getInt("state", ParaboxKey.STATE_ERROR)
                            val message = obj.getString("message", "")
                            onParaboxServiceStateChanged(state, message)
                        }
                    }
                }
            }
            super.handleMessage(msg)
        }
    }
}