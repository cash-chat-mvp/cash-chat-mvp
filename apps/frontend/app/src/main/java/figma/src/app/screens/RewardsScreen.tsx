import { useState } from "react";
import { CheckCircle, Play, Calendar, MessageCircle, Trophy } from "lucide-react";
import { usePoints } from "../contexts/PointsContext";
import { motion } from "motion/react";
import { Progress } from "../components/ui/progress";

interface Mission {
  id: string;
  title: string;
  description: string;
  points: number;
  icon: React.ComponentType<{ className?: string }>;
  completed: boolean;
  progress?: number;
  maxProgress?: number;
}

export function RewardsScreen() {
  const { points, addPoints, messageCount } = usePoints();
  const [claimedMissionIds, setClaimedMissionIds] = useState<string[]>([]);

  const missions: Mission[] = [
    {
      id: "1",
      title: "출석 체크",
      description: "오늘 하루 출석하고 포인트 받기",
      points: 10,
      icon: Calendar,
      completed: claimedMissionIds.includes("1"),
    },
    {
      id: "2",
      title: "채팅 미션",
      description: "오늘 AI와 10번 대화하기",
      points: 50,
      icon: MessageCircle,
      completed: claimedMissionIds.includes("2"),
      progress: Math.min(messageCount, 10),
      maxProgress: 10,
    },
    {
      id: "3",
      title: "동영상 시청",
      description: "광고 보고 룰렛 돌리기",
      points: 100,
      icon: Play,
      completed: claimedMissionIds.includes("3"),
    },
  ];

  const handleClaim = (missionId: string, missionPoints: number) => {
    setClaimedMissionIds((prev) => [...prev, missionId]);
    addPoints(missionPoints);
  };

  const targetPoints = 4500; // 커피 교환 목표
  const progressPercent = Math.min((points / targetPoints) * 100, 100);
  const remainingPoints = Math.max(targetPoints - points, 0);

  return (
    <div className="h-full overflow-y-auto bg-[#F4F6F8]">
      {/* Header */}
      <header className="bg-white px-4 py-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-4">리워드 미션</h1>
        
        {/* Progress Banner */}
        <div className="bg-gradient-to-r from-[#5C6BFA] to-[#4A5AE8] rounded-2xl p-4 text-white">
          <div className="flex items-center justify-between mb-2">
            <p className="font-semibold">커피 교환까지</p>
            <p className="text-sm">
              {points.toLocaleString()} / {targetPoints.toLocaleString()} P
            </p>
          </div>
          <Progress value={progressPercent} className="h-2 bg-white/30" />
          <p className="text-sm mt-2 text-white/80">
            {remainingPoints.toLocaleString()}P 더 모으면 스타벅스 아메리카노!
          </p>
        </div>
      </header>

      {/* Missions List */}
      <div className="p-4 space-y-3">
        {missions.map((mission) => {
          const Icon = mission.icon;
          const canClaim =
            !mission.completed &&
            (mission.maxProgress ? (mission.progress ?? 0) >= mission.maxProgress : true);

          return (
            <motion.div
              key={mission.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="bg-white rounded-2xl p-4 shadow-sm"
            >
              <div className="flex items-start gap-3">
                <div className="bg-[#F4F6F8] p-3 rounded-xl">
                  <Icon className="w-6 h-6 text-[#5C6BFA]" />
                </div>
                
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900 mb-1">
                    {mission.title}
                  </h3>
                  <p className="text-sm text-gray-600 mb-2">
                    {mission.description}
                  </p>
                  
                  {mission.maxProgress && (
                    <div className="mb-3">
                      <Progress
                        value={((mission.progress ?? 0) / mission.maxProgress) * 100}
                        className="h-2"
                      />
                      <p className="text-xs text-gray-500 mt-1">
                        진행도 {mission.progress}/{mission.maxProgress}
                      </p>
                    </div>
                  )}

                  <div className="flex items-center justify-between">
                    <span className="text-[#FF6B00] font-bold">
                      +{mission.points}P
                    </span>
                    
                    {mission.completed ? (
                      <div className="flex items-center gap-1 text-gray-400 text-sm">
                        <CheckCircle className="w-4 h-4" />
                        완료
                      </div>
                    ) : (
                      <button
                        onClick={() => handleClaim(mission.id, mission.points)}
                        disabled={!canClaim}
                        className={`px-4 py-2 rounded-xl font-semibold text-sm ${
                          canClaim
                            ? "bg-[#FF6B00] text-white"
                            : "bg-gray-200 text-gray-400 cursor-not-allowed"
                        }`}
                      >
                        {canClaim ? "받기" : "미달성"}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </motion.div>
          );
        })}

        {/* Special Event Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="bg-gradient-to-br from-[#FF6B00] to-[#FF8C3A] rounded-2xl p-6 text-white mt-6"
        >
          <div className="flex items-center gap-3 mb-3">
            <Trophy className="w-8 h-8" />
            <div>
              <h3 className="font-bold text-lg">행운의 룰렛</h3>
              <p className="text-sm text-white/80">최대 1,000P 획득 기회!</p>
            </div>
          </div>
          <button className="w-full bg-white text-[#FF6B00] py-3 rounded-xl font-bold mt-2">
            광고 보고 룰렛 돌리기
          </button>
        </motion.div>
      </div>
    </div>
  );
}
