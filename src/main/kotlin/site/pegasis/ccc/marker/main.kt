package site.pegasis.ccc.marker

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

var cleanUp: () -> Unit = {}
var lineFinished = true
fun main(args: Array<String>) {
    CommandLine(CCCMarker()).execute(*args)
    cleanUp()
}

@Command(
    name = "ccc-marker",
    mixinStandardHelpOptions = true,
    version = ["1.0"]
)
class CCCMarker : Callable<Unit> {
    @Parameters(
        arity = "1",
        index = "0",
        description = ["The directory containing .in and .out files used to test the program."]
    )
    private lateinit var testDataDirectory: File

    @Parameters(
        arity = "1",
        index = "1",
        description = ["Either the command to run the program or the source code file (supports .py and .java)."]
    )
    private lateinit var program: String

    @Option(
        names = ["-e", "--verify"],
        description = ["The command used to verify the program output, it will be called as <command> <input> <expectedOutput> <output>, a non-zero exit code indicates the program output is wrong."],
    )
    private var verifyCommand: String? = null

    private var verifyCommandTokens: Array<String>? = null

    @Option(
        names = ["-t", "--timeout"],
        description = ["Timeout of each test, in milliseconds."],
        defaultValue = "2000",
        showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    private var timeout: Int = 2000

    fun _call() {
        val runCodeWrapped: (inFile: File) -> RunCodeResult

        if (program.endsWith(".java") && " " !in program) {
            printDivider("Compiling")
            val sourceFile = File(program).absoluteFile
            expect(sourceFile.isFile, "\"$program\" is not a file or doesn't exist.")

            printlnStd("Compiling $program.....")
            val tempDir = File(".ccc_marker_temp_${System.currentTimeMillis()}").absoluteFile
            tempDir.mkdir()

            runCommand("javac", "-d", tempDir.absolutePath, sourceFile.absolutePath, assertSuccess = true)

            val packageName = javaPackageName(sourceFile)
            val className = program.substring(program.lastIndexOf('/') + 1, program.lastIndexOf('.'))
            val fullClassName = if (packageName != null) {
                "$packageName.$className"
            } else {
                className
            }

            runCodeWrapped = { inFile ->
                runCode("java", fullClassName, inFile = inFile, workingDirectory = tempDir)
            }
            cleanUp = {
                tempDir.deleteRecursively()
            }
        } else if (program.endsWith(".py") && " " !in program) {
            val absPath = File(program).absolutePath
            runCodeWrapped = { inFile ->
                runCode("python", absPath, inFile = inFile)
            }
        } else {
            val programTokens = Tokenizer.tokenize(program)
            runCodeWrapped = { inFile ->
                runCode(*programTokens, inFile = inFile)
            }
        }

        printDivider("Testing")

        if (verifyCommand != null) {
            verifyCommandTokens = Tokenizer.tokenize(verifyCommand!!)
            printlnStd("Answers will be verified using \"${verifyCommandTokens!!.joinToString(" ")} <input> <expectedOutput> <output>\".")
        }

        expect(testDataDirectory.isDirectory, "\"${testDataDirectory.path}\" is not a directory or doesn't exist.")
        val testCases = testDataDirectory.listFiles()!!.filter { file ->
            file.isFile && file.name.endsWith(".in")
        }.map { file ->
            TestCase(file, File(file.path.substring(0, file.path.length - 3) + ".out"))
        }.sorted()
        expect(testCases.isNotEmpty(), "\"${testDataDirectory.path}\" doesn't contain any test cases.")

        val times = arrayListOf<Int>()
        for (i in testCases.indices) {
            val testCase = testCases[i]
            printStd("[${i + 1}/${testCases.size}] Running test case ${testCase.name}.....  ")
            when (val result = runCodeWrapped(testCase.inFile)) {
                is RunCodeTimeout -> {
                    printError("Time Limit Exceeded (${timeout}ms)")
                    writeErrorFiles(testCase)
                    return
                }
                is RunCodeRuntimeError -> {
                    printError("Runtime Error")
                    printError(result.stderr)
                    writeErrorFiles(testCase)
                    return
                }
                is RunCodeFinished -> {
                    if (verifyResult(testCase, result.stdout)) {
                        lineFinished = true
                        println("${result.time}ms")
                        times.add(result.time)
                    } else {
                        printError("Wrong Answer")
                        writeErrorFiles(testCase, result.stdout)
                        return
                    }
                }
            }
        }

        printDivider("Accepted")
        printlnStd("Average: ${times.average().toInt()}ms")
        printlnStd("Min: ${times.minOrNull()}ms")
        printlnStd("Max: ${times.maxOrNull()}ms")
    }

    override fun call() {
        try {
            _call()
        } catch (e: Throwable) {
            printError(e.stackTraceToString())
        }
    }

    private fun verifyResult(testCase: TestCase, output: String): Boolean {
        if (verifyCommandTokens == null) {
            return testCase.expectedOutput == output
        } else {
            return runCommand(
                *verifyCommandTokens!!,
                testCase.input,
                testCase.expectedOutput,
                output,
                print = false
            ) == 0
        }
    }

    private fun javaPackageName(sourceFile: File): String? {
        val source = sourceFile.readText()
        if (source.indexOf("package") == 0) {
            val firstSemi = source.indexOf(";")
            val packageName = source.substring(7, firstSemi).trim()
            println("Package name is \"$packageName\".")
            return packageName
        } else {
            println("Package name is empty.")
            return null
        }
    }

    private fun runCommand(vararg args: String, print: Boolean = true, assertSuccess: Boolean = false): Int {
        if (print) println("shell$ ${args.joinToString(" ")}")
        val process = startProcess(*args)
        val stdout = process.inputStream.stringBuilder
        val stderr = process.errorStream.stringBuilder

        val status = process.waitFor()
        if (stdout.isNotBlank() && print) println(stdout)
        if (stderr.isNotBlank()) printError(stderr)
        if (print) println("       Process finished with exit code $status.")
        if (assertSuccess) expect(status == 0, "Process finished with exit code $status.")
        return status
    }

    private fun runCode(vararg args: String, inFile: File, workingDirectory: File? = null): RunCodeResult {
        val process = startProcess(*args, inFile = inFile, workingDirectory = workingDirectory)
        val start = System.currentTimeMillis()
        val stdout = process.inputStream.stringBuilder
        val stderr = process.errorStream.stringBuilder

        val timeouted = !process.waitFor(timeout.toLong(), TimeUnit.MILLISECONDS)

        if (timeouted) {
            process.destroyForcibly()
            return RunCodeTimeout
        } else if (process.exitValue() != 0) {
            return RunCodeRuntimeError(stderr.toString().toLF())
        } else {
            val time = System.currentTimeMillis() - start
            return RunCodeFinished(time.toInt(), stdout.toString().toLF())
        }
    }

    private val InputStream.stringBuilder: StringBuilder
        get() {
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(this)).forEachLine { line ->
                sb.append(line)
                sb.append('\n')
            }
            return sb
        }

    private fun startProcess(vararg args: String, inFile: File? = null, workingDirectory: File? = null): Process {
        val processBuilder = ProcessBuilder()
        processBuilder.command(*args)
        if (inFile != null) processBuilder.redirectInput(inFile)
        if (workingDirectory != null) processBuilder.directory(workingDirectory)
        return processBuilder.start()
    }

    private fun printDivider(text: String) {
        if (!lineFinished) {
            println()
            lineFinished = true
        }
        println()
        print("====")
        print(" $text ")
        println("=".repeat(94 - text.length))
        println()
    }

    private fun writeErrorFiles(testCase: TestCase, output: String? = null) {
        File("input.txt").writeText(testCase.input)
        File("expected_output.txt").writeText(testCase.expectedOutput)
        if (output != null) {
            File("output.txt").writeText(output)
            printError("Wrote input.txt, expected_output.txt and output.txt to disk.")
        } else {
            printError("Wrote input.txt and expected_output.txt to disk.")
        }
    }
}

sealed class RunCodeResult

data class RunCodeFinished(val time: Int, val stdout: String) : RunCodeResult()
data class RunCodeRuntimeError(val stderr: String) : RunCodeResult()
object RunCodeTimeout : RunCodeResult()

data class TestCase(val inFile: File, val outFile: File) : Comparable<TestCase> {
    val name: String
        get() = inFile.nameWithoutExtension
    val isSample: Boolean
        get() = "sample" in inFile.name
    val input: String
        get() = inFile.readText().toLF()
    val expectedOutput: String
        get() = outFile.readText().toLF()

    override fun compareTo(other: TestCase): Int {
        return if (isSample && !other.isSample) {
            -1
        } else if (!isSample && other.isSample) {
            1
        } else {
            name.compareTo(other.name)
        }
    }
}

fun String.toLF(): String {
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
}

fun expect(assertion: Boolean, message: Any) {
    if (!assertion) {
        error(message)
    }
}

fun printStd(message: Any?) {
    lineFinished = false
    print(message)
}

fun printlnStd(message: Any?) {
    if (!lineFinished) {
        println()
        lineFinished = true
    }
    println(message)
}

fun printError(message: Any?) {
    if (!lineFinished) {
        println()
        lineFinished = true
    }
    System.err.println("\u001B[31m$message\u001B[0m")
}
