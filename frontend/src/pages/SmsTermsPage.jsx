export default function SmsTermsPage() {
    return (
        <div className="min-h-screen bg-black">
            <div className="max-w-2xl mx-auto px-6 py-16">
                <div className="mb-10 text-center">
                    <p className="section-eyebrow">SMS Terms &amp; Conditions</p>
                    <h1 className="welcome-title text-3xl md:text-4xl mb-2 mx-auto">Martin House Motel</h1>
                    <p className="text-cream/60">731 South Main Street, Brookfield, Missouri 64628</p>
                    <p className="text-cream/60 mt-1">Effective Date: July 1, 2026</p>
                </div>

                <div className="bg-warm-white rounded-lg shadow-md p-8 md:p-10 space-y-4 text-black/80 leading-relaxed">
                    <p>
                        By checking the SMS consent box when making a reservation, you agree to receive SMS text
                        messages from Martin House Motel related to your stay, including:
                    </p>
                    <ul className="list-disc pl-6 space-y-1">
                        <li>Door access codes for your room</li>
                        <li>Check-in confirmation and instructions</li>
                        <li>Checkout confirmation and access code expiration notice</li>
                        <li>Reservation reminders</li>
                    </ul>
                    <p>Message frequency varies by stay. Message and data rates may apply.</p>
                    <p>
                        Consent to receive these messages is not a condition of booking a reservation or staying at
                        Martin House Motel. You may decline SMS consent and still complete your reservation — door
                        codes and confirmations will be provided by front desk staff instead.
                    </p>
                    <p>To opt out at any time, reply STOP to any message. For help, reply HELP or contact us directly.</p>
                    <p>
                        Martin House Motel<br />
                        731 South Main Street, Brookfield, Missouri 64628<br />
                        <a href="tel:+16602587257" className="text-green hover:underline">660-258-7257</a> | <a href="mailto:martinhousemotel@gmail.com" className="text-green hover:underline">martinhousemotel@gmail.com</a>
                    </p>
                    <p>
                        See our <a href="/privacy-policy" className="text-green underline">Privacy Policy</a> for how
                        we handle your personal information more broadly.
                    </p>
                </div>

                <p className="text-center text-cream/50 text-sm mt-8">
                    Powered by StayDesk | StayDesk is licensed under the Business Source License 1.1
                </p>
            </div>
        </div>
    );
}