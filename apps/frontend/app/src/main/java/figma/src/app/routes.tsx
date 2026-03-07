import { createBrowserRouter } from "react-router";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import { ChatScreen } from "./screens/ChatScreen";
import { RewardsScreen } from "./screens/RewardsScreen";
import { ShopScreen } from "./screens/ShopScreen";
import { MyPageScreen } from "./screens/MyPageScreen";
import { MainLayout } from "./components/MainLayout";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <OnboardingScreen />,
  },
  {
    element: <MainLayout />,
    children: [
      {
        path: "/chat",
        element: <ChatScreen />,
      },
      {
        path: "/rewards",
        element: <RewardsScreen />,
      },
      {
        path: "/shop",
        element: <ShopScreen />,
      },
      {
        path: "/mypage",
        element: <MyPageScreen />,
      },
    ],
  },
]);
