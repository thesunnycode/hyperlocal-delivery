import { createFileRoute } from "@tanstack/react-router";
import { LiveTracking } from "@/components/tracking/TrackingLive";
export const Route = createFileRoute("/track/$token")({
  head: () => ({
    meta: [
      { title: "Track delivery | Hyperlocal Delivery" },
      { name: "description", content: "See live status and updates for your parcel." },
      { property: "og:title", content: "Track delivery | Hyperlocal Delivery" },
      { property: "og:description", content: "See live status and updates for your parcel." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveTracking />,
});
