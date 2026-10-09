import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ConfirmDialog from './ConfirmDialog'

describe('ConfirmDialog', () => {
    it('calls onConfirm when the confirm button is clicked', async () => {
        const user = userEvent.setup()
        const onConfirm = vi.fn()
        render(<ConfirmDialog message="Are you sure?" onCancel={vi.fn()} onConfirm={onConfirm} />)

        await user.click(screen.getByRole('button', { name: 'Confirm' }))

        expect(onConfirm).toHaveBeenCalledTimes(1)
    })

    it('calls onCancel when clicking the backdrop', async () => {
        const user = userEvent.setup()
        const onCancel = vi.fn()
        render(<ConfirmDialog message="Are you sure?" onCancel={onCancel} onConfirm={vi.fn()} />)

        await user.click(screen.getByText('Are you sure?').closest('div.fixed'))

        expect(onCancel).toHaveBeenCalledTimes(1)
    })

    it('does not call onCancel when clicking inside the dialog itself', async () => {
        const user = userEvent.setup()
        const onCancel = vi.fn()
        render(<ConfirmDialog message="Are you sure?" onCancel={onCancel} onConfirm={vi.fn()} />)

        await user.click(screen.getByText('Are you sure?'))

        expect(onCancel).not.toHaveBeenCalled()
    })

    it('renders custom button labels', () => {
        render(<ConfirmDialog message="Delete this?" cancelLabel="Nevermind" confirmLabel="Delete" onCancel={vi.fn()} onConfirm={vi.fn()} />)

        expect(screen.getByRole('button', { name: 'Nevermind' })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    })
})
