import { createContext, useContext } from "react";

// Toast context lives here so ui/index.jsx only exports components.
export const ToastContext = createContext(() => {});

// useToast()("Saved") or useToast()("Couldn't save", "danger")
export function useToast() {
  return useContext(ToastContext);
}
