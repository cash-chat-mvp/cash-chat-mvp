import Foundation
import CashChatShared
import LiteRTLM

/// LiteRT-LM Swift API(Metal) 를 KMM `SwiftLlmBridge` 로 래핑한다.
/// Kotlin `SwiftBackedLocalLlmEngine` 이 이 브릿지를 `LocalLlmEngine`(Flow/suspend) 으로 감싼다.
///
/// 주의: Kotlin `Int` 파라미터는 Swift 에서 `Int32` 로 export 된다.
final class GemmaLlmBridge: SwiftLlmBridge {
    private var engine: Engine?
    private var conversation: Conversation?
    private var sampler: SamplerConfig?
    private var streamTask: Task<Void, Never>?

    func load(
        modelPath: String,
        temperature: Float,
        topP: Float,
        topK: Int32,
        maxTokens: Int32,
        onResult: @escaping (String?) -> Void
    ) {
        streamTask?.cancel()
        Task {
            do {
                let config = try EngineConfig(modelPath: modelPath, backend: .gpu)
                let engine = Engine(engineConfig: config)
                try await engine.initialize()
                let sampler = try SamplerConfig(topK: Int(topK), topP: topP, temperature: temperature)
                let conversation = try await engine.createConversation(
                    with: ConversationConfig(samplerConfig: sampler)
                )
                self.engine = engine
                self.conversation = conversation
                self.sampler = sampler
                onResult(nil)
            } catch {
                self.engine = nil
                self.conversation = nil
                onResult(error.localizedDescription)
            }
        }
    }

    func generate(
        prompt: String,
        onToken: @escaping (String) -> Void,
        onDone: @escaping (String?) -> Void
    ) {
        guard let conversation else {
            onDone("Gemma 엔진이 로드되지 않았습니다.")
            return
        }
        streamTask = Task {
            do {
                for try await chunk in conversation.sendMessageStream(Message(prompt)) {
                    if case let .text(text)? = chunk.contents.first {
                        onToken(text)
                    }
                }
                onDone(nil)
            } catch {
                onDone(error.localizedDescription)
            }
        }
    }

    func reset() {
        streamTask?.cancel()
        // 멀티턴 맥락 제거: 새 대화 세션을 생성한다.
        guard let engine, let sampler else { return }
        Task {
            self.conversation = try? await engine.createConversation(
                with: ConversationConfig(samplerConfig: sampler)
            )
        }
    }

    func release() {
        streamTask?.cancel()
        conversation = nil
        engine = nil
    }
}
