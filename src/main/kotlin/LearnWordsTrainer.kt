import java.io.File

data class Statistics(
    var countAllWords: Int,
    var countLearnedWords: Int,
    val learnedPercent: Int,
)

data class Question(
    val variants: List<Word>,
    var correctAnswer: Word,
)

class LearnWordsTrainer {

    private var question: Question? = null
    private val dictionary = loadDictionary()

    fun getStatistics(): Statistics {
        val countLearnedWords = dictionary.filter { it.correctAnswersCount >= 3 }.size
        val countAllWords = dictionary.size
        val learnedPercent = 100 * countLearnedWords / countAllWords
        return Statistics(countAllWords, countLearnedWords, learnedPercent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedWords = dictionary.filter { it.correctAnswersCount < 3 }
        if (notLearnedWords.isEmpty()) return null
        val questionWords = notLearnedWords.shuffled().take(NUMBER_OF_WORDS_CHOICE)
        val correctAnswer = questionWords.random()
        question = Question(
            variants = questionWords,
            correctAnswer = correctAnswer,
        )
        return question
    }

    fun checkAnswer(userAnswerIndex: Int?): Boolean {
        return question?.let {
            val correctAnswerId = it.variants.indexOf(it.correctAnswer)
            if (correctAnswerId == userAnswerIndex) {
                it.correctAnswer.correctAnswersCount++
                saveDictionary(dictionary)
                true
            } else false
        } ?: false
    }

    private fun loadDictionary(): List<Word> {
        val wordsFile: File = File("words.txt")
        val dictionary: MutableList<Word> = mutableListOf()
        wordsFile.readLines().forEach {
            val line = it.split("|")
            dictionary.add(Word(line[0], line[1], line[2].toIntOrNull() ?: 0))
        }
        return dictionary
    }

    private fun saveDictionary(dictionary: List<Word>) {
        val wordsFile: File = File("words.txt")
        wordsFile.writeText("")
        dictionary.forEach {
            wordsFile.appendText(
                "${it.original}|${it.translate}|${it.correctAnswersCount}\n"
            )
        }
    }
}