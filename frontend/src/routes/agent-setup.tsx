import { createFileRoute } from "@tanstack/react-router";
import { LiveSetup } from "@/components/auth/AuthLive";
export const Route = createFileRoute("/agent-setup")({
  head: () => ({
    meta: [
      { title: "Set up account | Hyperlocal Delivery" },
      { name: "description", content: "Set up account in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Set up account | Hyperlocal Delivery" },
      {
        property: "og:description",
        content: "Set up account in the Hyperlocal Delivery workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveSetup />,
});
