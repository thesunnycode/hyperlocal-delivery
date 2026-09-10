import { createFileRoute } from "@tanstack/react-router";
import { LiveAssignments } from "@/components/rider/RiderLive";
export const Route = createFileRoute("/agent/assignments")({
  head: () => ({
    meta: [
      { title: "Assignments | Hyperlocal Delivery" },
      { name: "description", content: "Assignments in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Assignments | Hyperlocal Delivery" },
      { property: "og:description", content: "Assignments in the Hyperlocal Delivery workspace." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveAssignments />,
});
