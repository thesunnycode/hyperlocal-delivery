import { Link } from "@tanstack/react-router";
import {
  ArrowRight,
  BarChart3,
  Check,
  LayoutDashboard,
  MapPin,
  Navigation,
  Repeat,
  Share2,
  Smartphone,
  Package,
  Phone,
  Clock3,
} from "lucide-react";
import { Brand } from "@/components/DeliveryApp";
import riderLoop from "@/assets/delivery-rider-loop.mp4.asset.json";
import riderImage from "@/assets/delivery-rider.jpg";

const FEATURES = [
  {
    icon: MapPin,
    title: "Live customer tracking",
    body: "Every delivery gets a private link customers can open on any phone — live status, arrival estimate, and the shop’s contact details. No app needed.",
  },
  {
    icon: Smartphone,
    title: "A rider app built for the road",
    body: "Riders see today’s runs as a simple to-do list, confirm each step with a tap, and call or navigate to the customer in one tap.",
  },
  {
    icon: LayoutDashboard,
    title: "One owner command center",
    body: "Create deliveries, assign riders and watch every parcel move — pickups, transit, doorstep — from a single board.",
  },
  {
    icon: Repeat,
    title: "Handles the messy days",
    body: "Missed attempts, returns and reassignments are one tap each, with a full history kept for every parcel.",
  },
  {
    icon: BarChart3,
    title: "Reports that add up",
    body: "A daily register, rider performance and delivery trends — so you always know how the business is moving.",
  },
  {
    icon: Share2,
    title: "Share in one tap",
    body: "Tracking links are ready for WhatsApp and SMS, so customers can follow their parcel or pass the link to family.",
  },
];

const STEPS = [
  {
    n: "1",
    title: "Create a delivery",
    body: "Add the customer’s name, phone and address. The parcel is assigned to a rider and gets its own tracking link instantly.",
  },
  {
    n: "2",
    title: "The rider takes it away",
    body: "They confirm pickup, start transit and head out for delivery — each step confirmed and time-stamped as it happens.",
  },
  {
    n: "3",
    title: "The customer watches it arrive",
    body: "The tracking page shows live progress, an arrival estimate and every update, until the parcel reaches the door.",
  },
];

function RiderVideo() {
  return (
    <video autoPlay loop muted playsInline poster={riderImage} aria-hidden="true">
      <source src="/delivery-rider-loop.webm" type="video/webm" />
      <source src={riderLoop.url} type="video/mp4" />
    </video>
  );
}

export function LandingPage() {
  return (
    <div className="landing">
      <header className="land-nav">
        <Brand />
        <nav aria-label="Landing sections">
          <a href="#features">Features</a>
          <a href="#how">How it works</a>
          <a href="#preview">Inside the app</a>
        </nav>
        <Link to="/login" className="land-signin">
          Sign in
        </Link>
        <Link to="/register" className="land-cta">
          Start free <ArrowRight size={15} />
        </Link>
      </header>

      <section className="land-hero">
        <div className="land-hero-copy">
          <span className="eyebrow">HYPERLOCAL DELIVERY</span>
          <h1>
            Every parcel, tracked.
            <br />
            Every rider, in view.
          </h1>
          <p>
            Run your local deliveries like clockwork — create a delivery, your rider picks it up,
            and the customer watches it arrive live. Whether you run a shop or you’re waiting on a
            parcel, everything lands in one place.
          </p>
          <div className="land-hero-actions">
            <Link to="/register" className="land-btn land-btn-primary">
              Create your business <ArrowRight size={17} />
            </Link>
            <Link to="/login" className="land-btn land-btn-ghost">
              Sign in
            </Link>
          </div>
          <ul className="land-hero-points">
            <li>
              <Check size={15} /> Set up in a minute
            </li>
            <li>
              <Check size={15} /> Riders need only a phone
            </li>
            <li>
              <Check size={15} /> Customers need no app at all
            </li>
          </ul>
        </div>
        <div className="land-hero-art" aria-hidden="true">
          <RiderVideo />
        </div>
      </section>

      <section className="land-section" id="features">
        <div className="land-head">
          <span className="eyebrow">WHAT YOU GET</span>
          <h2>Built for the whole journey</h2>
          <p>From the owner’s desk to the rider’s handlebars to the customer’s doorstep.</p>
        </div>
        <div className="land-features">
          {FEATURES.map((f) => (
            <article key={f.title} className="land-feature">
              <span className="land-feature-icon">
                <f.icon />
              </span>
              <h3>{f.title}</h3>
              <p>{f.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="land-section land-how" id="how">
        <div className="land-head">
          <span className="eyebrow">HOW IT WORKS</span>
          <h2>Three steps, door to door</h2>
        </div>
        <ol className="land-steps">
          {STEPS.map((s) => (
            <li key={s.n}>
              <span className="land-step-num">{s.n}</span>
              <div>
                <h3>{s.title}</h3>
                <p>{s.body}</p>
              </div>
            </li>
          ))}
        </ol>
        <p className="land-how-note">
          <Navigation size={15} /> The tracking link works on any phone — customers just tap and
          watch.
        </p>
      </section>

      <section className="land-section" id="preview">
        <div className="land-head">
          <span className="eyebrow">INSIDE THE APP</span>
          <h2>Three screens, one story</h2>
          <p>The owner’s board, the rider’s run, and the customer’s tracking page.</p>
        </div>
        <div className="land-previews">
          <figure className="land-preview land-preview-owner">
            <figcaption>
              <LayoutDashboard size={16} /> Owner console <span>01 / 03</span>
            </figcaption>
            <div className="preview-frame preview-desktop">
              <div className="preview-side">
                <strong>
                  H<span> Delivery</span>
                </strong>
                <small>OVERVIEW</small>
                <b>▦ &nbsp; Shipments</b>
                <span>♙ &nbsp; Riders</span>
                <span>▤ &nbsp; Register</span>
                <span>▥ &nbsp; Reports</span>
              </div>
              <div className="preview-main">
                <div className="preview-top">
                  <div>
                    <small>GOOD MORNING</small>
                    <h3>Shipments</h3>
                  </div>
                  <span className="preview-action">+ New delivery</span>
                </div>
                <div className="preview-metrics">
                  <div>
                    <small>All deliveries</small>
                    <strong>24</strong>
                  </div>
                  <div>
                    <small>In progress</small>
                    <strong>08</strong>
                  </div>
                  <div>
                    <small>Delivered</small>
                    <strong>16</strong>
                  </div>
                </div>
                <div className="preview-table">
                  <div className="preview-table-head">
                    <span>RECIPIENT</span>
                    <span>RIDER</span>
                    <span>STATUS</span>
                  </div>
                  <div>
                    <b>
                      Meera Sharma<small>#HL-1042 · Indiranagar</small>
                    </b>
                    <span>Rider 1</span>
                    <em>In transit</em>
                  </div>
                  <div>
                    <b>
                      Rahul Verma<small>#HL-1041 · Koramangala</small>
                    </b>
                    <span>Rider 2</span>
                    <em>Out for delivery</em>
                  </div>
                  <div>
                    <b>
                      Dev Nair<small>#HL-1040 · HSR Layout</small>
                    </b>
                    <span>Rider 1</span>
                    <em>Delivered</em>
                  </div>
                </div>
              </div>
            </div>
          </figure>
          <figure className="land-preview land-preview-rider">
            <figcaption>
              <Smartphone size={16} /> Rider app <span>02 / 03</span>
            </figcaption>
            <div className="preview-frame preview-mobile">
              <div className="preview-mobile-top">
                <strong>Today’s run</strong>
                <span>3 stops left</span>
              </div>
              <small className="preview-kicker">UP NEXT · #HL-1042</small>
              <div className="preview-rider-card">
                <span className="preview-rider-icon">
                  <Package size={20} />
                </span>
                <h3>Meera Sharma</h3>
                <p>
                  12th Main Road, Indiranagar
                  <br />
                  Bengaluru
                </p>
                <div className="preview-rider-meta">
                  <span>
                    <MapPin size={13} /> 2.1 km away
                  </span>
                  <span>
                    <Phone size={13} /> Call
                  </span>
                </div>
              </div>
              <div className="preview-stage">
                <span>Picked up</span>
                <strong>In transit</strong>
              </div>
              <div className="preview-stage-track">
                <i />
                <i />
                <i />
                <i />
                <i />
              </div>
              <div className="preview-mobile-action">
                Start transit <ArrowRight size={15} />
              </div>
            </div>
          </figure>
          <figure className="land-preview land-preview-track">
            <figcaption>
              <MapPin size={16} /> Customer tracking <span>03 / 03</span>
            </figcaption>
            <div className="preview-frame preview-mobile preview-customer">
              <div className="preview-mobile-top">
                <strong>HYPERLOCAL</strong>
                <span>LIVE TRACKING</span>
              </div>
              <div className="preview-status">
                <small>YOUR DELIVERY</small>
                <h3>Out for delivery</h3>
                <p>
                  <Clock3 size={13} /> Arriving in about 15 min
                </p>
                <div className="preview-status-bars">
                  <i />
                  <i />
                  <i />
                  <i />
                  <i />
                </div>
              </div>
              <div className="preview-address">
                <small>DELIVERING TO</small>
                <strong>Sameer Q.</strong>
                <span>Horamavu Main Rd, Bengaluru</span>
              </div>
              <div className="preview-updates">
                <small>RECENT UPDATES</small>
                <div>
                  <i />
                  <span>
                    <b>Out for delivery</b>
                    <small>just now</small>
                  </span>
                </div>
                <div>
                  <i />
                  <span>
                    <b>In transit</b>
                    <small>18 min ago</small>
                  </span>
                </div>
                <div>
                  <i />
                  <span>
                    <b>Picked up</b>
                    <small>32 min ago</small>
                  </span>
                </div>
              </div>
            </div>
          </figure>
        </div>
        <p className="land-preview-note">
          Illustrative previews · Actual screens may vary with delivery details
        </p>
      </section>

      <section className="land-band">
        <h2>Ready to run your deliveries like clockwork?</h2>
        <p>
          Create your business in a minute — your first delivery and its live tracking link are
          minutes behind that.
        </p>
        <div className="land-band-actions">
          <Link to="/register" className="land-btn land-btn-coral">
            Start free <ArrowRight size={17} />
          </Link>
          <Link to="/login" className="land-btn land-btn-ghost">
            Sign in
          </Link>
        </div>
      </section>

      <footer className="land-foot">
        <Brand dark />
        <p>Every delivery, handled beautifully.</p>
        <nav aria-label="Footer">
          <Link to="/login">Sign in</Link>
          <Link to="/register">Create a business</Link>
          <Link to="/forgot-password">Reset password</Link>
        </nav>
        <small>© 2026 Hyperlocal Delivery</small>
      </footer>
    </div>
  );
}
