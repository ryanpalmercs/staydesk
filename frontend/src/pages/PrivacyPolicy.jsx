const MO_315_015_URL = 'https://revisor.mo.gov/main/OneSection.aspx?section=315.015';
const MO_407_1500_URL = 'https://revisor.mo.gov/main/OneSection.aspx?section=407.1500';

const sections = [
    {
        id: 'introduction',
        title: '1. Introduction',
        body: (
            <p>
                Martin House Motel ("we," "us," or "our") is committed to protecting the privacy of our
                guests and website visitors. This Privacy Policy explains how we collect, use, store, and
                protect your personal information when you make a reservation, check in, or interact with
                our property management system (StayDesk).
            </p>
        ),
    },
    {
        id: 'information-we-collect',
        title: '2. Information We Collect',
        body: (
            <>
                <p className="mb-3">We collect the following personal information:</p>
                <ul className="list-disc pl-6 space-y-1">
                    <li>Full name</li>
                    <li>Physical address</li>
                    <li>Email address</li>
                    <li>Phone number</li>
                    <li>Payment card information (tokenized — we do not store raw card numbers)</li>
                    <li>Reservation and stay details (room number, check-in/check-out dates, length of stay)</li>
                    <li>Any notes or communications related to your stay</li>
                </ul>
            </>
        ),
    },
    {
        id: 'how-we-use-your-information',
        title: '3. How We Use Your Information',
        body: (
            <>
                <p className="mb-3">We use your information to:</p>
                <ul className="list-disc pl-6 space-y-1">
                    <li>Process and manage your reservation</li>
                    <li>Facilitate check-in and check-out</li>
                    <li>Send SMS notifications including door access codes, checkout confirmations, and reservation reminders</li>
                    <li>Process payments securely through our payment processor</li>
                    <li>Maintain guest registration records as required by Missouri law</li>
                    <li>Generate internal reports for business operations</li>
                    <li>Comply with legal obligations</li>
                </ul>
            </>
        ),
    },
    {
        id: 'sms-notifications-and-consent',
        title: '4. SMS Notifications and Consent',
        body: (
            <p>
                If you provide a phone number and separately opt in, we may send SMS text messages related to your
                stay (door access codes, check-in/checkout confirmations, reservation reminders). SMS consent is
                never required to book or complete a reservation. Full terms, message frequency, and opt-out
                instructions are in our{' '}
                <a href="/sms-terms" className="text-rust underline">SMS Terms &amp; Conditions</a>.
            </p>
        ),
    },
    {
        id: 'payment-information',
        title: '5. Payment Information',
        body: (
            <p>
                We use industry-standard payment processing. Card payments are handled by our payment
                processor and tokenized — we do not store raw card numbers, CVV codes, or full card data at
                any time. All payment data is handled in compliance with PCI DSS standards.
            </p>
        ),
    },
    {
        id: 'data-retention',
        title: '6. Data Retention',
        body: (
            <>
                <p className="mb-3">
                    We retain guest registration records for a minimum of three (3) years from the date of
                    checkout as required by Missouri law (Section{' '}
                    <a href={MO_315_015_URL} className="text-rust underline" target="_blank" rel="noopener noreferrer">
                        315.015, RSMo
                    </a>
                    ). Financial transaction records are retained for a minimum of three (3) years for tax
                    compliance purposes. Personally identifiable information is permanently deleted after the
                    applicable retention period. Anonymized transactional data (stay duration, room type,
                    revenue) may be retained indefinitely for business analytics purposes.
                </p>
                <p>
                    Reservations that were cancelled prior to check-in with no charge are retained for three
                    (3) years. Cancelled reservations where a cancellation or no-show fee was charged are
                    retained for up to ten (10) years in anonymized form to comply with Missouri's statute of
                    limitations for written contracts.
                </p>
            </>
        ),
    },
    {
        id: 'data-security',
        title: '7. Data Security',
        body: (
            <>
                <p className="mb-3">
                    We implement industry-standard security measures to protect your personal information
                    including:
                </p>
                <ul className="list-disc pl-6 space-y-1 mb-3">
                    <li>Encryption of all personally identifiable information at rest</li>
                    <li>Secure HTTPS transmission for all data in transit</li>
                    <li>Role-based access controls limiting data access to authorized staff only</li>
                    <li>Tokenized payment handling through our payment processor</li>
                </ul>
                <p>
                    While we take reasonable precautions, no method of data transmission or storage is 100%
                    secure. In the event of a data breach affecting your personal information, we will notify
                    you as required by Missouri Revised Statutes Section{' '}
                    <a href={MO_407_1500_URL} className="text-rust underline" target="_blank" rel="noopener noreferrer">
                        407.1500
                    </a>
                    .
                </p>
            </>
        ),
    },
    {
        id: 'data-sharing',
        title: '8. Data Sharing',
        body: (
            <>
                <p className="mb-3">
                    We do not sell your personal information. We may share your information with:
                </p>
                <ul className="list-disc pl-6 space-y-1">
                    <li>Payment processors (to process your transactions)</li>
                    <li>SMS service providers (to deliver door codes and notifications)</li>
                    <li>Law enforcement or government authorities when required by law</li>
                    <li>Legal counsel in connection with a dispute or legal proceeding</li>
                </ul>
            </>
        ),
    },
    {
        id: 'your-rights',
        title: '9. Your Rights',
        body: (
            <>
                <p className="mb-3">You have the right to:</p>
                <ul className="list-disc pl-6 space-y-1 mb-3">
                    <li>Request access to the personal information we hold about you</li>
                    <li>Request correction of inaccurate information</li>
                    <li>Request deletion of your information (subject to our legal retention obligations)</li>
                    <li>Opt out of SMS communications at any time by replying STOP</li>
                </ul>
                <p>To exercise any of these rights, contact us at the information below.</p>
            </>
        ),
    },
    {
        id: 'missouri-guest-registration',
        title: '10. Missouri Guest Registration',
        body: (
            <p>
                As required by Missouri law (Section{' '}
                <a href={MO_315_015_URL} className="text-rust underline" target="_blank" rel="noopener noreferrer">
                    315.015, RSMo
                </a>
                ), we are required to collect and retain certain guest registration information including
                name, address, and vehicle information. This information may be made available to law
                enforcement upon lawful request.
            </p>
        ),
    },
    {
        id: 'childrens-privacy',
        title: "11. Children's Privacy",
        body: (
            <p>
                Our services are not directed at children under the age of 13. We do not knowingly collect
                personal information from children under 13.
            </p>
        ),
    },
    {
        id: 'changes-to-this-policy',
        title: '12. Changes to This Policy',
        body: (
            <p>
                We may update this Privacy Policy from time to time. Changes will be posted on this page
                with an updated effective date. Continued use of our services following any changes
                constitutes acceptance of the updated policy.
            </p>
        ),
    },
];

const CONTACT_SECTION = { id: 'contact-us', title: '13. Contact Us' };

export default function PrivacyPolicyPage() {
    return (
        <div className="min-h-screen bg-cream">
            <div className="max-w-3xl mx-auto px-6 py-16">
                <div className="mb-10 text-center">
                    <div className="nav-logo inline-block mb-6" style={{ color: 'var(--color-brown)' }}>
                        Stay<span>Desk</span>
                    </div>
                    <p className="section-eyebrow">Privacy Policy</p>
                    <h1 className="section-title text-3xl md:text-4xl mb-2 mx-auto">Martin House Motel</h1>
                    <p className="text-charcoal/60">
                        731 South Main Street, Brookfield, Missouri 64628
                    </p>
                    <p className="text-charcoal/60 mt-1">Effective Date: July 1, 2026</p>
                </div>

                <nav className="bg-warm-white rounded-lg shadow-md p-6 mb-6">
                    <p className="text-sm font-semibold text-charcoal mb-3">Contents</p>
                    <ol className="grid sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
                        {[...sections, CONTACT_SECTION].map((section) => (
                            <li key={section.id}>
                                <a href={`#${section.id}`} className="text-rust hover:underline">
                                    {section.title}
                                </a>
                            </li>
                        ))}
                    </ol>
                </nav>

                <div className="bg-warm-white rounded-lg shadow-md p-8 md:p-10 space-y-8">
                    {sections.map((section) => (
                        <section key={section.id} id={section.id}>
                            <h2 className="text-xl font-semibold text-charcoal mb-3">{section.title}</h2>
                            <div className="text-charcoal/80 leading-relaxed">{section.body}</div>
                        </section>
                    ))}

                    <section id={CONTACT_SECTION.id}>
                        <h2 className="text-xl font-semibold text-charcoal mb-3">{CONTACT_SECTION.title}</h2>
                        <p className="text-charcoal/80 leading-relaxed mb-1">
                            If you have questions about this Privacy Policy or how we handle your information:
                        </p>
                        <p className="text-charcoal/80 leading-relaxed">
                            Martin House Motel<br />
                            731 South Main Street, Brookfield, Missouri 64628<br />
                            <a href="tel:+16602587257" className="text-rust hover:underline">660-258-7257</a> | <a href="mailto:martinhousemotel@gmail.com" className="text-rust hover:underline">martinhousemotel@gmail.com</a>
                        </p>
                    </section>
                </div>

                <p className="text-center text-charcoal/50 text-sm mt-8">
                    Powered by StayDesk | StayDesk is licensed under the Business Source License 1.1
                </p>
            </div>
        </div>
    );
}
