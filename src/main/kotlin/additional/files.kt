package additional

import java.io.File

fun main() {

    val wordsFile: File = File("words.txt")
    val arrayTextFromFile = wordsFile.readLines()

//    wordsFile.createNewFile()
//    wordsFile.writeText("COBa was here")
//    wordsFile.appendText("Ill be back")

//    println(wordsFile.readLines())

    arrayTextFromFile.forEach{
        println(it)
    }

}