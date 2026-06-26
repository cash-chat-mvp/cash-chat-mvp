package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Swift(LiteRT-LM Metal) 가 구현하는 저수준 콜백 브릿지.
 *
 * `LocalLlmEngine.generate()` 는 Kotlin `Flow` 를 돌려줘야 하는데 Swift 에서 Kotlin `Flow`/`callbackFlow`
 * 를 직접 만들 수 없다. 그래서 Swift 는 토큰 콜백만 노출하고, iosMain 의 [SwiftBackedLocalLlmEngine]
 * 어댑터가 이를 `callbackFlow`/`suspendCancellableCoroutine` 으로 감싼다.
 *
 * 모든 파라미터는 **일반 함수 타입**(suspend 아님)이라 Swift 클로저로 그대로 export 된다.
 * (suspend 람다는 `KotlinSuspendFunctionN` 으로 export 되어 Swift 에서 다루기 어렵다.)
 */
interface SwiftLlmBridge {
    /**
     * 모델을 로드한다. 완료 시 [onResult] 를 호출한다.
     * @param onResult 성공이면 `null`, 실패면 오류 메시지.
     */
    fun load(
        modelPath: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        onResult: (String?) -> Unit,
    )

    /**
     * 토큰을 스트리밍한다.
     * @param onToken 토큰 청크마다 호출.
     * @param onDone 스트림 종료 시 호출. 성공이면 `null`, 실패면 오류 메시지.
     */
    fun generate(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String?) -> Unit,
    )

    /** 대화 세션 초기화(멀티턴 맥락 제거). */
    fun reset()

    /** 가중치 RAM 해제. */
    fun release()
}

/**
 * [SwiftLlmBridge] 를 [LocalLlmEngine] 으로 감싸는 iOS actual 어댑터.
 * Swift 는 [SwiftLlmBridge] 만 구현하고, `doInitKoin(gemmaEngine = SwiftBackedLocalLlmEngine(bridge))`
 * 로 주입한다.
 */
class SwiftBackedLocalLlmEngine(
    private val bridge: SwiftLlmBridge,
) : LocalLlmEngine {

    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    @Throws(Exception::class)
    override suspend fun load(modelPath: String, params: SamplingParameters) {
        _state.value = EngineState.LOADING
        suspendCancellableCoroutine { cont ->
            bridge.load(
                modelPath = modelPath,
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                maxTokens = params.maxTokens,
            ) { error ->
                if (error == null) {
                    _state.value = EngineState.READY
                    cont.resume(Unit)
                } else {
                    _state.value = EngineState.ERROR
                    cont.resumeWithException(IllegalStateException(error))
                }
            }
        }
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        _state.value = EngineState.GENERATING
        bridge.generate(
            prompt = prompt,
            onToken = { token -> trySend(token) },
            onDone = { error ->
                _state.value = EngineState.READY
                if (error == null) close() else close(IllegalStateException(error))
            },
        )
        awaitClose { /* Swift 측 취소는 reset()/release() 로 처리 */ }
    }

    override fun resetSession() {
        bridge.reset()
        if (_state.value == EngineState.GENERATING) _state.value = EngineState.READY
    }

    override fun release() {
        bridge.release()
        _state.value = EngineState.UNINITIALIZED
    }
}
