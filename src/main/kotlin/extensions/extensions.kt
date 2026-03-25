package extensions

fun printLog(text: Any?) {
    println("[PRINTLOG] $text")
}

fun Double.getPercent(value: Double) : Double {
    return ((this / 100.0) * value).to1Digits()
}

fun Double.addPercent(value: Double) : Double {
    return (this + getPercent(value)).to1Digits()
}

fun Double.removePercent(value: Double) : Double {
    return (this - getPercent(value)).to1Digits()
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.to1Digits() = String.format("%.1f", this).replace(",", ".").toDouble()