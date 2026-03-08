import { createContext, useContext, useState, ReactNode } from "react";

interface PointsContextType {
  points: number;
  addPoints: (amount: number) => void;
  spendPoints: (amount: number) => boolean;
  messageCount: number;
  incrementMessageCount: () => void;
}

const PointsContext = createContext<PointsContextType | undefined>(undefined);

export function PointsProvider({ children }: { children: ReactNode }) {
  const [points, setPoints] = useState(1250);
  const [messageCount, setMessageCount] = useState(0);

  const addPoints = (amount: number) => {
    setPoints((prev) => prev + amount);
  };

  const spendPoints = (amount: number): boolean => {
    if (points >= amount) {
      setPoints((prev) => prev - amount);
      return true;
    }
    return false;
  };

  const incrementMessageCount = () => {
    setMessageCount((prev) => prev + 1);
  };

  return (
    <PointsContext.Provider
      value={{ points, addPoints, spendPoints, messageCount, incrementMessageCount }}
    >
      {children}
    </PointsContext.Provider>
  );
}

export function usePoints() {
  const context = useContext(PointsContext);
  if (!context) {
    throw new Error("usePoints must be used within PointsProvider");
  }
  return context;
}
