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
            val sourceFile = File(program).absoluteFile
            assert(sourceFile.exists())
            assert(sourceFile.isFile)

            println("Compiling $program.....")
            val tempDir = File(".ccc-marker-temp-${System.currentTimeMillis()}")
            tempDir.mkdir()
            val packageName = javaPackageName(sourceFile)

            runCommand("javac", "-d", tempDir.absolutePath, sourceFile.absolutePath)

            val className = "$packageName.${program.substring(program.lastIndexOf('/') + 1, program.lastIndexOf('.'))}"
            runCodeWrapped = { input ->
                runCode("java", className, input = input, workingDirectory = tempDir)
            }
        }

        val result = runCodeWrapped(
            """
            4 5
            WWWWW
            W.W.W
            WWS.W
            WWWWW
        """.trimIndent()
        )
        println(result)
    }


    private fun javaPackageName(sourceFile: File): String {
        val stream = sourceFile.inputStream()
        val source = stream.readBytes().toString(Charsets.UTF_8)
        stream.close()

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
            return RunCodeRuntimeError(stderr)
        } else {
            val time = System.currentTimeMillis() - start
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val stdout = reader.readText()
            reader.close()
            return RunCodeFinished(time.toInt(), stdout)
        }
    }

    private fun startProcess(vararg args: String, workingDirectory: File? = null): Process {
        val processBuilder = ProcessBuilder()
        processBuilder.command(*args)
        if (workingDirectory != null) processBuilder.directory(workingDirectory)
        return processBuilder.start()
    }
}

interface RunCodeResult

data class RunCodeFinished(val time: Int, val stdout: String) : RunCodeResult
data class RunCodeRuntimeError(val stderr: String) : RunCodeResult
class RunCodeTimeout : RunCodeResult
