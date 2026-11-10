import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import "./index.css";
import { applyStoredPreferences } from "./theme/theme";
import { BrowserRouter } from "react-router";
import App from "./App.jsx";
import { ToastProvider } from "./ui";


applyStoredPreferences();

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <App />
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>
);