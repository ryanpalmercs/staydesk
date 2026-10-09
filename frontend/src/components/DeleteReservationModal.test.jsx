import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import DeleteReservationModal from './DeleteReservationModal'

describe('DeleteReservationModal', () => {
    it('requires typing the guest name exactly before the delete button enables', async () => {
        const user = userEvent.setup()
        render(<DeleteReservationModal guest={{ name: 'Jane Doe' }} onClose={vi.fn()} onConfirm={vi.fn()} />)

        const deleteButton = screen.getByRole('button', { name: 'Delete' })
        const input = screen.getByRole('textbox')

        expect(deleteButton).toBeDisabled()

        await user.type(input, 'jane doe')
        expect(deleteButton).toBeDisabled()

        await user.clear(input)
        await user.type(input, 'JANE DOE')
        expect(deleteButton).toBeEnabled()
    })

    it('falls back to requiring the literal word DELETE when there is no guest', async () => {
        const user = userEvent.setup()
        render(<DeleteReservationModal guest={null} onClose={vi.fn()} onConfirm={vi.fn()} />)

        await user.type(screen.getByRole('textbox'), 'DELETE')
        expect(screen.getByRole('button', { name: 'Delete' })).toBeEnabled()
    })

    it('calls onConfirm then onClose when confirmed successfully', async () => {
        const user = userEvent.setup()
        const onConfirm = vi.fn().mockResolvedValue()
        const onClose = vi.fn()
        render(<DeleteReservationModal guest={{ name: 'Jane Doe' }} onClose={onClose} onConfirm={onConfirm} />)

        await user.type(screen.getByRole('textbox'), 'JANE DOE')
        await user.click(screen.getByRole('button', { name: 'Delete' }))

        expect(onConfirm).toHaveBeenCalledTimes(1)
        expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('shows an error and does not close when onConfirm rejects', async () => {
        const user = userEvent.setup()
        const onConfirm = vi.fn().mockRejectedValue(new Error('failed'))
        const onClose = vi.fn()
        render(<DeleteReservationModal guest={{ name: 'Jane Doe' }} onClose={onClose} onConfirm={onConfirm} />)

        await user.type(screen.getByRole('textbox'), 'JANE DOE')
        await user.click(screen.getByRole('button', { name: 'Delete' }))

        expect(await screen.findByText('Something went wrong.')).toBeInTheDocument()
        expect(onClose).not.toHaveBeenCalled()
    })
})
