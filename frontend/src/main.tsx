import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.tsx';

// Token layer first: every scoped sheet below reads --hl-* through its own
// prefix. See styles/tokens.css for the palette, the type ramp, the spacing
// and radius scales, and the motion rules.
import './styles/tokens.css';

import './styles/base.css';
import './styles/auth.css';
import './styles/rider.css';
import './styles/agent.css';
import './styles/track.css';
import './styles/reports.css';
import './styles/owner.css';
import './styles/edge.css';
import './styles/overlay.css';

// The bridge is imported LAST on purpose. It re-points each scope's private
// tokens (--ow-*, --ag-*, --tk-* …) at the shared roles, and custom-property
// declarations at equal specificity resolve last-wins — so this overrides the
// literal hexes still sitting at the top of the six scoped sheets. Those
// literals are dead and listed for deletion in REDESIGN-NOTES.md; keeping them
// in this pass makes the diff reviewable and the change revertible in two
// files. It also carries the display-type corrections and the motion layer,
// both of which need to win over the sheets they refine.
import './styles/scopes.css';

// index.html always ships the #root div, and createRoot(null) would throw
// here exactly as it did before — the assertion keeps that behaviour
// rather than substituting a different error.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
