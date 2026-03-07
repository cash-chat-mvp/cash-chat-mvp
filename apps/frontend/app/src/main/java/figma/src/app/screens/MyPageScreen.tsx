import { ChevronRight, Gift, History, Bell, HelpCircle, Settings, LogOut } from "lucide-react";
import { usePoints } from "../contexts/PointsContext";
import { motion } from "motion/react";

export function MyPageScreen() {
  const { points } = usePoints();

  const menuItems = [
    { icon: Gift, label: "내 기프티콘 보관함", badge: "2" },
    { icon: History, label: "포인트 적립/사용 내역", badge: null },
    { icon: Bell, label: "공지사항", badge: "N" },
    { icon: HelpCircle, label: "고객센터", badge: null },
    { icon: Settings, label: "설정", badge: null },
  ];

  return (
    <div className="h-full overflow-y-auto bg-[#F4F6F8]">
      {/* Profile Section */}
      <header className="bg-gradient-to-br from-[#5C6BFA] to-[#4A5AE8] px-6 py-8 text-white">
        <div className="flex items-center gap-4 mb-6">
          <div className="w-20 h-20 bg-white/20 rounded-full flex items-center justify-center text-4xl">
            👤
          </div>
          <div>
            <h2 className="text-2xl font-bold mb-1">홍길동님</h2>
            <p className="text-white/80 text-sm">gildong@kakao.com</p>
          </div>
        </div>

        {/* Points Card */}
        <motion.div
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          className="bg-white/10 backdrop-blur-sm rounded-2xl p-4"
        >
          <p className="text-white/80 text-sm mb-1">보유 포인트</p>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold">{points.toLocaleString()}</span>
            <span className="text-lg">P</span>
          </div>
          <p className="text-white/60 text-xs mt-2">
            누적 획득 포인트: 15,750 P
          </p>
        </motion.div>
      </header>

      {/* Menu List */}
      <div className="p-4 space-y-2 mt-4">
        {menuItems.map((item, index) => {
          const Icon = item.icon;
          return (
            <motion.button
              key={item.label}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.05 }}
              className="w-full bg-white rounded-2xl p-4 flex items-center justify-between hover:shadow-md transition-shadow"
            >
              <div className="flex items-center gap-3">
                <div className="bg-[#F4F6F8] p-2 rounded-xl">
                  <Icon className="w-5 h-5 text-[#5C6BFA]" />
                </div>
                <span className="font-medium text-gray-900">{item.label}</span>
              </div>
              <div className="flex items-center gap-2">
                {item.badge && (
                  <span className="bg-[#FF6B00] text-white text-xs font-bold px-2 py-1 rounded-full">
                    {item.badge}
                  </span>
                )}
                <ChevronRight className="w-5 h-5 text-gray-400" />
              </div>
            </motion.button>
          );
        })}
      </div>

      {/* Stats Cards */}
      <div className="px-4 mt-6 mb-4">
        <h3 className="font-bold text-gray-900 mb-3">나의 활동</h3>
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-white rounded-2xl p-4 text-center">
            <p className="text-2xl font-bold text-[#5C6BFA] mb-1">7</p>
            <p className="text-xs text-gray-600">연속 출석</p>
          </div>
          <div className="bg-white rounded-2xl p-4 text-center">
            <p className="text-2xl font-bold text-[#FF6B00] mb-1">142</p>
            <p className="text-xs text-gray-600">총 대화수</p>
          </div>
          <div className="bg-white rounded-2xl p-4 text-center">
            <p className="text-2xl font-bold text-green-600 mb-1">3</p>
            <p className="text-xs text-gray-600">교환 상품</p>
          </div>
        </div>
      </div>

      {/* Logout Button */}
      <div className="px-4 pb-8">
        <button className="w-full flex items-center justify-center gap-2 text-gray-500 py-3 rounded-xl border border-gray-300 hover:bg-gray-50 transition-colors">
          <LogOut className="w-5 h-5" />
          <span>로그아웃</span>
        </button>
      </div>

      {/* App Info */}
      <div className="px-4 pb-8 text-center text-xs text-gray-400">
        <p>AI Chat+ v1.0.0</p>
        <p className="mt-1">© 2026 AI Chat Plus. All rights reserved.</p>
      </div>
    </div>
  );
}
