import { useEffect, useState } from "react";
import api, { errorMessage } from "../apiClient";
import { roleLabel } from "../permissions";

const ROLES = ["ADMIN", "FACULTY", "STUDENT"];

// Admin only: see all accounts and change their role.
function Users({ currentUserId }) {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [savingId, setSavingId] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get("/api/admin/users")
      .then(({ data }) => !cancelled && setUsers(Array.isArray(data) ? data : []))
      .catch((err) => !cancelled && setError(errorMessage(err, "Couldn't load users.")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  const changeRole = async (user, role) => {
    setSavingId(user.id);
    setError("");
    try {
      const { data } = await api.put(`/api/admin/users/${user.id}/role`, { role });
      setUsers((list) => list.map((item) => (item.id === data.id ? data : item)));
    } catch (err) {
      setError(errorMessage(err, "Couldn't change the role."));
    } finally {
      setSavingId(null);
    }
  };

  return (
    <main className="page-content cp-users">
      <header className="cp-report-header">
        <div>
          <h1>Users</h1>
          <p>Change what each account can do. Role changes apply at the next login.</p>
        </div>
      </header>

      {error && (
        <p className="cp-report-notice" role="alert">
          {error}
        </p>
      )}

      <div className="cp-table-wrap">
        {loading ? (
          <p className="cp-muted">Loading users…</p>
        ) : (
          <table className="cp-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>{user.name}</td>
                  <td>{user.email}</td>
                  <td>
                    {user.id === currentUserId ? (
                      <span className="cp-role-badge">{roleLabel(user.role)} (you)</span>
                    ) : (
                      <select
                        aria-label={`Role for ${user.name}`}
                        value={user.role}
                        disabled={savingId === user.id}
                        onChange={(event) => changeRole(user, event.target.value)}
                      >
                        {ROLES.map((role) => (
                          <option key={role} value={role}>
                            {roleLabel(role)}
                          </option>
                        ))}
                      </select>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </main>
  );
}

export default Users;
