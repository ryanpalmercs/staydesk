import { useEffect, useState } from "react"

const ROOM_PHOTOS = [
    ['room-1.jpg', 'Guest room with double bed'],
    ['room-2.jpg', 'Guest room with TV and mini-fridge'],
    ['room-3.jpg', 'Guest room, alternate view'],
    ['bathroom-1.jpg', 'Bathroom with sink and toilet'],
    ['bathroom-2.jpg', 'Shower'],
    ['entryway.jpg', 'Room entryway and closet'],
]

export default function WelcomePage() {
    const [lightboxIndex, setLightboxIndex] = useState(null)

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

    return (
        <div className="min-h-screen bg-black">
            <div className="max-w-3xl mx-auto px-6 py-16">
                <div className="mb-10 text-center">
                    <h1 className="welcome-title text-3xl md:text-4xl mb-2 mx-auto">Martin House Motel</h1>
                    <p className="text-cream/60">731 South Main Street, Brookfield, Missouri 64628</p>
                </div>

                <div className="bg-warm-white rounded-lg shadow-md p-8 md:p-10 space-y-6">
                    <section>
                        <h2 className="text-xl font-semibold text-charcoal mb-3">About Us</h2>
                        <p className="text-charcoal/80 leading-relaxed">
                            Martin House Motel is a 27-room motel located in Brookfield, Missouri. We've been
                            serving travelers along Highway 36 with clean, comfortable rooms and friendly
                            service. Whether you're passing through or staying a while, we're happy to have you.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-semibold text-charcoal mb-3">Our Rooms</h2>
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

                    <section>
                        <h2 className="text-xl font-semibold text-charcoal mb-3">Book a Room</h2>
                        <p className="text-charcoal/80 leading-relaxed mb-3">
                            We currently take reservations by phone or in person at the front desk. Give us a
                            call and we'll be glad to help you find a room for your stay.
                        </p>
                        <p className="text-charcoal/80 leading-relaxed">
                            <a href="tel:+16602587257" className="text-rust hover:underline font-medium">
                                (660) 258-7257
                            </a>
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-semibold text-charcoal mb-3">Contact</h2>
                        <p className="text-charcoal/80 leading-relaxed">
                            Martin House Motel<br />
                            731 South Main Street, Brookfield, Missouri 64628<br />
                            <a href="tel:+16602587257" className="text-rust hover:underline">(660) 258-7257</a><br />
                            <a href="mailto:martinhousemotel@gmail.com" className="text-rust hover:underline">martinhousemotel@gmail.com</a><br />
                            <a href="https://www.facebook.com/profile.php?id=61590948650618" target="_blank" rel="noopener noreferrer" className="text-rust hover:underline">
                                Find us on Facebook
                            </a>
                        </p>
                    </section>

                    <section className="pt-2 border-t border-tan">
                        <p className="text-sm text-charcoal/60">
                            <a href="/privacy-policy" className="text-rust underline">Privacy Policy</a>
                            {' '}&middot;{' '}
                            <a href="/sms-terms" className="text-rust underline">SMS Terms &amp; Conditions</a>
                        </p>
                    </section>
                </div>

                <p className="text-center text-cream/50 text-sm mt-8">
                    Powered by StayDesk | StayDesk is licensed under the Business Source License 1.1
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
