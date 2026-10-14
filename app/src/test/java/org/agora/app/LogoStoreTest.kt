package org.agora.app

import org.agora.app.data.local.LogoStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server's default logo means "no logo of the church": the app then shows its bundled knot. */
class LogoStoreTest {
    @Test
    fun recognisesTheServersDefaultLogo() {
        // Start of assets/church-logo.svg of the web app (v3.0+, light and dark fill)
        val default = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="-82 -82 2884 2884"><style>g{fill:#204E44}""" +
            """@media (prefers-color-scheme:dark){g{fill:#9ACBBE}}</style><g fill="#204E44"><path d="M0 0"/></g></svg>"""
        assertTrue(LogoStore.isDefaultLogo(default))
    }

    @Test
    fun keepsUploadedLogos() {
        assertFalse(LogoStore.isDefaultLogo("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><circle r="40" fill="#204E44"/></svg>"""))
        assertFalse(LogoStore.isDefaultLogo("""<?xml version="1.0"?><svg viewBox="-82 -82 2884 2884"><path fill="#123456" d="M0 0"/></svg>"""))
    }
}
