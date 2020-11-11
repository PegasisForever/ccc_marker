# CCC Marker

- Check program using all .in and .out files in a directory
- Use an external command to verify answer in cases where multiple answers are correct
- Timeout
- Automatically compile .java and .cpp file

```
$ ccc-marker --help
Usage: ccc-marker [-hV] [-e=<verifyCommand>] [-t=<timeout>] <testDataDirectory>
                  <program>
      <testDataDirectory>   The directory containing .in and .out files used to
                              test the program.
      <program>             Either the command to run the program or the source
                              code file (supports .py, .cpp and .java).
  -e, --verify=<verifyCommand>
                            The command used to verify the program output, it
                              will be called as <command> <input>
                              <expectedOutput> <output>, a non-zero exit code
                              indicates the program output is wrong.
  -h, --help                Show this help message and exit.
  -t, --timeout=<timeout>   Timeout of each test, in milliseconds.
                              Default: 2000
  -V, --version             Print version information and exit.
```

## Example output

```
$ ccc-marker all_data/s1_j4/ S1.java

==== Compiling =====================================================================================

Compiling S1.java.....
shell$ javac -d /home/pegasis/Projects/Algorithms/CompetitonPractise/src/CCC/c19/.ccc_marker_temp_1605121375899 /home/pegasis/Projects/Algorithms/CompetitonPractise/src/CCC/c19/S1.java
       Process finished with exit code 0.
Package name is "CCC.c19".

==== Testing =======================================================================================

[1/19] Running test case j4.sample01.....  47ms
[2/19] Running test case j4.sample02.....  47ms
[3/19] Running test case j4.01.....  47ms
[4/19] Running test case j4.02.....  46ms
[5/19] Running test case j4.05.....  50ms
[6/19] Running test case j4.06.....  48ms
[7/19] Running test case j4.07.....  51ms
[8/19] Running test case j4.08.....  51ms
[9/19] Running test case j4.09.....  55ms
[10/19] Running test case j4.10.....  53ms
[11/19] Running test case j4.11.....  49ms
[12/19] Running test case j4.12.....  51ms
[13/19] Running test case j4.13.....  51ms
[14/19] Running test case j4.14.....  106ms
[15/19] Running test case j4.15.....  104ms
[16/19] Running test case j4.16.....  106ms
[17/19] Running test case j4.17.....  102ms
[18/19] Running test case j4.18.....  107ms
[19/19] Running test case j4.19.....  102ms

==== Accepted ======================================================================================

Program: S1.java
Test cases: all_data/s1_j4
Average: 67ms
Min: 46ms
Max: 107ms
```
