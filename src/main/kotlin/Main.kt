import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import messagetypes.Message
import messagetypes.Response
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.random.nextInt


lateinit var storage: Storage
val storagePath: File = File("storage")
val storageFile: File = File(storagePath.path + File.separator + "storageFile.json")
val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    // TODO handle remaining cases (numpad open(coroutine/second thread)), store pin hashed, open method
    println("Hello World!")
    println("Program arguments: ${args.joinToString()}")

    storage = if (!storageFile.createNewFile() && !storagePath.mkdir() && storageFile.readText().isNotEmpty()) {
        try {
            Gson().fromJson(storageFile.readText(), Storage::class.java)
        } catch (ignore: Exception) {
            logger.debug { "Storage file error" }
            Storage()
        }
    } else {
        logger.debug { "No storage file" }
        Storage()
    }

    val serverSocket = ServerSocket(5687)

    // API loop
    while (true) {
        val socket = serverSocket.accept()
        socket.soTimeout = 1500
        GlobalScope.launch(Dispatchers.IO) {
            println("Connection from: " + socket.inetAddress)
            val message: Message = Gson().fromJson(
                decrypt(BufferedReader(InputStreamReader(socket.getInputStream())).readLine()), Message::class.java
            )
            val response = Response()
            if (message.type == "login") {
                if (message.isNewDevice!!) {
                    if (storage.pin == null) {
                        storage.pin = message.pin
                        response.text = "Success!"
                        response.internalMessage = generateToken()
                        logger.info { "Hello World, ig :)" }
                    } else {
                        response.text = "Not a new device!"
                        logger.warn(socket.inetAddress.toString() + " tried setting this up as new device")
                    }
                } else {
                    if (message.pin == storage.pin && storage.pin != null) {
                        response.text = "Success!"
                        response.internalMessage = generateToken()
                        logger.info(socket.inetAddress.toString() + " logged in")
                    } else {
                        response.text = "Wrong pin!"
                        logger.warn(socket.inetAddress.toString() + " tried logging in with a wrong pin")
                    }
                }
            } else {
                if (authenticateToken(message)) {
                    when (message.type) {
                        "open" -> {
                            open()
                            response.text = "Success!"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " opened the door")
                        }
                        "keypadConfig" -> {
                            storage.isKeypadEnabled = (message.content!!.toInt() in 1 until 10)
                            storage.keypadTime = message.content.toInt()
                            response.text = "Saved config! :D"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " changed the keypad configuration")
                        }
                        "otpAdd" -> {
                            val tempOtp: Otp = Gson().fromJson(message.content, Otp::class.java)
                            storage.otps[tempOtp.pin] = LocalDate.parse(tempOtp.expirationDate)
                            response.text = "Saved OTP! :D"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " added a new OTP")
                        }
                        "otpRemove" -> {
                            storage.otps.remove(Gson().fromJson(message.content, Otp::class.java).pin)
                            response.text = "Removed OTP! :O"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " removed an OTP")
                        }
                        "keepLogs" -> {
                            storage.keepLogs = message.content!!.lowercase().contains("true")
                            response.text = "Saved preference! :D"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " changed the keeping of logs to ${storage.keepLogs}")
                        }
                        "changePin" -> {
                            storage.pin = Integer.parseInt(message.content)
                            storage.tokens.clear()
                            response.text = "Changed pin! :D"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " changed the pin")
                        }
                        "globalLogout" -> {
                            storage.tokens.clear()
                            response.text = "Logged everyone out! :/"
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " logged everyone out")
                        }
                        "reset" -> {
                            storage = Storage()
                            File("LogFile.log").writeText("")
                            response.text = "Ready for a new start :D"
                            response.internalMessage = "success"
                            logger.warn(socket.inetAddress.toString() + " reset this device")
                            logger.info(socket.inetAddress.toString() + " Ready for a new start :D")
                        }
                        "requestLogs" -> {
                            val logArray: Array<String> = File("LogFile.log").readLines().toTypedArray()
                            response.text = Gson().toJson(logArray)
                            println(response.text)
                            response.internalMessage = "success"
                            logger.info(socket.inetAddress.toString() + " requested the logs")
                        }
                    }
                } else {
                    response.text = "Invalid token :C Login again!"
                    response.internalMessage = "invalid token"
                    logger.warn(socket.inetAddress.toString() + " tried logging in with an invalid token")
                }
            }

            withContext(Dispatchers.IO) {
                PrintWriter(socket.getOutputStream(), true).println(encrypt(Gson().toJson(response)))
                socket.close()
            }
            storageFile.writeText(Gson().toJson(storage))
        }
    }
}

fun authenticateToken(message: Message): Boolean {
    return if (storage.tokens.contains(message.token)) {
        if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
            logger.debug { "used an outdated token" }
            storage.tokens.remove(message.token!!)
            false
        } else {
            renewToken(message.token!!)
            true
        }
    } else if (storage.otps.contains(message.token)) {
        logger.debug { "used an otp as token" }
        storage.otps.remove(message.token)
        true
    } else {
        false
    }
}

fun generateToken(): String {
    val stringToken: String = Random.nextInt(IntRange(1000000, 9999999)).toString()
    storage.tokens[stringToken] = LocalDateTime.now().plusDays(30)
    return stringToken
}

fun renewToken(token: String) {
    storage.tokens[token] = LocalDateTime.now().plusDays(30)
}

fun open() {
    //TODO
    println("OPEN!")
}

fun encrypt(message: String): String {
    //TODO
    return message
}

fun decrypt(message: String): String {
    //TODO
    return message
}
