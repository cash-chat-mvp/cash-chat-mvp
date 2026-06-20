import SwiftUI
import CashChatShared

struct ShopScreen: View {
    @StateObject private var vm = ShopViewModel()
    private let accent = Color(red: 0.36, green: 0.42, blue: 0.98)
    private let coin = Color(red: 0.69, green: 0.49, blue: 0.0)

    var body: some View {
        VStack(spacing: 0) {
            header
            categoryBar
            content
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            if let notice = vm.notice {
                Text(notice)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(Color(red: 0.1, green: 0.1, blue: 0.16).opacity(0.92))
                    .clipShape(Capsule())
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.25), value: vm.notice)
        .onAppear { vm.load() }
    }

    private var header: some View {
        HStack {
            Text("상점").font(.system(size: 22, weight: .heavy))
            Spacer()
            HStack(spacing: 4) {
                Image(systemName: "bitcoinsign.circle.fill")
                Text("\(vm.balance)")
            }
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(coin)
            .padding(.horizontal, 11).padding(.vertical, 5)
            .background(Color(red: 1.0, green: 0.97, blue: 0.90))
            .clipShape(Capsule())
        }
        .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 10)
        .background(Color(.systemBackground))
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(vm.categories, id: \.code) { c in
                    let selected = c.code == vm.selectedCategory
                    Button { vm.select(c.code) } label: {
                        HStack(spacing: 5) {
                            Image(systemName: c.icon)
                            Text(c.label)
                        }
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(selected ? accent : Color(.tertiarySystemGroupedBackground))
                        .foregroundStyle(selected ? .white : Color(.label))
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(Color(.systemBackground))
    }

    @ViewBuilder private var content: some View {
        if vm.isLoading {
            VStack { Spacer(); ProgressView(); Spacer() }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if vm.items.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "shippingbox").font(.system(size: 40)).foregroundStyle(.secondary)
                Text("준비 중인 카테고리예요").font(.subheadline).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(vm.items, id: \.itemCode) { item in
                        itemRow(item)
                    }
                }
                .padding(16)
            }
        }
    }

    private func itemRow(_ item: ShopCatalogDto.Item) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(item.name).font(.system(size: 15, weight: .bold))
                Text(item.effectSummary).font(.system(size: 12.5)).foregroundStyle(.secondary)
                if let q = vm.inventory[item.itemCode], q > 0 {
                    Text("보유 \(q)개").font(.system(size: 11, weight: .semibold)).foregroundStyle(accent)
                }
            }
            Spacer()
            VStack(spacing: 6) {
                HStack(spacing: 3) {
                    Image(systemName: "bitcoinsign.circle.fill")
                    Text("\(item.priceCoin)")
                }
                .font(.system(size: 13, weight: .heavy))
                .foregroundStyle(coin)
                Button {
                    vm.purchase(item)
                } label: {
                    Text(vm.purchasingCode == item.itemCode ? "구매 중…" : "구매")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 16).padding(.vertical, 7)
                        .background(accent).foregroundStyle(.white)
                        .clipShape(Capsule())
                }
                .disabled(vm.purchasingCode != nil)
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
