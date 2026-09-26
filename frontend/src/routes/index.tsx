import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { getRole, getToken } from "@/lib/hl/apiClient";
export const Route = createFileRoute("/")({ head: () => ({ meta: [{ title: "Hyperlocal Delivery | Local delivery management" }, { name: "description", content: "Assign, track and deliver local orders with riders and live customer tracking." }, { property: "og:title", content: "Hyperlocal Delivery | Local delivery management" }, { property: "og:description", content: "Assign, track and deliver local orders with riders and live customer tracking." }, { property: "og:type", content: "website" }, { name: "twitter:card", content: "summary_large_image" }] }), component: Home });

function Home() {
  const navigate = useNavigate();
  useEffect(() => {
    navigate({ to: !getToken() ? "/login" : getRole() === "AGENT" ? "/agent/assignments" : "/owner/shipments", replace: true });
  }, [navigate]);
  return <div className="live-loading"><Loader2 className="spin" /> Opening your workspace…</div>;
}
