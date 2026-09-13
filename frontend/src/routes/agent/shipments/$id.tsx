import { createFileRoute } from "@tanstack/react-router";
import { LiveAgentDetail } from "@/components/rider/RiderLive";
export const Route = createFileRoute("/agent/shipments/$id")({
  head: () => ({
    meta: [
      { title: "Delivery details | Hyperlocal Delivery" },
      { name: "description", content: "Delivery details in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Delivery details | Hyperlocal Delivery" },
      {
        property: "og:description",
        content: "Delivery details in the Hyperlocal Delivery workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveAgentDetail />,
});
