import { createFileRoute } from "@tanstack/react-router";
import { LiveShipments } from "@/components/owner/OwnerLive";
export const Route = createFileRoute("/owner/shipments")({
  head: () => ({
    meta: [
      { title: "Shipments | Hyperlocal Delivery" },
      { name: "description", content: "Shipments in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Shipments | Hyperlocal Delivery" },
      { property: "og:description", content: "Shipments in the Hyperlocal Delivery workspace." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveShipments />,
});
