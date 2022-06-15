import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.*
import messagetypes.Message
import messagetypes.Response
import org.mindrot.jbcrypt.BCrypt
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.time.LocalDate
import kotlin.random.Random
import kotlin.random.nextInt

object ConnectionHandler : CoroutineScope by MainScope() {
    private val gson = Gson()
    private fun Socket.readLine() = BufferedReader(InputStreamReader(getInputStream())).readLine()
    private fun String.toMessage(): Message = gson.fromJson(this, Message::class.java)
    private const val success = "Success!"

    suspend fun handleConnection(socket: Socket) = coroutineScope {
        val ipAddress = socket.inetAddress.toString()
        val stringSent = socket.readLine()
        val message: Message
        try {
            message = stringSent.toMessage()
        } catch (jse: JsonSyntaxException) {
            return@coroutineScope
        }

        var response = Response()
        if (message.type == "login") {
            response = handleLogin(message, ipAddress)
        } else if (message.type == "otpOpen") {
            response = otpOpen(message, ipAddress)
        } else {
            if (authenticateToken(message)) {
                response = when (message.type) {
                    "useTLs" -> {
                        configTls(message, ipAddress)
                    }
                    "open" -> {
                        open(message, ipAddress)
                    }
                    "keypadConfig" -> {
                        configureKeypad(message, ipAddress)
                    }
                    "otpAdd" -> {
                        optAdd(message, ipAddress)
                    }
                    "otpRemove" -> {
                        otpRemove(message, ipAddress)
                    }
                    "keepLogs" -> {
                        keepLogs(message, ipAddress)
                    }
                    "changePin" -> {
                        changePin(message, ipAddress)
                    }
                    "globalLogout" -> {
                        globalLogout(ipAddress)
                    }
                    "reset" -> {
                        reset(ipAddress)
                    }
                    "requestLogs" -> {
                        requestLogs(ipAddress)
                    }
                    else -> {
                        Response("Unknown message type", "unknownMessageType")
                    }
                }
            } else {
                response.text = "Invalid token :C Login again!"
                response.internalMessage = "invalid token"
                logger.warn("$ipAddress tried logging in with an invalid token")
            }
        }

        withContext(Dispatchers.IO) {
            PrintWriter(socket.getOutputStream(), true).println(Gson().toJson(response))
            socket.close()
        }
        storageFile.writeText(gsonPretty.toJson(storage))
    }

    private suspend fun configTls(message: Message, ipAddress: String): Response = coroutineScope {
        val response = Response()
        val tempTlsMode = storage.useTls
        storage.useTls = message.content?.toBooleanStrictOrNull() ?: tempTlsMode
        if (storage.useTls != tempTlsMode) {
//            startSecureSocket()
            response.text = "Changed TLS mode to ${if (storage.useTls) "enabled" else "disabled"}"
            logger.info("$ipAddress changed TLS mode to ${if (storage.useTls) "enabled" else "disabled"}")
        } else {
            logger.info("$ipAddress tried changing TLS mode to the current value")
            response.text = "TLS is already in this mode!"
        }
        response.internalMessage = "success"
        return@coroutineScope response
    }

    private fun handleLogin(message: Message, ipAddress: String): Response {
        val response = Response()
        if (message.isNewDevice!!) {
            if (storage.pin == null) {
                storage.pin = BCrypt.hashpw(message.pin.toString(), BCrypt.gensalt())
                response.text = success
                response.internalMessage = generateToken()
                logger.info { "Hello World, ig :)" }
            } else {
                response.text = "Not a new device!"
                logger.warn("$ipAddress tried setting this up as new device")
            }
        } else {
            if (storage.pin?.let {
                    checkpwSafe(message.pin.toString(), it)
                } == true) {
                response.text = success
                response.internalMessage = generateToken()
                logger.info("$ipAddress logged in")
            } else {
                response.text = "Wrong pin!"
                logger.warn("$ipAddress tried logging in with a wrong pin")
            }
        }
        return response
    }

    private suspend fun open(message: Message, ipAddress: String): Response = coroutineScope {
        val response: Response
        if (message.content?.toIntOrNull() != null) {
            doOpen(message.content.toInt())
            response = Response().apply {
                text = success
                internalMessage = "success"
            }
            logger.info("$ipAddress opened the door")
        } else {
            response = Response().apply {
                text = "The message didn't contain a time"
                internalMessage = "noTimeSupplied"
            }
            logger.info("$ipAddress tried opening the door with an invalid message")
        }
        return@coroutineScope response
    }

    private suspend fun otpOpen(message: Message, ipAddress: String): Response = coroutineScope {
        val response = Response()
        if (storage.otps.contains(message.token)) {
            storage.otps.remove(message.token)
            if (message.content?.toIntOrNull() != null) {
                doOpen(message.content.toInt())
                response.text = success
                response.internalMessage = "success"
                logger.info("$ipAddress opened the door with an otp")
            } else {
                response.text = "The message didn't contain a time"
                response.internalMessage = "noTimeSupplied"
                logger.info("$ipAddress tried opening the door with an invalid message")
            }
        } else {
            response.text = "Used a wrong otp"
            response.internalMessage = "wrongOtp"
            logger.info("$ipAddress tried using an invalid otp")
        }
        return@coroutineScope response
    }

    private suspend fun configureKeypad(message: Message, ipAddress: String): Response = coroutineScope {
        val tempResponse = Response()
        val tempKeypadMode = storage.isKeypadEnabled
        storage.isKeypadEnabled = (message.content!!.toInt() in 1 until 10)
        launch(Dispatchers.IO) {

            if (tempKeypadMode != storage.isKeypadEnabled) {
                switchKeypad()
            }
        }
        storage.keypadTime = message.content.toInt()
        tempResponse.text = "Saved config! :D"
        tempResponse.internalMessage = "success"
        logger.info(
            ipAddress + " changed the keypad configuration to " + if (storage.isKeypadEnabled) "enabled (${storage.keypadTime} second/s)" else "disabled"
        )
        return@coroutineScope tempResponse
    }

    private suspend fun optAdd(message: Message, ipAddress: String): Response = coroutineScope {
        val response = Response()
        val tempOtp: Otp = Gson().fromJson(message.content, Otp::class.java)
        storage.otps[tempOtp.pin] = LocalDate.parse(tempOtp.expirationDate)
        response.text = "Saved OTP! :D"
        response.internalMessage = "success"
        logger.info("$ipAddress added a new OTP")
        return@coroutineScope response
    }

    private suspend fun otpRemove(message: Message, ipAddress: String): Response = coroutineScope {
        val response = Response()
        storage.otps.remove(Gson().fromJson(message.content, Otp::class.java).pin)
        response.text = "Removed OTP! :O"
        response.internalMessage = "success"
        logger.info("$ipAddress removed an OTP")
        return@coroutineScope response
    }

    private suspend fun keepLogs(message: Message, ipAddress: String): Response = coroutineScope {
        val response = Response()
        storage.keepLogs = message.content!!.lowercase().contains("true")
        response.text = "Saved preference! :D"
        response.internalMessage = "success"
        logger.info(ipAddress + " changed the keeping of logs to ${storage.keepLogs}")
        return@coroutineScope response
    }

    private suspend fun changePin(message: Message, ipAddress: String): Response = coroutineScope {
        val response = Response()
        storage.pin = BCrypt.hashpw(message.content, BCrypt.gensalt())
        storage.tokens.clear()
        response.text = "Changed pin! :D"
        response.internalMessage = "success"
        logger.info("$ipAddress changed the pin")
        return@coroutineScope response
    }

    private suspend fun globalLogout(ipAddress: String): Response = coroutineScope {
        val response = Response()
        storage.tokens.clear()
        response.text = "Logged everyone out! :/"
        response.internalMessage = "success"
        logger.info("$ipAddress logged everyone out")
        return@coroutineScope response
    }

    private suspend fun reset(ipAddress: String): Response = coroutineScope {
        val response = Response()
        doReset()
        response.text = "Ready for a new start :D"
        response.internalMessage = "success"
        logger.warn("$ipAddress reset this device")
        logger.info("$ipAddress Ready for a new start :D")
        return@coroutineScope response
    }

    private suspend fun requestLogs(ipAddress: String): Response = coroutineScope {
        val response = Response()
        val logArray: Array<String> = File("LogFile.log").readLines().toTypedArray()
        response.text = Gson().toJson(logArray)
        println(response.text)
        response.internalMessage = "success"
        logger.info("$ipAddress requested the logs")
        return@coroutineScope response
    }

    private fun authenticateToken(message: Message): Boolean {
        return if (storage.tokens.contains(message.token)) {
            if (storage.tokens[message.token]!!.isBefore(LocalDate.now())) {
                logger.debug { "used an outdated token" }
                storage.tokens.remove(message.token!!)
                false
            } else {
                renewToken(message.token!!)
                true
            }
        } else {
            false
        }
    }

    private fun generateToken(): String {
        val stringToken: String = Random.nextInt(IntRange(1000000, 9999999)).toString()
        storage.tokens[stringToken] = LocalDate.now().plusDays(30)
        return stringToken
    }

    private fun renewToken(token: String) {
        storage.tokens[token] = LocalDate.now().plusDays(30)
    }

}