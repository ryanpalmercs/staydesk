import { useEffect, useState } from "react"
import { Wifi, ParkingCircle, Snowflake, Tv, WashingMachine, Coffee, PawPrint, CigaretteOff, ChevronDown, Navigation } from "lucide-react"
import BookingSearchWidget from "../components/BookingSearchWidget"
import { getRoomTypes, getRates } from "../api/publicBookingApi"
import { displayPrice } from "../utils/price"

const ROOM_PHOTOS = [
    ['room-1.jpg', 'Guest room with double bed'],
    ['room-2.jpg', 'Guest room with TV and mini-fridge'],
    ['room-3.jpg', 'Guest room, alternate view'],
    ['bathroom-1.jpg', 'Bathroom with sink and toilet'],
    ['bathroom-2.jpg', 'Shower'],
    ['entryway.jpg', 'Room entryway and closet'],
]

const AMENITIES = [
    [Wifi, 'Free WiFi'],
    [ParkingCircle, 'Free parking'],
    [Snowflake, 'A/C in every room'],
    [Tv, 'TV in every room'],
    [WashingMachine, 'Guest laundry'],
    [Coffee, 'Free coffee'],
]

const NEARBY = [
    ["Walt Disney's Hometown", '9 miles east', null],
    ["Gen. John J. Pershing's Hometown", '5 miles west', null],
    ["Raspberry's BBQ", 'Local BBQ', 'https://www.raspberrysbbq.com/'],
    ['Pigskin Pub & Pizza', 'Local pizza', 'https://www.facebook.com/p/Pigskin-Pub-Pizza-Brookfield-100063587860168/'],
    ['The Clubhouse', 'Local hangout', 'https://www.facebook.com/p/The-Clubhouse-Brookfield-61579920094456/'],
    ['The Tillman House', 'Local museum', 'https://brookfieldcity.com/residents/fun-entertainment/the-tillman-house/'],
]

const FAQS = [
    ['What time is check-in and check-out?', 'Check-in is at 3:00 PM and check-out is at 11:00 AM.'],
    ['Is parking free?', 'Yes, on-site parking is free for all guests.'],
    ['Are pets allowed?', 'We have 2 pet-friendly rooms available. A pet fee applies only if you bring a pet — book one of these rooms pet-free with no extra charge.'],
    ['Is smoking allowed?', 'Martin House Motel is a non-smoking property.'],
    ['Do rooms have WiFi?', 'Yes, free WiFi is available throughout the property.'],
]

const DIRECTIONS_URL = 'https://www.google.com/maps/search/?api=1&query=Martin+House+Motel+Brookfield+Missouri+64628'

export default function WelcomePage() {
    const [lightboxIndex, setLightboxIndex] = useState(null)
    const [roomTypes, setRoomTypes] = useState([])
    const [nightlyRate, setNightlyRate] = useState(null)
    const [openFaq, setOpenFaq] = useState(null)

    function showPrev() {
        setLightboxIndex(i => (i - 1 + ROOM_PHOTOS.length) % ROOM_PHOTOS.length)
    }

    function showNext() {
        setLightboxIndex(i => (i + 1) % ROOM_PHOTOS.length)
    }

    useEffect(() => {
        if (lightboxIndex === null) {
            return
        }

        function handleKeyDown(e) {
            if (e.key === 'Escape') setLightboxIndex(null)
            if (e.key === 'ArrowLeft') showPrev()
            if (e.key === 'ArrowRight') showNext()
        }

        window.addEventListener('keydown', handleKeyDown)
        return () => window.removeEventListener('keydown', handleKeyDown)
    }, [lightboxIndex])

    useEffect(() => {
        getRoomTypes().then(res => setRoomTypes(res.data)).catch(() => {})
        // Rates aren't room-type-specific in this pricing model -- every room type shares the
        // same nightly rate for a given guest count, so one lookup covers all room cards.
        getRates().then(res => setNightlyRate(res.data.find(r => r.rateType === 'NIGHTLY' && r.guestCount === 2))).catch(() => {})
    }, [])

    return (
        <div className="min-h-screen bg-cream">
            <div className="flex items-center justify-between px-6 py-4 bg-warm-white border-b border-tan">
                <span className="welcome-title !text-black !text-xl !mb-0" style={{ fontFamily: "'DM Serif Display', serif" }}>Martin House Motel</span>
                <a href="/book" target="_blank" rel="noopener noreferrer" className="btn-primary !w-auto !px-5 !py-2 text-sm">Check availability</a>
            </div>

            <div className="relative overflow-hidden">
                <img src="/images/rooms/room-1.jpg" alt="Guest room at Martin House Motel"
                     className="w-full h-64 sm:h-[420px] object-cover" />
                <div className="absolute inset-0" style={{ background: 'linear-gradient(0deg, rgba(26,26,26,0.75) 10%, rgba(26,26,26,0.1) 70%)' }} />
                <div className="absolute inset-0 flex flex-col justify-end px-6 pb-8 sm:px-10 sm:pb-10">
                    <p className="section-eyebrow !text-gold">Brookfield, Missouri &middot; Highway 36</p>
                    <h1 className="welcome-title !text-2xl sm:!text-4xl !mb-3 max-w-xl" style={{ fontFamily: "'DM Serif Display', serif" }}>A warm stop along the highway, now under new family ownership</h1>
                    <p className="text-cream/85 text-sm sm:text-base max-w-md mb-4">
                        Recently taken over by a local family investing in fresh renovations throughout &mdash; same
                        clean, comfortable rooms and genuinely friendly welcome.
                    </p>
                    <p className="text-sm">
                        <a href="tel:+16602587257" className="text-cream underline">Or call (660) 258-7257</a>
                    </p>
                </div>
            </div>

            <div className="max-w-3xl mx-auto px-6 -mt-8 sm:-mt-10 relative z-10">
                <BookingSearchWidget />
            </div>

            <div className="max-w-5xl mx-auto px-6 py-14">
                <section className="mb-12 grid sm:grid-cols-2 gap-10">
                    <div>
                        <p className="section-eyebrow">Our story</p>
                        <h2 className="section-title">New family, fresh updates, the same warm welcome</h2>
                        <p className="text-black/80 leading-relaxed">
                            Martin House Motel recently changed hands to a local family who call Brookfield home.
                            We're renovating throughout the property &mdash; modernizing while keeping the same clean
                            rooms, fair rates, and friendly service travelers along Highway 36 have always found here.
                        </p>
                    </div>
                    <div>
                        <p className="section-eyebrow">What's included</p>
                        <h2 className="section-title">Our amenities</h2>
                        <div className="grid grid-cols-2 gap-4">
                            {AMENITIES.map(([Icon, label]) => (
                                <div key={label} className="flex items-center gap-2">
                                    <Icon size={20} className="text-green flex-shrink-0" />
                                    <span className="text-sm text-black/80">{label}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>

                <section className="mb-12">
                    <p className="section-eyebrow">Where you'll stay</p>
                    <h2 className="section-title">Our rooms</h2>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                        {ROOM_PHOTOS.map(([file, alt], idx) => (
                            <button
                                key={file}
                                type="button"
                                onClick={() => setLightboxIndex(idx)}
                                className="p-0 border-0 bg-transparent cursor-zoom-in"
                            >
                                <img
                                    src={`/images/rooms/${file}`}
                                    alt={alt}
                                    className="w-full h-32 sm:h-36 object-cover rounded-md hover:opacity-90 transition-opacity"
                                />
                            </button>
                        ))}
                    </div>
                </section>

                {roomTypes.length > 0 && (
                    <section className="mb-12">
                        <p className="section-eyebrow">Rooms &amp; rates</p>
                        <h2 className="section-title">Choose your room</h2>
                        <div className="grid sm:grid-cols-2 gap-4">
                            {roomTypes.map(rt => (
                                <div key={rt.id} className="border border-tan rounded-lg p-5 bg-warm-white flex flex-col">
                                    <div className="flex items-start justify-between gap-2">
                                        <h3 className="font-semibold" style={{ fontFamily: "'DM Serif Display', serif" }}>{rt.name}</h3>
                                        {rt.petFriendly && (
                                            <span className="text-xs text-green font-medium flex-shrink-0 flex items-center gap-1">
                                                <PawPrint size={14} /> Pet friendly
                                            </span>
                                        )}
                                    </div>
                                    {nightlyRate && <p className="text-sm font-medium mt-2">{displayPrice(nightlyRate.amount)} / night</p>}
                                    <a href="/book" target="_blank" rel="noopener noreferrer" className="btn-secondary !w-auto mt-4 self-start">
                                        Check availability
                                    </a>
                                </div>
                            ))}
                        </div>
                    </section>
                )}

                <section className="mb-12 bg-black rounded-lg p-8 sm:p-10 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-5">
                    <div>
                        <h2 className="welcome-title !text-2xl !mb-2">Ready to book your stay?</h2>
                        <p className="text-cream/75 text-sm">Check real-time availability and book online in a couple of minutes.</p>
                    </div>
                    <a href="/book" target="_blank" rel="noopener noreferrer" className="btn-primary !w-auto !bg-cream !text-black flex-shrink-0 text-center">Check availability</a>
                </section>

                <section className="mb-12">
                    <p className="section-eyebrow">Good to know</p>
                    <h2 className="section-title">Hotel policies</h2>
                    <div className="grid sm:grid-cols-2 gap-x-8 gap-y-5">
                        <div className="flex gap-3">
                            <ParkingCircle size={22} className="text-green flex-shrink-0" />
                            <div>
                                <p className="font-semibold text-sm">Parking</p>
                                <p className="text-sm text-black/70">Free on-site parking for all guests.</p>
                            </div>
                        </div>
                        <div className="flex gap-3">
                            <PawPrint size={22} className="text-green flex-shrink-0" />
                            <div>
                                <p className="font-semibold text-sm">Pets</p>
                                <p className="text-sm text-black/70">2 pet-friendly rooms available &mdash; a fee applies only if you bring a pet.</p>
                            </div>
                        </div>
                        <div className="flex gap-3">
                            <CigaretteOff size={22} className="text-green flex-shrink-0" />
                            <div>
                                <p className="font-semibold text-sm">Smoking</p>
                                <p className="text-sm text-black/70">Non-smoking property.</p>
                            </div>
                        </div>
                        <div className="flex gap-3">
                            <Wifi size={22} className="text-green flex-shrink-0" />
                            <div>
                                <p className="font-semibold text-sm">WiFi</p>
                                <p className="text-sm text-black/70">Free WiFi throughout the property.</p>
                            </div>
                        </div>
                    </div>
                </section>

                <section className="mb-12">
                    <p className="section-eyebrow">Getting here</p>
                    <h2 className="section-title">Nearby &amp; on the way</h2>
                    <div className="grid sm:grid-cols-2 gap-8">
                        <div>
                            <p className="text-black/80 text-sm mb-4">Martin House Motel sits right off US Highway 36 in Brookfield, Missouri.</p>
                            <div className="flex flex-col">
                                {NEARBY.map(([name, note, url]) => (
                                    <div key={name} className="flex justify-between items-baseline text-sm py-2 border-b border-tan/60">
                                        {url ? (
                                            <a href={url} target="_blank" rel="noopener noreferrer" className="text-green underline">{name}</a>
                                        ) : (
                                            <span className="text-black/80">{name}</span>
                                        )}
                                        <span className="text-muted flex-shrink-0 ml-2">{note}</span>
                                    </div>
                                ))}
                            </div>
                            <a href={DIRECTIONS_URL} target="_blank" rel="noopener noreferrer"
                               className="text-green underline text-sm inline-flex items-center gap-1 mt-4">
                                <Navigation size={14} /> Get directions
                            </a>
                        </div>
                        <iframe
                            title="Map showing Martin House Motel location"
                            src="https://maps.google.com/maps?q=Martin+House+Motel,+731+South+Main+Street,+Brookfield,+Missouri+64628&output=embed"
                            className="w-full h-64 sm:h-full min-h-64 rounded-lg border border-tan"
                            loading="lazy"
                            referrerPolicy="no-referrer-when-downgrade"
                        />
                    </div>
                </section>

                <section className="mb-12">
                    <p className="section-eyebrow">Questions</p>
                    <h2 className="section-title">Frequently asked questions</h2>
                    <div className="divide-y divide-tan max-w-2xl">
                        {FAQS.map(([q, a], idx) => (
                            <div key={q}>
                                <button type="button" onClick={() => setOpenFaq(o => (o === idx ? null : idx))}
                                        className="w-full flex items-center justify-between gap-4 py-3 text-left">
                                    <span className="text-sm font-medium">{q}</span>
                                    <ChevronDown size={18} className={`text-muted flex-shrink-0 transition-transform ${openFaq === idx ? 'rotate-180' : ''}`} />
                                </button>
                                {openFaq === idx && <p className="text-sm text-black/70 pb-3 pr-8">{a}</p>}
                            </div>
                        ))}
                    </div>
                </section>

                <section className="grid sm:grid-cols-3 gap-8 pt-2 border-t border-tan">
                    <div>
                        <p className="section-eyebrow">Address</p>
                        <p className="text-black/80 text-sm leading-relaxed">731 South Main Street<br />Brookfield, Missouri 64628</p>
                    </div>
                    <div>
                        <p className="section-eyebrow">Contact</p>
                        <p className="text-black/80 text-sm leading-relaxed">
                            <a href="tel:+16602587257" className="text-green hover:underline">(660) 258-7257</a><br />
                            <a href="mailto:martinhousemotel@gmail.com" className="text-green hover:underline">martinhousemotel@gmail.com</a><br />
                            <a href="https://www.facebook.com/profile.php?id=61590948650618" target="_blank" rel="noopener noreferrer" className="text-green hover:underline">
                                Find us on Facebook
                            </a>
                        </p>
                    </div>
                    <div>
                        <p className="section-eyebrow">Policies</p>
                        <p className="text-black/80 text-sm leading-relaxed">
                            <a href="/privacy-policy" className="text-green underline">Privacy Policy</a><br />
                            <a href="/sms-terms" className="text-green underline">SMS Terms &amp; Conditions</a><br />
                            <a href="/terms" className="text-green underline">Terms of Service</a>
                        </p>
                    </div>
                </section>

                <p className="text-center text-black/40 text-sm mt-10">
                    Martin House Motel is a property of Heavenly Host Property Management, LLC &middot; Powered by StayDesk
                </p>
            </div>

            {lightboxIndex !== null && (
                <div
                    className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-6 cursor-zoom-out"
                    onClick={() => setLightboxIndex(null)}
                >
                    <img
                        src={`/images/rooms/${ROOM_PHOTOS[lightboxIndex][0]}`}
                        alt={ROOM_PHOTOS[lightboxIndex][1]}
                        className="max-w-full max-h-full rounded-md cursor-default"
                        onClick={e => e.stopPropagation()}
                    />
                    <button
                        type="button"
                        onClick={e => { e.stopPropagation(); showPrev() }}
                        className="absolute left-4 top-1/2 -translate-y-1/2 text-white text-4xl leading-none px-2 hover:opacity-70"
                        aria-label="Previous photo"
                    >
                        &#8249;
                    </button>
                    <button
                        type="button"
                        onClick={e => { e.stopPropagation(); showNext() }}
                        className="absolute right-4 top-1/2 -translate-y-1/2 text-white text-4xl leading-none px-2 hover:opacity-70"
                        aria-label="Next photo"
                    >
                        &#8250;
                    </button>
                    <button
                        type="button"
                        onClick={() => setLightboxIndex(null)}
                        className="absolute top-4 right-4 text-white text-3xl leading-none"
                        aria-label="Close"
                    >
                        &times;
                    </button>
                </div>
            )}
        </div>
    );
}
