import java.time.LocalDateTime

data class Storage(var ipAddress: String? = null, var pin: Int? = null, var tokens: HashMap<String, LocalDateTime> = HashMap())
