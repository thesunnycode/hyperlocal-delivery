import { useNavigate } from 'react-router-dom';
import EdgeStateCard from '../../components/EdgeStateCard.tsx';

/**
 * AD · 404 — a mistyped or stale reporting URL. Distinct from AD4's
 * no-rows-match (a valid query returning nothing, recovered with "Clear
 * filters"): this is a route that never existed, so recovery is navigation.
 *
 * Inline, not full-screen: it renders inside the owner console's
 * <Outlet>, so the sidebar stays. This visitor is a legitimate owner on a bad link,
 * and taking their navigation away would be the wrong reading of the error.
 */
export default function AdminNotFoundPage() {
  const navigate = useNavigate();
  return (
    <EdgeStateCard
      code="404"
      title="No such report"
      body={<>That address doesn&rsquo;t match any report we publish. It may be a saved link from an older version of the dashboard.</>}
      actions={[
        { label: 'Back to overview', onClick: () => navigate('/owner/reports/overview') },
        { label: 'Open the register', onClick: () => navigate('/owner/register') }
      ]}
      foot="Looking for one shipment rather than a report? The register searches by token, customer or rider."
    />
  );
}
