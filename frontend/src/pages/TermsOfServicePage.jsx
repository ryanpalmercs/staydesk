const sections = [
    {
        id: 'introduction',
        title: '1. Introduction',
        body: (
            <p>
                These Terms of Service ("Terms") govern your use of Martin House Motel's ("we," "us," or "our")
                online booking site and your stay with us, including reservations made online, by phone, or
                in person, and any remote check-in you complete ahead of arrival. By making a reservation or
                completing remote check-in, you agree to these Terms.
            </p>
        ),
    },
    {
        id: 'reservations',
        title: '2. Reservations',
        body: (
            <>
                <p className="mb-3">When you book a room through our website, phone, or in person:</p>
                <ul className="list-disc pl-6 space-y-1">
                    <li>Rates shown are per room type and guest count, and may not include applicable taxes and fees</li>
                    <li>A valid payment card is required to complete a reservation</li>
                    <li>Room type availability is confirmed at the time of booking; a specific room is assigned at check-in</li>
                    <li>You will receive a confirmation number by email and/or text message</li>
                </ul>
            </>
        ),
    },
    {
        id: 'payment',
        title: '3. Payment Terms',
        body: (
            <>
                <p className="mb-3">
                    Reservations made online or by phone are charged in full at the time of booking. Reservations
                    made in person are charged at check-in. In addition to your room charge, an incidental hold is
                    placed on your card at check-in to cover potential damages or additional charges, and is
                    released if unused.
                </p>
                <p>
                    Payments are processed securely through our payment processor. We do not store your full card
                    number.
                </p>
            </>
        ),
    },
    {
        id: 'cancellation',
        title: '4. Cancellation & No-Show Policy',
        body: (
            <p>
                [PLACEHOLDER — cancellation window, refund eligibility, and no-show charges to be provided by
                Martin House Motel and confirmed by counsel before this policy is enforced.]
            </p>
        ),
    },
    {
        id: 'check-in-check-out',
        title: '5. Check-In & Check-Out',
        body: (
            <>
                <p className="mb-3">
                    Standard check-in is at 3:00 PM and check-out is at 11:00 AM, unless otherwise arranged with
                    the front desk.
                </p>
                <p>
                    If you complete remote check-in ahead of arrival, you confirm that the reservation details
                    are correct, agree to these Terms and our house rules below, and authorize the incidental
                    hold described above. Your door code is issued once remote check-in is complete and is for
                    your use only — do not share it with anyone not staying in your room.
                </p>
            </>
        ),
    },
    {
        id: 'house-rules',
        title: '6. House Rules',
        body: (
            <ul className="list-disc pl-6 space-y-1">
                <li>Only registered guests may occupy a room</li>
                <li>Quiet hours are observed overnight out of respect for other guests</li>
                <li>Smoking is not permitted inside guest rooms</li>
                <li>Pets [PLACEHOLDER — confirm property pet policy]</li>
                <li>Guests are responsible for any damage to the room or property beyond normal wear and use</li>
            </ul>
        ),
    },
    {
        id: 'liability',
        title: '7. Liability',
        body: (
            <p>
                [PLACEHOLDER — liability limitation and assumption-of-risk language to be drafted by counsel
                and made consistent with Missouri innkeeper law before this document is relied upon.]
            </p>
        ),
    },
    {
        id: 'conduct',
        title: '8. Guest Conduct',
        body: (
            <p>
                We may refuse service, decline a reservation, or end a stay without refund in cases of
                disruptive, unlawful, or unsafe conduct, or a materially false reservation.
            </p>
        ),
    },
    {
        id: 'governing-law',
        title: '9. Governing Law',
        body: (
            <p>
                These Terms are governed by the laws of the State of Missouri, without regard to its conflict
                of law provisions.
            </p>
        ),
    },
    {
        id: 'changes',
        title: '10. Changes to These Terms',
        body: (
            <p>
                We may update these Terms from time to time. The effective date below reflects the most recent
                revision.
            </p>
        ),
    },
];

const CONTACT_SECTION = { id: 'contact-us', title: '11. Contact Us' };

export default function TermsOfServicePage() {
    return (
        <div className="min-h-screen bg-black">
            <div className="max-w-3xl mx-auto px-6 py-16">
                <div className="mb-6 rounded-lg border border-gold bg-warm-white px-5 py-4 text-sm text-black">
                    <strong>Draft — not yet reviewed by an attorney.</strong> This document is a working draft
                    for internal review and is not final. Do not rely on it, publish it as binding, or treat it
                    as legal advice until it has been reviewed by counsel licensed in Missouri.
                </div>

                <div className="mb-10 text-center">
                    <p className="section-eyebrow">Terms of Service</p>
                    <h1 className="welcome-title text-3xl md:text-4xl mb-2 mx-auto">Martin House Motel</h1>
                    <p className="text-cream/60">
                        731 South Main Street, Brookfield, Missouri 64628
                    </p>
                    <p className="text-cream/60 mt-1">Draft — Effective Date: TBD</p>
                </div>

                <nav className="bg-warm-white rounded-lg shadow-md p-6 mb-6">
                    <p className="text-sm font-semibold text-black mb-3">Contents</p>
                    <ol className="grid sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
                        {[...sections, CONTACT_SECTION].map((section) => (
                            <li key={section.id}>
                                <a href={`#${section.id}`} className="text-green hover:underline">
                                    {section.title}
                                </a>
                            </li>
                        ))}
                    </ol>
                </nav>

                <div className="bg-warm-white rounded-lg shadow-md p-8 md:p-10 space-y-8">
                    {sections.map((section) => (
                        <section key={section.id} id={section.id}>
                            <h2 className="text-xl font-semibold text-black mb-3">{section.title}</h2>
                            <div className="text-black/80 leading-relaxed">{section.body}</div>
                        </section>
                    ))}

                    <section id={CONTACT_SECTION.id}>
                        <h2 className="text-xl font-semibold text-black mb-3">{CONTACT_SECTION.title}</h2>
                        <p className="text-black/80 leading-relaxed mb-1">
                            Questions about these Terms:
                        </p>
                        <p className="text-black/80 leading-relaxed">
                            Martin House Motel<br />
                            731 South Main Street, Brookfield, Missouri 64628<br />
                            <a href="tel:+16602587257" className="text-green hover:underline">660-258-7257</a> | <a href="mailto:martinhousemotel@gmail.com" className="text-green hover:underline">martinhousemotel@gmail.com</a>
                        </p>
                    </section>
                </div>

                <p className="text-center text-cream/50 text-sm mt-8">
                    Powered by StayDesk | StayDesk is licensed under the Business Source License 1.1
                </p>
            </div>
        </div>
    );
}
