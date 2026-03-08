import { Outlet, Link, useLocation } from "react-router";
import { MessageCircle, Gift, Store, User } from "lucide-react";

export function MainLayout() {
  const location = useLocation();

  const navItems = [
    { path: "/chat", icon: MessageCircle, label: "채팅" },
    { path: "/rewards", icon: Gift, label: "리워드" },
    { path: "/shop", icon: Store, label: "상점" },
    { path: "/mypage", icon: User, label: "MY" },
  ];

  return (
    <div className="flex flex-col h-screen bg-[#F4F6F8] max-w-[430px] mx-auto">
      <div className="flex-1 overflow-hidden">
        <Outlet />
      </div>
      
      {/* Bottom Navigation */}
      <nav className="bg-white border-t border-gray-200 pb-safe">
        <div className="flex justify-around items-center h-16">
          {navItems.map(({ path, icon: Icon, label }) => {
            const isActive = location.pathname === path;
            return (
              <Link
                key={path}
                to={path}
                className="flex flex-col items-center justify-center flex-1 gap-1"
              >
                <Icon
                  className={`w-6 h-6 ${
                    isActive ? "text-[#5C6BFA]" : "text-gray-400"
                  }`}
                />
                <span
                  className={`text-xs ${
                    isActive ? "text-[#5C6BFA] font-semibold" : "text-gray-400"
                  }`}
                >
                  {label}
                </span>
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
