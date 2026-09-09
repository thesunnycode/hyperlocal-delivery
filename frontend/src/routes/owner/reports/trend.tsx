import { createFileRoute } from "@tanstack/react-router";
import { LiveReports } from "@/components/owner/OwnerLive";
export const Route = createFileRoute("/owner/reports/trend")({
  head: () => ({
    meta: [
      { title: "Delivery trend | Hyperlocal Delivery" },
      { name: "description", content: "Delivery trend in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Delivery trend | Hyperlocal Delivery" },
      {
        property: "og:description",
        content: "Delivery trend in the Hyperlocal Delivery workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveReports mode="trend" />,
});
