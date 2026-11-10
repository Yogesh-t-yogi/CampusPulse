import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import "./index.css";
import { applyStoredPreferences } from "./theme/theme";
import App from "./App.jsx";


applyStoredPreferences();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);