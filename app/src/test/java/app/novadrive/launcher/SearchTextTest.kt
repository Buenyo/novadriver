package app.novadrive.launcher

import app.novadrive.launcher.apps.SearchText
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test fun removesAccentsAndCase() {
        assertEquals("radio fm", SearchText.normalize("  Rádio FM "))
        assertEquals("configuracoes", SearchText.normalize("Configurações"))
    }
}
