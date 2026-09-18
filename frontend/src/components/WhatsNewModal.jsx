import { format, parseISO } from "date-fns"
import Modal from "./Modal"

function WhatsNewModal({ entries, onAcknowledge }) {
    return (
        <Modal onClose={onAcknowledge} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">What's New</h2>

            <div className="flex flex-col gap-4 mb-6">
                {entries.map(entry => (
                    <div key={entry.id}>
                        <p className="text-sm text-muted mb-1">
                            {entry.version && <span className="font-medium">{entry.version} — </span>}
                            {format(parseISO(entry.date), 'MMM d, yyyy')}
                        </p>
                        <ul className="flex flex-col gap-2 list-disc pl-5">
                            {entry.notes.map((note, i) => (
                                <li key={i} className="text-sm text-black">{note}</li>
                            ))}
                        </ul>
                    </div>
                ))}
            </div>

            <div className="flex justify-end">
                <button type="button" onClick={onAcknowledge} className="btn btn-primary">Got it</button>
            </div>
        </Modal>
    )
}

export default WhatsNewModal
