import SwiftUI
import GoogleMobileAds
import UIKit

/// 채팅 리스트에 메시지 버블처럼 삽입되는 네이티브 광고(시안 B).
struct ChatNativeAdView: View {
    @StateObject private var loader = ChatNativeAdLoader()

    var body: some View {
        Group {
            if let ad = loader.nativeAd {
                NativeAdContainer(nativeAd: ad)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.trailing, 48)
            } else {
                EmptyView()
            }
        }
        .onAppear { if loader.nativeAd == nil { loader.load() } }
    }
}

private struct NativeAdContainer: UIViewRepresentable {
    let nativeAd: NativeAd

    func makeUIView(context: Context) -> NativeAdView {
        let adView = NativeAdView()

        let icon = UIImageView()
        icon.translatesAutoresizingMaskIntoConstraints = false
        icon.widthAnchor.constraint(equalToConstant: 32).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 32).isActive = true

        let headline = UILabel()
        headline.font = .systemFont(ofSize: 14, weight: .semibold)
        headline.numberOfLines = 2

        let advertiser = UILabel()
        advertiser.font = .systemFont(ofSize: 11)
        advertiser.textColor = .secondaryLabel

        let rating = UILabel()
        rating.font = .systemFont(ofSize: 11)
        rating.textColor = .secondaryLabel

        let badge = UILabel()
        badge.text = "Ad"
        badge.font = .systemFont(ofSize: 10)
        badge.textColor = .secondaryLabel

        let media = MediaView()
        media.translatesAutoresizingMaskIntoConstraints = false
        media.heightAnchor.constraint(equalToConstant: 96).isActive = true

        let cta = UIButton(type: .system)
        cta.isUserInteractionEnabled = false

        let textStack = UIStackView(arrangedSubviews: [headline, advertiser, rating])
        textStack.axis = .vertical
        let topRow = UIStackView(arrangedSubviews: [icon, textStack, badge])
        topRow.axis = .horizontal
        topRow.spacing = 8
        topRow.alignment = .top

        let container = UIStackView(arrangedSubviews: [topRow, media, cta])
        container.axis = .vertical
        container.spacing = 8
        container.translatesAutoresizingMaskIntoConstraints = false
        container.isLayoutMarginsRelativeArrangement = true
        container.layoutMargins = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)

        adView.addSubview(container)
        NSLayoutConstraint.activate([
            container.topAnchor.constraint(equalTo: adView.topAnchor),
            container.bottomAnchor.constraint(equalTo: adView.bottomAnchor),
            container.leadingAnchor.constraint(equalTo: adView.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: adView.trailingAnchor),
        ])

        adView.headlineView = headline
        adView.advertiserView = advertiser
        adView.starRatingView = rating
        adView.iconView = icon
        adView.mediaView = media
        adView.callToActionView = cta

        adView.backgroundColor = .secondarySystemGroupedBackground
        adView.layer.cornerRadius = 14
        adView.clipsToBounds = true
        return adView
    }

    func updateUIView(_ adView: NativeAdView, context: Context) {
        (adView.headlineView as? UILabel)?.text = nativeAd.headline
        (adView.advertiserView as? UILabel)?.text = nativeAd.advertiser ?? nativeAd.store ?? ""
        if let stars = nativeAd.starRating {
            (adView.starRatingView as? UILabel)?.text = "★ \(stars)"
            adView.starRatingView?.isHidden = false
        } else {
            adView.starRatingView?.isHidden = true
        }
        (adView.iconView as? UIImageView)?.image = nativeAd.icon?.image
        (adView.iconView as? UIImageView)?.isHidden = nativeAd.icon?.image == nil
        (adView.callToActionView as? UIButton)?.setTitle(nativeAd.callToAction ?? "자세히 보기", for: .normal)
        adView.mediaView?.mediaContent = nativeAd.mediaContent
        adView.nativeAd = nativeAd
    }
}
