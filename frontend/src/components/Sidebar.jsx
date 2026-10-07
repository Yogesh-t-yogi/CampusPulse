import {
  LayoutDashboard,
  BookOpen,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  Sparkles
} from "lucide-react";

function Sidebar({ activePage, setActivePage }) {

  const menuItems = [
    {
      name: "Dashboard",
      icon: LayoutDashboard
    },
    {
      name: "Curriculum",
      icon: BookOpen
    },
    {
      name: "Job Market",
      icon: BriefcaseBusiness
    },
    {
      name: "Gap Analysis",
      icon: ChartNoAxesCombined
    },
    {
      name: "AI Recommendations",
      icon: Sparkles
    }
  ];

  return (
    <aside className="sidebar">

      <div className="sidebar-logo">

        <div className="logo-icon">
          <Sparkles size={22} />
        </div>

        <div>
          <h2>CampusPulse</h2>
          <span>AI Intelligence</span>
        </div>

      </div>


      <nav className="sidebar-nav">

        <p className="nav-title">
          MAIN MENU
        </p>

        {menuItems.map((item) => {

          const Icon = item.icon;

          return (
            <button
              key={item.name}
              onClick={() => setActivePage(item.name)}
              className={
                activePage === item.name
                  ? "nav-item active"
                  : "nav-item"
              }
            >

              <Icon size={19} />

              <span>
                {item.name}
              </span>

            </button>
          );

        })}

      </nav>


      <div className="sidebar-footer">

        <div className="ai-status">

          <span className="status-dot"></span>

          <div>
            <strong>AI Engine</strong>
            <small>Connected</small>
          </div>

        </div>

      </div>

    </aside>
  );
}

export default Sidebar;