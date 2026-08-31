import { useState } from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import {
  BarChart3,
  ChevronRight,
  ClipboardList,
  LogOut,
  Menu,
  Package,
  Search,
  UserRound,
  Users,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { clearSession } from "@/lib/hl/apiClient";

const ownerNav = [
  { href: "/owner/shipments", label: "Shipments", icon: Package },
  { href: "/owner/register", label: "Register", icon: ClipboardList },
  { href: "/owner/agents", label: "Riders", icon: Users },
  { href: "/owner/reports/overview", label: "Reports", icon: BarChart3 },
  { href: "/owner/account", label: "Account", icon: UserRound },
] as const;
function Brand({ dark = false }: { dark?: boolean }) {
  return (
    <Link to="/owner/shipments" className={`brand ${dark ? "brand-dark" : ""}`}>
      <span className="brand-mark">H</span>
      <span>
        <strong>Hyperlocal</strong>
        <small>DELIVERY</small>
      </span>
    </Link>
  );
}
function Sidebar({ close }: { close: () => void }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const navigate = useNavigate();
  return (
    <aside className="sidebar">
      <Brand dark />
      <div className="side-label">WORKSPACE</div>
      <nav className="side-nav" aria-label="Main navigation">
        {ownerNav.map(({ href, label, icon: Icon }, i) => (
          <Link
            key={href}
            to={href}
            onClick={close}
            className={`side-link ${path.startsWith(href) || (label === "Reports" && path.startsWith("/owner/reports")) ? "active" : ""}`}
          >
            <Icon size={19} />
            <span>{label}</span>
            <small>0{i + 1}</small>
          </Link>
        ))}
      </nav>
      <div className="side-bottom">
        <div className="side-pulse">
          <span className="pulse-dot" /> LIVE WORKSPACE
        </div>
        <p>Connected to your Hyperlocal service.</p>
      </div>
      <div className="side-profile-row">
        <Link to="/owner/account" className="side-profile" onClick={close}>
          <span className="avatar">H</span>
          <span>
            <strong>Your account</strong>
            <small>Profile & settings</small>
          </span>
          <ChevronRight size={16} />
        </Link>
        <Button
          variant="ghost"
          size="icon"
          className="side-signout"
          aria-label="Sign out"
          onClick={() => {
            close();
            clearSession();
            navigate({ to: "/login" });
          }}
        >
          <LogOut size={17} />
        </Button>
      </div>
    </aside>
  );
}
function Workspace({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="workspace">
      <div className={`side-holder ${open ? "side-open" : ""}`}>
        <Sidebar close={() => setOpen(false)} />
      </div>
      {open && <div className="nav-scrim" onClick={() => setOpen(false)} />}
      <div className="workspace-main">
        <div className="mobile-top">
          <Brand dark />
          <Button
            size="icon"
            variant="ghost"
            aria-label={open ? "Close menu" : "Open menu"}
            onClick={() => setOpen(!open)}
          >
            {open ? <X /> : <Menu />}
          </Button>
        </div>
        {children}
      </div>
    </div>
  );
}
function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {action}
    </header>
  );
}
function PanelHeading({ title, aside }: { title: string; aside?: React.ReactNode }) {
  return (
    <div className="section-heading">
      <h2>{title}</h2>
      {aside}
    </div>
  );
}
function Empty({ title, body }: { title: string; body: string }) {
  return (
    <div className="empty">
      <Search size={24} />
      <h3>{title}</h3>
      <p>{body}</p>
    </div>
  );
}
function AgentFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="agent-shell">
      <header className="agent-header">
        <Brand />
        <nav>
          <Link to="/agent/assignments">Assignments</Link>
          <Link to="/agent/account">Account</Link>
        </nav>
      </header>
      {children}
      <nav className="agent-mobile-nav">
        <Link to="/agent/assignments">
          <Package size={20} />
          Today
        </Link>
        <Link to="/agent/account">
          <UserRound size={20} />
          Account
        </Link>
      </nav>
    </div>
  );
}

export { AgentFrame, Brand, Workspace, PageHeader, PanelHeading, Empty };
