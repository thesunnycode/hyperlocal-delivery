import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { useNavigate } from "@tanstack/react-router";
import { getRole, getToken } from "@/lib/hl/apiClient";
import { LandingPage } from "@/components/landing/LandingPage";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Hyperlocal Delivery — Local deliveries, tracked door to door" },
      {
        name: "description",
        content:
          "Create a delivery, your rider picks it up, and the customer watches it arrive live. The simple way for local businesses to run and track deliveries.",
      },
      {
        property: "og:title",
        content: "Hyperlocal Delivery — Local deliveries, tracked door to door",
      },
      {
        property: "og:description",
        content:
          "Create a delivery, your rider picks it up, and the customer watches it arrive live. The simple way for local businesses to run and track deliveries.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Home,
});

function Home() {
  const navigate = useNavigate();
  useEffect(() => {
    if (getToken())
      navigate({
        to: getRole() === "AGENT" ? "/agent/assignments" : "/owner/shipments",
        replace: true,
      });
  }, [navigate]);
  return <LandingPage />;
}
