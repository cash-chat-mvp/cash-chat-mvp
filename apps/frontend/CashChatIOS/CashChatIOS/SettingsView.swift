import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var themeSettings: ThemeSettings
    @Environment(\.dismiss) private var dismiss

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach([ThemeMode.system, .light, .dark], id: \.rawValue) { mode in
                        Button {
                            themeSettings.themeMode = mode
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: mode.systemImageName)
                                    .foregroundStyle(
                                        themeSettings.themeMode == mode
                                            ? Color(red: 0.36, green: 0.42, blue: 0.98)
                                            : Color(.secondaryLabel)
                                    )
                                    .frame(width: 22)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(mode.label)
                                        .foregroundStyle(
                                            themeSettings.themeMode == mode
                                                ? Color(red: 0.36, green: 0.42, blue: 0.98)
                                                : Color(.label)
                                        )
                                        .font(.body)
                                    Text(mode.description)
                                        .font(.caption)
                                        .foregroundStyle(Color(.secondaryLabel))
                                }

                                Spacer()

                                if themeSettings.themeMode == mode {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(Color(red: 0.36, green: 0.42, blue: 0.98))
                                        .fontWeight(.semibold)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("디스플레이")
                }

                Section {
                    EmptyView()
                } footer: {
                    VStack(spacing: 4) {
                        Text("Cash Chat")
                            .font(.subheadline.weight(.semibold))
                        Text("v\(appVersion)")
                            .font(.caption)
                        Spacer().frame(height: 4)
                        Text("wildNomadLab")
                            .font(.caption.weight(.medium))
                        Text("© 2026 wildNomadLab. All rights reserved.")
                            .font(.caption2)
                    }
                    .foregroundStyle(Color(.secondaryLabel))
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
                }
            }
            .navigationTitle("설정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("완료") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
    }
}
