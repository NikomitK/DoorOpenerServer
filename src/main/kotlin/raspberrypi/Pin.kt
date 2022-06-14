package raspberrypi

import java.lang.Exception
import java.util.*

const val SET = "raspi-gpio set "
const val GET = "raspi-gpio get "
const val DEFINE = " op"
const val SET_HIGH = " dh"
const val SET_LOW = " dl"

abstract class Pin(val pinNumber: Int) {

    protected fun execute(command: String?): String {
        try {
            Scanner(
                Runtime.getRuntime().exec(command).inputStream
            ).use { s -> return if (s.hasNext()) s.nextLine() else "" }
        } catch (e: Exception) {
            System.err.println("Error during command: " + e.message)
            return ""
        }
    }



}