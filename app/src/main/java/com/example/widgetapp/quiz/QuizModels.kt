package com.example.widgetapp.quiz

enum class Subject(val label: String) {
    MATH("数学"),
    CHINESE("语文"),
    ENGLISH("英语"),
}

enum class AnswerType { CHOICES, TEXT }

data class QuizQuestion(
    val id: Long,
    val subject: Subject,
    val prompt: String,
    val answerType: AnswerType,
    val choices: List<String> = emptyList(),
    val correctAnswer: String,
    val explanation: String,
)

data class AnswerResult(
    val isCorrect: Boolean,
    val title: String,
    val explanation: String,
)

data class QuizUiState(
    val question: QuizQuestion? = null,
    val userAnswer: String = "",
    val isLoading: Boolean = true,
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
    val result: AnswerResult? = null,
    val isNextReady: Boolean = false,
)
