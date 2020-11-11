package site.pegasis.ccc.marker

import java.io.Reader

class Tokenizer(private val reader: Reader) {
    private var ch: Int

    init {
        ch = reader.read()
    }

    fun nextToken(): String? {
        skipWhite()
        if (ch == -1) {
            return null
        }
        val sb = StringBuilder()
        var quoteChar = 0.toChar()
        while (ch != -1) {
            when (ch.toChar()) {
                ' ', '\t' -> {
                    if (quoteChar.toInt() == 0) {
                        return sb.toString()
                    }
                    sb.append(ch.toChar())
                }
                '\n', '\r' -> return sb.toString()
                '\'', '"' -> if (quoteChar.toInt() == 0) {
                    quoteChar = ch.toChar()
                } else if (quoteChar.toInt() == ch) {
                    quoteChar = 0.toChar()
                } else {
                    sb.append(ch.toChar())
                }
                '\\' -> {
                    if (quoteChar.toInt() != 0) {
                        ch = reader.read()
                        when (ch.toChar()) {
                            '\n', '\r' -> {
                                while (ch == ' '.toInt() || ch == '\n'.toInt() || ch == '\r'.toInt() || ch == '\t'.toInt()) {
                                    ch = reader.read()
                                }
                                continue
                            }
                            'n' -> ch = '\n'.toInt()
                            'r' -> ch = '\r'.toInt()
                            't' -> ch = '\t'.toInt()
                        }
                    }
                    sb.append(ch.toChar())
                }
                else -> sb.append(ch.toChar())
            }
            ch = reader.read()
        }
        return sb.toString()
    }

    fun skipWhite() {
        while (ch != -1) {
            when (ch.toChar()) {
                ' ', '\t', '\n', '\r' -> {
                }
                '#' -> {
                    ch = reader.read()
                    while (ch != '\n'.toInt() && ch != '\r'.toInt() && ch != -1) {
                        ch = reader.read()
                    }
                }
                else -> return
            }
            ch = reader.read()
        }
    }


    companion object {
        fun tokenize(command: String): Array<String> {
            val args = arrayListOf<String>()
            val tokenizer = Tokenizer(command.reader())
            var token: String?
            while (tokenizer.nextToken().also { token = it } != null) {
                args.add(token!!)
            }
            return args.toTypedArray()
        }
    }
}
