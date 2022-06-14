import java.time.LocalDate
import java.time.LocalDateTime

data class Storage(
    var ipAddress: String? = null,
    var pin: Int? = null,
    var tokens: HashMap<String, LocalDate> = HashMap(),
    var isKeypadEnabled: Boolean = false,
    var keypadTime: Int = -1,
    var keepLogs: Boolean = true,
    var otps: HashMap<String, LocalDate> = HashMap(),
    var useTls: Boolean = false
)
