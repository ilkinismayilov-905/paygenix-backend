import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// Testing Library only auto-cleans with vitest globals, which this project does not enable.
afterEach(cleanup)
// lazy route chunks load slower on CI runners than the 1 s default
configure({ asyncUtilTimeout: 3000 })

// jsdom has no <dialog> behaviour; mimic the browser
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); this.dispatchEvent(new Event('close')) }
