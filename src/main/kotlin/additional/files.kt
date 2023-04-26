package additional

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
    var learnedWord: Int,
)

fun main() {

    val wordsFile: File = File("words.txt")
    var dictionary: Word

//    wordsFile.createNewFile()
//    wordsFile.writeText("COBa was here")
//    wordsFile.appendText("Ill be back")
//    println(wordsFile.readLines())

    val listTextFromFile = wordsFile.readLines()
    listTextFromFile.forEach {
        val line = it.split("|")
        dictionary = Word(
            original = line[0],
            translate = line[1],
            correctAnswersCount = line[2].toInt(),
            learnedWord = line[3].toInt(),
        )
        println(dictionary)
    }

}