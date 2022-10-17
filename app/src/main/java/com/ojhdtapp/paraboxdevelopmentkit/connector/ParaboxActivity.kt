package com.ojhdtapp.paraboxdevelopmentkit.connector

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 与ParaboxService通信的抽象Activity
 * @param T ParaboxService实现类泛型
 * @param serviceClass ParaboxService实现类Class
 * @see ParaboxService
 * @since 1.0.0
 */
abstract class ParaboxActivity<T>(private val serviceClass: Class<T>) : ComponentActivity() {
    /**
     * 与主端建立连接时调用
     * @since 1.0.0
    */
    abstract fun onParaboxServiceConnected()

    /**
     * 与主端断开连接时调用
     * @since 1.0.0
    */
    abstract fun onParaboxServiceDisconnected()
    /**
       * 核心服务状态变化时调用。
     * @param state 核心服务状态。预定义状态可参考 [ParaboxKey]
     * @param message 附加信息，可用于 toast 提示。使用 [getState] 获取状态时为 Null
     * @since 1.0.0
     * @see ParaboxKey
     * @see getState
     */
    abstract fun onParaboxServiceStateChanged(state: Int, message: String?)
    /**
     * msg.what未匹配预定义Key时屌用，用于自定义Request，Notification。
     *
     * 具体可参阅 https://docs.parabox.ojhdt.dev/developer/#command
     * @param msg 从核心服务接收到的消息。
     * - msg.what 预定义Key
     * - msg.arg1 发送客户端类型，必定为SERVICE
     * - msg.arg2 消息类型，可选值为REQUEST，COMMAND或NOTIFICATION
     * - msg.obj 附加信息，可转换为Bundle类型
     * @param metadata 消息元数据。
     * @since 1.0.0
     * @see ParaboxKey
     */
    abstract fun customHandleMessage(msg: Message, metadata: ParaboxMetadata)

    var paraboxService: Messenger? = null
    private lateinit var client: Messenger
    private lateinit var paraboxServiceConnection: ServiceConnection

    var deferredMap = mutableMapOf<String, CompletableDeferred<ParaboxResult>>()

    /**
     * 与核心服务连接。建议于 onStart 调用
     * @since 1.0.0
     */
    fun bindParaboxService() {
        val intent = Intent(this, serviceClass)
        startService(intent)
        bindService(
            intent,
            paraboxServiceConnection, BIND_AUTO_CREATE
        )
    }

    /**
     * 与核心服务断开连接。建议于 onStop 调用
     * @since 1.0.0
     */
    fun unbindParaboxService() {
        if (paraboxService != null) {
            unbindService(paraboxServiceConnection)
        }
    }

    /**
     * 预定义COMMAND，用于启动核心服务。将触发 [ParaboxService.onStartParabox] 回调。
     *
     * 仅核心服务状态为 STOP 时有效
     * @param onResult 结果回调，于 [sendCommand] 超时或核心服务返回结果时调用
     * @since 1.0.0
     * @see ParaboxService.onStartParabox
     * @see ParaboxResult
     * @see ParaboxKey
     * @see sendCommand
     */
    fun startParaboxService(onResult: (ParaboxResult) -> Unit) {
        sendCommand(
            command = ParaboxKey.COMMAND_START_SERVICE,
            onResult = onResult
        )
    }
    /**
     * 预定义COMMAND，用于停止核心服务。将触发 [ParaboxService.onStopParabox] 回调。
     *
     * 仅核心服务状态为 RUNNING 时有效
     * @param onResult 结果回调，于 [sendCommand] 超时或核心服务返回结果时调用
     * @since 1.0.0
     * @see ParaboxService.onStopParabox
     * @see ParaboxResult
     * @see ParaboxKey
     * @see sendCommand
     */
    fun stopParaboxService(onResult: (ParaboxResult) -> Unit) {
        sendCommand(
            command = ParaboxKey.COMMAND_STOP_SERVICE,
            onResult = onResult,
        )
    }
    /**
     * 预定义COMMAND，用于停止核心服务。将触发 [ParaboxService.onStopParabox] 回调，无视核心服务状态
     * @param onResult 结果回调，于 [sendCommand] 超时或核心服务返回结果时调用
     * @since 1.0.0
     * @see ParaboxService.onStopParabox
     * @see ParaboxResult
     * @see ParaboxKey
     * @see sendCommand
     */
    fun forceStopParaboxService(onResult: (ParaboxResult) -> Unit) {
        sendCommand(
            command = ParaboxKey.COMMAND_FORCE_STOP_SERVICE,
            onResult = onResult
        )
    }

    /**
     * 预定义COMMAND，用于获取核心服务状态。将触发 [onParaboxServiceStateChanged] 回调
     * @since 1.0.0
     * @see sendCommand
     */
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

    /**
     * 向ParaboxService发送一条命令（COMMAND）。常用于需要确定得到回复才能继续进行的逻辑。如消息发送/接收，更新配置等。
     *
     * 自带回送验证及超时机制。保证每一次通信都必然在超时时间内触发 onResult 回调。
     *
     * 如需自定义Command，可参阅 https://docs.parabox.ojhdt.dev/developer/#command
     * @param command 命令类型
     * @param extra 附加数据,将作为Message的obj传递
     * @param timeoutMillis 触发超时的时间，单位为毫秒
     * @param onResult 结果回调，超时或核心服务返回结果时调用
     * @since 1.0.0
     * @see ParaboxResult
     * @see ParaboxKey
     */
    fun sendCommand(
        command: Int,
        extra: Bundle = Bundle(),
        timeoutMillis: Long = 3000,
        onResult: (ParaboxResult) -> Unit
    ) {
        lifecycleScope.launch {
            val timestamp = System.currentTimeMillis()
            val key = "${timestamp}${ParaboxUtil.getRandomNumStr(8)}"
            try {
                withTimeout(timeoutMillis) {
                    val deferred = CompletableDeferred<ParaboxResult>()
                    deferredMap[key] = deferred
                    coreSendCommand(timestamp, key, command, extra)
                    deferred.await().also {
                        onResult(it)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                deferredMap[key]?.cancel()
                onResult(
                    ParaboxResult.Fail(
                        command,
                        timestamp,
                        ParaboxKey.ERROR_TIMEOUT
                    )
                )
            } catch (e: RemoteException) {
                deferredMap[key]?.cancel()
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
    /**
     * command 较底层实现。请勿直接调用
     * @since 1.0.0
     * @see sendCommand
     */
    private fun coreSendCommand(
        timestamp: Long,
        key: String,
        command: Int,
        extra: Bundle = Bundle()
    ) {
        if (paraboxService == null) {
            deferredMap[key]?.complete(
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
                            sender = ParaboxKey.CLIENT_CONTROLLER,
                            key = key
                        )
                    )
                }).apply {
                replyTo = client
            }
            paraboxService!!.send(msg)
        }
    }

    /**
     * 发送 REQUEST 的回送验证。
     *
     * 对于自定义 REQUEST，务必在处理 REQUEST 后调用，否则将导致原 REQUEST 超时。
     *
     * 可参阅 https://docs.parabox.ojhdt.dev/developer/#_10 获取有关通讯机制的更多信息
     * @param isSuccess 本次Request是否正常处理
     * @param metadata 消息元数据
     * @param extra 附加数据，isSuccess 为 true 时，作为 ParaboxResult.Success 的 obj 传递
     * @param errorCode 错误码，isSuccess 为 false 时，作为 ParaboxResult.Fail 的 errorCode 传递
     * @since 1.0.0
     * @see ParaboxService.sendRequest
     * @see ParaboxResult
     */
    fun sendRequestResponse(
        isSuccess: Boolean,
        metadata: ParaboxMetadata,
        extra: Bundle = Bundle(),
        errorCode: Int? = null
    ) {
        if (isSuccess) {
            ParaboxResult.Success(
                commandOrRequest = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                obj = extra,
            )
        } else {
            ParaboxResult.Fail(
                commandOrRequest = metadata.commandOrRequest,
                timestamp = metadata.timestamp,
                errorCode = errorCode!!
            )
        }.also {
            deferredMap[metadata.key]?.complete(it)
//            coreSendCommandResponse(isSuccess, metadata, it)
        }
    }

    /**
     * request 的较底层实现，请勿直接调用
     * @since 1.0.0
     * @see sendRequestResponse
     */
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
                            deferredMap[metadata.key] = deferred

                            // 指令种类判断
                            when (msg.what) {
                                else -> customHandleMessage(msg, metadata)
                            }

                            deferred.await().also {
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
                                commandOrRequest = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                obj = obj
                            )
                        } else {
                            ParaboxResult.Fail(
                                commandOrRequest = metadata.commandOrRequest,
                                timestamp = metadata.timestamp,
                                errorCode = errorCode
                            )
                        }
                        deferredMap[metadata.key]?.complete(result)
                    } catch (e: NullPointerException) {
                        e.printStackTrace()
                    } catch (e: ClassNotFoundException) {
                        e.printStackTrace()
                    }
                }

                ParaboxKey.TYPE_NOTIFICATION -> {
                    val timestamp = obj.getLong("timestamp")
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