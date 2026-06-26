//
//  CashChatIOSTests.swift
//  CashChatIOSTests
//
//  Created by gudals-mac on 3/8/26.
//

import Testing
import CashChatShared
@testable import CashChatIOS

struct CashChatIOSTests {

    @Test func gemmaReadyStateShowsEngineUnavailableWhenEngineIsNotLinked() async throws {
        let presentation = GemmaDownloadPresentation(
            state: ModelDownloadStateReady(localPath: "/tmp/gemma.litertlm"),
            engineUnavailableReason: "Gemma engine is not linked."
        )

        #expect(presentation.title == "Gemma 엔진 준비 필요")
        #expect(presentation.body == "Gemma engine is not linked.")
    }

}
