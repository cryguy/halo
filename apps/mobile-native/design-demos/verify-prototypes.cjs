const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const { join } = require('node:path')

function loadPlaywright() {
  try {
    return require('playwright')
  } catch (error) {
    if (!process.env.HALO_PLAYWRIGHT_MODULE) throw error
    return require(process.env.HALO_PLAYWRIGHT_MODULE)
  }
}

const { chromium } = loadPlaywright()
const prototypes = [
  'Halo Android Direction A - Strict Parity.html',
]

async function verifyPrototype(page, prototype) {
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  await page.goto(pathToFileURL(join(__dirname, prototype)).href)
  await page.waitForFunction(() => window.__ready === true)
  assert.equal(await page.locator('.phone').count(), 6, 'expected six phone prototypes')

  const homePhone = page.locator('.phone').nth(0)
  await homePhone.locator('[data-screen="home"] [data-go="detail"]').first().click()
  assert.equal(await homePhone.getAttribute('data-current'), 'detail', 'Home should open Detail')
  await homePhone.locator('.season-button').click()
  assert.equal(await homePhone.locator('.bottom-sheet').isVisible(), true, 'season sheet should open')
  await homePhone.locator('[data-season="Season 2"]').click()
  assert.equal(
    await homePhone.locator('[data-screen="detail"] .season-button span').textContent(),
    'Season 2',
    'season choice should update',
  )

  await homePhone.locator('[data-screen="detail"] [data-go="home"]').first().click()
  await homePhone.locator('[data-tab="library"]').click()
  assert.equal(await homePhone.getAttribute('data-current'), 'library', 'tab should open Library')

  const sourcePhone = page.locator('.phone').nth(4)
  await sourcePhone.locator('[data-download]').first().click()
  assert.match(
    await sourcePhone.locator('[data-download]').first().getAttribute('class'),
    /done/,
    'download action should enter the active state',
  )

  const settingsPhone = page.locator('.phone').nth(5)
  const autoplay = settingsPhone.locator('[data-toggle] .switch')
  assert.match(await autoplay.getAttribute('class'), /on/, 'autoplay should begin enabled')
  await settingsPhone.locator('[data-toggle]').click()
  assert.doesNotMatch(await autoplay.getAttribute('class'), /on/, 'autoplay should toggle off')

  assert.deepEqual(pageErrors, [], `page errors: ${pageErrors.join(' | ')}`)
  assert.deepEqual(consoleErrors, [], `console errors: ${consoleErrors.join(' | ')}`)
  process.stdout.write(`PASS ${prototype}\n`)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  try {
    for (const prototype of prototypes) {
      const page = await browser.newPage({ viewport: { width: 1600, height: 1800 } })
      try {
        await verifyPrototype(page, prototype)
      } finally {
        await page.close()
      }
    }
  } finally {
    await browser.close()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
