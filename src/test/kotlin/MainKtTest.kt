import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class MainKtTest {
    @Test
    fun tokenTest() {
        var i = 0
        while(i<20) {
            generateToken()
            i++
        }
    }
}

