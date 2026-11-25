import { useEffect, useState } from "react";
import { API_BASE_URL } from "../../api";

/** Whether anyone may sign up. false (default): the HOD or college admin creates accounts. */
export function useRegistrationOpen() {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    let alive = true;
    fetch(`${API_BASE_URL}/api/auth/registration`)
      .then((response) => (response.ok ? response.json() : { open: false }))
      .then((data) => alive && setOpen(Boolean(data.open)))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  return open;
}
