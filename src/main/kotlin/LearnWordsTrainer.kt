import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
    val groupAlphabet: Int,
    var audio: String? = null,
)

data class Statistics(
    val countAllWords: Int,
    val countLearnedWords: Int,
    val learnedPercent: Int,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
)

class LearnWordsTrainer(
    private val fileName: String = "words.txt",
    val learnedAnswerCount: Int = 3,
    val countOfQuestionWords: Int = 4,
) {

    var question: Question? = null
    private val dictionary = loadDictionary()

    fun getStatistics(): Statistics {
        val countLearnedWords = dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }.size
        val countAllWords = dictionary.size
        val learnedPercent = 100 * countLearnedWords / countAllWords
        return Statistics(countAllWords, countLearnedWords, learnedPercent)
    }

    fun getNextQuestion(step: Int): Question? {
        val wordsInStep = dictionary.filter { it.groupAlphabet == step }
        val notLearnedWords = wordsInStep.filter { it.correctAnswersCount < learnedAnswerCount }
        if (notLearnedWords.isEmpty()) return null
        val questionWords = if (notLearnedWords.size < countOfQuestionWords) {
            val learnedWords = dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }.shuffled()
            notLearnedWords.shuffled().take(countOfQuestionWords) +
                    learnedWords.take(countOfQuestionWords - notLearnedWords.size)
        } else {
            notLearnedWords.shuffled().take(countOfQuestionWords)
        }.shuffled()
        var correctAnswer = questionWords.random()
        while (correctAnswer.correctAnswersCount > 2) {
            correctAnswer = questionWords.random()
        }
        question = Question(
            variants = questionWords,
            correctAnswer = correctAnswer,
        )
        println(correctAnswer)
        return question
    }

    fun checkAnswer(userAnswerIndex: Int?): Boolean {
        return question?.let {
            val correctAnswerId = it.variants.indexOf(it.correctAnswer)
            if (correctAnswerId == userAnswerIndex) {
                it.correctAnswer.correctAnswersCount++
                saveDictionary()
                true
            } else false
        } ?: false
    }

    fun saveAudio(){
        saveDictionary()
    }

    fun getListOfWordsForAudioRecord(step: Int): List<Word> {
        return dictionary.filter { it.groupAlphabet == step }
    }

    private fun loadDictionary(): List<Word> {
        try {
            val wordsFile: File = File(fileName)
            if (!wordsFile.exists()) {
                File("words.txt").copyTo(wordsFile)
            }
            val dictionary: MutableList<Word> = mutableListOf()
            wordsFile.readLines().forEach {
                val line = it.split("|")
                val lastItem = if (line.size > 4) line[4] else ""
                dictionary.add(
                    Word(
                        line[0],
                        line[1],
                        line[2].toIntOrNull() ?: 0,
                        line[3].toInt(),
                        lastItem
                    )
                )
            }
            return dictionary
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalStateException("Некорректный файл")
        }
    }

    private fun saveDictionary() {
        val wordsFile: File = File(fileName)
        wordsFile.writeText("")
        dictionary.forEach {
            wordsFile.appendText(
                "${it.original}|${it.translate}|${it.correctAnswersCount}|${it.groupAlphabet}|${it.audio}\n"
            )
        }
    }

    fun resetProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }
}