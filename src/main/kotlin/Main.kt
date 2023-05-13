fun Question.asConsoleString(): String {
    val variants = this.variants
        .mapIndexed { index: Int, word: Word -> "${index + 1} - ${word.translate}" }
        .joinToString(separator = "\n")
    return this.correctAnswer.original + "\n" + variants + "\n0 - выйти в главное меню"
}

fun main() {

    val trainer = try {
        LearnWordsTrainer(learnedAnswerCount = 3, countOfQuestionWords =  4)
    } catch (e: Exception) {
        println("Невозможно загрузить словарь")
        return
    }

    while (true) {
        println("Меню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        when (readln().toIntOrNull()) {
            1 -> {
                while (true) {
                    val question = trainer.getNextQuestion()
                    if (question == null) {
                        println("Вы выучили все слова")
                        break
                    } else {
                        println(question.asConsoleString())

                        val userAnswerInput = readln().toIntOrNull()
                        if (userAnswerInput == 0) break

                        if (trainer.checkAnswer(userAnswerInput?.minus(1))) {
                            println("Верно!")
                        } else println(
                            "Не верно. Перевод слова ${question.correctAnswer.translate} - " +
                                    question.correctAnswer.original
                        )
                    }
                }
            }

            2 -> {
                val statistics = trainer.getStatistics()
                println(
                    "Выучено ${statistics.countLearnedWords} из ${statistics.countAllWords} слов | " +
                            "${statistics.learnedPercent}%"
                )
            }

            0 -> break
            else -> println("Введите номер пункта меню")
        }
    }
}