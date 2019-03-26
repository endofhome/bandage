package functional.tests

import Bandage
import config.BandageConfig
import org.junit.jupiter.api.Test

class BandageConfigTest {

    @Test
    fun `application is configured correctly`() {
        Bandage.init(BandageConfig)
    }
}
