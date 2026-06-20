import SwiftUI

struct BenefitZoneScreen: View {
    @StateObject private var attendanceVM = AttendanceViewModel()
    @State private var animateIn = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Text("혜택존").font(.system(size: 22, weight: .heavy))
                    Spacer()
                    HStack(spacing: 4) {
                        Image(systemName: "bitcoinsign.circle.fill")
                        Text("\(attendanceVM.balance)")
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(red: 0.69, green: 0.49, blue: 0.0))
                    .padding(.horizontal, 11).padding(.vertical, 5)
                    .background(Color(red: 1.0, green: 0.97, blue: 0.90))
                    .clipShape(Capsule())
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)

                AttendanceWidgetView(vm: attendanceVM)
                    .padding(.horizontal, 16)

                BenefitInfoCardView(icon: "tv.fill", title: "리워드 광고", badge: .next,
                    description: "광고 1회 시청 → +40 코인 · 하루 10회까지", dimmed: false)
                    .padding(.horizontal, 16)
                BenefitInfoCardView(icon: "target", title: "데일리 미션", badge: .soon,
                    description: "매일 바뀌는 3가지 미션을 완료하고 코인 적립", dimmed: true)
                    .padding(.horizontal, 16)
                BenefitInfoCardView(icon: "gamecontroller.fill", title: "TNK 오퍼월", badge: .next,
                    description: "앱 설치·설문 참여로 대량 코인 (최대 +1,500 코인)", dimmed: false)
                    .padding(.horizontal, 16)
                    .onTapGesture {
                        if let top = TnkOfferwallManager.topViewController() {
                            TnkOfferwallManager.present(from: top)
                        }
                    }
            }
            .padding(.bottom, 16)
        }
        .refreshable { await attendanceVM.refresh() }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            if let toast = attendanceVM.toast {
                Text(toast)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.1, green: 0.1, blue: 0.16).opacity(0.92))
                    .clipShape(Capsule())
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.25), value: attendanceVM.toast)
        .onChange(of: attendanceVM.toast) { _, newValue in
            guard newValue != nil else { return }
            // 이전 타이머를 취소하고 새로 예약 — 연속 토스트 시 타이머 누적/조기 사라짐 방지.
            attendanceVM.scheduleToastDismiss()
        }
        .opacity(animateIn ? 1 : 0)
        .offset(y: animateIn ? 0 : 14)
        .animation(.easeOut(duration: 0.34), value: animateIn)
        .onAppear {
            animateIn = false
            attendanceVM.load()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
                withAnimation(.easeOut(duration: 0.34)) { animateIn = true }
            }
        }
        .onDisappear { animateIn = false }
        .onChange(of: scenePhase) { _, phase in
            // 오퍼월/백그라운드에서 복귀 시 잔액·출석 갱신 (비동기 적립 반영).
            if phase == .active { Task { await attendanceVM.refresh() } }
        }
    }
}
