import { RouterProvider } from "react-router";
import { router } from "./routes";
import { PointsProvider } from "./contexts/PointsContext";

export default function App() {
  return (
    <PointsProvider>
      <RouterProvider router={router} />
    </PointsProvider>
  );
}
