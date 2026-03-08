import { useState, useRef, useEffect } from "react";
import { Menu, Send, Gift, X, Play, MessageCircle, Store, User, Sparkles } from "lucide-react";
import { usePoints } from "../contexts/PointsContext";
import { motion, AnimatePresence } from "motion/react";
import { useNavigate, Link } from "react-router";

// ─── Ad System ───────────────────────────────────────────────────────────────

interface AdInfo {
  brand: string;
  tagline: string;
  cta: string;
  emoji: string;
  bg: string;
  accent: string;
  category: string;
}

const AD_DATABASE: { keywords: string[]; ad: AdInfo }[] = [
  {
    keywords: ["음식", "맛집", "먹", "배달", "요리", "레스토랑", "점심", "저녁", "아침", "메뉴", "식사", "밥", "라면", "치킨", "피자"],
    ad: { brand: "배달의민족", tagline: "지금 주문하면 3,000원 즉시 할인! 오늘 뭐 먹지?", cta: "지금 주문하기", emoji: "🍔", bg: "#FFF4E8", accent: "#FF6B00", category: "음식·배달" },
  },
  {
    keywords: ["여행", "휴가", "비행기", "호텔", "숙소", "관광", "해외", "국내여행", "여행지", "리조트"],
    ad: { brand: "야놀자", tagline: "특가 숙소 최대 50% 할인! 지금 예약하세요", cta: "특가 확인하기", emoji: "✈️", bg: "#E8F4FF", accent: "#0066CC", category: "여행·숙박" },
  },
  {
    keywords: ["운동", "헬스", "다이어트", "피트니스", "건강", "살", "체중", "근육", "요가", "달리기"],
    ad: { brand: "나이키 트레이닝 클럽", tagline: "무료 운동 플랜으로 오늘부터 시작하세요!", cta: "무료로 시작", emoji: "💪", bg: "#E8FFE8", accent: "#00AA44", category: "건강·피트니스" },
  },
  {
    keywords: ["쇼핑", "구매", "할인", "세일", "옷", "패션", "신발", "가방", "코디", "브랜드"],
    ad: { brand: "무신사", tagline: "지금 가입하면 첫 구매 10% 쿠폰 증정!", cta: "쿠폰 받기", emoji: "🛍️", bg: "#F0E8FF", accent: "#6600CC", category: "패션·쇼핑" },
  },
  {
    keywords: ["공부", "학습", "영어", "수학", "시험", "교육", "강의", "자격증", "취업", "스펙"],
    ad: { brand: "클래스101", tagline: "1개월 무제한 클래스 무료 체험 중!", cta: "무료체험 시작", emoji: "📚", bg: "#FFF0E8", accent: "#CC4400", category: "교육·자기계발" },
  },
  {
    keywords: ["게임", "앱", "스마트폰", "핸드폰", "갤럭시", "아이폰", "충전"],
    ad: { brand: "원스토어", tagline: "신작 게임 출시! 첫 충전 100% 보너스 캐시", cta: "플레이하기", emoji: "🎮", bg: "#E8E8FF", accent: "#4444CC", category: "게임·앱" },
  },
  {
    keywords: ["금융", "투자", "주식", "코인", "저축", "대출", "보험", "적금"],
    ad: { brand: "토스", tagline: "간편 송금부터 투자까지! 가입 시 1,000P 지급", cta: "가입하기", emoji: "💰", bg: "#E8FFF4", accent: "#0099AA", category: "금융·재테크" },
  },
  {
    keywords: ["영화", "드라마", "OTT", "넷플릭스", "유튜브", "콘텐츠", "음악", "노래"],
    ad: { brand: "웨이브", tagline: "첫 달 무료! 국내 최대 콘텐츠 라이브러리", cta: "무료 감상", emoji: "🎬", bg: "#FFE8F4", accent: "#CC0066", category: "엔터테인먼트" },
  },
];

const DEFAULT_AD: AdInfo = {
  brand: "CashAI Premium",
  tagline: "광고 없이 AI와 무제한 대화! 월 990원부터",
  cta: "업그레이드",
  emoji: "⭐",
  bg: "#FFF8E8",
  accent: "#CC8800",
  category: "프리미엄",
};

const REWARD_ADS: AdInfo[] = [
  { brand: "삼성 갤럭시 S25", tagline: "새로운 AI 폰의 시작. 사전예약 시 Galaxy Buds 증정!", cta: "사전예약 하기", emoji: "📱", bg: "#E8F0FF", accent: "#1428A0", category: "전자제품" },
  { brand: "스타벅스 코리아", tagline: "봄 시즌 신메뉴 출시! 앱 주문 시 별 2배 적립", cta: "앱으로 주문", emoji: "☕", bg: "#E8F5E9", accent: "#00704A", category: "음료·카페" },
  { brand: "현대자동차 아이오닉6", tagline: "전기차 시대의 혁신. 지금 시승 신청하세요!", cta: "시승 신청", emoji: "🚗", bg: "#FFF3E0", accent: "#002C5F", category: "자동차" },
];

function matchAd(userMessage: string): AdInfo {
  const lower = userMessage.toLowerCase();
  for (const entry of AD_DATABASE) {
    if (entry.keywords.some((kw) => lower.includes(kw))) {
      return entry.ad;
    }
  }
  return DEFAULT_AD;
}

function getRewardAd(count: number): AdInfo {
  return REWARD_ADS[count % REWARD_ADS.length];
}

// ─── AI Response Generator ────────────────────────────────────────────────────

const AI_RESPONSES: { keywords: string[]; response: string }[] = [
  {
    keywords: ["안녕", "hello", "hi", "반가"],
    response: "안녕하세요! 😊 저는 CashAI 비서예요. 질문하시면 바로 답변 드릴게요. 대화하면서 포인트도 모아보세요!",
  },
  {
    keywords: ["음식", "맛집", "먹", "배달", "메뉴", "점심", "저녁", "밥"],
    response: "오늘 뭐 드실지 고민이시군요! 😋 요즘 인기 있는 메뉴는 마라탕, 스시 부리또, 감자탕 등이에요. 위치를 알려주시면 근처 맛집도 추천해 드릴게요!",
  },
  {
    keywords: ["여행", "휴가", "비행기", "해외", "여행지"],
    response: "여행 계획을 세우고 계시군요! ✈️ 요즘 인기 여행지는 일본 오사카, 태국 방콕, 베트남 다낭이에요. 예산과 기간을 알려주시면 맞춤 코스를 추천해 드릴게요!",
  },
  {
    keywords: ["운동", "헬스", "다이어트", "건강"],
    response: "건강 관리에 관심이 있으시군요! 💪 하루 30분 걷기만 해도 큰 효과가 있어요. 목표를 알려주시면 맞춤 운동 루틴을 만들어 드릴게요!",
  },
  {
    keywords: ["공부", "영어", "자격증", "시험"],
    response: "학습 목표가 있으시군요! 📚 꾸준함이 가장 중요해요. 하루 1시간씩 집중 학습하는 포모도로 기법을 추천드려요. 어떤 분야를 공부하시나요?",
  },
];

function generateAIResponse(userMessage: string): string {
  const lower = userMessage.toLowerCase();
  for (const entry of AI_RESPONSES) {
    if (entry.keywords.some((kw) => lower.includes(kw))) {
      return entry.response;
    }
  }
  return `"${userMessage.slice(0, 20)}${userMessage.length > 20 ? "..." : ""}"에 대해 답변 드릴게요! 🤖 관련 정보를 분석해보면, 이 주제는 다양한 관점에서 살펴볼 수 있어요. 더 구체적인 질문을 해주시면 더욱 정확한 답변을 드릴 수 있답니다!`;
}

// ─── Types ────────────────────────────────────────────────────────────────────

type MessageType = "text" | "inline-ad" | "reward-prompt";

interface Message {
  id: string;
  type: MessageType;
  text?: string;
  isUser?: boolean;
  timestamp: Date;
  adInfo?: AdInfo;
}

// ─── Inline Ad Card ───────────────────────────────────────────────────────────

function InlineAdCard({ ad }: { ad: AdInfo }) {
  const [dismissed, setDismissed] = useState(false);
  if (dismissed) return null;
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      className="mx-1"
    >
      <div
        className="rounded-2xl overflow-hidden shadow-sm border border-black/5"
        style={{ background: ad.bg }}
      >
        {/* Ad Label */}
        <div className="flex items-center justify-between px-3 pt-2 pb-1">
          <span className="text-[10px] text-gray-400 font-medium tracking-wide">💡 맞춤 광고 · {ad.category}</span>
          <button
            onClick={() => setDismissed(true)}
            className="text-gray-300 hover:text-gray-500 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
        {/* Ad Content */}
        <div className="px-3 pb-3 flex items-center gap-3">
          <div
            className="w-12 h-12 rounded-xl flex items-center justify-center text-2xl flex-shrink-0"
            style={{ background: ad.accent + "20" }}
          >
            {ad.emoji}
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-gray-900 text-sm">{ad.brand}</p>
            <p className="text-xs text-gray-600 mt-0.5 leading-relaxed">{ad.tagline}</p>
          </div>
        </div>
        <div className="px-3 pb-3">
          <button
            className="w-full py-2 rounded-xl text-xs font-semibold text-white transition-opacity hover:opacity-90"
            style={{ background: ad.accent }}
          >
            {ad.cta} →
          </button>
        </div>
      </div>
    </motion.div>
  );
}

// ─── Reward Ad Prompt (in chat) ───────────────────────────────────────────────

function RewardAdPrompt({ onWatchAd }: { onWatchAd: () => void }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="flex justify-start"
    >
      <div className="max-w-[80%] bg-white rounded-[20px] rounded-tl-sm px-4 py-3 shadow-sm border border-[#5C6BFA]/20">
        <div className="flex items-center gap-2 mb-2">
          <span className="text-lg">🤔</span>
          <p className="text-sm font-semibold text-gray-800">AI가 생각을 오래하네요...</p>
        </div>
        <p className="text-xs text-gray-500 leading-relaxed mb-3">
          복잡한 질문이라 분석에 시간이 걸리고 있어요.<br />
          짧은 광고를 보고 오시면 바로 답변 드릴게요! 🎁
        </p>
        <button
          onClick={onWatchAd}
          className="flex items-center justify-center gap-2 w-full bg-[#FF6B00] text-white py-2.5 rounded-xl text-sm font-semibold hover:bg-[#e55f00] transition-colors"
        >
          <Play className="w-4 h-4 fill-white" />
          광고 보고 바로 답변 받기
          <span className="bg-white/20 text-white text-xs px-1.5 py-0.5 rounded-full">+30P</span>
        </button>
      </div>
    </motion.div>
  );
}

// ─── Reward Ad Modal ──────────────────────────────────────────────────────────

function RewardAdModal({
  ad,
  onComplete,
  onClose,
}: {
  ad: AdInfo;
  onComplete: () => void;
  onClose: () => void;
}) {
  const [progress, setProgress] = useState(0);
  const [phase, setPhase] = useState<"watching" | "complete">("watching");
  const [canSkip, setCanSkip] = useState(false);
  const DURATION = 5000;

  useEffect(() => {
    const start = Date.now();
    const interval = setInterval(() => {
      const elapsed = Date.now() - start;
      const pct = Math.min((elapsed / DURATION) * 100, 100);
      setProgress(pct);
      if (elapsed >= 3000) setCanSkip(true);
      if (elapsed >= DURATION) {
        clearInterval(interval);
        setPhase("complete");
      }
    }, 50);
    return () => clearInterval(interval);
  }, []);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex flex-col"
      style={{ background: "rgba(0,0,0,0.92)" }}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 pt-safe pt-4 pb-3">
        <span className="text-white/60 text-xs">리워드 광고</span>
        {canSkip && phase === "watching" && (
          <button
            onClick={onClose}
            className="text-white/60 text-xs border border-white/30 px-3 py-1 rounded-full"
          >
            건너뛰기
          </button>
        )}
      </div>

      {/* Ad Content */}
      <div className="flex-1 flex flex-col items-center justify-center px-6">
        <AnimatePresence mode="wait">
          {phase === "watching" ? (
            <motion.div
              key="watching"
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="w-full"
            >
              {/* Fake Ad Creative */}
              <div
                className="w-full rounded-3xl overflow-hidden shadow-2xl"
                style={{ background: ad.bg }}
              >
                <div
                  className="h-48 flex flex-col items-center justify-center gap-3"
                  style={{ background: ad.accent + "15" }}
                >
                  <span className="text-7xl">{ad.emoji}</span>
                  <p className="font-bold text-gray-900" style={{ color: ad.accent }}>
                    {ad.brand}
                  </p>
                </div>
                <div className="p-5 text-center">
                  <p className="font-bold text-gray-900 mb-1.5">{ad.tagline}</p>
                  <button
                    className="mt-3 px-6 py-2.5 rounded-full text-white text-sm font-semibold"
                    style={{ background: ad.accent }}
                  >
                    {ad.cta}
                  </button>
                </div>
              </div>

              {/* Progress */}
              <div className="mt-5">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-white/60 text-xs">광고 시청 중...</span>
                  <span className="text-white/60 text-xs">{Math.ceil((DURATION - (progress / 100) * DURATION) / 1000)}초 남음</span>
                </div>
                <div className="w-full bg-white/20 rounded-full h-1.5 overflow-hidden">
                  <motion.div
                    className="h-full rounded-full bg-[#FF6B00]"
                    style={{ width: `${progress}%` }}
                  />
                </div>
              </div>
            </motion.div>
          ) : (
            <motion.div
              key="complete"
              initial={{ scale: 0.8, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="text-center"
            >
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: "spring", delay: 0.1 }}
                className="w-24 h-24 bg-[#FF6B00] rounded-full flex items-center justify-center mx-auto mb-5"
              >
                <Gift className="w-12 h-12 text-white" />
              </motion.div>
              <h2 className="text-white text-2xl font-bold mb-2">시청 완료! 🎉</h2>
              <p className="text-white/70 mb-1">리워드 포인트가 지급되었어요</p>
              <div className="bg-[#FF6B00]/20 border border-[#FF6B00]/40 rounded-2xl px-8 py-4 mt-4 mb-6">
                <p className="text-[#FF6B00] font-bold text-3xl">+30 P</p>
              </div>
              <button
                onClick={onComplete}
                className="w-full bg-[#5C6BFA] text-white py-4 rounded-2xl font-bold text-base"
              >
                AI 답변 확인하기 →
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <div className="h-8" />
    </motion.div>
  );
}

// ─── Main Chat Screen ─────────────────────────────────────────────────────────

export function ChatScreen() {
  const { points, addPoints, messageCount, incrementMessageCount } = usePoints();
  const navigate = useNavigate();

  const [messages, setMessages] = useState<Message[]>([
    {
      id: "1",
      type: "text",
      text: "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!",
      isUser: false,
      timestamp: new Date(),
    },
  ]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [showMissionReward, setShowMissionReward] = useState(false);
  const [chatIdle, setChatIdle] = useState(true); // ← NEW: idle mode

  // Reward ad state
  const [showRewardAdModal, setShowRewardAdModal] = useState(false);
  const [currentRewardAd, setCurrentRewardAd] = useState<AdInfo>(REWARD_ADS[0]);
  const [pendingAIResponse, setPendingAIResponse] = useState("");
  const [rewardAdCount, setRewardAdCount] = useState(0);

  const sentCountRef = useRef(0);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const idleInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  useEffect(() => {
    if (messageCount > 0 && messageCount % 10 === 0) {
      setShowMissionReward(true);
    }
  }, [messageCount]);

  const addInlineAd = (userText: string) => {
    const ad = matchAd(userText);
    setMessages((prev) => [
      ...prev,
      {
        id: Date.now().toString() + "-ad",
        type: "inline-ad",
        timestamp: new Date(),
        adInfo: ad,
      },
    ]);
  };

  const handleSend = async (overrideValue?: string) => {
    const text = (overrideValue ?? inputValue).trim();
    if (!text || isLoading) return;

    // Exit idle mode
    setChatIdle(false);

    sentCountRef.current += 1;
    const thisCount = sentCountRef.current;

    const userMessage: Message = {
      id: Date.now().toString(),
      type: "text",
      text,
      isUser: true,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue("");
    setIsLoading(true);
    incrementMessageCount();

    const isRewardAdTurn = thisCount % 3 === 0;

    if (isRewardAdTurn) {
      const aiResponse = generateAIResponse(text);
      setPendingAIResponse(aiResponse);

      setTimeout(() => {
        setIsLoading(false);
        setMessages((prev) => [
          ...prev,
          {
            id: Date.now().toString() + "-reward-prompt",
            type: "reward-prompt",
            timestamp: new Date(),
          },
        ]);
      }, 1800);
    } else {
      const aiResponse = generateAIResponse(text);

      setTimeout(() => {
        setMessages((prev) => [
          ...prev,
          {
            id: Date.now().toString() + "-ai",
            type: "text",
            text: aiResponse,
            isUser: false,
            timestamp: new Date(),
          },
        ]);
        setIsLoading(false);

        setTimeout(() => {
          addInlineAd(text);
        }, 400);
      }, 2000);
    }
  };

  const handleWatchRewardAd = () => {
    const ad = getRewardAd(rewardAdCount);
    setCurrentRewardAd(ad);
    setShowRewardAdModal(true);
  };

  const handleRewardAdComplete = () => {
    addPoints(30);
    setRewardAdCount((c) => c + 1);
    setShowRewardAdModal(false);

    setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now().toString() + "-ai",
          type: "text",
          text: pendingAIResponse,
          isUser: false,
          timestamp: new Date(),
        },
      ]);
    }, 300);
  };

  const handleRewardAdClose = () => {
    setShowRewardAdModal(false);
    setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now().toString() + "-ai",
          type: "text",
          text: pendingAIResponse,
          isUser: false,
          timestamp: new Date(),
        },
      ]);
    }, 300);
  };

  const claimMissionReward = () => {
    addPoints(10);
    setShowMissionReward(false);
  };

  // Quick suggestion chips
  const suggestions = ["오늘 점심 추천해줘", "여행 계획 짜줘", "영어 공부 방법", "다이어트 팁"];

  return (
    <div className="flex flex-col h-full bg-[#F4F6F8] relative">

      {/* ── IDLE OVERLAY ─────────────────────────────────────────── */}
      <AnimatePresence>
        {chatIdle && (
          <motion.div
            key="idle"
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.35, ease: "easeInOut" }}
            className="absolute inset-0 z-40 flex flex-col items-center justify-center"
            style={{ background: "linear-gradient(160deg, #EEF0FF 0%, #F8F4FF 50%, #FFF4EE 100%)" }}
          >
            {/* Top points pill */}
            <button
              onClick={() => navigate("/shop")}
              className="absolute top-5 right-5 flex items-center gap-1.5 bg-[#FF6B00] text-white px-3.5 py-1.5 rounded-full shadow-md"
            >
              <span className="text-sm font-semibold">🪙 {points.toLocaleString()} P</span>
            </button>

            {/* AI Branding */}
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 }}
              className="flex flex-col items-center mb-10"
            >
              <div className="w-20 h-20 rounded-[28px] bg-[#5C6BFA] flex items-center justify-center shadow-xl mb-4"
                style={{ boxShadow: "0 8px 32px rgba(92,107,250,0.35)" }}>
                <Sparkles className="w-10 h-10 text-white" />
              </div>
              <h1 className="font-bold text-gray-900 text-2xl tracking-tight">CashAI 비서</h1>
              <p className="text-gray-400 mt-1.5 text-sm">무엇이든 물어보세요 · 대화할수록 포인트 적립</p>
            </motion.div>

            {/* Centered Input */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.18 }}
              className="w-full px-5"
            >
              <div className="flex items-center gap-2 bg-white rounded-2xl px-4 py-3.5 shadow-lg border border-[#5C6BFA]/10"
                style={{ boxShadow: "0 4px 24px rgba(92,107,250,0.12)" }}>
                <input
                  ref={idleInputRef}
                  type="text"
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onFocus={() => {}}
                  onKeyDown={(e) => e.key === "Enter" && handleSend()}
                  placeholder="메시지를 입력하세요..."
                  className="flex-1 text-sm text-gray-800 bg-transparent focus:outline-none placeholder:text-gray-400"
                />
                <button
                  onClick={() => handleSend()}
                  disabled={!inputValue.trim()}
                  className="bg-[#5C6BFA] text-white p-2.5 rounded-xl disabled:opacity-30 transition-all"
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>

              {/* Suggestion chips */}
              <div className="flex gap-2 mt-3 flex-wrap justify-center">
                {suggestions.map((s) => (
                  <button
                    key={s}
                    onClick={() => handleSend(s)}
                    className="bg-white/80 border border-[#5C6BFA]/20 text-gray-600 text-xs px-3 py-1.5 rounded-full shadow-sm hover:bg-[#5C6BFA]/5 transition-colors"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </motion.div>

            {/* Floating Nav Buttons */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.28 }}
              className="absolute bottom-10 left-0 right-0 flex justify-center"
            >
              <div className="flex items-center gap-3 bg-white/80 backdrop-blur-md rounded-2xl px-5 py-3 shadow-lg border border-white/60"
                style={{ boxShadow: "0 4px 24px rgba(0,0,0,0.10)" }}>
                {/* Chat – active */}
                <button
                  onClick={() => idleInputRef.current?.focus()}
                  className="flex flex-col items-center gap-1"
                >
                  <div className="w-11 h-11 rounded-xl bg-[#5C6BFA] flex items-center justify-center shadow-sm">
                    <MessageCircle className="w-5 h-5 text-white" />
                  </div>
                  <span className="text-[10px] font-semibold text-[#5C6BFA]">채팅</span>
                </button>

                <div className="w-px h-8 bg-gray-200 mx-1" />

                {/* Rewards */}
                <Link to="/rewards" className="flex flex-col items-center gap-1">
                  <div className="w-11 h-11 rounded-xl bg-gray-100 flex items-center justify-center">
                    <Gift className="w-5 h-5 text-gray-500" />
                  </div>
                  <span className="text-[10px] text-gray-400">리워드</span>
                </Link>

                {/* Shop */}
                <Link to="/shop" className="flex flex-col items-center gap-1">
                  <div className="w-11 h-11 rounded-xl bg-gray-100 flex items-center justify-center">
                    <Store className="w-5 h-5 text-gray-500" />
                  </div>
                  <span className="text-[10px] text-gray-400">상점</span>
                </Link>

                {/* MyPage */}
                <Link to="/mypage" className="flex flex-col items-center gap-1">
                  <div className="w-11 h-11 rounded-xl bg-gray-100 flex items-center justify-center">
                    <User className="w-5 h-5 text-gray-500" />
                  </div>
                  <span className="text-[10px] text-gray-400">MY</span>
                </Link>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── CHAT LAYOUT (always rendered, shown when not idle) ────── */}
      {/* App Bar */}
      <header className="bg-white px-4 py-3 flex items-center justify-between shadow-sm z-10">
        <button className="p-2">
          <Menu className="w-6 h-6 text-gray-700" />
        </button>
        <h1 className="font-semibold text-gray-900">CashAI 비서</h1>
        <button
          onClick={() => navigate("/shop")}
          className="flex items-center gap-1 bg-[#FF6B00] text-white px-3 py-1.5 rounded-full"
        >
          <span className="text-sm font-semibold">🪙 {points.toLocaleString()} P</span>
        </button>
      </header>

      {/* Chat counter badge */}
      <div className="flex justify-center py-2">
        <div className="bg-white/80 rounded-full px-3 py-1 shadow-sm">
          <p className="text-xs text-gray-500">
            💬 대화 {messageCount}회 · 3번째 대화마다 리워드 광고
          </p>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 pb-4 space-y-3">
        {messages.map((message) => {
          if (message.type === "inline-ad" && message.adInfo) {
            return (
              <div key={message.id} className="max-w-[82%]">
                <InlineAdCard ad={message.adInfo} />
              </div>
            );
          }

          if (message.type === "reward-prompt") {
            return (
              <div key={message.id}>
                <RewardAdPrompt onWatchAd={handleWatchRewardAd} />
              </div>
            );
          }

          return (
            <motion.div
              key={message.id}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              className={`flex ${message.isUser ? "justify-end" : "justify-start"}`}
            >
              {!message.isUser && (
                <div className="w-8 h-8 rounded-full bg-[#5C6BFA] flex items-center justify-center text-white text-xs mr-2 flex-shrink-0 mt-1">
                  AI
                </div>
              )}
              <div
                className={`max-w-[75%] rounded-[20px] px-4 py-3 ${
                  message.isUser
                    ? "bg-[#5C6BFA] text-white rounded-tr-sm"
                    : "bg-white text-gray-900 shadow-sm rounded-tl-sm"
                }`}
              >
                <p className="text-sm whitespace-pre-line">{message.text}</p>
              </div>
            </motion.div>
          );
        })}

        {isLoading && (
          <div className="flex justify-start gap-2 items-end">
            <div className="w-8 h-8 rounded-full bg-[#5C6BFA] flex items-center justify-center text-white text-xs flex-shrink-0">
              AI
            </div>
            <div className="bg-white rounded-[20px] rounded-tl-sm px-4 py-3 shadow-sm">
              <div className="flex gap-1 items-center">
                {[0, 0.2, 0.4].map((delay, i) => (
                  <motion.div
                    key={i}
                    animate={{ y: [0, -4, 0] }}
                    transition={{ repeat: Infinity, duration: 0.7, delay }}
                    className="w-2 h-2 bg-[#5C6BFA]/40 rounded-full"
                  />
                ))}
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Mission Reward Popup */}
      <AnimatePresence>
        {showMissionReward && (
          <motion.div
            initial={{ y: 100, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 100, opacity: 0 }}
            className="absolute bottom-20 left-4 right-4 bg-white rounded-2xl p-4 shadow-2xl border-2 border-[#FF6B00] z-20"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Gift className="w-8 h-8 text-[#FF6B00]" />
                <div>
                  <p className="font-semibold text-gray-900">대화 미션 달성!</p>
                  <p className="text-sm text-gray-600">10P를 받으세요</p>
                </div>
              </div>
              <button
                onClick={claimMissionReward}
                className="bg-[#FF6B00] text-white px-4 py-2 rounded-xl font-semibold"
              >
                받기
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Input Area */}
      <div className="bg-white px-4 py-3 border-t border-gray-100 shadow-sm">
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && !isLoading && handleSend()}
            placeholder="메시지를 입력하세요..."
            className="flex-1 bg-[#F4F6F8] rounded-full px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-[#5C6BFA]/50"
          />
          <button
            onClick={() => handleSend()}
            disabled={!inputValue.trim() || isLoading}
            className="bg-[#5C6BFA] text-white p-3 rounded-full disabled:opacity-40 disabled:cursor-not-allowed transition-opacity"
          >
            <Send className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Reward Ad Modal */}
      <AnimatePresence>
        {showRewardAdModal && (
          <RewardAdModal
            ad={currentRewardAd}
            onComplete={handleRewardAdComplete}
            onClose={handleRewardAdClose}
          />
        )}
      </AnimatePresence>
    </div>
  );
}