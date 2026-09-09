import { createFileRoute } from "@tanstack/react-router";
import { LiveReports } from "@/components/owner/OwnerLive";
export const Route = createFileRoute("/owner/reports/overview")({
  head: () => ({
    meta: [
      { title: "Reports overview | Hyperlocal Delivery" },
      { name: "description", content: "Reports overview in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Reports overview | Hyperlocal Delivery" },
      {
        property: "og:description",
        content: "Reports overview in the Hyperlocal Delivery workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveReports mode="overview" />,
});
