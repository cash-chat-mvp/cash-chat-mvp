import { useNavigate } from "react-router";
import { Sparkles, Coins } from "lucide-react";
import { motion } from "motion/react";

export function OnboardingScreen() {
  const navigate = useNavigate();

  const handleStart = () => {
    navigate("/chat");
  };

  return (
    <div className="flex flex-col items-center justify-between h-screen bg-gradient-to-b from-[#5C6BFA] to-[#4A5AE8] max-w-[430px] mx-auto p-8">
      {/* Logo & Animation */}
      <div className="flex-1 flex items-center justify-center">
        <motion.div
          initial={{ scale: 0, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.5 }}
          className="text-center"
        >
          <div className="relative inline-block">
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
              className="absolute -top-4 -right-4"
            >
              <Coins className="w-12 h-12 text-[#FF6B00]" />
            </motion.div>
            <Sparkles className="w-24 h-24 text-white mb-4" />
          </div>
          <h1 className="text-4xl font-bold text-white mb-4">AI Chat+</h1>
          <p className="text-white/80 text-lg">대화하고 포인트 받자!</p>
        </motion.div>
      </div>

      {/* CTA Buttons */}
      <div className="w-full space-y-3 mb-8">
        <motion.button
          initial={{ y: 50, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.3 }}
          onClick={handleStart}
          className="w-full bg-white text-[#5C6BFA] rounded-2xl py-4 px-6 font-semibold text-lg shadow-lg"
        >
          카카오로 3초 만에 시작하기
        </motion.button>
        
        <motion.button
          initial={{ y: 50, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.4 }}
          onClick={handleStart}
          className="w-full bg-black text-white rounded-2xl py-4 px-6 font-semibold text-lg"
        >
          Apple로 로그인
        </motion.button>

        <p className="text-center text-white/60 text-sm mt-4">
          가입하면 즉시 500P 지급!
        </p>
      </div>
    </div>
  );
}
