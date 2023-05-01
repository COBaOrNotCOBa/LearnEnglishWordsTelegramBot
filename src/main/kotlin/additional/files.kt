package additional

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
    var learnedWord: Int,
)

fun main() {

    var startMenu: String
    val wordsFile: File = File("words.txt")
    val dictionary: MutableList<Word> = mutableListOf()

    val listTextFromFile = wordsFile.readLines()
    listTextFromFile.forEach {
        val line = it.split("|")
        dictionary.add(
            Word(
                original = line[0],
                translate = line[1],
                correctAnswersCount = line[2].toInt(),
                learnedWord = line[3].toInt(),
            )
        )
    }

    var countLearnedWords: Int
    var countAllWords: Int
    var dictionaryNotLearned: List<Word>
    var userChoice: String = ""
    do {
        println("Меню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        startMenu = readln()
        when (startMenu) {
            "1" -> {
                while (userChoice != "0") {
                    dictionaryNotLearned = dictionary.filter {
                        it.correctAnswersCount < 3
                    }
                    if (dictionaryNotLearned.isEmpty()) {
                        println("Вы выучили все слова")
                        break
                    }

                    dictionaryNotLearned = dictionaryNotLearned.shuffled().take(4)
                    for (i in 0..3) {
                        println("${i + 1}: ${dictionaryNotLearned[i].original}")
                    }
                    dictionaryNotLearned = dictionaryNotLearned.shuffled().take(1)

                    println("Найди правильный перевод слова: ${dictionaryNotLearned[0].translate}")

                    println("Выберете вариант ответа от 1 до 4. Или нажмите 0 для возврата в главное меню")
                    userChoice = readln()
                }
            }

            "2" -> {
                countAllWords = dictionary.size
                countLearnedWords = dictionary.filter {
                    it.correctAnswersCount >= 3
                }.size

                println("Выучено $countLearnedWords из $countAllWords слов | ${100 * countLearnedWords / countAllWords}%")
            }

            "0" -> println("Выход")
            else -> println("Введите номер пункта меню")
        }
    } while (startMenu != "0")
}