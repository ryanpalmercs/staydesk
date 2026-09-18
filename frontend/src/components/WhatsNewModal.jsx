import { releaseNotes } from "../data/releaseNotes"
import Modal from "./Modal"

function WhatsNewModal({ onAcknowledge }) {
    const latest = releaseNotes[0]

    return (
        <Modal onClose={onAcknowledge} size="sm">
            <h2 className="text-lg text-black font-semibold mb-1">{latest.title}</h2>
            <p className="text-sm text-muted mb-4">{latest.date}</p>

            <ul className="flex flex-col gap-2 mb-6 list-disc pl-5">
                {latest.notes.map((note, i) => (
                    <li key={i} className="text-sm text-black">{note}</li>
                ))}
            </ul>

            <div className="flex justify-end">
                <button type="button" onClick={onAcknowledge} className="btn btn-primary">Got it</button>
            </div>
        </Modal>
    )
}

export default WhatsNewModal
