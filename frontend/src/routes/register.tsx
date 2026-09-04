import { createFileRoute } from "@tanstack/react-router";
import { LiveSignup } from "@/components/auth/AuthLive";
export const Route = createFileRoute("/register")({
  head: () => ({
    meta: [
      { title: "Create account | Hyperlocal Delivery" },
      { name: "description", content: "Create account in the Hyperlocal Delivery workspace." },
      { property: "og:title", content: "Create account | Hyperlocal Delivery" },
      {
        property: "og:description",
        content: "Create account in the Hyperlocal Delivery workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: () => <LiveSignup />,
});
