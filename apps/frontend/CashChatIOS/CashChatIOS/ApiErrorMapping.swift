import Foundation
import CashChatShared

extension Error {
    /// KMM이 던진 Kotlin ApiException의 code를 추출한다(없으면 nil).
    var apiErrorCode: String? {
        let ns = self as NSError
        return (ns.userInfo["KotlinException"] as? ApiException)?.code
    }
}
