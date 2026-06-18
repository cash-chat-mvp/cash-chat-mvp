import Foundation
import SwiftUI
import Combine
import CashChatShared

@MainActor
final class ShopViewModel: ObservableObject {
    // BE ShopItemCategory와 1:1 (phase1은 ENHANCE만 활성)
    let categories: [(code: String, label: String, icon: String)] = [
        ("ENHANCE", "강화", "wand.and.stars"),
        ("COSMETIC", "꾸미기", "paintbrush.fill"),
        ("VOUCHER", "상품권", "ticket.fill"),
    ]

    @Published var selectedCategory = "ENHANCE"
    @Published var items: [ShopCatalogDto.Item] = []
    @Published var phase1Active = true
    @Published var inventory: [String: Int] = [:]   // itemCode -> qty
    @Published var balance: Int64 = 0
    @Published var isLoading = false
    @Published var notice: String?
    @Published var purchasingCode: String?

    private let shopApi = KoinHelper().shopApi()
    private let points = KoinHelper().pointsRepository()
    private let collector = FlowCollector()
    private var didLoad = false
    private var noticeTask: DispatchWorkItem?

    deinit {
        collector.cancel()
        noticeTask?.cancel()
    }

    func load() {
        if !didLoad {
            didLoad = true
            collector.collectBalance(repo: points) { [weak self] v in
                Task { @MainActor in self?.balance = v.int64Value }
            }
            Task { await loadInventory() }
        }
        Task { await loadCategory(selectedCategory) }
    }

    func select(_ code: String) {
        guard code != selectedCategory else { return }
        selectedCategory = code
        Task { await loadCategory(code) }
    }

    func loadCategory(_ code: String) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let catalog = try await shopApi.getItems(category: code)
            self.items = catalog.items.sorted { $0.displayOrder < $1.displayOrder }
            self.phase1Active = catalog.phase1Active
        } catch {
            self.items = []
        }
    }

    func loadInventory() async {
        guard let inv = try? await shopApi.getInventory() else { return }
        var map: [String: Int] = [:]
        for it in inv.items { map[it.itemCode] = Int(it.qty) }
        self.inventory = map
    }

    func purchase(_ item: ShopCatalogDto.Item) {
        guard purchasingCode == nil else { return }
        purchasingCode = item.itemCode
        Task {
            defer { purchasingCode = nil }
            do {
                let result = try await shopApi.purchase(itemCode: item.itemCode, qty: 1, idempotencyKey: UUID().uuidString)
                // 서버 권위 잔액으로 PointsRepository 보정(혜택존 잔액과 동기화).
                points.applyDelta(delta: result.coinBalance - balance)
                var map: [String: Int] = [:]
                for it in result.inventory { map[it.itemCode] = Int(it.qty) }
                self.inventory = map
                showNotice("구매 완료: \(item.name)")
            } catch {
                // KMM 예외는 Swift 에서 NSError 로 브리지되어 코드 분기가 불안정하므로 일반 안내.
                // 화면 잔액은 임시값(LocalPointsRepository)이라 서버 실잔액 부족 시에도 거절될 수 있다(이슈 B).
                showNotice("구매에 실패했어요 (코인 부족 등)")
            }
        }
    }

    private func showNotice(_ text: String) {
        notice = text
        noticeTask?.cancel()
        let task = DispatchWorkItem { [weak self] in self?.notice = nil }
        noticeTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: task)
    }
}
