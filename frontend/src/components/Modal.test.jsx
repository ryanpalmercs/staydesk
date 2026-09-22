import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import Modal from './Modal'

describe('Modal', () => {
    it('closes immediately on backdrop click when not dirty', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<Modal onClose={onClose} isDirty={false}><p>Content</p></Modal>)

        await user.click(screen.getByText('Content').closest('div.fixed'))

        expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('does not close on a click inside the modal box', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<Modal onClose={onClose} isDirty={false}><p>Content</p></Modal>)

        await user.click(screen.getByText('Content'))

        expect(onClose).not.toHaveBeenCalled()
    })

    it('asks for confirmation before closing when dirty, and only closes after confirming', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<Modal onClose={onClose} isDirty={true}><p>Content</p></Modal>)

        await user.click(screen.getByText('Content').closest('div.fixed'))
        expect(onClose).not.toHaveBeenCalled()
        expect(screen.getByText('Discard unsaved changes?')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'Discard' }))
        expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('lets the user back out of the discard confirmation without closing', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<Modal onClose={onClose} isDirty={true}><p>Content</p></Modal>)

        await user.click(screen.getByText('Content').closest('div.fixed'))
        await user.click(screen.getByRole('button', { name: 'Keep Editing' }))

        expect(onClose).not.toHaveBeenCalled()
        expect(screen.queryByText('Discard unsaved changes?')).not.toBeInTheDocument()
    })

    it('closes on Escape the same way as a backdrop click', async () => {
        const onClose = vi.fn()
        render(<Modal onClose={onClose} isDirty={false}><p>Content</p></Modal>)

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
