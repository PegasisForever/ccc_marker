package site.pegasis.ccc.marker

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    CommandLine(CCCMarker()).execute(*args)
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
        description = ["The command used to verify the program output, it will be called as <command> <input> <output> <expectedOutput>, a non-zero exit code indicates the program output is wrong."]
    )
    private lateinit var verifyCommand: String

    @Option(
        names = ["-t", "--timeout"],
        description = ["Timeout of each test, in milliseconds."],
        defaultValue = "2000",
        showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    private lateinit var timeout: java.lang.Integer


    override fun call() {
        var runCodeWrapped: (input: String) -> RunCodeResult = {
            throw NotImplementedError()
        }
        if (program.endsWith(".java") && " " !in program) {
            printDivider("Compiling")
            val sourceFile = File(program).absoluteFile
            assert(sourceFile.isFile)

            println("Compiling $program.....")
            val tempDir = File(".ccc-marker-temp-${System.currentTimeMillis()}").absoluteFile
            tempDir.mkdir()
            val packageName = javaPackageName(sourceFile)

            runCommand("javac", "-d", tempDir.absolutePath, sourceFile.absolutePath)

            val className = "$packageName.${program.substring(program.lastIndexOf('/') + 1, program.lastIndexOf('.'))}"
            runCodeWrapped = { input ->
                runCode("java", className, input = input, workingDirectory = tempDir)
            }
        }

        printDivider("Testing")
        assert(testDataDirectory.isDirectory)

        val testCases = testDataDirectory.listFiles()!!.filter { file ->
            file.isFile && file.name.endsWith(".in")
        }.map { file ->
            TestCase(file, File(file.path.substring(0, file.path.length - 3) + ".out"))
        }.sorted()
        val times = arrayListOf<Int>()

        for (i in testCases.indices) {
            val testCase = testCases[i]
            print("[${i + 1}/${testCases.size}] Running test case ${testCase.name}.....  ")
            val result = runCodeWrapped(testCase.input)
            if (result is RunCodeTimeout) {
                println()
                System.err.println("Time limit exceeded (${timeout}ms)")
                // todo write in and out to file
                return
            } else if (result is RunCodeRuntimeError) {
                println()
                System.err.println("Runtime error:")
                System.err.println(result.stderr)
                return
                // todo write in and out to file
            } else if (result is RunCodeFinished) {
                println("${result.time}ms")
                times.add(result.time)
            } else {
                error("wtf")
                // todo use sealed class
            }
        }

        printDivider("Accepted")
        println("Average: ${times.average().toInt()}ms")
        println("Min: ${times.minOrNull()}ms")
        println("Max: ${times.maxOrNull()}ms")
    }


    private fun javaPackageName(sourceFile: File): String {
        val source = sourceFile.readText()
        val firstSemi = source.indexOf(";")
        assert(firstSemi >= 0)
        val packageName = source.substring(7, firstSemi).trim()
        println("Package name is $packageName.")
        return packageName
    }

    private fun runCommand(vararg args: String) {
        println("shell$ ${args.joinToString(" ")}")
        val process = startProcess(*args)

        BufferedReader(InputStreamReader(process.inputStream)).run {
            val stdout = readText().trim()
            if (stdout.isNotBlank()) System.err.println(stdout)
            close()
        }
        BufferedReader(InputStreamReader(process.errorStream)).run {
            val stderr = readText().trim()
            if (stderr.isNotBlank()) System.err.println(stderr)
            close()
        }
        val status = process.waitFor()
        println("       Process finished with exit code $status")
        assert(status == 0)
    }

    private fun runCode(vararg args: String, input: String, workingDirectory: File? = null): RunCodeResult {
        val process = startProcess(*args, workingDirectory = workingDirectory)
        val start = System.currentTimeMillis()
        BufferedWriter(OutputStreamWriter(process.outputStream)).run {
            write(input)
            close()
        }

        val timeouted = !process.waitFor((timeout as Int).toLong(), TimeUnit.MILLISECONDS)

        if (timeouted) {
            return RunCodeTimeout()
        } else if (process.exitValue() != 0) {
            val reader = BufferedReader(InputStreamReader(process.errorStream))
            val stderr = reader.readText()
            reader.close()
            return RunCodeRuntimeError(stderr.toLF())
        } else {
            val time = System.currentTimeMillis() - start
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val stdout = reader.readText()
            reader.close()
            return RunCodeFinished(time.toInt(), stdout.toLF())
        }
    }

    private fun startProcess(vararg args: String, workingDirectory: File? = null): Process {
        val processBuilder = ProcessBuilder()
        processBuilder.command(*args)
        if (workingDirectory != null) processBuilder.directory(workingDirectory)
        return processBuilder.start()
    }

    private fun printDivider(text: String) {
        println()
        print("====")
        print(" $text ")
        println("=".repeat(94 - text.length))
        println()
    }
}

interface RunCodeResult

data class RunCodeFinished(val time: Int, val stdout: String) : RunCodeResult
data class RunCodeRuntimeError(val stderr: String) : RunCodeResult
class RunCodeTimeout : RunCodeResult

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
