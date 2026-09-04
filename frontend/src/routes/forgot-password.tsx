import { createFileRoute } from "@tanstack/react-router";
import { LiveForgot } from "@/components/auth/AuthLive";
export const Route = createFileRoute("/forgot-password")({
  head: () => ({
    meta: [
      { title: "Reset password | Hyperlocal Delivery" },
      { name: "description", content: "Reset password in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Reset password | Hyperlocal Delivery" },
      {
        property: "og:description",
        content: "Reset password in the Hyperlocal Delivery workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveForgot />,
});
