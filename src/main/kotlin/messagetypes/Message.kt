package messagetypes

data class Message(val type: String, val pin: Int? = null, val isNewDevice: Boolean? = null, val token: String? = null, val content: String? = null)
