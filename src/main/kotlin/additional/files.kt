package additional

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
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
            )
        )
    }

    var countLearnedWords: Int
    var countAllWords: Int
    var dictionaryNotLearnedWords: List<Word>
    var dictionaryForUserChoice: List<Word>
    var rightAnswer: Word
    var userChoice: Int?

    do {
        println("Меню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        startMenu = readln()
        when (startMenu) {
            "1" -> {
                while (true) {
                    dictionaryNotLearnedWords = dictionary.filter {
                        it.correctAnswersCount < 3
                    }
                    if (dictionaryNotLearnedWords.isEmpty()) {
                        println("Вы выучили все слова")
                        break
                    }

                    println()
                    println("Выберете вариант ответа от 1 до 4. Или нажмите 0 для возврата в главное меню.")
                    dictionaryForUserChoice = dictionaryNotLearnedWords.shuffled().take(NUMBER_OF_WORDS_CHOICE)
                    for (i in 0..3) {
                        println("${i + 1}: ${dictionaryForUserChoice[i].original}")
                    }
                    rightAnswer = dictionaryForUserChoice.shuffled().take(1)[0]
                    println("Найди перевод слова: ${rightAnswer.translate}")

                    userChoice = readln().toInt() ?: 0

                    when (userChoice) {
                        0 -> break
                        in 1..NUMBER_OF_WORDS_CHOICE -> {
                            if (rightAnswer == dictionaryForUserChoice[userChoice - 1]) {
                                println(
                                    "Верно! Перевод слова ${rightAnswer.translate} - " +
                                            dictionaryForUserChoice[userChoice - 1].original
                                )
                                rightAnswer.correctAnswersCount += 1
                                saveDictionary(dictionary, wordsFile)

                            } else println("Не верно.")
                        }

                        else -> println("Не верный номер")
                    }
                }
            }

            "2" -> {
                countAllWords = dictionary.size
                countLearnedWords = dictionary.filter {
                    it.correctAnswersCount >= 3
                }.size

                println(
                    "Выучено $countLearnedWords из $countAllWords слов | " +
                            "${100 * countLearnedWords / countAllWords}%"
                )
            }

            "0" -> println("Выход")
            else -> println("Введите номер пункта меню")
        }
    } while (startMenu != "0")
}

fun saveDictionary(dictionary: List<Word>, wordsFile: File) {
    wordsFile.writeText("")
    dictionary.forEach {
        wordsFile.appendText(
            "${it.original}|${it.translate}|${it.correctAnswersCount}\n"
        )
    }
}

val NUMBER_OF_WORDS_CHOICE = 4